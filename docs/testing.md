# Testing NexaRag plugins

Install pinned dependencies with `pnpm install --frozen-lockfile`; run `node --test`. Offline tests use fake credentials and stub retrieval. They do not spend OpenAI credits.

The suite checks official MCP SDK initialization/tool listing/tool calling; input and output schemas; citations and empty evidence; OAuth discovery, dynamic registration, browser binding, PKCE, client/resource/redirect binding, single-use codes, expiry, tampering, rotation and revocation; unauthenticated rejection; origin and body limits; per-member rate limits; sanitized upstream failures; admin token generation; and ChatGPT package app references without desktop MCP declarations.

GitHub Actions runs the suite on Windows/Linux/macOS with Node 22 and 24. A local test pass does not prove every hosted matrix job passed or that a ChatGPT account is connected.

## Live smoke test

Run `node --env-file=.env scripts/smoke-mcp.js https://nexarag-fypg.onrender.com data/gpt-token.txt` on the original setup machine. Use your own private code file elsewhere. This performs OAuth registration, consent, code exchange, official SDK tool discovery, and a real retrieval; it prints only a status summary and document filenames. The request can incur OpenAI usage. It does not install a plugin or prove answer quality in ChatGPT.

## Answer evaluation

Install/connect the plugin and use samples/questions.md. Record prompt, selected tool, returned sources, final answer, citations, and pass/fail. Verify known facts, unsupported facts, combined-source questions, and hostile text treated as evidence rather than instructions. Ask the leave-notice question and Aurora budget question first. Require an answer citation to match a returned excerpt. Do not interpret an HTTP 200 with irrelevant sources as a successful answer.

Test a second member independently. Revoke its hash and confirm old OAuth access fails. Check expiry prompts reconnection. A missing Plugins or developer-mode control is an account/workspace restriction, not a server test failure.
