#!/bin/sh
set -e

# Render (and most PaaS) inject PORT dynamically; default to 8080 for local `docker run`.
export PORT="${PORT:-8080}"
export SERVER_PORT="${SERVER_PORT:-5000}"
export SOCKETIO_PORT="${SOCKETIO_PORT:-5001}"

# IMPORTANT: only substitute our three variables. nginx's own runtime variables
# ($host, $remote_addr, $http_upgrade, etc.) use the same ${...} syntax and must
# be left untouched, or the proxy config breaks.
envsubst '${PORT} ${SOCKETIO_PORT} ${SERVER_PORT}' \
    < /etc/nginx/templates/nginx.conf.template \
    > /etc/nginx/nginx.conf

nginx -t
nginx

echo "nginx listening on :$PORT -> REST :$SERVER_PORT, Socket.IO :$SOCKETIO_PORT"

exec java -jar /app/commengine.jar
