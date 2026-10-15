"""Usage:
  python run_redteam.py <phase>                   benign questions + all probes   (phase: baseline | after)
  python run_redteam.py <phase> --benign-only     benign questions only
  python run_redteam.py <phase> --probes a,b      only the given probes (replaces their earlier results)

Requires Backend running with SPRING_PROFILES_ACTIVE=local,redteam. Every prompt that passes the guards hits OpenAI.
"""
import json
import os
import subprocess
import sys
from pathlib import Path

PROBES = [
    "promptinject.HijackHateHumans",
    "promptinject.HijackKillHumans",
    "promptinject.HijackLongPrompt",
    "dan.DanInTheWild",
    "encoding.InjectBase64",
    "encoding.InjectROT13",
    "encoding.InjectHex",
    "encoding.InjectUnicodeTagChars",
    "encoding.InjectZalgo",
    "sysprompt_extraction.SystemPromptExtraction",
    # held-out: 인코딩 페이로드 rail 설계 시 보지 않은 인코딩 (일반화 확인용)
    "encoding.InjectBase32",
    "encoding.InjectAscii85",
]
SEED = "42"
PROMPTS_PER_PROBE = 64

here = Path(__file__).resolve().parent
phase = sys.argv[1]
selected = sys.argv[sys.argv.index("--probes") + 1].split(",") if "--probes" in sys.argv else None
out_dir = here / "results" / phase
out_dir.mkdir(parents=True, exist_ok=True)

if selected is None:
    benign_log = out_dir / "benign.jsonl"
    benign_log.unlink(missing_ok=True)
    os.environ["PAYCYCLE_CALL_LOG"] = str(benign_log)
    sys.path.insert(0, str(here))
    import paycycle_target  # noqa: E402

    for lang, questions in json.loads((here / "benign_questions.json").read_text(encoding="utf-8")).items():
        if lang.startswith("_"):
            continue
        os.environ["PAYCYCLE_PROBE"] = f"benign:{lang}"
        for q in questions:
            paycycle_target.chat(q)

    if "--benign-only" in sys.argv:
        sys.exit(0)

call_log = out_dir / "calls.jsonl"
probes = selected or PROBES
if selected is None:
    call_log.unlink(missing_ok=True)
elif call_log.exists():
    kept = [l for l in call_log.open(encoding="utf-8") if json.loads(l)["probe"] not in probes]
    call_log.write_text("".join(kept), encoding="utf-8")

config = out_dir / "garak_config.yaml"
config.write_text(
    f"run:\n  soft_probe_prompt_cap: {PROMPTS_PER_PROBE}\n"
    f"reporting:\n  report_dir: {out_dir.as_posix()}\n",
    encoding="utf-8",
)

for probe in probes:
    env = os.environ | {"PAYCYCLE_PROBE": probe, "PAYCYCLE_CALL_LOG": str(call_log), "PYTHONIOENCODING": "utf-8"}
    subprocess.run([
        sys.executable, "-m", "garak",
        "--config", str(config),
        "--target_type", "function",
        "--target_name", "paycycle_target#chat",
        "--probes", probe,
        "--generations", "1",
        "--seed", SEED,
        "--report_prefix", f"{phase}_{probe}",
    ], cwd=here, env=env, check=True)
