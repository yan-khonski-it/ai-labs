import logging
from functools import lru_cache


@lru_cache
def read_file(filename: str) -> str:
  """
  Reads content of a file and returns it as a string.
  """
  if filename is None or filename == "":
    logging.error("Filename cannot be None or empty.")
    return ""

  try:
    with open(filename, "r") as file:
      data = file.read()
      if data is None or data == "":
        logging.error(f"File: {filename} is empty.")
        return ""

      return data
  except FileNotFoundError:
    logging.error(f"File: {filename} is not found.")
    return ""
