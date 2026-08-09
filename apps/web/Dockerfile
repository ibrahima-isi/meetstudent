# syntax=docker/dockerfile:1
FROM node:22-alpine AS build
WORKDIR /app

# Copy manifests first so dependency install stays cached across source edits.
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci

COPY . .
RUN npm run build

FROM node:22-alpine AS runtime
WORKDIR /app
ENV NODE_ENV=production

# The SSR build is self-contained: dist/frontend holds both the browser assets
# and the server bundle, with dependencies already bundled in.
COPY --from=build /app/dist/frontend ./dist/frontend

# Run unprivileged; the node image ships a `node` user for this.
USER node

ENV PORT=4200
EXPOSE 4200
CMD ["node", "dist/frontend/server/server.mjs"]
