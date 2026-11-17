import argparse
import hashlib
import json
import random
import subprocess
from pathlib import Path


def generate(target: Path, files: int, seed: int):
    rng = random.Random(seed)
    target.mkdir(parents=True, exist_ok=True)
    for index in range(files):
        extension = [".java", ".py", ".js", ".md"][index % 4]
        folder = target / f"module_{index % 20:02d}"
        folder.mkdir(exist_ok=True)
        path = folder / f"file_{index:05d}{extension}"
        lines = [
            f"// generated file {index}",
            f"public class Generated{index} {{",
            f"    static final int VALUE = {rng.randrange(1_000_000)};",
            "    static final String TAB_VALUE = \"\\t\";",
            "}",
        ]
        if index % 37 == 0:
            lines.append("x" * 4096)
        path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    manifest = {
        "files": files,
        "seed": seed,
        "sha256": hashlib.sha256(
            "".join(str(path.relative_to(target)) for path in sorted(target.rglob("*")) if path.is_file()).encode()
        ).hexdigest(),
    }
    (target / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    subprocess.run(["git", "init", "--quiet"], cwd=target, check=True)
    subprocess.run(["git", "add", "."], cwd=target, check=True)
    subprocess.run(
        ["git", "-c", "user.name=RepoMind Bench", "-c", "user.email=bench@example.invalid", "commit", "--quiet", "-m", "synthetic repository"],
        cwd=target,
        check=True,
    )
    return manifest


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("target", type=Path)
    parser.add_argument("--files", type=int, default=10)
    parser.add_argument("--seed", type=int, default=20261113)
    args = parser.parse_args()
    print(json.dumps(generate(args.target, args.files, args.seed), indent=2))
