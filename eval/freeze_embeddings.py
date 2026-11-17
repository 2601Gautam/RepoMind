"""Regenerate the optional local CPU replay from a pinned source revision and MiniLM weights."""
import argparse
import csv
import gzip
import hashlib
import io
import json
from pathlib import Path
import subprocess
import zipfile

import numpy as np
import onnxruntime as ort
from tokenizers import Tokenizer

ROOT = Path(__file__).resolve().parents[1]
REVISION = "7988a6d76bc4e82169645eabc8e15bf05f37defb"
MODEL = "sentence-transformers/all-MiniLM-L6-v2@1110a243fdf4706b3f48f1d95db1a4f5529b4d41"
SUFFIXES = {".java", ".js", ".jsx", ".css", ".yml", ".sql"}

def read_sources():
    data = subprocess.check_output(["git", "archive", "--format=zip", REVISION, "src/main",
                                    "frontend/src", "migrations", "init.sql"], cwd=ROOT)
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        return {name: archive.read(name).decode("utf-8").replace("\r\n", "\n")
                for name in sorted(archive.namelist()) if Path(name).suffix in SUFFIXES}

def windows(sources):
    chunks = []
    for path, source in sources.items():
        lines = source.rstrip("\n").split("\n")
        starts = [0] if len(lines) <= 100 else range(0, len(lines), 85)
        for first in starts:
            last = min(first + 100, len(lines))
            content = f"File: {path} (lines {first + 1}-{last})\n\n" + "\n".join(lines[first:last])
            chunks.append(dict(path=path, start=first + 1, end=last, text=content))
    return chunks

def encode(model_dir, texts):
    tok = Tokenizer.from_file(str(model_dir / "tokenizer.json"))
    tok.enable_truncation(max_length=256)
    tok.enable_padding(pad_id=0, pad_token="[PAD]")
    opts = ort.SessionOptions()
    opts.intra_op_num_threads = 4
    model = ort.InferenceSession(str(model_dir / "model.onnx"), opts, providers=["CPUExecutionProvider"])
    vectors = []
    for offset in range(0, len(texts), 12):
        encoded = tok.encode_batch(texts[offset:offset+12])
        ids = np.array([e.ids for e in encoded], dtype=np.int64)
        mask = np.array([e.attention_mask for e in encoded], dtype=np.int64)
        inputs = {"input_ids": ids, "attention_mask": mask, "token_type_ids": np.zeros_like(ids)}
        tokens = model.run(None, {inp.name: inputs[inp.name] for inp in model.get_inputs()})[0]
        pooled = (tokens * mask[..., None]).sum(1) / mask.sum(1)[:, None]
        pooled /= np.linalg.norm(pooled, axis=1, keepdims=True)
        vectors.extend(pooled.round(7).tolist())
    return vectors

def read_directory_sources(source_dir, roots, suffixes):
    """Second-set mode: a directory snapshot with no Git history. Paths are relative to source_dir."""
    found = {}
    for top in roots:
        for path in sorted((source_dir / top).rglob("*")):
            if path.is_file() and path.suffix in suffixes:
                found[path.relative_to(source_dir).as_posix()] = path.read_bytes().decode("utf-8").replace("\r\n", "\n")
    return found

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("model_dir", type=Path, help="Pinned model.onnx and tokenizer.json")
    parser.add_argument("--set", dest="set_name", help="Golden set folder under eval/golden (default: the original repository)")
    parser.add_argument("--source-dir", type=Path, help="Required with --set: directory holding the frozen source snapshot")
    parser.add_argument("--roots", default="backend/src,frontend/src", help="Comma-separated folders inside --source-dir to index")
    parser.add_argument("--suffixes", default=".js,.jsx,.css", help="Comma-separated file suffixes to index")
    args = parser.parse_args()
    if args.set_name:
        if not args.source_dir:
            parser.error("--set requires --source-dir")
        sources = read_directory_sources(args.source_dir, args.roots.split(","), set(args.suffixes.split(",")))
        manifest = {path: hashlib.sha256(text.encode()).hexdigest() for path, text in sources.items()}
        revision = "directory-snapshot:" + hashlib.sha256(json.dumps(manifest, sort_keys=True).encode()).hexdigest()
        set_dir = ROOT / "eval/golden" / args.set_name
        (set_dir / "sources.sha256.json").write_text(json.dumps(manifest, indent=1, sort_keys=True), encoding="utf-8")
    else:
        sources = read_sources()
        revision = REVISION
        set_dir = ROOT / "eval/golden"
    chunks = windows(sources)
    cases_path = set_dir / "retrieval.tsv"
    with cases_path.open(encoding="utf-8", newline="") as file:
        cases = list(csv.DictReader(file, delimiter="\t"))
    for case in cases:
        if args.set_name:
            matches = [case["file"]] if case["file"] in sources else []
        else:
            matches = [p for p in sources if Path(p).name == case["file"]]
        if len(matches) != 1:
            raise ValueError(case["id"] + ": ambiguous or absent source")
        case["path"] = matches[0]
        case["start"], case["end"] = int(case["start"]), int(case["end"])
        assert 1 <= case["start"] <= case["end"] <= len(sources[matches[0]].splitlines()), case
    vectors = encode(args.model_dir, [c["text"] for c in chunks] + [q["question"] for q in cases])
    for record, vector in zip(chunks + cases, vectors, strict=True):
        record["embedding"] = vector
    fixture = {"sourceRevision": revision, "model": MODEL, "files": len(sources),
               "weightsSha256": hashlib.sha256((args.model_dir/"model.onnx").read_bytes()).hexdigest(),
               "labelsSha256": hashlib.sha256(cases_path.read_bytes().replace(b"\r\n",b"\n")).hexdigest(),
               "chunks": chunks, "cases": cases}
    out = set_dir / "minilm-replay.json.gz"
    out.write_bytes(gzip.compress(json.dumps(fixture, separators=(",",":")).encode(), mtime=0))
    print(f"{len(sources)} files, {len(chunks)} chunks, {len(cases)} questions -> {out}")

if __name__ == "__main__":
    main()

