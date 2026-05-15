#!/bin/sh
set -eu

SERVICE_SCALE="${SERVICE_SCALE:-1}"

case "$SERVICE_SCALE" in
  ''|*[!0-9]*)
    echo "Invalid SERVICE_SCALE='$SERVICE_SCALE', defaulting to 1" >&2
    SERVICE_SCALE=1
    ;;
esac

if [ "$SERVICE_SCALE" -gt 1 ]; then
  export JAVA_DEBUG=false
  unset JAVA_DEBUG_PORT
  echo "SERVICE_SCALE=$SERVICE_SCALE -> remote debug disabled"
fi

exec /opt/jboss/container/java/run/run-java.sh
