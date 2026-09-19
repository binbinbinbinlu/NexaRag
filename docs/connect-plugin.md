# Connect the NexaRag plugin

## Your personal account

Open ChatGPT Settings and look for **Security and login > Developer mode**. Availability varies by account and workspace. If present, enable it, open Plugins, select **+**, and create an MCP connection:

- Name: NexaRag
- URL: `https://nexarag-fypg.onrender.com/mcp`
- Authentication: OAuth
- Permission: `knowledge:read`

Use dynamic registration if offered; do not invent client IDs or enter the OpenAI API key. Start Connect. The NexaRag page asks for the private member access code issued by the administrator. Enter it only there and approve read access. Confirm the discovered tool is `search_company_knowledge`.

Start a new chat and select the connected app from the tools menu or use its @ mention where available. Ask a sample question and check the actual tool result and citation. A server test does not establish that your account is connected.

If developer mode or app creation is unavailable, use the Codex desktop path below or ask a company administrator to publish an approved connection. A plugin file cannot bypass these account restrictions.

## Package instructions with the ChatGPT app

After registering the MCP connection, copy the technical app ID. A management URL containing `plugin_asdk_app_abc` refers to app ID `asdk_app_abc`; remove only the `plugin_` prefix. Do not use the plugin ID in `.app.json`.

Run `node scripts/build-chatgpt-plugin.js asdk_app_YOUR_REAL_ID`. Upload/import the generated `dist/chatgpt/plugins/nexarag` package using the plugin import controls available to your account. If an archive is required, zip the contents so `.codex-plugin`, `.app.json`, and `skills` are at the archive root. Install and test it in a new conversation. This generated package references the app; it does not create or share that app.

For workspace GitHub import, an admin can commit the generated marketplace tree in a dedicated repository/subdirectory, import that repository and path, then enable the referenced app for intended roles. Personal/non-admin users cannot perform workspace publication themselves.

## Codex desktop on Windows or Mac

Import the `plugins/nexarag` folder or its ZIP using the plugin import controls in Codex. This package contains a remote MCP declaration and a company-knowledge skill. Complete the OAuth connection when prompted; the browser sign-in uses your private NexaRag access code. Start a new task, open **Sources > Use plugins**, and select NexaRag.

For a direct diagnostic connection in the Codex CLI, run:

```sh
codex mcp add nexarag --url https://nexarag-fypg.onrender.com/mcp
codex mcp login nexarag
```

A direct MCP connection is a diagnostic alternative; install the full plugin to include the answering skill. Avoid enabling both connections simultaneously if they expose duplicate tools.

## Verify and maintain

Ask “How far ahead should I request annual leave?” The fictional handbook says at least 10 working days, with manager approval before booking travel. Ask “What is the Aurora budget?” The documents do not provide a budget.

Access expires after one hour. Reconnect through the app/plugin settings when prompted. Never paste codes into chat. After a server tool change, refresh the connected app and start a new chat. After a plugin skill change, update/reimport the plugin. Removing a plugin does not delete documents from the vector store.

Official references: [connect and test](https://developers.openai.com/plugins/deploy/connect-chatgpt), [plugin packaging](https://developers.openai.com/plugins/build/plugins), [plugin access](https://help.openai.com/en/articles/20001256-plugins-in-chatgpt-and-codex).
