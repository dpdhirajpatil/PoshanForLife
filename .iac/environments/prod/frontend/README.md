# environments/prod/frontend — placeholder

Not built yet. When prod is built, copy `environments/dev/frontend/main.tf`
(same `modules/ecs-static-site`), pointing at `prod/backend` state, app name
`poshan-frontend-prod`, host `ui.poshanforlife.com`, GitHub Environment `prod`.
The deploy workflow (`.github/workflows/frontend-deploy-prod.yml`) already
exists, locked to the `prod` branch, and stays inert until the FRONTEND_*
variables are set on the `prod` GitHub Environment.
