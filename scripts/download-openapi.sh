#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

ENV_FILE="$PROJECT_DIR/.env"
if [ -f "$ENV_FILE" ]; then
  export $(grep -E '^PISPI_' "$ENV_FILE" | xargs)
fi

AIP_HOST="${PISPI_AIP_HOST:-95.111.247.232}"
AIP_PORT="${PISPI_AIP_PORT:-8081}"
CERT="${PISPI_MTLS_KEYSTORE_PATH:-$PROJECT_DIR/src/main/resources/client/client.p12}"
CERT_PASSWORD="${PISPI_MTLS_KEYSTORE_PASSWORD:?PISPI_MTLS_KEYSTORE_PASSWORD is required}"
OUTPUT="$PROJECT_DIR/documentation/interface-participant-openapi.json"

CERT="${CERT#classpath:}"
if [[ "$CERT" != /* ]]; then
  CERT="$PROJECT_DIR/src/main/resources/$CERT"
fi

echo "Downloading interface-participant OpenAPI from https://$AIP_HOST:$AIP_PORT ..."

curl -k \
  --cert-type P12 \
  --cert "$CERT:$CERT_PASSWORD" \
  "https://$AIP_HOST:$AIP_PORT/v3/api-docs" \
  -o "$OUTPUT" \
  --fail \
  --silent \
  --show-error

echo "Saved to $OUTPUT"
