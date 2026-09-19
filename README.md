# Company RAG for your team's custom GPTs

Each teammate connects their own custom GPT in ChatGPT to one company knowledge
API. The API retrieves relevant document excerpts from an OpenAI vector store;
the teammate's GPT writes the answer with source citations.

`Company Drive → manual export/upload → shared vector store → protected search API → teammate's custom GPT`

This is a runnable starter, not a deployed service. The Drive folder is a placeholder
in `config/source.example.json`; automatic Drive synchronization is not implemented.
Everyone should use the same vector store ID. No company files have been uploaded.

To try fictional company documents, use the [sample corpus](samples/README.md)
and its [14 evaluation questions](samples/questions.md). For the ChatGPT side,
follow [Connect your own GPT](docs/connect-your-gpt.md).

## 1. Local configuration

Requires Node.js 22 or later on macOS, Windows, or Linux. No npm dependencies are
needed. Mac users can follow the complete [macOS setup guide](docs/macos.md).

Windows PowerShell:

```powershell
Copy-Item .env.example .env
Copy-Item config/members.example.json config/members.json
```

macOS/Linux Terminal (zsh or bash):

```sh
cp -n .env.example .env
cp -n config/members.example.json config/members.json
```

The `node` commands below work on all three platforms. For uploads on macOS, use
a Mac file path such as `"$HOME/Documents/Employee Handbook.pdf"` instead of `C:\...`.

Put the company OpenAI API key in `.env` locally. Do not paste it into ChatGPT
instructions or distribute it to teammates. API storage/search billing belongs
to the company API project, separately from teammates' ChatGPT access.

Create the shared store:

```powershell
node --env-file=.env src/admin.js create-store "Company knowledge"
```

Copy its `vs_...` ID into every member entry in `config/members.json`.
Generate a separate credential for each teammate's GPT:

```powershell
node src/admin.js token
```

The command prints a secret `token` and its `tokenHash`. Give the token privately
to that teammate; store only the hash in `config/members.json`. Add one entry per
teammate with a unique `id`, `tokenHash`, and the shared `vectorStoreId`.
The server refuses missing, malformed, or duplicate member credentials.

## 2. Add company knowledge

Replace the Drive folder URL placeholder when it is known. This configuration is
an integration note, not an active connector. Export approved Drive documents to
supported text-bearing files such as PDF, DOCX, TXT, or Markdown. Use clear filenames
because these become citations. Upload each exported file explicitly:

```powershell
node --env-file=.env src/admin.js upload vs_YOUR_ID "C:\path\Employee Handbook.pdf"
```

The command uploads the file and waits up to five minutes for indexing. Save the
printed file ID. Files and their indexed content are stored in the company's
OpenAI API project. Scanned PDFs may require OCR before ingestion.

Uploads are additive: running the same upload twice creates duplicates. To replace
a document, upload and verify its replacement, then delete the old file by ID:

```powershell
node --env-file=.env src/admin.js delete-file file-OLD_ID
```

This deletes that file from OpenAI and all vector stores using it. Deletions can
take time to disappear from search. There is no Drive deletion or permission sync;
an administrator must maintain the indexed collection. For a future Drive connector,
persist Drive file IDs, modification times, and uploaded file IDs, and reconcile
updates and deletions. All documents in this store must be approved for all teammates.

## 3. Test and start

```powershell
node --test
node --env-file=.env src/server.js
```

The local service listens on `127.0.0.1:3000`. `GET /health` checks liveness only;
`GET /openapi.json` returns the GPT Action schema. `POST /search` requires an
`Authorization: Bearer <teammate-token>` header and a JSON body:

```json
{ "query": "How do I request annual leave?", "limit": 5 }
```

The service enforces credential-based store selection, input/response size bounds,
30 requests per minute per credential, and upstream timeouts. It does not log
questions, retrieved content, or tokens. Tests use a simulated retrieval service;
run a real search after configuring the API key and uploading a document.

## 4. Host the API

Deploy the included Dockerfile to your company's container host behind HTTPS.
Set `OPENAI_API_KEY`, `PUBLIC_BASE_URL=https://your-company-knowledge-domain`, and
`MEMBERS_FILE=/run/config/members.json`. Mount the real members file read-only at
that path. The image contains neither credentials nor company documents. For a
non-Docker deployment, set `HOST=0.0.0.0` behind your HTTPS reverse proxy.

ChatGPT needs a reachable HTTPS endpoint; localhost cannot serve teammates' GPTs.
Use a secret manager for production credentials. Configure proxy request-size,
connection, and rate limits. The included rate limiter is per process; multiple
replicas need a shared rate limiter. Restart after editing member credentials.

## 5. Connect each teammate's GPT

1. Open the custom GPT editor and add an Action.
2. Import the schema from `https://your-company-knowledge-domain/openapi.json`.
3. Select **API Key** authentication with **Bearer**, and enter that teammate's
   issued token (not the company OpenAI key).
4. Add the instructions from `docs/gpt-instructions.md` to the GPT's instructions.
5. Test a question whose answer is in an uploaded document and check its citation.
6. Keep each GPT private or within your approved company sharing scope.

GPT creation and Actions must be available under your teammates' ChatGPT accounts
and workspace policies. A credential belongs to the GPT integration, not to the
signed-in person: anyone able to use a GPT with that credential can retrieve the
shared documents. For individually authenticated users on shared GPTs, integrate
your company identity provider using OAuth before rollout. This starter uses
revocable per-GPT API keys; it does not implement OAuth or SSO.

To revoke a credential, remove its member entry and restart the service. This
blocks future searches; it cannot remove excerpts already present in conversations.

## Acceptance checks before team rollout

- An authorized GPT answers a known question with the correct filename citation.
- An unknown question produces no invented company policy.
- Missing/revoked tokens receive 401, and callers cannot choose another store.
- Every teammate credential points to the same approved shared store.
- HTTPS hosting and a real upstream search work from ChatGPT.
- Your company has approved the documents and ChatGPT accounts used for this flow.

## Official integration references

Project documentation: [architecture](docs/architecture.md),
[operator runbook](docs/operations.md), and [testing guide](docs/testing.md).
An importable [Action schema](docs/openapi.json) is also included; replace its
placeholder server URL with your HTTPS deployment URL before importing it.

- [GPT Actions](https://developers.openai.com/api/docs/actions/introduction)
- [Action authentication](https://developers.openai.com/api/docs/actions/authentication)
- [Retrieval and vector stores](https://developers.openai.com/api/docs/guides/retrieval)
