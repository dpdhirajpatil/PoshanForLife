# Stage 1: build the Angular app for one environment.
#   docker build --build-arg BUILD_CONFIGURATION=dev -t poshan-frontend .
# BUILD_CONFIGURATION: production (default) | dev | qa — picks the API URL
# from src/environments/environment.<config>.ts.
FROM node:20-alpine AS build
ARG BUILD_CONFIGURATION=production
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npx ng build --configuration "$BUILD_CONFIGURATION"

# Stage 2: serve the static files with nginx.
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
