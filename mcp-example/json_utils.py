import json
import logging


def try_to_parse_json_response(json_str: str) -> dict | None:
  if json_str is None or json_str == "":
    return None

  json_str = json_str.strip()
  json_str = json_str.replace("```json", "")
  json_str = json_str.replace("```", "")

  try:
    return json.loads(json_str)
  except json.decoder.JSONDecodeError as e:
    logging.error(f"Failed to parse json: {e}")
    return None
