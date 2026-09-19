# Operator runbook

## Members

Generate a separate private access code per teammate with `node src/admin.js token`. Send the raw token through an approved private channel. Put only its hash in the member configuration, with a unique ID and the shared approved vector store ID. Preserve other members when editing MEMBERS_JSON. Redeploy to load changes.

The teammate enters the code on NexaRag's OAuth consent page, never in chat, plugin files, or OpenAI API settings. The historical local `data/gpt-token.txt` contains the original demo-owner code; its filename does not change its role. Do not distribute that single code to the entire company.

Remove a member or replace its hash and redeploy to revoke both old access codes and issued OAuth tokens. An empty member list intentionally fails startup; stop the service to revoke everyone. Revocation cannot remove excerpts already saved in chats. For a compromised OAuth encryption secret, rotate OAUTH_SIGNING_KEY and reconnect clients. Without that variable, rotating OPENAI_API_KEY also rotates the derived OAuth key.

## Documents

Keep a private inventory of Drive source, filename, upload date, OpenAI file ID, and store ID. The Drive folder in config/source.example.json is not synced. Export approved files manually and upload them with:

```sh
node --env-file=.env src/admin.js upload vs_YOUR_ID path/to/document.pdf
```

Repeated uploads create duplicates. After uploading a replacement, verify retrieval before deleting the old file:

```sh
node --env-file=.env src/admin.js delete-file file-OLD_ID
```

Deletion removes the OpenAI file from every store that references it and may take time to affect search. If removal must be immediate, pause access until verified. An upload can complete before indexing fails; inspect the printed file ID before retrying. Never upload the sample answer sheet as source knowledge.

## Release

Run `pnpm install --frozen-lockfile` and `node --test`. GitHub CI covers Node 22/24 on Windows, macOS, and Linux. Deploy the tested commit and smoke-test OAuth plus a real MCP retrieval. Refresh connected-app tool metadata and update the plugin package when skills change. Code rollback does not undo document changes.

Version 2 removes the GPT Action endpoints. Rolling back to the pre-migration commit restores the old protocol, but plugins cannot use it. Coordinate server and client versions. Keep the indexed store intact during migration.

## Limits

This is a single-service shared-corpus deployment with access-code login, not SSO. One-hour OAuth access requires reconnecting; refresh tokens are not issued. Free Render cold starts, process-local rate limits, and pending-flow loss on restart are demo constraints. Avoid logging credentials, authorization URLs, document excerpts, or company questions. Back up member configuration and source inventory privately.
