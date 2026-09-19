# Run NexaRag on a Mac

Use Terminal with zsh (the default Mac shell) or bash. The application uses Node.js
standard libraries with no native add-ons or Windows-specific runtime dependencies.
Use Node.js 22 or later built for your Mac's architecture (Apple silicon or Intel).
Docker and PowerShell are not required for local development.

## 1. Get the code and run tests

Install Git and Node.js if needed, then check their availability:

```sh
git --version
node --version
```

Clone the private repository using your authorized GitHub account:

```sh
git clone https://github.com/binbinbinbinlu/NexaRag.git
cd NexaRag
node --test
```

If already cloned, enter its folder and run `git pull` instead. Tests do not need
an API key or network access. No `npm install` step is needed.

## 2. Configure local secrets

From the repository root:

```sh
cp -n .env.example .env
cp -n config/members.example.json config/members.json
chmod 600 .env config/members.json
nano .env
```

The copies preserve existing files. In `.env`, set `OPENAI_API_KEY` to the company
API key and leave the localhost settings for development. Save in nano with
Control-O, Return, then exit with Control-X. These real configuration files are
ignored by Git. Use a plain-text editor so JSON and environment files remain valid.

## 3. Create a demo store and load the fictional documents

```sh
node --env-file=.env src/admin.js create-store "NexaRag fictional demo"
```

Copy the returned `vs_...` ID into this command:

```sh
demo_store_id='vs_REPLACE_WITH_RETURNED_ID'
for file in samples/documents/*.md; do
  node --env-file=.env src/admin.js upload "$demo_store_id" "$file" || break
done
```

This performs real OpenAI uploads and can incur API charges. Keep the printed file
IDs. Repeating uploads creates duplicates; if a file fails, investigate that ID
before retrying. Keep demo files separate from real company knowledge.

For other files, use quoted Mac paths so spaces work correctly:

```sh
node --env-file=.env src/admin.js upload "$demo_store_id" "$HOME/Documents/Employee Handbook.pdf"
```

## 4. Issue a GPT credential

```sh
node src/admin.js token
nano config/members.json
```

Replace the example member ID with your name, its `tokenHash` with the generated
hash, and its `vectorStoreId` with your demo store ID. Save the raw `token` privately
for the GPT Action authentication field. Do not use the OpenAI key in the GPT.

## 5. Start and check the server

```sh
node --env-file=.env src/server.js
```

Leave this Terminal open. In another Terminal, check:

```sh
curl --fail http://127.0.0.1:3000/health
```

Expect `{"status":"ok"}`. Stop the server with Control-C. If port 3000 is occupied,
change both `PORT` and `PUBLIC_BASE_URL` in `.env` to matching local values.

## 6. Connect ChatGPT

Local operation on a Mac does not expose the API to ChatGPT. Deploy behind publicly
reachable HTTPS following the root README, then use
[Connect your own GPT](connect-your-gpt.md). Teammates who only use the connected
GPT in ChatGPT do not need to install Node.js or run this service on their own Macs.

## Troubleshooting

- `node: command not found`: install Node.js and open a new Terminal.
- Unsupported `--env-file` or missing built-in APIs: check that `node --version`
  reports 22 or newer.
- `.env` not found: run commands from the repository root; Finder normally hides
  dotfiles, but `ls -a` in Terminal shows them.
- Private repository clone fails: authenticate with a GitHub account that has access.
- Configuration error: replace all example placeholders, and keep valid JSON.
- Upload path not found: use `/Users/...` or `"$HOME/..."`, not a Windows drive path.

CI runs the offline tests on the GitHub-hosted macOS runner with Node 22 and 24.
Live OpenAI uploads and a hosted GPT connection require the separate setup above.
