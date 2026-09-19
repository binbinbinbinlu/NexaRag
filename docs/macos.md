# Run NexaRag on a Mac

Teammates only need ChatGPT or Codex; they do not need a local server. The hosted MCP connection works on Mac and Windows. Administrators and developers can use Terminal (zsh or bash) with Node.js 22/24 and Git.

```sh
git clone https://github.com/binbinbinbinlu/NexaRag.git
cd NexaRag
npm install --global pnpm@11.19.0
pnpm install --frozen-lockfile
node --test
cp -n .env.example .env
cp -n config/members.example.json config/members.json
chmod 600 .env config/members.json
nano .env
```

Set OPENAI_API_KEY privately. Use a plain-text editor. Generate a code with `node src/admin.js token`, put its hash and approved store ID in config/members.json, and retain the raw code privately. The sample member configuration is not usable until replaced.

```sh
node --env-file=.env src/server.js
```

In another Terminal, open http://127.0.0.1:3000/health. Remote ChatGPT needs the hosted HTTPS `/mcp` endpoint, not your localhost. Follow [Connect the plugin](connect-plugin.md).

For a new store only, follow [the sample upload instructions](../samples/README.md). The current deployed demo already has its files; do not upload duplicates. Quote paths with spaces:

```sh
node --env-file=.env src/admin.js upload vs_YOUR_ID "$HOME/Documents/Employee Handbook.pdf"
```

The app uses JavaScript and pinned npm packages with no application-specific native build step. Docker is optional for local development. Keep Node/pnpm versions aligned with the lockfile and CI.
