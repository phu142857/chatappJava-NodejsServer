# syntax=docker/dockerfile:1.7

# ---------------------------------------------------
# STAGE 1 — BUILD STAGE
# ---------------------------------------------------
FROM node:22-alpine AS build

WORKDIR /app

# Copy dependency files first to leverage Docker layer cache
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

# Copy application source and build static assets
COPY . .
RUN npm run build


# ---------------------------------------------------
# STAGE 2 — RUNTIME STAGE
# ---------------------------------------------------
FROM nginx:1.27-alpine

# Copy nginx configuration
COPY docker/nginx/default.conf /etc/nginx/conf.d/default.conf

# Script to inject runtime environment variables
COPY docker/entrypoint/40-env-config.sh /docker-entrypoint.d/40-env-config.sh

# Copy compiled frontend assets from build stage
COPY --from=build /app/dist /usr/share/nginx/html

# Ensure entrypoint script is executable
RUN chmod +x /docker-entrypoint.d/40-env-config.sh

# Expose HTTP port
EXPOSE 80

# Basic container healthcheck
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget -q -O /dev/null http://127.0.0.1/ || exit 1