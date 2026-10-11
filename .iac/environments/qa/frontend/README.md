# environments/qa/frontend — placeholder

Not built yet. When qa is built, copy `environments/dev/frontend/main.tf`
(same `modules/ecs-static-site`), pointing at `qa/backend` state, app name
`poshan-frontend-qa`, host `qa.ui.poshanforlife.com`, GitHub Environment `qa`.
The deploy workflow (`.github/workflows/frontend-deploy-qa.yml`) already
exists, locked to the `qa` branch, and stays inert until the FRONTEND_*
variables are set on the `qa` GitHub Environment.
