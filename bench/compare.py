import json
import sys
from pathlib import Path


def main():
    before = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
    after = json.loads(Path(sys.argv[2]).read_text(encoding="utf-8"))
    old = before["summary"]["median"]
    new = after["summary"]["median"]
    change = new - old
    percent = (change / old * 100) if old else None
    print("metric,previous,now,absolute_change,percent_change")
    print(f"median,{old:.3f},{new:.3f},{change:.3f},{percent if percent is not None else 'n/a'}")


if __name__ == "__main__":
    main()
