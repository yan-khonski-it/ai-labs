#!/usr/bin/env bash

# Right now, the script does not work because OpenAI API does not allow to work with uploaded text files.

# This script analyses the file uploaded by the previous command.

set -euo pipefail
OPENAI_API_KEY=${OPENAI_API_KEY_AI_LABS_CODE_ANALYSER}

FILE_ID="${1:?Usage: $0 <file_id>}"
MODEL="${MODEL:-gpt-5-mini}"

curl -sS https://api.openai.com/v1/responses \
  -H "Authorization: Bearer $OPENAI_API_KEY" \
  -H "Content-Type: application/json" \
  -d @- <<JSON
{
  "model": "$MODEL",
  "input": [{
    "role": "user",
    "content": [
      { "type": "input_file", "file_id": "$FILE_ID" },
      { "type": "input_text",
        "text": "Analyze the attached file and reply with a single valid JSON object ONLY with these keys: ShortSummary, Summary, MostInportantThings, RelatedFunctionality." }
    ]
  }],
  "text": { "format": { "type": "json_object" } }
}
JSON