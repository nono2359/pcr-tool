#!/usr/bin/env python3
"""Update the Japanese room-motion name master distributed by nono2359/pcr-tool."""
from __future__ import annotations
import argparse, json, re
from datetime import datetime, timezone
from pathlib import Path
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_MASTER = ROOT / "app" / "src" / "main" / "assets" / "spine" / "data" / "room-motion-names-ja.json"
DEFAULT_BASE_URL = "https://wthee.xyz"

def load_names(path: Path) -> dict[str, str]:
    if not path.exists():
        return {"COMMON": "\u5171\u901a\u30e2\u30fc\u30b7\u30e7\u30f3"}
    payload = json.loads(path.read_text("utf-8-sig"))
    items = payload.get("motions", payload)
    if isinstance(items, dict):
        return {str(key): str(value) for key, value in items.items() if value}
    return {str(item["id"]): str(item["name"]) for item in items if item.get("name")}

def available_motion_ids(base_url: str) -> list[str]:
    url = f"{base_url.rstrip('/')}/redive/jp/resource/spine/cysp_room/"
    with urlopen(Request(url, headers={"User-Agent": "PCR-Tool-Furniture-Master-Updater/1"}), timeout=60) as response:
        html = response.read()
    ids = {value.decode("ascii") for value in re.findall(rb"ROOM_SPINEUNIT_ANIMATION_([0-9]{6}|COMMON)\.cysp", html)}
    ids.add("COMMON")
    return sorted(ids, key=lambda value: (value != "COMMON", value))

def main() -> int:
    parser = argparse.ArgumentParser(description="Update the nono2359/pcr-tool furniture motion name master")
    parser.add_argument("--master", type=Path, default=DEFAULT_MASTER)
    parser.add_argument("--source", type=Path, help="Import a motions-array JSON or an ID-to-name JSON object")
    parser.add_argument("--set", action="append", default=[], metavar="ID=NAME", help="Add or replace one furniture motion name")
    parser.add_argument("--base-url", default=DEFAULT_BASE_URL)
    args = parser.parse_args()
    names = load_names(args.master)
    if args.source:
        names.update(load_names(args.source))
    for value in args.set:
        motion_id, separator, name = value.partition("=")
        if not separator or not name.strip():
            parser.error(f"--set must use ID=NAME: {value}")
        if motion_id != "COMMON" and not re.fullmatch(r"\d{6}", motion_id):
            parser.error(f"Motion ID must be six digits: {motion_id}")
        names[motion_id] = name.strip()
    available = available_motion_ids(args.base_url)
    ordered = sorted(set(available) | set(names), key=lambda value: (value != "COMMON", value))
    payload = {
        "updatedAt": datetime.now(timezone.utc).isoformat(),
        "motions": [{"id": motion_id, "name": names[motion_id]} for motion_id in ordered if motion_id in names],
    }
    args.master.parent.mkdir(parents=True, exist_ok=True)
    temporary = args.master.with_suffix(args.master.suffix + ".tmp")
    temporary.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", "utf-8")
    temporary.replace(args.master)
    missing = [motion_id for motion_id in available if motion_id != "COMMON" and motion_id not in names]
    print(f"Furniture master: {args.master}")
    print(f"Named: {len(names)} / available motions: {len(available)}")
    if missing:
        print(f"Missing ({len(missing)}): {', '.join(missing)}")
        print("Commit and push the updated JSON to publish it to the app.")
        return 2
    print("No unresolved motion IDs.")
    print("Commit and push the updated JSON to publish it to the app.")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
