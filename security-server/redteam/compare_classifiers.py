"""Usage: python compare_classifiers.py <hf_model_id>  (one model per process to bound memory)"""
import json
import os
import sys
import time
from pathlib import Path

import psutil
import torch
from transformers import AutoModelForSequenceClassification, AutoTokenizer

model_id = sys.argv[1]
data = json.loads((Path(__file__).parent / "model_selection_set.json").read_text(encoding="utf-8"))

tok = AutoTokenizer.from_pretrained(model_id)
model = AutoModelForSequenceClassification.from_pretrained(model_id, dtype=torch.bfloat16).eval()
labels = model.config.id2label
attack_idx = next(
    (i for i, n in labels.items() if any(k in n.upper() for k in ("INJECT", "JAILBREAK", "MALICIOUS", "UNSAFE"))),
    1,
)


def score(text: str) -> float:
    with torch.no_grad():
        logits = model(**tok(text, return_tensors="pt", truncation=True, max_length=512)).logits.float()
    return logits.softmax(-1)[0][attack_idx].item()


print(f"== {model_id}  labels={labels}  attack_idx={attack_idx}")
fp = tp = nb = na = 0
start = time.time()
for kind in ("benign", "attack"):
    for lang, texts in data[kind].items():
        scores = [score(t) for t in texts]
        flagged = sum(s >= 0.5 for s in scores)
        print(f"{kind:6} {lang}: flagged {flagged}/{len(texts)}  {[round(s, 2) for s in scores]}")
        if kind == "benign":
            fp, nb = fp + flagged, nb + len(texts)
        else:
            tp, na = tp + flagged, na + len(texts)
n = nb + na
print(f"SUMMARY FPR={fp}/{nb} ({100 * fp / nb:.0f}%)  TPR={tp}/{na} ({100 * tp / na:.0f}%)  "
      f"RSS={psutil.Process(os.getpid()).memory_info().rss // 2**20}MB  latency={1000 * (time.time() - start) / n:.0f}ms/req")
