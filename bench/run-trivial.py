import json
import os
import platform
import statistics
import subprocess
import sys
import time
from datetime import datetime, timezone
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
RESULTS = ROOT / "bench" / "results"


def machine():
    return {
        "os": platform.platform(),
        "python": platform.python_version(),
        "java": subprocess.run(["java", "-version"], capture_output=True, text=True).stderr.splitlines()[0],
        "docker": subprocess.run(["docker", "--version"], capture_output=True, text=True).stdout.strip(),
        "docker_compose": subprocess.run(["docker", "compose", "version"], capture_output=True, text=True).stdout.strip(),
    }


def main():
    runs = []
    for run in range(5):
        target = ROOT / "bench" / "tmp" / f"trivial-{run}"
        start = time.perf_counter()
        subprocess.run(
            [sys.executable, str(ROOT / "bench" / "generate_repo.py"), str(target), "--files", "10", "--seed", "20261113"],
            check=True,
            capture_output=True,
            text=True,
        )
        runs.append({"run": run + 1, "duration_ms": (time.perf_counter() - start) * 1000})
    values = [run["duration_ms"] for run in runs]
    result = {
        "name": "phase0-trivial-synthetic-repository",
        "commit": subprocess.run(["git", "rev-parse", "HEAD"], cwd=ROOT, capture_output=True, text=True, check=True).stdout.strip(),
        "date": datetime.now(timezone.utc).isoformat(),
        "machine": machine(),
        "config": {"files": 10, "seed": 20261113, "warmup_runs_discarded": 0, "measured_runs": 5},
        "runs": runs,
        "summary": {
            "median": statistics.median(values),
            "p95": sorted(values)[max(0, int(len(values) * 0.95) - 1)],
            "min": min(values),
            "max": max(values),
        },
    }
    RESULTS.mkdir(parents=True, exist_ok=True)
    output = RESULTS / "phase0-trivial.json"
    output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(output)


if __name__ == "__main__":
    main()
