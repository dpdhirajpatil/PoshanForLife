# Poshan for Life: start and stop the AWS environment

Our dev environment (the backend API and the staff portal) runs on AWS, and we pay for it by the hour. Start it when you need it and stop it when you're done.

| Env | API | Portal |
|---|---|---|
| dev | https://dev.api.poshanforlife.com | https://dev.portal.poshanforlife.com |
| qa, prod | not deployed yet | not deployed yet |

## Commands

```bash
./poshan-env.sh status dev     # is it running? are the URLs answering?
./poshan-env.sh start  dev     # bring it up          (~5-10 min)
./poshan-env.sh stop   dev     # shut it down         (~5-15 min)
./poshan-env.sh kill   dev     # force-stop if stop hangs or dev is frozen (~1-3 min)
```

`start` and `stop` handle the API and the portal together, because they run on the same server. `start` finishes only when the API answers, and then prints both URLs.

`kill` cuts off anyone using the environment straight away, so it asks you to type the env name to confirm. Use it only when `stop` is stuck or dev has stopped responding.

## Before you stop it

Someone else may be using dev. **Post in the team chat before you stop it**, and run `status` first.

## What it costs (dev)

| State | Per day | Per month |
|---|---|---|
| Running | ~$1.18 | ~$36 |
| Stopped | ~$0.86 | ~$26 |

Stopping saves about $0.30 a day, because the server shuts down. The load balancer and its IP addresses keep billing even when dev is stopped. Nothing is lost when you stop: the code, the data and the URLs stay the same.

## One-time setup

1. **Install the AWS CLI v2:** https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html
   (macOS: `brew install awscli`)
2. **Get AWS credentials** from Dhiraj (an access key for the `poshan-env-operators` group, which can only start and stop environments). Then run:
   ```bash
   aws configure --profile poshan
   # Access key / secret: as given
   # Default region: ap-south-1
   # Output format: json
   ```
3. **Point the script at that profile** in every terminal where you use it:
   ```bash
   export AWS_PROFILE=poshan
   ```
   To make this permanent, add that line to `~/.zshrc` or `~/.bashrc`.
4. **Make the script executable** (only once): `chmod +x poshan-env.sh`
5. **Check it works:** `./poshan-env.sh status dev`

**Windows:** run the script in WSL or Git Bash. It doesn't run in plain PowerShell or cmd.

## If something goes wrong

| You see | Do this |
|---|---|
| `no usable AWS credentials` | Run `export AWS_PROFILE=poshan`, or redo step 2 of the setup. |
| `credentials are for account …` | You're using another AWS account's profile. Set `AWS_PROFILE=poshan`. |
| `AccessDenied` | Your access key isn't in the operators group. Ask Dhiraj. |
| `start` ends with "API is not healthy yet" | The dev database (Supabase) pauses after about a week without use. Ask Dhiraj to resume it, then run `status` again. |
| `stop` says the server is still shutting down | Wait a few minutes and run `status` again. If it's still stuck, run `kill dev`. |

## For the infra owner: creating the operators group

`iam-policy.json` is the minimum permission set the script needs. It can scale the Poshan services and servers up and down, and nothing else: it can't delete anything or read secrets.

```bash
aws iam create-group --group-name poshan-env-operators
aws iam put-group-policy --group-name poshan-env-operators \
  --policy-name poshan-env-power --policy-document file://iam-policy.json
aws iam create-user --user-name <colleague>
aws iam add-user-to-group --user-name <colleague> --group-name poshan-env-operators
aws iam create-access-key --user-name <colleague>   # send the key over a private channel
```
