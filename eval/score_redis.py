"""Audit and summarize a RedisImpactIT run; accepts plain or compressed JSON."""
import argparse
import gzip
import json
from math import ceil
from pathlib import Path


def describe(rows):
    times = sorted(row["microseconds"] for row in rows)
    return {
        "reads": len(rows),
        "p50Ms": times[ceil(len(times) * 0.50) - 1] / 1000,
        "p95Ms": times[ceil(len(times) * 0.95) - 1] / 1000,
    }


def load_run(path):
    data = path.read_bytes()
    if path.suffix == ".gz":
        data = gzip.decompress(data)
    return json.loads(data)


def require(condition, message):
    if not condition:
        raise ValueError(message)


def summarize(raw):
    n, rounds, reads = raw["users"], raw["rounds"], raw["readsPerPathPerRound"]
    lists, windows = raw["repositoryList"], raw["chatWindow"]
    for name, rows in [("repositoryList", lists), ("chatWindow", windows)]:
        require(len(rows) == rounds * reads * 2, "Unexpected observation count for " + name)
        pairs = {}
        for row in rows:
            r, i, person = row["round"], row["request"], row["person"]
            require(0 <= r < rounds and 0 <= i < reads and person == i % n, "Invalid case identity")
            require(row["microseconds"] >= 0, "Negative latency")
            if name == "repositoryList":
                require(row["listSql"] == int(row["mode"] != "hit"), "Unexpected list query count")
            pairs.setdefault((r, i), []).append(row["mode"])
        require(len(pairs) == rounds * reads, "Missing request pair")
        for (_, i), modes in pairs.items():
            expected = {"redis", "indexedSql"} if name == "chatWindow" else {
                "uncached", "miss" if i < n else "hit"}
            require(len(modes) == 2 and set(modes) == expected, "Mismatched request pair")
    subsets = {
        "uncachedList": [r for r in lists if r["mode"] == "uncached"],
        "cachedListMixed": [r for r in lists if r["mode"] != "uncached"],
        "cachedListHits": [r for r in lists if r["mode"] == "hit"],
        "cachedListMisses": [r for r in lists if r["mode"] == "miss"],
        "indexedSqlWindow": [r for r in windows if r["mode"] == "indexedSql"],
        "redisWindow": [r for r in windows if r["mode"] == "redis"],
    }
    results = {name: describe(rows) for name, rows in subsets.items()}
    for name, rows in subsets.items():
        if "List" in name:
            results[name]["listSqlStatements"] = sum(row["listSql"] for row in rows)
    per_round = [
        {name: describe([row for row in rows if row["round"] == r]) for name, rows in subsets.items()}
        for r in range(rounds)
    ]
    deltas = {}
    for metric in ("p50Ms", "p95Ms"):
        for label, before, after in [
                ("list", "uncachedList", "cachedListMixed"),
                ("window", "indexedSqlWindow", "redisWindow")]:
            deltas[label + "ReductionPct_" + metric] = round(
                (1 - results[after][metric] / results[before][metric]) * 100, 2)
    return {
        "config": {k: v for k, v in raw.items() if k not in ("repositoryList", "chatWindow")},
        "results": results, "reductions": deltas, "perRound": per_round,
    }


def summarize_appends(raw):
    rows = raw["samples"]
    rounds, writes = raw["rounds"], raw["timedWritesPerPath"]
    require(len(rows) == 2 * writes and writes % rounds == 0, "Incomplete append benchmark")
    require(raw["serverEvalshaDelta"] == writes and raw["serverEvalDelta"] == 0, "Unexpected script calls")
    samples = {}
    for name in ("threeCalls", "script"):
        selected = [row for row in rows if row["mode"] == name]
        expected = {(r, i) for r in range(rounds) for i in range(writes // rounds)}
        require(len(selected) == writes and {(r["round"], r["write"]) for r in selected} == expected,
                "Incomplete append pairs")
        stats = describe(selected)
        stats["writes"] = stats.pop("reads")
        stats["perRoundP95Ms"] = [describe([row for row in selected if row["round"] == r])["p95Ms"]
                                  for r in range(rounds)]
        samples[name] = stats
    return {"variants": samples, "warmupPerPath": raw["warmupPerPath"], "warmEvalshaCalls": writes,
            "warmEvalFallbacks": raw["serverEvalDelta"],
            "p95ReductionPct": round((1 - samples["script"]["p95Ms"] / samples["threeCalls"]["p95Ms"]) * 100, 2)}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("run", type=Path)
    parser.add_argument("--append", type=Path, help="Paired conversation-memory append observations")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    summary = summarize(load_run(args.run))
    if args.append:
        summary["append"] = summarize_appends(load_run(args.append))
    report = json.dumps(summary, indent=2) + "\n"
    if args.output:
        args.output.write_text(report, encoding="utf-8")
    else:
        print(report)
