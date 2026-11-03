// Local `ng serve` against the AWS dev backend: `npm run start:aws`.
// Must be the HTTPS hostname: the ALB's own DNS name 301s HTTP to HTTPS on a
// host the certificate doesn't cover, and browsers won't follow a redirect on
// a CORS request anyway. The backend's CORS list includes http://localhost:4200.
export const environment = {
  production: false,
  apiBaseUrl: 'https://dev.api.poshanforlife.com/api/v1',
};
