# NexaRag: company knowledge plugin

NexaRag lets teammates search the same approved company documents from ChatGPT or Codex. Version 2 replaces custom GPT Actions with an authenticated MCP service and a plugin containing evidence-based answering instructions.

**MCP endpoint:** https://nexarag-fypg.onrender.com/mcp

**Health:** https://nexarag-fypg.onrender.com/health

The demo store already contains four fictional documents. The Drive source is still a placeholder; there is no automatic Drive sync. Do not upload real documents until everyone with access is approved to read them.

## Start here

- [Detailed plugin setup guide with screenshots](docs/setup-guide.md)
- [PDF setup guide](docs/NexaRag-setup-guide.pdf)
- [Connect ChatGPT or Codex](docs/connect-plugin.md)
- [Mac setup](docs/macos.md), [Render deployment](docs/render.md), [operator runbook](docs/operations.md)
- [Architecture and authentication](docs/architecture.md), [testing](docs/testing.md), [sample questions](samples/questions.md)

## Personal accounts and team distribution

Your account is personal or you are not a workspace administrator. Start with an individual MCP connection where developer mode is available, or import the desktop plugin into Codex. Company-wide marketplace publication requires a workspace administrator. Account and product availability are controlled by OpenAI.

The source package in `plugins/nexarag` declares a remote MCP server and can be classified **Desktop only**. For ChatGPT web, first register the connected app in your account, then build a package referencing its real app ID:

```sh
node scripts/build-chatgpt-plugin.js asdk_app_YOUR_REAL_ID
```

This generates `dist/chatgpt/plugins/nexarag` and a marketplace under `dist/chatgpt`. It deliberately omits MCP declarations, so it does not introduce the desktop restriction. The app must still be available and authorized. The generator rejects plugin IDs and never invents an app ID. See the setup guide before importing.

## Developer setup (Windows, Mac, Linux)

Install Node.js 22 or 24 and Git, then:

```sh
git clone https://github.com/binbinbinbinlu/NexaRag.git
cd NexaRag
npm install --global pnpm@11.19.0
pnpm install --frozen-lockfile
node --test
```

Copy `.env.example` to `.env` and `config/members.example.json` to `config/members.json`, preserving existing files. Set the OpenAI project API key only in `.env` or Render. Generate a private member access code with `node src/admin.js token`; put only its hash in the member configuration. All teammate entries should reference the same approved vector store.

```sh
node --env-file=.env src/server.js
```

Local health is available at http://127.0.0.1:3000/health. Remote ChatGPT needs public HTTPS. The included Dockerfile and Render configuration deploy the service.

## What changed in v2

`/mcp` exposes `search_company_knowledge` using Streamable HTTP. OAuth authorization code + PKCE replaces static Action Bearer tokens. Old member tokens become private sign-in access codes. `/openapi.json` and `/search` have been removed. Existing indexed files stay in the same OpenAI vector store. No new store or duplicate upload is needed.

The service returns excerpts; ChatGPT/Codex produces the answer. It does not provide SSO, Drive sync, document writes, or a public plugin-directory listing. Current access tokens last one hour and then require reconnection; refresh tokens are not issued. See the runbook for deployment, credential rotation, and production limits.
