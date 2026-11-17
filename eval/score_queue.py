"""Validate the local queue transport traces and reproduce the reported metrics (standard library only)."""
import argparse
import json
import math
from pathlib import Path
from statistics import median


def require(value, message):
    if not value:
        raise ValueError(message)


def percentile_ms(values, fraction):
    ordered = sorted(values)
    return round(ordered[math.ceil(len(ordered) * fraction) - 1] / 1000, 3)


def summarize(raw):
    result = {}
    for mode in ("direct", "sqs"):
        for workers in (1, 4):
            batches = [b for b in raw["batches"] if (b["transport"], b["workers"]) == (mode, workers)]
            require(len(batches) == 3 and {b["round"] for b in batches} == {0, 1, 2}, "Missing or duplicated round")
            jobs = [j for b in batches for j in b["jobs"]]
            require(all(b["peakActive"] == workers and len(b["jobs"]) == 12 for b in batches), "Incomplete batch")
            require(all(0 <= j["submitMicros"] <= j["completionMicros"] and
                        0 <= j["waitMicros"] <= j["completionMicros"] for j in jobs), "Invalid duration")
            duration = median(b["batchMicros"] for b in batches) / 1_000_000
            result[f"{mode}{workers}"] = {
                "jobs": len(jobs), "medianBatchSeconds": round(duration, 4),
                "medianJobsPerSecond": round(12 / duration, 4),
                "batchSeconds": [round(b["batchMicros"] / 1_000_000, 4) for b in batches],
                "submissionP50ms": percentile_ms([j["submitMicros"] for j in jobs], .50),
                "submissionP95ms": percentile_ms([j["submitMicros"] for j in jobs], .95),
                "queueWaitP95ms": percentile_ms([j["waitMicros"] for j in jobs], .95),
                "completionP95ms": percentile_ms([j["completionMicros"] for j in jobs], .95),
            }
    require(len(raw["batches"]) == 12, "Unexpected extra arms")
    return {"configuration": {k: v for k, v in raw.items() if k != "batches"}, "arms": result,
            "sqsFourOverOne": round(result["sqs1"]["medianBatchSeconds"] / result["sqs4"]["medianBatchSeconds"], 3),
            "directFourOverOne": round(result["direct1"]["medianBatchSeconds"] / result["direct4"]["medianBatchSeconds"], 3)}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("speed_json", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    output = json.dumps(summarize(json.loads(args.speed_json.read_text(encoding="utf8"))), indent=2) + "\n"
    if args.output:
        args.output.write_text(output, encoding="utf8")
    else:
        print(output, end="")
