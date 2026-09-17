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

# Start the Spring Boot app FIRST, in the background, and don't start nginx (which
# is what actually opens the platform's public $PORT) until the REST API is really
# answering requests. Spring Boot + Hibernate + Hikari + the Socket.IO server can
# easily take 20-40+ seconds to finish starting, and nginx itself starts almost
# instantly -- starting nginx first (the previous version of this script) left a
# window where the public port was open but had nothing to proxy to, returning
# 502 "Connection refused" for every request until Java finally caught up.
java -jar /app/commengine.jar &
JAVA_PID=$!

echo "Waiting for REST API on :$SERVER_PORT to become ready..."
until curl -sf "http://127.0.0.1:${SERVER_PORT}/api/health" > /dev/null 2>&1; do
    if ! kill -0 "$JAVA_PID" 2>/dev/null; then
        echo "Java process exited before becoming ready -- see the log above for the real error."
        exit 1
    fi
    sleep 1
done
echo "REST API is ready."

# The Socket.IO server (netty-socketio) is started via a Spring @PostConstruct
# hook, which runs before the embedded Tomcat server starts listening during
# context refresh -- so by the time /api/health responds, port $SOCKETIO_PORT
# is already up too. No separate probe needed.

nginx
echo "nginx listening on :$PORT -> REST :$SERVER_PORT, Socket.IO :$SOCKETIO_PORT"

# Tie the container's lifecycle to the Java process: if it exits/crashes, the
# container exits too (so the platform notices and restarts it), rather than
# silently sitting there with nginx proxying to a dead backend.
wait "$JAVA_PID"