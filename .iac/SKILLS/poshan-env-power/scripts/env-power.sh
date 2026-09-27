#!/usr/bin/env bash
# Start / stop / status for one Poshan backend environment's compute.
#
#   env-power.sh status <dev|qa|prod>
#   env-power.sh stop   <dev|qa|prod>   # ECS service -> 0 tasks, ASG -> 0 instances
#   env-power.sh start  <dev|qa|prod>   # ASG -> 1 instance, ECS service -> 1 task
#
# Account, profile and region come from that env's terraform.tfvars
# (allowed_account_ids / aws_profile / aws_region), and the script refuses to
# run if the current credentials resolve to any other account.
#
# Only EC2 compute is stopped. The ALB, its public IPs, ECR, logs and IAM stay
# up (and the ALB keeps billing) — removing those is a `terraform destroy`,
# not a stop. Terraform ignores the counts changed here (see
# modules/ecs-ec2-app/ecs.tf), so an apply won't undo a stop.
#
# Written for macOS's bash 3.2: no associative arrays or mapfile.

set -euo pipefail

die() { echo "ERROR: $*" >&2; exit 1; }
log() { echo "==> $*"; }

[ $# -eq 2 ] || die "usage: $(basename "$0") <start|stop|status> <dev|qa|prod>"
ACTION=$1
ENV=$2

case "$ACTION" in start | stop | status) ;; *) die "action must be start, stop or status (got '$ACTION')" ;; esac
case "$ENV" in
  dev | qa | prod) ;;
  local) die "'local' runs on your machine (docker compose), it has no AWS stack to start/stop" ;;
  *) die "environment must be dev, qa or prod (got '$ENV')" ;;
esac

IAC_DIR=$(cd "$(dirname "$0")/../../.." && pwd)
TFVARS="$IAC_DIR/environments/$ENV/backend/terraform.tfvars"
[ -f "$TFVARS" ] || die "$TFVARS not found — '$ENV' backend isn't set up yet"

tfvar() { sed -n "s/^[[:space:]]*$1[[:space:]]*=[[:space:]]*\"\([^\"]*\)\".*/\1/p" "$TFVARS" | head -1; }
REGION=$(tfvar aws_region)
REGION=${REGION:-ap-south-1}
PROFILE=$(tfvar aws_profile)
ACCOUNT=$(sed -n 's/^[[:space:]]*allowed_account_ids[[:space:]]*=[[:space:]]*\["\([0-9]*\)".*/\1/p' "$TFVARS" | head -1)
[ -n "$ACCOUNT" ] || die "allowed_account_ids is not set in $TFVARS — refusing to run without an account guard"

AWS=(aws --region "$REGION" --output text)
[ -n "$PROFILE" ] && AWS+=(--profile "$PROFILE")

ACTUAL=$("${AWS[@]}" sts get-caller-identity --query Account) || die "AWS credentials not usable${PROFILE:+ for profile '$PROFILE'}"
[ "$ACTUAL" = "$ACCOUNT" ] || die "credentials resolve to account $ACTUAL, but $ENV must run in $ACCOUNT"

APP="poshan-backend-$ENV"
CLUSTER="$APP-cluster"
SERVICE="$APP-service"
ASG="$APP-ecs-asg"

svc_field() { "${AWS[@]}" ecs describe-services --cluster "$CLUSTER" --services "$SERVICE" --query "services[0].$1"; }
asg_field() { "${AWS[@]}" autoscaling describe-auto-scaling-groups --auto-scaling-group-names "$ASG" --query "AutoScalingGroups[0].$1"; }
in_service() { "${AWS[@]}" autoscaling describe-auto-scaling-groups --auto-scaling-group-names "$ASG" --query "length(AutoScalingGroups[0].Instances[?LifecycleState=='InService'])"; }
# Every instance the ASG still holds, in any state. ECS adds a managed-draining
# lifecycle hook, so a stopped instance sits in Terminating:Wait (still running,
# still billed) until ECS finishes draining it — "not InService" isn't "gone".
asg_instances() { "${AWS[@]}" autoscaling describe-auto-scaling-groups --auto-scaling-group-names "$ASG" --query 'length(AutoScalingGroups[0].Instances)'; }
# ACTIVE + agent connected only: a terminating host stays counted as registered
# (DRAINING) for a while, which must not satisfy "the new host has joined".
registered() {
  local arns
  arns=$("${AWS[@]}" ecs list-container-instances --cluster "$CLUSTER" --status ACTIVE --query 'containerInstanceArns')
  if [ -z "$arns" ] || [ "$arns" = "None" ]; then echo 0; return; fi
  # shellcheck disable=SC2086 # arns is a whitespace-separated ARN list on purpose
  "${AWS[@]}" ecs describe-container-instances --cluster "$CLUSTER" --container-instances $arns \
    --query 'length(containerInstances[?agentConnected==`true`])'
}

# Poll until "$1" (a command) prints "$2", or give up after $3 seconds.
wait_for() {
  local cmd=$1 want=$2 timeout=$3 waited=0 got
  while :; do
    got=$($cmd)
    [ "$got" = "$want" ] && return 0
    [ "$waited" -ge "$timeout" ] && { echo "   (still '$got' after ${timeout}s, wanted '$want')"; return 1; }
    sleep 15
    waited=$((waited + 15))
    echo "   ...$cmd = $got (want $want, ${waited}s)"
  done
}

status() {
  local svc_status
  svc_status=$(svc_field status 2>/dev/null || true)
  [ "$svc_status" = "ACTIVE" ] || die "service $SERVICE not found in $CLUSTER — has '$ENV' backend been applied?"
  echo "Environment : $ENV  (account $ACCOUNT, $REGION${PROFILE:+, profile $PROFILE})"
  echo "ECS service : desired=$(svc_field desiredCount) running=$(svc_field runningCount) pending=$(svc_field pendingCount)"
  echo "ASG         : min=$(asg_field MinSize) max=$(asg_field MaxSize) desired=$(asg_field DesiredCapacity) in-service=$(in_service) total-incl-terminating=$(asg_instances)"
  echo "Cluster     : active container instances=$(registered)"
  local tg health
  tg=$("${AWS[@]}" elbv2 describe-target-groups --names "$APP-tg" --query 'TargetGroups[0].TargetGroupArn' 2>/dev/null || true)
  if [ -n "$tg" ] && [ "$tg" != "None" ]; then
    health=$("${AWS[@]}" elbv2 describe-target-health --target-group-arn "$tg" --query 'TargetHealthDescriptions[].TargetHealth.State' | tr '\t' ' ')
    echo "ALB targets : ${health:-none}"
  fi
  local dns
  dns=$("${AWS[@]}" elbv2 describe-load-balancers --names "$APP-alb" --query 'LoadBalancers[0].DNSName' 2>/dev/null || true)
  [ -n "$dns" ] && echo "URL         : http://$dns/api/v1/health"
}

stop() {
  log "Scaling ECS service $SERVICE to 0 tasks"
  "${AWS[@]}" ecs update-service --cluster "$CLUSTER" --service "$SERVICE" --desired-count 0 --query 'service.desiredCount' >/dev/null
  wait_for "svc_field runningCount" 0 300 || die "tasks did not stop; nothing else changed"

  log "Scaling ASG $ASG to 0 instances (max stays at $(asg_field MaxSize))"
  "${AWS[@]}" autoscaling update-auto-scaling-group --auto-scaling-group-name "$ASG" --min-size 0 --desired-capacity 0
  # ECS managed draining can hold the instance for a few minutes after its
  # tasks are gone; it's only off the bill once the ASG no longer lists it.
  wait_for asg_instances 0 900 || echo "WARNING: instance still draining/terminating (still billed) — re-run 'status' in a few minutes"
  log "Stopped. EC2 compute (instance, its public IP, root EBS) no longer bills; the ALB still does."
}

start() {
  log "Scaling ASG $ASG to 1 instance"
  "${AWS[@]}" autoscaling update-auto-scaling-group --auto-scaling-group-name "$ASG" --min-size 1 --max-size 1 --desired-capacity 1
  wait_for in_service 1 600 || die "instance did not reach InService"
  log "Waiting for the instance to register with $CLUSTER"
  wait_for registered 1 600 || die "instance is up but never joined the cluster — check the ECS agent (SSM into the host)"

  log "Scaling ECS service $SERVICE to 1 task"
  "${AWS[@]}" ecs update-service --cluster "$CLUSTER" --service "$SERVICE" --desired-count 1 --query 'service.desiredCount' >/dev/null
  wait_for "svc_field runningCount" 1 600 || echo "WARNING: task not running yet — check 'status' and /ecs/$APP logs"
  log "Started. The ALB health check needs ~1-2 more minutes to turn healthy."
}

case "$ACTION" in
  status) status ;;
  stop) status; echo; stop; echo; status ;;
  start) status; echo; start; echo; status ;;
esac
