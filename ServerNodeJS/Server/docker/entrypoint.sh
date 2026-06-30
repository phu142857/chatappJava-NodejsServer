#!/bin/sh
set -eu

echo "Ensuring admin account exists..."
if node scripts/seedAdmin.js; then
  echo "Admin account is ready."
else
  echo "Admin seed failed; continuing with server startup."
fi

exec node server.js
