# Architecture and access model

## Request flow

1. A teammate asks their custom GPT a company question.
2. The GPT sends a query and optional result limit to `POST /search` using its
   configured Bearer credential.
3. The server hashes the credential and compares it with configured member hashes.
4. The server chooses the member's vector store, applies a rate limit, and validates
   the request. A caller cannot supply a store ID or change permissions.
5. OpenAI searches the store and returns document excerpts. The server returns
   filenames, file IDs, relevance scores, excerpts, and source identifiers.
6. The custom GPT uses those excerpts to compose and cite its answer.

The service performs retrieval, not answer generation. Answer quality and citation
faithfulness must also be checked in the GPT; passing API tests alone cannot verify
the model's responses. Source identifiers such as S1 are local to one search.

## Components

| File | Responsibility |
| --- | --- |
| `src/server.js` | HTTP routing, authentication, configuration validation, limits |
| `src/openai.js` | OpenAI transport and search result formatting |
| `src/admin.js` | Offline administrator commands for credentials and documents |
| `src/schema.js` | GPT Action OpenAPI definition |
| `docs/openapi.json` | Importable schema with a placeholder deployment domain |
| `config/source.example.json` | Future Drive source placeholder; not consumed by the service |

## Data and trust boundaries

Use the same vector store ID in all member entries for this company's shared
knowledge model. Member credentials are separate so integrations can be revoked
independently. The credential authenticates a GPT integration, not its human user.
Sharing a configured GPT also grants its users access to this document collection.

Original files and their index reside in the company's OpenAI API project. Queries
go to OpenAI for retrieval; returned excerpts enter the teammate's ChatGPT
conversation. The service keeps no persistent query history. Hosting platform logs
must be configured separately so they do not capture request bodies or credentials.

The OpenAI key grants administrative API access and stays server-side. Teammate
tokens only authorize search in this service. Store hashes rather than raw tokens
in the member file. Public health and schema endpoints contain no document data.

## Current boundaries

- Drive integration is a placeholder; upload, replacement, and deletion are manual.
- No OAuth/SSO, document-level ACL synchronization, or per-human identity is present.
- Rate limits are in memory, per credential, per server process.
- Health is a liveness check and does not test OpenAI connectivity or index readiness.
- Search returns up to five excerpts, each truncated to 6,000 characters; context
  may be incomplete. Relevance scores are not a guarantee of factual support.
- The GPT instructions discourage following instructions inside documents; they
  are not an absolute defense against prompt injection.
- Removing access cannot erase previously retrieved conversation content.

Future automatic Drive ingestion should reconcile a manifest of Drive IDs,
modification times, and OpenAI file IDs, with explicit handling of deleted files,
failed indexing, retries, and duplication. Keep the current manual ingestion path
until the company's Drive provider and folder are specified.
