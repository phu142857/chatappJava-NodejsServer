# syntax=docker/dockerfile:1.7
FROM node:22-alpine

ENV NODE_ENV=production \
    NPM_CONFIG_UPDATE_NOTIFIER=false \
    NPM_CONFIG_FUND=false

WORKDIR /app

RUN addgroup -S app && adduser -S app -G app

COPY --chown=app:app package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm \
    npm ci --omit=dev --no-audit --no-fund && \
    npm cache clean --force

COPY --chown=app:app . .

RUN mkdir -p uploads/avatars temp && \
    chown -R app:app uploads temp && \
    chmod +x docker/entrypoint.sh

USER app

EXPOSE 49664

HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=5 \
  CMD wget -q -O /dev/null http://127.0.0.1:49664/api/health || exit 1

ENTRYPOINT ["./docker/entrypoint.sh"]
