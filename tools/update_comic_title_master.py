#!/usr/bin/env python3
"""Update the bundled Japanese one-panel comic title master."""

from __future__ import annotations

import argparse
import json
import re
import urllib.request
from pathlib import Path

SOURCE_URL = (
    "https://raw.githubusercontent.com/esterTion/redive_master_db_diff/master/"
    "v1_20eabfb5cb9604b2c7cc630c83181d4dc3e7aca4d21b5979a3e6f45391d97549.sql"
)
ROW_PATTERN = re.compile(
    r'^INSERT INTO .* VALUES \(\d+, "(.*)", \d+, (\d+)\);$'
)
DEFAULT_OUTPUT = (
    Path(__file__).resolve().parents[1]
    / "app"
    / "src"
    / "main"
    / "assets"
    / "comic-titles-ja.json"
)


def load_source(source: str | None) -> str:
    if source:
        return Path(source).read_text(encoding="utf-8")
    with urllib.request.urlopen(SOURCE_URL, timeout=30) as response:
        return response.read().decode("utf-8")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", help="Use a local SQL file instead of downloading it")
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    args = parser.parse_args()

    titles: dict[int, str] = {}
    for line in load_source(args.source).splitlines():
        match = ROW_PATTERN.match(line)
        if match:
            title, unit_id = match.groups()
            titles[int(unit_id)] = title.replace('""', '"')

    if len(titles) < 150:
        raise RuntimeError(f"Unexpected comic title count: {len(titles)}")

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        json.dumps(dict(sorted(titles.items())), ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
        newline="\n",
    )
    print(f"Updated {args.output} ({len(titles)} titles)")


if __name__ == "__main__":
    main()