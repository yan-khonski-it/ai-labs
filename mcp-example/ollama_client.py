import logging
import ollama

from ollama import ChatResponse


OLLAMA_MODEL = "gemma3:4b"
LOG_PROMPT_MAX_SIZE = 10000


# noinspection PyMethodMayBeStatic
class OllamaClient:
  """
  Talks to Ollama running locally.
  """

  def validate_ollama_model(self):
    logging.info(f"Initializing Ollama client with model: {OLLAMA_MODEL}.")

    try:
      ollama.list()
      models = ollama.list()["models"]
      if not any(current_model.model.startswith(OLLAMA_MODEL) for current_model in models):
        logging.error(f"Ollama model: {OLLAMA_MODEL} not found in Ollama. Please download it.")
        raise ValueError(f"Ollama model: {OLLAMA_MODEL} not found in Ollama.")

      logging.info(f"Ollama server connected and model: {OLLAMA_MODEL} is available.")
    except Exception as e:
      logging.error(f"Failed to connect to Ollama server or model is not available. {e}")

  def query_ollama(self, prompt: str, log_response: bool = True) -> str:
    """
    Query Ollama model with prompt.
    """

    if len(prompt) < LOG_PROMPT_MAX_SIZE:
      logging.info(f"Querying Ollama for prompt.\n{prompt}")
    else:
      logging.info(f"Querying Ollama for prompt of length: {len(prompt)}.\n{prompt[:LOG_PROMPT_MAX_SIZE]}")

    try:
      response: ChatResponse = ollama.chat(model=OLLAMA_MODEL, message=[
        {
          "role": "user",
          "content": prompt,
        },
      ])

      response_json = response["message"]["content"]
      if log_response:
        logging.info("Response from Ollama.\n" + response_json)

    except Exception as e:
      logging.error(f"Failed to get response from Ollama. {e}")
      return ""

    return response_json