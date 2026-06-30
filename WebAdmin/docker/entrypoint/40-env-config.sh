#!/bin/sh
set -eu

# Preserve an explicitly empty value so the app can use same-origin /api routing.
api_base_url="${VITE_API_BASE_URL-http://localhost:49664}"
escaped_api_base_url=$(printf '%s' "$api_base_url" | sed "s/'/'\\\\''/g")

cat > /usr/share/nginx/html/env-config.js <<EOF
window.__APP_CONFIG__ = Object.assign({}, window.__APP_CONFIG__, {
  VITE_API_BASE_URL: '${escaped_api_base_url}'
});
EOF
