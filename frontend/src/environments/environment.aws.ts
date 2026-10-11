// Local `ng serve` against the AWS dev backend: `npm run start:aws`.
// Plain HTTP to the load balancer until HTTPS is live; then switch to
// 'https://dev.api.poshanforlife.com/api/v1' (the ALB will redirect HTTP).
export const environment = {
  production: false,
  apiBaseUrl: 'http://poshan-backend-dev-alb-1544740052.ap-south-1.elb.amazonaws.com/api/v1',
};
