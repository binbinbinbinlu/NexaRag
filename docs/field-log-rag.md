# Ask NexaRag about the construction field log

The importer reads the live field-log export and checklist, then indexes one text
document per project plus a project catalog in the existing NexaRag member store.
The existing `search_company_knowledge` MCP tool can search these documents without
changing your ChatGPT connection or access code.

Included: all baseline log entries, published Drive/Slack contributions, project
IDs and addresses, dates, trades, original source links, open and completed checklist
items, and existing photo captions/descriptions/links. It does not independently
analyze photo pixels, ingest unpublished/raw Drive or Slack history, or infer that
proposed work was completed. Historical attention summaries are marked as historical.

## Refresh

From the NexaRag repository, with Node 22+ and the existing private credentials:

```sh
node --env-file=.env --env-file=NEXA-construction-field-log/.pipeline/publish.env scripts/sync-field-log.js --dry-run
node --env-file=.env --env-file=NEXA-construction-field-log/.pipeline/publish.env scripts/sync-field-log.js
```

`--dry-run` reads the live source and creates a private local export without OpenAI
writes. `OPENAI_API_KEY` and member configuration use the normal NexaRag configuration.
The second env file provides `PIPELINE_PUBLISH_TOKEN` and `SITES_BYPASS_TOKEN`.
An operator may store it elsewhere and change the path. Never commit these files.
All members sharing the target vector store can search imported project records.

Optional `FIELD_LOG_URL` changes the HTTPS source origin. If members use multiple
stores, explicitly select an approved store with `FIELD_LOG_VECTOR_STORE_ID`.
Outputs and the last-run report are under ignored `data/field-log-sync/`.

This is an on-demand snapshot import; it does not create a recurring schedule.
Refresh after field-log changes. Documents state their capture date. Unchanged
documents are reused within a UTC day. Changed documents are indexed before older
managed attachments are removed. Unrelated documents are untouched. Retired uploaded
files remain in OpenAI Files for recovery; only their vector-store attachments are
removed. A failed run may leave additional uploads and can be retried. The local
lock prevents concurrent runs on this machine; use one designated import runner.

The live export API supplies baseline and publications. Checklist status is decoded
from the site's server-rendered JSON without executing scripts. A changed page format
or unavailable checklist database fails the import instead of assuming tasks are open.

## Ask questions

Select your NexaRag/NexaDemo connection in ChatGPT and ask:

- Use NexaRag to summarize the latest recorded updates for project 8620. Cite sources.
- What open checklist items are recorded for project 916, and what is the snapshot date?
- What did the logs say about the marked TJI at Clyde Hill?
- List the projects in the construction field log catalog.

Search returns selected evidence, not a full database scan. Use catalog/checklist
snapshot counts for their stated date; do not infer arbitrary totals from search
matches. The original Site and linked media retain their own access controls.

OpenAI automatically chunks and indexes uploaded text; see the
[official retrieval guide](https://developers.openai.com/api/docs/guides/retrieval).
