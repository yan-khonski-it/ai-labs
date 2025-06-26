import sys
import logging
import file_utils

from mcp.server.fastmcp import FastMCP


logging.basicConfig(level=logging.INFO, stream=sys.stdout, format="%(asctime)s - %(levelname)s - %(message)s")

DATA_LOCATION = "./data/hikaricp-code"
ALL_CODE_FILE_LOCATION = "./data/HikariCP.converted.txt"
DEFAULT_CODE_TYPE = ".java"

mcp = FastMCP(
    name = "Class Data MCP Server",
    host = "0.0.0.0",
    port = 8050,
)

@mcp.tool(description="Return string containing the source code of the specified class_name")
def get_class_data(class_name: str) -> str:
  logging.info(f"Getting class data from {class_name}.")
  if not class_name.endswith(DEFAULT_CODE_TYPE):
    class_name = class_name + DEFAULT_CODE_TYPE
  return file_utils.read_file(DATA_LOCATION + "/" + class_name)

def get_all_code() -> str:
  logging.info("Getting all code.")
  return file_utils.read_file(ALL_CODE_FILE_LOCATION)


if __name__ == "__main__":
  logging.info("Starting Class Data MCP server.")
  mcp.run(transport="stdio")