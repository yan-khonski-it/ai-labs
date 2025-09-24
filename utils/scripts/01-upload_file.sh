#!/usr/bin/env bash

# This script uploads a file to OpenAI LLM for analysis

# exit immediately if any simple command returns a non-zero status
set -euo pipefail
OPENAI_API_KEY=${OPENAI_API_KEY_AI_LABS_CODE_ANALYSER}


if [[ -z "${OPENAI_API_KEY:-}" ]]; then
  echo "ERROR: Set OPENAI_API_KEY" >&2; exit 1
fi
if [[ $# -lt 1 ]]; then
  echo "Usage: $0 <path-to-file>" >&2; exit 1
fi

FILE="$1"
echo "File: $FILE"
if [[ ! -f "$FILE" ]]; then
  echo "ERROR: Not a file: $FILE" >&2; exit 1
fi

RESP_JSON="$(curl -sS https://api.openai.com/v1/files \
  -H "Authorization: Bearer $OPENAI_API_KEY" \
  -F purpose=user_data \
  -F file=@"$FILE")"

echo "RESP_JSON: $RESP_JSON"

# Extract file id without jq:
FILE_ID="$(printf '%s' "$RESP_JSON" | grep -oE '"id"\s*:\s*"file_[^"]+"' | head -n1 | cut -d'"' -f4)"

if [[ -z "$FILE_ID" ]]; then
  echo "Upload failed. Full response:" >&2
  echo "$RESP_JSON" >&2
  exit 2
fi

echo "$FILE_ID"