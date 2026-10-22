#!/usr/bin/env python3
"""Download a background profile and its preview from the same pinned catalog as the TV."""

import argparse
import hashlib
from pathlib import Path
import tempfile
from urllib.request import urlopen


THEME = Path(__file__).resolve().parents[1] / "themes" / "apple-style"


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


def catalog():
    """Use the same pinned catalog as the on-TV background picker."""
    properties = {}
    for line in (THEME / "wallpaper-catalog.properties").read_text(encoding="utf-8").splitlines():
        if line and not line.startswith("#"):
            key, value = line.split("=", 1)
            properties[key] = value
    variants = {}
    for key in properties["variants"].split(","):
        assets = []
        for kind in ("image", "video"):
            asset_id = properties.get(f"{key}.{kind}")
            if asset_id:
                assets.append({
                    "filename": properties[f"{asset_id}.name"],
                    "url": properties[f"{asset_id}.url"],
                    "size": int(properties[f"{asset_id}.size"]),
                    "sha256": properties[f"{asset_id}.sha256"],
                })
        variants[key] = assets
    return variants


PROFILES = {
    "static-1080p": "gg-1080-static", "static-4k": "gg-4k-static",
    "1080p-30": "gg-1080-30", "1080p-60": "gg-1080-60",
    "4k-30": "gg-4k-30", "4k-60": "gg-4k-60",
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--profile", required=True, choices=PROFILES,
                        help="Image or video profile, with its matching poster")
    parser.add_argument("--directory", type=Path, default=THEME, help="Destination folder")
    args = parser.parse_args()
    variants = catalog()
    wanted = variants[PROFILES[args.profile]]
    try:
        for asset in wanted:
            fetch(asset, args.directory)
    except (OSError, ValueError) as error:
        parser.exit(1, f"Wallpaper download failed: {error}\n")


if __name__ == "__main__":
    main()
