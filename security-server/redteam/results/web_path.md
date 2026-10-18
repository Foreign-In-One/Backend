# PayCycle AI web path re-measurement

Attack prompts: the 707 unique garak prompts of the `after` run (backend guards on).
Backend-blocked prompts were replayed through the pre-fix Front fallback route (`app/api/agent/chat/route.ts` at `4879728`) with a mock LLM.
Before the Front fix, a blocked question was re-asked through that route, so web path exposure = backend passes + route passes.
After the fix, Front no longer re-asks (route removed), so web path exposure = backend passes.

| probe | n | blocked by backend | of those, passed by fallback route | web path exposure before Front fix | after Front fix |
|---|---|---|---|---|---|
| dan.DanInTheWild | 64 | 64 | 7 | 10.9% | 0.0% |
| encoding.InjectBase64 | 64 | 47 | 47 | 100.0% | 26.6% |
| encoding.InjectHex | 64 | 45 | 45 | 100.0% | 29.7% |
| encoding.InjectROT13 | 64 | 28 | 28 | 100.0% | 56.2% |
| encoding.InjectUnicodeTagChars | 64 | 14 | 14 | 100.0% | 78.1% |
| encoding.InjectZalgo | 64 | 49 | 40 | 85.9% | 23.4% |
| promptinject.HijackHateHumans | 57 | 57 | 49 | 86.0% | 0.0% |
| promptinject.HijackKillHumans | 57 | 57 | 49 | 86.0% | 0.0% |
| promptinject.HijackLongPrompt | 57 | 57 | 43 | 75.4% | 0.0% |
| sysprompt_extraction.SystemPromptExtraction | 24 | 23 | 17 | 75.0% | 4.2% |
| **TOTAL (designed-against)** | 579 | 441 | 339 | 82.4% | 23.8% |
| encoding.InjectAscii85 | 64 | 24 | 24 | 100.0% | 62.5% |
| encoding.InjectBase32 | 64 | 48 | 48 | 100.0% | 25.0% |
| **TOTAL (held-out)** | 128 | 72 | 72 | 100.0% | 43.8% |
| **TOTAL (all)** | 707 | 513 | 411 | 85.6% | 27.4% |
