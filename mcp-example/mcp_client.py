import asyncio
import json_utils
import logging
import sys

from contextlib import AsyncExitStack
from mcp import ClientSession, StdioServerParameters
from mcp.client.stdio import stdio_client
from ollama_client import OllamaClient
from typing import Any, Dict, List, Optional


# Configure logging.
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
        stdio_client(server_parameters)
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
    """
    Get available tools from MCP server in OpenAI format.
    """
    tools_result = await self.session.list_tools()
    return [
      {
        "type": "function",
        "function": {
          "name": tool.name,
          "description": tool.description,
          "parameters": tool.inputSchema
        }
      }
      for tool in tools_result
    ]

  async def call_tool(self, tool_name: str, **kwargs) -> str|None:
    """
    Calls specific tool on MCP server.
    """
    logging.info(f"Calling tool {tool_name} with arguments: {kwargs}.")

    try:
      tool_result = await self.session.call_tool(tool_name, kwargs)
      if tool_result is None:
        logging.info(f"Tool {tool_name} returned None.")
        return None

      result_text = tool_result.content[0].text
      if result_text is None:
        logging.info(f"Tool {tool_name} returned no text.")
        return None

      logging.info(f"Tool {tool_name} returned text result of length {len(result_text)}.")
      return result_text

    except Exception as e:
      logging.info(f"Failed to call tool {tool_name}. Exception: {e}")
      return None

  async def cleanup(self):
    await self.exit_stack.aclose()

async def process_user_input(mcp_client: McpClient, ollama_client: OllamaClient, user_input: str) -> None:
  """
  Processes user input by call Ollama to select the right tool, then using the tool collect information,
  then colling Ollama to analyse the information and answer user input.
  """

  logging.info(f"Processing user input: {user_input}")

  # Step 1 - get tools from MCP server
  tools_description_str = mcp_client.tools_description_str

  # Step 2 prepare the prompt, so the model can choose the right tool.
  tool_prompt = (
    f"You are connected to a tool server that returns data of source code of a specific class or full source code, using the following tools:\n"
    f"{tools_description_str}\n\n"
    f"Based on the user's input, decide which tool to use and provide the tool name and arguments as JSON\n."
    f"User input:\n\n{user_input}\n\n"
    "Your response should only include a JSON object with 'tool' and 'arguments'."
  )

  # Query Ollama model
  model_response = ollama_client.query_ollama(tool_prompt)

  if model_response is None:
    logging.error(f"No tool returned from Ollama.")
    return

  model_decision = json_utils.try_to_parse_json_response(model_response)
  if model_decision is None:
    logging.error(f"No tool returned from Ollama. JSON response is None.")
    return

  tool_name = model_decision.get("tool")
  tool_text_result = None # This is the code we will send to Ollama for processing
  if tool_name == "get_class_data":
    class_name = model_decision.get("arguments", {})
    if class_name is not None and class_name != "":
      arguments = {"class_name": class_name}
      tool_text_result = await mcp_client.call_tool(tool_name, **arguments)

  if tool_text_result is not None and tool_text_result != "":
    result_prompt = (
      "Given the code bellow:\n\n" +
      f"```{tool_text_result}```\n" +
      "Answer the question based on the code. If you don't know the answer, say 'I don't know.'\n" +
      "User question:\n\n" + user_input + "\n"
    )

    ollama_client.query_ollama(result_prompt)
    return

  tool_name = "get_all_code"
  arguments = {}
  tool_text_result = await mcp_client.call_tool(tool_name, **arguments)
  if tool_text_result is None:
    logging.error(f"Tool [get_all_code] returned None.")
    return

  result_prompt = (
      "Given the code bellow:\n\n" +
      f"```{tool_text_result}```\n" +
      "Answer the question based on the code. If you don't know the answer, say 'I don't know.'\n" +
      "User question:\n\n" + user_input + "\n"
  )

  ollama_client.query_ollama(result_prompt)

async def main():
  logging.info("Starting MCP client.")

  ollama_client = OllamaClient()
  ollama_client.validate_ollama_model()

  mcp_client = McpClient()
  await mcp_client.connect_to_server()

  while True:
    print("\n\n")
    user_input: str = input("Enter class name (optionally) and your question about the code.\n" +
                            "For example, HikariPool, how does getConnection method works internally?\n" +
                            "Press Enter to continue or type 'exit' or 'q' or 'quit' to exit.\n")

    if user_input is None or user_input == "":
      logging.info("User input is empty. Skipping...")
      continue

    if user_input == "exit" or user_input == "q" or user_input == "quit":
      logging.info("Exiting the client.")
      break

    await process_user_input(mcp_client, ollama_client, user_input)
    print("\n\n")

  await mcp_client.cleanup()


if __name__ == "__main__":
  asyncio.run(main())