#!/usr/bin/env bash
# Poshan for Life — start / stop the AWS environments (backend API + staff portal).
#
#   ./poshan-env.sh status <dev|qa|prod>   # is it running? are the URLs healthy?
#   ./poshan-env.sh start  <dev|qa|prod>   # bring it up            (~5-10 min)
#   ./poshan-env.sh stop   <dev|qa|prod>   # shut it down to save   (~5-15 min)
#   ./poshan-env.sh kill   <dev|qa|prod>   # force-stop a frozen/stuck env (~1-3 min)
#
# kill (any env) and every start/stop on prod ask you to type the env name
# back; add --yes as a third argument to skip that.
#
# Needs: bash, AWS CLI v2, curl, and AWS credentials for the Poshan account
# (set AWS_PROFILE if they're not your default profile). See README.md.
#
# Standalone: everything it needs is in the CONFIG block below — no repo or
# Terraform checkout required. Only EC2 compute is stopped; the load balancer
# and its IPs keep billing while an env is stopped.
#
# Written for macOS's bash 3.2: no associative arrays or mapfile.

set -euo pipefail

# ---------------------------------------------------------------- CONFIG ----
REGION=ap-south-1

# AWS account each env must run in (the script refuses any other account).
env_account() {
  case "$1" in
    dev | qa | prod) echo 046369253799 ;;
  esac
}

# Public URLs per env: API health check, portal.
env_api_url() {
  case "$1" in
    dev) echo https://dev.api.poshanforlife.com/api/v1/health ;;
    qa) echo https://qa.api.poshanforlife.com/api/v1/health ;;
    prod) echo https://api.poshanforlife.com/api/v1/health ;;
  esac
}
env_portal_url() {
  case "$1" in
    dev) echo https://dev.portal.poshanforlife.com/ ;;
    qa) echo https://qa.portal.poshanforlife.com/ ;;
    prod) echo https://portal.poshanforlife.com/ ;;
  esac
}
# ------------------------------------------------------------------------------

die() { echo "ERROR: $*" >&2; exit 1; }
log() { echo "==> $*"; }

usage() { sed -n '2,13p' "$0" | sed 's/^# \{0,1\}//'; }

if [ $# -eq 0 ] || [ "$1" = "help" ] || [ "$1" = "-h" ] || [ "$1" = "--help" ]; then
  usage
  exit 0
fi
[ $# -eq 2 ] || [ $# -eq 3 ] || { usage; exit 1; }
ACTION=$1
ENV=$2
YES=false
if [ $# -eq 3 ]; then
  case "$3" in --yes | -y) YES=true ;; *) die "third argument can only be --yes (got '$3')" ;; esac
fi

case "$ACTION" in start | stop | kill | status) ;; *) die "action must be start, stop, kill or status (got '$ACTION')" ;; esac
case "$ENV" in
  dev | qa | prod) ;;
  local) die "'local' runs on your own machine, there is no AWS environment to start/stop" ;;
  *) die "environment must be dev, qa or prod (got '$ENV')" ;;
esac

command -v aws >/dev/null 2>&1 || die "AWS CLI not found — install v2: https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html"
command -v curl >/dev/null 2>&1 || die "curl not found"

ACCOUNT=$(env_account "$ENV")
AWS=(aws --region "$REGION" --output text)

ACTUAL=$("${AWS[@]}" sts get-caller-identity --query Account 2>/dev/null) ||
  die "no usable AWS credentials${AWS_PROFILE:+ for profile '$AWS_PROFILE'} — run 'aws configure' (see README.md)"
[ "$ACTUAL" = "$ACCOUNT" ] ||
  die "your credentials are for account $ACTUAL, but $ENV lives in $ACCOUNT — set AWS_PROFILE to the Poshan profile"

APP="poshan-backend-$ENV"
CLUSTER="$APP-cluster"
SERVICE="$APP-service"
FE_SERVICE="poshan-frontend-$ENV-service"
ASG="$APP-ecs-asg"

svc_field() { "${AWS[@]}" ecs describe-services --cluster "$CLUSTER" --services "$SERVICE" --query "services[0].$1"; }
# The portal is a second service on the same cluster and host; skipped if absent.
fe_field() { "${AWS[@]}" ecs describe-services --cluster "$CLUSTER" --services "$FE_SERVICE" --query "services[0].$1" 2>/dev/null; }
has_frontend() { [ "$(fe_field status)" = "ACTIVE" ]; }
asg_field() { "${AWS[@]}" autoscaling describe-auto-scaling-groups --auto-scaling-group-names "$ASG" --query "AutoScalingGroups[0].$1"; }
in_service() { "${AWS[@]}" autoscaling describe-auto-scaling-groups --auto-scaling-group-names "$ASG" --query "length(AutoScalingGroups[0].Instances[?LifecycleState=='InService'])"; }
# Every instance the ASG still holds, in any state. ECS adds a managed-draining
# lifecycle hook, so a stopping instance sits in Terminating:Wait (still running,
# still billed) until ECS finishes draining it — "not InService" isn't "gone".
asg_instances() { "${AWS[@]}" autoscaling describe-auto-scaling-groups --auto-scaling-group-names "$ASG" --query 'length(AutoScalingGroups[0].Instances)'; }
# ACTIVE + agent connected only: a terminating host stays registered (DRAINING)
# for a while, which must not count as "the new host has joined".
registered() {
  local arns
  arns=$("${AWS[@]}" ecs list-container-instances --cluster "$CLUSTER" --status ACTIVE --query 'containerInstanceArns')
  if [ -z "$arns" ] || [ "$arns" = "None" ]; then echo 0; return; fi
  # shellcheck disable=SC2086 # arns is a whitespace-separated ARN list on purpose
  "${AWS[@]}" ecs describe-container-instances --cluster "$CLUSTER" --container-instances $arns \
    --query 'length(containerInstances[?agentConnected==`true`])'
}
http_code() { curl -s -o /dev/null -m 10 -w '%{http_code}' "$1" 2>/dev/null || true; }
api_up() { http_code "$(env_api_url "$ENV")"; }

# Poll until "$1" (a command) prints "$2", or give up after $3 seconds.
wait_for() {
  local cmd=$1 want=$2 timeout=$3 waited=0 got
  while :; do
    got=$($cmd)
    [ "$got" = "$want" ] && return 0
    [ "$waited" -ge "$timeout" ] && { echo "   (still '$got' after ${timeout}s, wanted '$want')"; return 1; }
    sleep 15
    waited=$((waited + 15))
    echo "   ...waiting: $cmd = $got (want $want, ${waited}s)"
  done
}

confirm() {
  [ "$ACTION" = "status" ] && return 0
  [ "$ACTION" = "kill" ] || [ "$ENV" = "prod" ] || return 0
  $YES && return 0
  [ -t 0 ] || die "'$ACTION $ENV' needs confirmation: run it in a terminal, or add --yes"
  local what="$ACTION $ENV"
  [ "$ACTION" = "kill" ] && what="FORCE-STOP $ENV (anyone using it is cut off immediately)"
  [ "$ENV" = "prod" ] && what="$what — PRODUCTION, real users"
  printf '%s\nType "%s" to continue: ' "$what" "$ENV"
  local answer
  read -r answer
  [ "$answer" = "$ENV" ] || die "not confirmed, nothing changed"
}

status() {
  local svc_status
  svc_status=$(svc_field status 2>/dev/null || true)
  [ "$svc_status" = "ACTIVE" ] || die "'$ENV' is not deployed on AWS yet (no service $SERVICE)"
  local desired running
  desired=$(asg_field DesiredCapacity)
  running=$(asg_instances)
  echo "Environment : $ENV  (account $ACCOUNT, $REGION)"
  if [ "$desired" = "0" ] && [ "$running" = "0" ]; then
    echo "State       : STOPPED"
  elif [ "$desired" = "0" ]; then
    echo "State       : STOPPING (server still shutting down, still billed)"
  else
    echo "State       : RUNNING (or starting)"
  fi
  echo "Backend API : desired=$(svc_field desiredCount) running=$(svc_field runningCount)"
  has_frontend && echo "Portal      : desired=$(fe_field desiredCount) running=$(fe_field runningCount)"
  echo "Server      : desired=$desired in-service=$(in_service) total-incl-shutting-down=$running"
  echo "API URL     : $(env_api_url "$ENV")  -> HTTP $(api_up)"
  echo "Portal URL  : $(env_portal_url "$ENV")  -> HTTP $(http_code "$(env_portal_url "$ENV")")"
}

stop() {
  if has_frontend; then
    log "Stopping the portal"
    "${AWS[@]}" ecs update-service --cluster "$CLUSTER" --service "$FE_SERVICE" --desired-count 0 --query 'service.desiredCount' >/dev/null
    wait_for "fe_field runningCount" 0 300 || die "portal did not stop; nothing else changed"
  fi
  log "Stopping the backend API"
  "${AWS[@]}" ecs update-service --cluster "$CLUSTER" --service "$SERVICE" --desired-count 0 --query 'service.desiredCount' >/dev/null
  wait_for "svc_field runningCount" 0 300 || die "API did not stop; nothing else changed"

  log "Shutting down the server"
  "${AWS[@]}" autoscaling update-auto-scaling-group --auto-scaling-group-name "$ASG" --min-size 0 --desired-capacity 0
  wait_for asg_instances 0 900 || echo "WARNING: server still shutting down (still billed) — run 'status' again in a few minutes, or 'kill' if it's stuck"
  log "Stopped."
}

# Same end state as stop, without waiting for anything to drain. ECS parks the
# host in Terminating:Wait until its tasks are gone, which never happens on a
# frozen host, so this releases the lifecycle hook itself.
kill_env() {
  log "FORCE-STOP $ENV"
  has_frontend && "${AWS[@]}" ecs update-service --cluster "$CLUSTER" --service "$FE_SERVICE" --desired-count 0 --query 'service.desiredCount' >/dev/null
  "${AWS[@]}" ecs update-service --cluster "$CLUSTER" --service "$SERVICE" --desired-count 0 --query 'service.desiredCount' >/dev/null
  "${AWS[@]}" autoscaling update-auto-scaling-group --auto-scaling-group-name "$ASG" --min-size 0 --desired-capacity 0

  local hook waited=0 ids id
  hook=$("${AWS[@]}" autoscaling describe-lifecycle-hooks --auto-scaling-group-name "$ASG" \
    --query "LifecycleHooks[?LifecycleTransition=='autoscaling:EC2_INSTANCE_TERMINATING'].LifecycleHookName | [0]")
  while [ "$(asg_instances)" != "0" ]; do
    if [ -n "$hook" ] && [ "$hook" != "None" ]; then
      ids=$("${AWS[@]}" autoscaling describe-auto-scaling-groups --auto-scaling-group-names "$ASG" \
        --query "AutoScalingGroups[0].Instances[?LifecycleState=='Terminating:Wait'].InstanceId")
      for id in $ids; do
        [ "$id" = "None" ] && continue
        "${AWS[@]}" autoscaling complete-lifecycle-action --auto-scaling-group-name "$ASG" \
          --lifecycle-hook-name "$hook" --instance-id "$id" --lifecycle-action-result CONTINUE >/dev/null 2>&1 || true
      done
    fi
    [ "$waited" -ge 600 ] && { echo "WARNING: server still terminating after 10 min — run 'status' again shortly"; return 0; }
    sleep 15
    waited=$((waited + 15))
    echo "   ...servers left: $(asg_instances) (${waited}s)"
  done
  log "Force-stopped. 'start' brings it back."
}

start() {
  log "Starting the server"
  "${AWS[@]}" autoscaling update-auto-scaling-group --auto-scaling-group-name "$ASG" --min-size 1 --max-size 1 --desired-capacity 1
  wait_for in_service 1 600 || die "server did not come up"
  log "Waiting for the server to join the cluster"
  wait_for registered 1 600 || die "server is up but never joined the cluster — ask the infra owner"

  log "Starting the backend API"
  "${AWS[@]}" ecs update-service --cluster "$CLUSTER" --service "$SERVICE" --desired-count 1 --query 'service.desiredCount' >/dev/null
  wait_for "svc_field runningCount" 1 600 || echo "WARNING: API not running yet"

  if has_frontend; then
    log "Starting the portal"
    "${AWS[@]}" ecs update-service --cluster "$CLUSTER" --service "$FE_SERVICE" --desired-count 1 --query 'service.desiredCount' >/dev/null
    wait_for "fe_field runningCount" 1 300 || echo "WARNING: portal not running yet"
  fi

  log "Waiting for the API to answer (Spring Boot takes ~1-2 min)"
  if wait_for api_up 200 420; then
    log "Started. API: $(env_api_url "$ENV")  Portal: $(env_portal_url "$ENV")"
  else
    echo "WARNING: API is not healthy yet. Most common cause on dev: the Supabase database"
    echo "         paused after a week idle — resume it in the Supabase dashboard, then wait."
  fi
}

confirm
case "$ACTION" in
  status) status ;;
  stop) status; echo; stop; echo; status ;;
  start) status; echo; start; echo; status ;;
  kill) status; echo; kill_env; echo; status ;;
esac
