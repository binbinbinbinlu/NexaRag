# Deploy NexaRag to Render

The root `render.yaml` defines a Docker web service with HTTPS, a health check,
and private environment inputs. It uses the Free instance for the initial demo.
Free instances sleep after idle time and are not suitable for production reliability.
Choose an appropriate paid instance separately before a team production rollout.

## Required configuration

Prepare an OpenAI API project key and an indexed vector store using the root README
or sample upload instructions. Generate a NexaRag token using `node src/admin.js token`.
Keep its raw token privately for GPT Action authentication.

In Render, supply these environment variables:

| Name | Value |
| --- | --- |
| `OPENAI_API_KEY` | Company API project key |
| `MEMBERS_JSON` | Entire member configuration array, with token hashes and store IDs |

Example shape only (replace both placeholders):

```json
[{"id":"demo-owner","tokenHash":"REPLACE_WITH_GENERATED_SHA256_HASH","vectorStoreId":"vs_REPLACE"}]
```

The server reads `MEMBERS_JSON` instead of a mounted members file when present.
Invalid or empty JSON fails startup. The public Action schema uses Render's injected
`RENDER_EXTERNAL_URL` automatically. Set `PUBLIC_BASE_URL` only if using a custom
HTTPS domain; do not copy the local localhost value to Render.

## Create the service

1. Sign in to Render and connect the private `binbinbinbinlu/NexaRag` GitHub repository.
   Limit the GitHub app's repository access to NexaRag where supported.
2. Create a new Blueprint from that repository and its `render.yaml` on `master`.
3. Supply the two environment values above and review the Free instance selection.
4. Deploy and wait for the service to report healthy.
5. Open the actual service URL's `/health` and `/openapi.json` endpoints.
6. Test an authorized search against a known indexed fact and a request without a
   token (which must return 401).
7. Import that URL's `/openapi.json` into the custom GPT and configure its Bearer token.

Do not commit secret values. The Docker image excludes `.env`, member files, and
company documents. Restart/redeploy after changing the environment member list.
An HTTP 200 health check proves liveness only; it does not prove indexing or OpenAI
credentials work. A real authenticated search is required before declaring the
deployment usable.

The service uses no local persistent data. Uploaded source files and the retrieval
index reside in the company's OpenAI project. OpenAI usage is billed separately.
Render's Free tier has usage limits and idle startup delays; a cold start can cause
a GPT Action request to time out. Warm-up testing does not eliminate this production
limitation.

References: [Blueprint configuration](https://render.com/docs/blueprint-spec),
[Render environment variables](https://render.com/docs/environment-variables),
and [Free instance limitations](https://render.com/docs/free).
