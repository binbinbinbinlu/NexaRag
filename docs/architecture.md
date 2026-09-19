# Architecture

ChatGPT/Codex -> NexaRag plugin skill + OAuth MCP connection -> `/mcp` -> authorized member's OpenAI vector store -> cited excerpts -> answer.

The tool is `search_company_knowledge`, with `query` (1-2000 characters) and optional `limit` (1-5). Its strict schema rejects caller-selected store IDs. It is read-only, non-destructive, and searches a bounded corpus. The server returns `sources` with filename, file ID, citation identifier, score, and excerpt text. There is no UI widget or server-generated answer.

## Transport

The official MCP SDK implements stateless Streamable HTTP with JSON responses. Each authenticated POST creates a request-local server and transport. There are no persistent MCP sessions or standalone SSE streams. SDK clients initialize, list tools, and call the tool. Origin checks reject untrusted browser origins. HTTPS is required outside localhost. Request bodies are limited to 16 KiB and MCP calls to 30 per minute per member per process.

## Authentication

The SDK OAuth router provides discovery, dynamic client registration, authorization code exchange, client authentication, and S256 PKCE verification. Resource metadata is at `/.well-known/oauth-protected-resource/mcp`; authorization metadata is at `/.well-known/oauth-authorization-server`.

NexaRag allows the documented ChatGPT callback paths and HTTP loopback `/callback` URLs for desktop clients. It rejects arbitrary third-party redirects. The browser consent form is bound to an HttpOnly SameSite cookie, a random expiring flow, and a same-origin check. Users authenticate with high-entropy administrator-issued access codes, stored server-side only as SHA-256 hashes. This is possession-based access, not corporate SSO or email identity verification.

Authorization codes are single use, expire in 60 seconds, and bind the client, redirect, resource, member, and PKCE challenge. Pending consent lasts five minutes. Both are in process memory; a restart during sign-in requires restarting Connect. Completed client registrations and access tokens are AES-256-GCM encrypted, purpose-bound, audience-bound, and survive restarts with unchanged configuration. Access tokens last one hour; refresh tokens and a per-session revocation endpoint are not implemented. Reconnect to renew access.

A dedicated `OAUTH_SIGNING_KEY` of at least 32 characters is preferred. If omitted, HKDF derives a purpose-specific encryption key from the existing OpenAI API key and service origin. Neither the key nor member hashes appear in client-visible credentials. Rotating that source key or the public origin invalidates OAuth credentials. Removing a member or replacing its access-code hash and redeploying revokes its existing OAuth access immediately on the new process.

## Storage and permissions

Approved files and indexes remain in the company's OpenAI project. The Drive configuration is a placeholder. All teammates should have separate access codes pointing to the same approved shared store. The server supports multiple stores administratively; the tool cannot choose one. Logs should not record tokens, codes, queries, or excerpts.

The desktop plugin includes `.mcp.json`; the ChatGPT web build references a registered app in `.app.json` and omits all MCP declarations. A plugin install does not create provider authorization or grant workspace access.
