# Render deployment

The existing service is https://nexarag-fypg.onrender.com. Deploy v2 from `master`; no new vector store is needed. Docker installs the pinned pnpm version and frozen dependency lockfile, then runs Node as an unprivileged user.

| Setting | Value |
| --- | --- |
| Runtime | Docker |
| Dockerfile | ./Dockerfile |
| Health check | /health |
| HOST | 0.0.0.0 |
| OPENAI_API_KEY | Private company OpenAI project key |
| MEMBERS_JSON | Array of member IDs, token hashes, and approved vector store IDs |
| OAUTH_SIGNING_KEY | Optional dedicated secret of at least 32 characters |

Render supplies PORT and RENDER_EXTERNAL_URL. Leave PUBLIC_BASE_URL unset unless using a custom HTTPS domain; never deploy the localhost value. Keep the existing environment values when updating the service. No secret belongs in GitHub or plugin manifests.

For a new service, connect the private repository and create a Blueprint from `render.yaml` or a Docker web service on master. Configure the variables, deploy, and wait for health. Test public discovery, a 401 on unauthenticated `/mcp`, OAuth sign-in, SDK initialize/list/call, and a real sample retrieval before onboarding users.

The free instance can sleep while idle and cause connection timeouts. Its local filesystem is ephemeral. OAuth client registrations and completed access tokens survive restarts through encrypted credentials; pending browser flows and authorization codes do not. Rate limits are per process. Choose production hosting and an organizational identity provider separately before a dependable company rollout. This migration does not upgrade the plan or purchase services.

OpenAI indexing/retrieval costs are separate from ChatGPT and hosting charges. The service's health endpoint proves liveness, not search quality.
