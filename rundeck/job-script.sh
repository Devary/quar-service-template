#!/usr/bin/env bash
set -euo pipefail

WORKSPACE="${RD_OPTION_WORKSPACE:-}"
IMAGE="${RD_OPTION_IMAGE:-}"
TAG="${RD_OPTION_TAG:-}"
NAMESPACE="${RD_OPTION_NAMESPACE:-default}"
DEPLOYMENT="${RD_OPTION_DEPLOYMENT:-${RD_OPTION_IMAGE}}"
CONTAINER="${RD_OPTION_CONTAINER:-${RD_OPTION_IMAGE}}"
PORT="${RD_OPTION_PORT:-8080}"

: "${WORKSPACE:?workspace required}"
: "${IMAGE:?image required}"

if [[ -z "${TAG}" ]]; then
  TAG="latest"
fi

echo "WORKSPACE=${WORKSPACE}"
echo "IMAGE=${IMAGE}"
echo "TAG=${TAG}"
echo "NAMESPACE=${NAMESPACE}"
echo "DEPLOYMENT=${DEPLOYMENT}"
echo "CONTAINER=${CONTAINER}"
echo "PORT=${PORT}"

cd "${WORKSPACE}"

bash ./k8s/deploy.sh "${IMAGE}" "${TAG}" "${NAMESPACE}" "${DEPLOYMENT}" "${CONTAINER}" "${PORT}"
