# Operator runbook

## Initial deployment

Follow the commands in the root README to create the store, ingest a small approved
document, and issue member credentials. Keep production secrets outside source
control. Deploy the Docker image behind an HTTPS reverse proxy and mount the
members JSON file read-only. Set the public URL to the externally reachable origin.

For this project every member entry must reference the same shared store. Verify
that assumption when provisioning accounts; the service also supports separate
stores, so it does not enforce this organizational policy automatically.

Before onboarding teammates, complete the live checks in `docs/testing.md`.

## Onboard or revoke a GPT

Run `node src/admin.js token` privately, save the hash in the member configuration,
and give the raw token to the intended GPT owner through a private channel. Restart
the service to reload configuration. The owner enters the token in the Action's
Bearer authentication field. Never put it in GPT instructions.

Remove the member entry and restart to revoke access. If rotating a credential,
replace its hash, restart, and update the GPT Action credential. Old-token requests
must return 401. Revoking the last member requires stopping the service: startup
intentionally refuses an empty member list.

## Maintain documents

Keep an administrator inventory of source filename, Drive source, upload date,
OpenAI file ID, and vector store ID. Do not commit company content or this inventory
if it contains sensitive metadata. Repeated uploads create duplicate files.

For an update, upload the replacement, wait for indexing, check a representative
query, and then delete the old file. Deleting an OpenAI file removes it from all
stores that reference it. Search removal can be delayed; if a document must become
unavailable immediately, pause access until removal is verified.

An upload can succeed before attachment or indexing fails. Use the printed file ID
to inspect the API project's file/store state before retrying; otherwise retries
can leave duplicates or orphaned uploads. Indexing timeout does not imply deletion
or failure of the uploaded file.

## Troubleshooting

| Symptom | Check |
| --- | --- |
| Startup fails | API key exists, members JSON is valid, IDs/hashes are unique, HTTPS URL is valid |
| 401 | GPT Bearer token, corresponding hash, and whether the service restarted after edits |
| 400 | JSON content type, nonempty query no longer than 2,000 characters, limit 1–5 |
| 413 | Request body exceeds 16 KiB |
| 429 | Wait for Retry-After; examine usage of the specific GPT credential |
| 502 | OpenAI credentials, API project access, vector store ID, connectivity, upstream availability |
| No useful evidence | Indexing completed, correct store selected, source text extractable, question precise |
| ChatGPT cannot connect | Public HTTPS routing, Action schema server URL, workspace Action domain policy |

The endpoint intentionally hides upstream error details from clients. Diagnose
using the company's API dashboard and infrastructure monitoring without logging
secrets or company excerpts. Back up member hashes and the source inventory securely.

## Release and rollback

Run `node --test` before release. GitHub Actions runs the same offline suite on
Windows/Linux/macOS and Node 22/24. Deploy an immutable image tied to the tested commit.
After deployment, verify health, authorized retrieval, unauthorized rejection, and
one GPT answer. Roll back the image if these fail. Document uploads and deletions
are independent of image deployment and are not reversed by a code rollback.
