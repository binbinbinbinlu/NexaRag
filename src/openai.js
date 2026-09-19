export async function openai(path, { method = 'POST', body, apiKey = process.env.OPENAI_API_KEY, fetchImpl = fetch } = {}) {
  if (!apiKey) throw new Error('OPENAI_API_KEY is required');
  const multipart = body instanceof FormData;
  const response = await fetchImpl(`https://api.openai.com/v1${path}`, {
    method,
    headers: { Authorization: `Bearer ${apiKey}`, ...(!multipart && body !== undefined ? { 'Content-Type': 'application/json' } : {}) },
    body: body === undefined ? undefined : multipart ? body : JSON.stringify(body),
    signal: AbortSignal.timeout(20000),
  });
  if (!response.ok) throw new Error(`OpenAI request failed (${response.status})`);
  return response.json();
}

export async function searchStore(vectorStoreId, query, limit) {
  const result = await openai(`/vector_stores/${encodeURIComponent(vectorStoreId)}/search`, {
    body: { query, max_num_results: limit, rewrite_query: true },
  });
  return result.data.map((item, index) => ({
    citation: `S${index + 1}`,
    file_id: item.file_id,
    filename: item.filename,
    score: item.score,
    excerpts: item.content.filter(c => c.type === 'text').map(c => c.text).join('\n').slice(0, 6000),
  }));
}
