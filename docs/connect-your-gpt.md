# Ask questions using your own custom GPT

For illustrated instructions and current account restrictions, see the
[detailed setup guide](setup-guide.md). As of 19 September 2026, OpenAI says new
GPT creation is unavailable on personal accounts; existing GPTs may remain editable,
and managed workspaces may allow creation. Check the linked official guidance there.

NexaRag is a search service that your custom GPT calls through an Action. Your GPT
keeps its own instructions and uses the returned excerpts to answer company questions.

## What must be running first

The GitHub repository contains source code; its GitHub URL is not a running search
API. An administrator must configure the company OpenAI API key, create and populate
a vector store, issue a GPT credential, and deploy the server behind reachable HTTPS.
Follow the root README for those steps. Use `samples/README.md` for a fictional trial.

You need these three things from the administrator:

- The service URL, for example `https://knowledge.your-company.example`.
- Your NexaRag Bearer token, generated with `node src/admin.js token` and configured
  by its hash in the server's member file.
- The GPT instructions in `docs/gpt-instructions.md`.

Never enter the company's OpenAI API key into the GPT. The NexaRag token is a
separate search credential. No live service URL or credential is provisioned by
the checked-in example files.

## Configure the GPT

1. In ChatGPT, open your custom GPT in its editor. Your account/workspace must allow
   GPT editing and Actions.
2. In its configuration, add an **Action**.
3. Import the schema from `https://YOUR-SERVICE-DOMAIN/openapi.json`, replacing the
   domain with the running service. Alternatively paste `docs/openapi.json` and
   replace its placeholder server URL. Do not use the GitHub repository URL as the
   API server URL.
4. Set Action authentication to **API Key**, choose **Bearer**, and enter your
   NexaRag token.
5. Add the contents of `docs/gpt-instructions.md` to the GPT's instructions. For the
   sample corpus also add: “These are fictional Nexa Demo documents. Label answers
   as demo information, not actual company policy.”
6. Test `searchCompanyKnowledge` in the editor with a question found in an indexed
   document. Verify the response has `sources` and supporting `excerpts`.
7. Save the GPT with the intended private/company sharing scope. Anyone who can use
   a GPT with this credential can retrieve its configured shared documents.
8. Open a conversation with the saved GPT and ask a question normally.

For the sample documents, try: “How far ahead should I request annual leave?”
Expect **10 working days**, line-manager approval before booking travel, and a
citation to `01-employee-handbook.md`.

Then ask: “What is the Aurora project budget?” The GPT should say it is not provided,
not invent a number. See `samples/questions.md` for more checks.

## When it does not work

- No Action option: check GPT editing access and workspace policies.
- Connection error: the server must be running on reachable HTTPS, not localhost.
- 401: check your NexaRag token and restart the server after updating member hashes.
- 502: check the server's OpenAI key, store ID, and upstream connectivity.
- Empty/unhelpful results: confirm indexing completed and your credential points
  to the populated store.
- Answers without calling search: check the GPT instructions and Action test result.

Official references: [GPT Actions](https://developers.openai.com/api/docs/actions/introduction)
and [Action authentication](https://developers.openai.com/api/docs/actions/authentication).
