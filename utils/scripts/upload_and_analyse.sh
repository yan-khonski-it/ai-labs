#!/usr/bin/env bash

# Usage:
#   export OPENAI_API_KEY=sk-...
#   ./analyze_file_simple_json_escape.sh /path/to/file.java
#   MODEL=gpt-5-mini ./analyze_file_simple_json_escape.sh file.txt

# exit immediately if any simple command returns a non-zero status
set -euo pipefail
OPENAI_API_KEY=${OPENAI_API_KEY_AI_LABS_CODE_ANALYSER}
FILE="${1:?Usage: $0 <path-to-file>}"
[[ -f "$FILE" ]] || { echo "Not a file: $FILE" >&2; exit 1; }

MODEL="${MODEL:-gpt-5}"


if [[ -z "${OPENAI_API_KEY:-}" ]]; then
  echo "ERROR: Set OPENAI_API_KEY" >&2; exit 1
fi

# Read file into a variable (UTF-8 text expected)
CONTENT="$(cat -- "$FILE")"

# Minimal JSON escaping (handles the usual suspects for source/text files)
# Order matters: escape backslashes before quotes/newlines/etc.
CONTENT=${CONTENT//\\/\\\\}      # backslash -> \\
CONTENT=${CONTENT//\"/\\\"}      # double quote -> \"
CONTENT=${CONTENT//$'\r'/\\r}    # CR -> \r
CONTENT=${CONTENT//$'\n'/\\n}    # LF -> \n
CONTENT=${CONTENT//$'\t'/\\t}    # TAB -> \t

# Call Responses API (JSON mode -> reply must be valid JSON)
curl -sS https://api.openai.com/v1/responses \
  -H "Authorization: Bearer $OPENAI_API_KEY" \
  -H "Content-Type: application/json" \
  -d @- <<JSON
{
  "model": "$MODEL",
  "input": [{
    "role": "user",
    "content": [
      { "type": "input_text",
        "text": "Analyze the following file content and reply with a SINGLE valid JSON object ONLY with these keys: ShortSummary, Summary, MostInportantThings, RelatedFunctionality." },
      { "type": "input_text",
        "text": "$CONTENT"
      }
    ]
  }],
  "text": { "format": { "type": "json_object" } }
}
JSON