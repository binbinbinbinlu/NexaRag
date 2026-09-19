# Fictional company test data

The four Markdown files in `documents/` form a small shared knowledge corpus:
employee handbook, expenses/travel, IT support, and Project Aurora. All facts and
people are invented. No company data or real credentials are included.

Use a separate demo vector store so fictional policies cannot appear in real company
answers. Run these commands from the repository root after setting the API key in
`.env`:

```powershell
node --env-file=.env src/admin.js create-store "NexaRag fictional demo"
```

Copy the returned store ID, then upload the four source documents.

Windows PowerShell:

```powershell
$demoStoreId = 'vs_REPLACE_WITH_RETURNED_ID'
Get-ChildItem samples/documents -Filter *.md | ForEach-Object {
    node --env-file=.env src/admin.js upload $demoStoreId $_.FullName
    if ($LASTEXITCODE -ne 0) { throw "Sample upload failed; check the printed file ID before retrying." }
}
```

macOS/Linux Terminal (zsh or bash):

```sh
demo_store_id='vs_REPLACE_WITH_RETURNED_ID'
for file in samples/documents/*.md; do
  node --env-file=.env src/admin.js upload "$demo_store_id" "$file" || break
done
```

Both loops stop after an upload failure. Check the output and printed file ID
before retrying, since preceding uploads may already have completed.

Keep the printed file IDs for cleanup. Uploads use the company's OpenAI API project
and can incur API charges. Repeating the command uploads duplicates.

Configure a demo member credential with this store ID following the root README.
After starting and hosting the service, connect your GPT using
[`docs/connect-your-gpt.md`](../docs/connect-your-gpt.md).

Ask the questions in [`questions.md`](questions.md) and compare the answers and
citations. Upload only `documents/*.md`, not this README or the answer sheet.
These files have been generated locally; they are not automatically indexed or
uploaded by cloning the repository.
