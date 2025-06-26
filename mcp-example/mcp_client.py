import asyncio
import json_utils
import logging
import sys

from contextlib import AsyncExitStack
from mcp import ClientSession, StdioServerParameters
from mcp.client.stdio import stdio_client
from ollama_client import OllamaClient
from typing import Any, Dict, List, Optional


logging.basicConfig(level=logging.INFO, stream=sys.stdout, format="%(asctime)s - %(levelname)s - %(message)s")
logging.getLogger("asyncio").setLevel(logging.WARNING)

# Create server parameters for stdio connection
server_parameters = StdioServerParameters(
    command="python",
    args=["mcp_server.py"],
    env=None
)


class McpClient:
  """
  Class for interacting with MCP server and Ollama client
  """

  def __init__(self):
    self.session: Optional[ClientSession] = None

    self.stdio: Optional[Any] = None
    self.write: Optional[Any] = None

    self.exit_stack = AsyncExitStack()
    self.tools = None
    self.tools_description_str = ""

  async def connect_to_server(self):
    stdio_transport = await self.exit_stack.enter_async_context(
        stdio_client(server_parameters=server_parameters)
    )

    self.stdio, self.write = stdio_transport
    self.session = await self.exit_stack.enter_async_context(
        ClientSession(self.stdio, self.write)
    )

    # Initialize the connection
    await self.session.initialize()

    # List available tools
    self.tools = await self.session.list_tools()

    for tool in self.tools.tools:
      self.tools_description_str = self.tools_description_str + f"{tool.name}: {tool.description}\n"

    logging.info(f"Connected to MCP server with tools.\n{self.tools_description_str}")

  async def get_mcp_tools(self):