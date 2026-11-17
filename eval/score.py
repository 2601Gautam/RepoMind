"""Score stored search traces and inspect signed-off case reviews using only the standard library."""
import argparse
import csv
import hashlib
import json
from math import ceil
from pathlib import Path

ROOT = Path(__file__).resolve().parent
RESULTS = ROOT / "results"


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def search_scores(report):
    scores = {}
    hits = {}
    for name, rows in report["variants"].items():
        if len(rows) != 40 or len({row["id"] for row in rows}) != 40:
            raise ValueError("Expected 40 distinct evaluation questions")
        ranks = {}
        times = []
        for row in rows:
            expected = row["expected"]
            ranking = row["ranked"]
            if len(ranking) != report["topK"] or len(row["lookupMicros"]) != report["timedPasses"]:
                raise ValueError("Incomplete observation for " + row["id"])
            rank = next((index for index, span in enumerate(ranking, 1)
                         if span["path"] == expected["path"] and span["start"] <= expected["start"]
                         and span["end"] >= expected["end"]), None)
            if rank != row["firstSupportingRank"]:
                raise ValueError("Inconsistent evidence rank for " + row["id"])
            ranks[row["id"]] = rank
            times.extend(row["lookupMicros"])
        hits[name] = {case for case, rank in ranks.items() if rank is not None}
        times.sort()
        scores[name] = {
            "hits": len(hits[name]), "questions": len(rows),
            "identifierHits": sum(case.startswith("ID") for case in hits[name]),
            "proseHits": sum(case.startswith("NL") for case in hits[name]),
            "mrrAt12": round(sum(1 / r for r in ranks.values() if r) / len(rows), 4),
            "lookupSamples": len(times),
            "lookupMs": {str(p): times[ceil(len(times) * p / 100) - 1] / 1000 for p in (50, 95)},
            "misses": sorted(set(ranks) - hits[name])
        }
    scores["gains"] = sorted(hits["candidate"] - hits["vector"])
    scores["losses"] = sorted(hits["vector"] - hits["candidate"])
    return scores


def generation_scores():
    cases = read_json(ROOT / "golden/generation.json")
    with (RESULTS / "generation-review.tsv").open(encoding="utf-8", newline="") as source:
        review = {r["id"]: r for r in csv.DictReader(source, delimiter="\t")}
    result = {}
    for version in ("before", "after"):
        answers = read_json(RESULTS / f"generation-{version}.json")
        if not len(cases) == len(review) == len(answers) == 24:
            raise ValueError("Incomplete generation run or review")
        by_feature = {}
        for case, answer in zip(cases, answers):
            case_id = case["id"]
            record = review[case_id]
            digest = hashlib.sha256(answer["answer"].encode()).hexdigest()
            if case_id != answer["id"] or record[version + "_sha256"] != digest:
                raise ValueError("Review does not match output for " + case_id)
            verdict = record[version + "_verdict"]
            if verdict not in {"pass", "fail"}:
                raise ValueError("Unreviewed answer " + case_id)
            feature = by_feature.setdefault(case["feature"], {"pass": 0, "cases": 0})
            feature["pass"] += verdict == "pass"
            feature["cases"] += 1
        result[version] = {
            "features": by_feature, "pass": sum(v["pass"] for v in by_feature.values()),
            "finishedWithinCap": sum(a["stop"] == "stop" for a in answers),
            "promptTokens": sum(a["tokens"]["prompt_tokens"] for a in answers),
            "completionTokens": sum(a["tokens"]["completion_tokens"] for a in answers),
        }
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--retrieval", type=Path, default=RESULTS / "retrieval-paired.json")
    parser.add_argument("--output", type=Path)
    parser.add_argument("--retrieval-only", action="store_true",
                        help="Score only search traces, e.g. for a second golden set that has no generation cases")
    args = parser.parse_args()
    score = {"retrieval": search_scores(read_json(args.retrieval))}
    if not args.retrieval_only:
        score["generation"] = generation_scores()
    output = json.dumps(score, indent=2) + "\n"
    if args.output:
        args.output.write_text(output, encoding="utf-8")
    else:
        print(output, end="")

