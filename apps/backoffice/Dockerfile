# syntax=docker/dockerfile:1
# Backoffice: Angular SPA built to static files, served by unprivileged nginx.
# Requires apps/backoffice/package.json + package-lock.json (the scaffold).
#
# DIST_DIR is the application builder output *relative to /app*. Angular 20
# default: dist/<project-name>/browser. Override with --build-arg DIST_DIR=...
ARG DIST_DIR=dist/backoffice/browser

FROM node:22-alpine AS build
ARG DIST_DIR
WORKDIR /app
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci
COPY . .
RUN npm run build && test -f "${DIST_DIR}/index.html"

FROM nginxinc/nginx-unprivileged:1.28-alpine
ARG DIST_DIR
USER root
COPY docker/nginx.conf /etc/nginx/conf.d/default.conf
COPY --chmod=755 docker/40-write-config.sh /docker-entrypoint.d/40-write-config.sh
COPY --from=build /app/${DIST_DIR}/ /usr/share/nginx/html/
# The entrypoint script writes config.json at start, as the nginx user.
RUN chown -R nginx:nginx /usr/share/nginx/html
USER nginx
EXPOSE 8080
