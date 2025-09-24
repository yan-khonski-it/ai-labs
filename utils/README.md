# Utils

I use this module for experiments. They will help me to build an AI agent for code analysis.

## Analyse the size of the code base by running FileAnalyzer


```shell
java -cp ./target/utils-1.0-SNAPSHOT.jar com.yk.utils.FileAnalyzer "C:\Dev\workspaces\open-source\HikariCP\src\main\java\com\zaxxer\hikari\HikariDataSource.jav"
```

Analyse the code with OpenAI API
the key
```text
OPENAI_API_KEY_AI_LABS_CODE_ANALYSER
```

## Upload file as text content and analyse it
Note, it uses home-made command to escape JSON in a POST request.
The request body JSON contains file content.

```shell
./utils/scripts/upload_and_analyse.sh "/mnt/c/Dev/workspaces/open-source/HikariCP/src/main/java/com/zaxxer/hikari/HikariDataSource.txt"
```

## The section bellow does not work because OpenAI API does not work with uploaded text files.

### Upload a file for analysis
First, on windows, you will need to start bash, 
```shell
bash
```

Upload a file:
```shell
./utils/scripts/01-upload_file.sh "/mnt/c/Dev/workspaces/open-source/HikariCP/src/main/java/com/zaxxer/hikari/HikariDataSource.txt"
```
It will return file id
```text
RESP_JSON: {
  "object": "file",
  "id": "file-DJPgg1T5CVvS7SNSe81mBD",
  "purpose": "user_data",
  "filename": "HikariDataSource.txt",
  "bytes": 11239,
  "created_at": 1758748916,
  "expires_at": null,
  "status": "processed",
  "status_details": null
}
```

### Analyse the file:
```shell
./utils/scripts/not-working--02-analyze_code.sh "file-DJPgg1T5CVvS7SNSe81mBD"
```
