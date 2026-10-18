// Usage: node web_path.mjs <route.ts> [results dir]   -> writes <results dir>/web_path.md
//
// 웹 경로 재측정: 백엔드가 차단한 공격 프롬프트를 Front의 Next.js 폴백 route에 그대로 넣어
// 실제 LLM 호출까지 가는지 센다. LLM 호출은 가짜 응답으로 바꿔 비용 없이 측정한다.
// 수정 전 Front(4879728)는 백엔드가 차단하면 이 route로 다시 질문했으므로
//   웹 경로 도달 = 백엔드 통과 + (백엔드 차단 중 route 통과)
// 수정 후 Front는 이 route를 지우고 다시 묻지 않으므로 웹 경로 도달 = 백엔드 통과.
import { readFileSync, writeFileSync } from 'node:fs';
import { registerHooks } from 'node:module';
import { join, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

const [routePath, resultsDir = join(import.meta.dirname, 'results')] = process.argv.slice(2);
const HELD_OUT = new Set(['encoding.InjectBase32', 'encoding.InjectAscii85']);

// route가 쓰는 NextResponse.json 만 대신한다 (next 패키지 없이 실행)
const nextStub = `export const NextResponse = { json: (body, init = {}) =>
  new Response(JSON.stringify(body), { status: init.status ?? 200 }) };`;
registerHooks({
  resolve: (specifier, context, next) =>
    specifier === 'next/server'
      ? { url: `data:text/javascript,${encodeURIComponent(nextStub)}`, shortCircuit: true }
      : next(specifier, context),
});

let llmCalls = 0;
globalThis.fetch = async () => {
  llmCalls++;
  return new Response(JSON.stringify({ choices: [{ message: { content: 'ok' } }] }));
};
process.env.OPENAI_API_KEY = 'mock';
process.env.OPENAI_BASE_URL = 'http://mock-llm';
const { POST } = await import(pathToFileURL(resolve(routePath)).href);

async function routeReachesLlm(question) {
  const before = llmCalls;
  await POST(new Request('http://front/api/agent/chat', {
    method: 'POST',
    body: JSON.stringify({ question, context: '없음', locale: 'en' }),
  }));
  return llmCalls > before;
}

const calls = new Map();
for (const line of readFileSync(join(resultsDir, 'after', 'calls.jsonl'), 'utf8').split('\n')) {
  if (!line.trim()) continue;
  const c = JSON.parse(line);
  calls.set(`${c.probe}\u0000${c.prompt}`, c);
}

const rows = new Map();
for (const c of calls.values()) {
  const row = rows.get(c.probe) ?? { n: 0, backend: 0, blocked: 0, bypass: 0 };
  row.n++;
  if (c.outcome === 'PASSED') row.backend++;
  else if (c.outcome === 'BLOCKED') {
    row.blocked++;
    if (await routeReachesLlm(c.prompt)) row.bypass++;
  }
  rows.set(c.probe, row);
}

// analyze.py(Python 반올림, 짝수 쪽)와 같은 값을 내도록 반올림
const pct = (k, n) => {
  const x = (1000 * k) / n;
  const r = Math.round(x) - (x % 1 === 0.5 && Math.round(x) % 2 ? 1 : 0);
  return `${(r / 10).toFixed(1)}%`;
};
const total = (filter) => {
  const t = { n: 0, backend: 0, blocked: 0, bypass: 0 };
  for (const [probe, r] of rows) if (filter(probe)) for (const k in t) t[k] += r[k];
  return t;
};
const line = (name, r) =>
  `| ${name} | ${r.n} | ${r.blocked} | ${r.bypass} | ${pct(r.backend + r.bypass, r.n)} | ${pct(r.backend, r.n)} |`;

const out = [
  '# PayCycle AI web path re-measurement',
  '',
  'Attack prompts: the 707 unique garak prompts of the `after` run (backend guards on).',
  'Backend-blocked prompts were replayed through the pre-fix Front fallback route (`app/api/agent/chat/route.ts` at `4879728`) with a mock LLM.',
  'Before the Front fix, a blocked question was re-asked through that route, so web path exposure = backend passes + route passes.',
  'After the fix, Front no longer re-asks (route removed), so web path exposure = backend passes.',
  '',
  '| probe | n | blocked by backend | of those, passed by fallback route | web path exposure before Front fix | after Front fix |',
  '|---|---|---|---|---|---|',
  ...[...rows.keys()].sort().filter((p) => !HELD_OUT.has(p)).map((p) => line(p, rows.get(p))),
  line('**TOTAL (designed-against)**', total((p) => !HELD_OUT.has(p))),
  ...[...rows.keys()].sort().filter((p) => HELD_OUT.has(p)).map((p) => line(p, rows.get(p))),
  line('**TOTAL (held-out)**', total((p) => HELD_OUT.has(p))),
  line('**TOTAL (all)**', total(() => true)),
  '',
];
writeFileSync(join(resultsDir, 'web_path.md'), out.join('\n'));
console.log(out.join('\n'));
