#!/usr/bin/env python3
"""Download selected custom wallpaper videos from a pinned Release and verify their contents."""

import argparse
import hashlib
import json
from pathlib import Path
import tempfile
from urllib.request import urlopen


THEME = Path(__file__).resolve().parents[1] / "themes" / "custom-version"


def matches(path, asset):
    if not path.is_file() or path.stat().st_size != asset["size"]:
        return False
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest() == asset["sha256"]


def fetch(asset, directory):
    name = asset["filename"]
    if Path(name).name != name or name in ("", ".", ".."):
        raise ValueError("Wallpaper filename must be a basename")
    target = directory / name
    if matches(target, asset):
        print(f"Verified existing {name}", flush=True)
        return target
    directory.mkdir(parents=True, exist_ok=True)
    temporary = None
    try:
        print(f"Downloading {name} ({asset['size'] / 1024 / 1024:.1f} MiB)", flush=True)
        with tempfile.NamedTemporaryFile(dir=directory, prefix=name + ".", suffix=".part", delete=False) as out:
            temporary = Path(out.name)
            with urlopen(asset["url"], timeout=60) as response:
                for chunk in iter(lambda: response.read(1024 * 1024), b""):
                    out.write(chunk)
        if not matches(temporary, asset):
            raise ValueError(f"Size or SHA-256 mismatch for {name}; existing file preserved")
        temporary.replace(target)
        print(f"Verified {name}", flush=True)
        return target
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--size", required=True, choices=("1080p", "4k", "both"),
                        help="Download only the resolution(s) needed by the target device")
    parser.add_argument("--directory", type=Path, default=THEME, help="Destination folder")
    args = parser.parse_args()
    manifest = json.loads((THEME / "wallpapers.json").read_text(encoding="utf-8"))
    sizes = ("1080p", "4k") if args.size == "both" else (args.size,)
    try:
        for size in sizes:
            fetch(manifest[size], args.directory)
    except (OSError, ValueError) as error:
        parser.exit(1, f"Wallpaper download failed: {error}\n")


if __name__ == "__main__":
    main()
