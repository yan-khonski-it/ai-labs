# ai-agent-example

It is an AI agent that analyses the source code of a repository, so you can ask questions about the code.

## Setup
1. You need to run pre-analyse the code, so it builds knowledge notes used for analysis.
2. After you get knowledge notes, you can run the agent that uses the notes to anser questions. This part works similarly to mcp-example.

## pre-analyse
You will need OpenAI API key.
```text
OPENAI_API_KEY
```

You will need source code repository checked out.

Run
```shell
java -jar ./target/ai-agent-example-1.0-SNAPSHOT.jar "C:\Dev\workspaces\open-source\HikariCP\src\main\java\com\zaxxer\hikari" "C:\Dev\workspaces\open-source\HikariCP-analysed"
```