import hashlib
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch


ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("fetch_wallpapers", ROOT / "tools/fetch-wallpapers.py")
fetcher = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fetcher)


class FetchWallpapersTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.source = self.root / "source.mov"
        self.source.write_bytes(b"test video payload")
        self.destination = self.root / "downloads"
        self.asset = {
            "filename": "wallpaper_1920x1080.mov",
            "url": self.source.as_uri(),
            "size": self.source.stat().st_size,
            "sha256": hashlib.sha256(self.source.read_bytes()).hexdigest(),
        }

    def test_download_and_checksum(self):
        target = fetcher.fetch(self.asset, self.destination)
        self.assertEqual(target.read_bytes(), self.source.read_bytes())
        self.assertEqual(list(self.destination.iterdir()), [target])

    def test_verified_file_does_not_download_again(self):
        target = fetcher.fetch(self.asset, self.destination)
        with patch.object(fetcher, "urlopen", side_effect=AssertionError("unexpected download")):
            self.assertEqual(fetcher.fetch(self.asset, self.destination), target)

    def test_bad_checksum_preserves_existing_file_and_cleans_partial(self):
        self.destination.mkdir()
        target = self.destination / self.asset["filename"]
        target.write_bytes(b"keep this file")
        with self.assertRaisesRegex(ValueError, "mismatch"):
            fetcher.fetch(dict(self.asset, sha256="0" * 64), self.destination)
        self.assertEqual(target.read_bytes(), b"keep this file")
        self.assertEqual(list(self.destination.iterdir()), [target])

    def test_bad_size_leaves_no_download(self):
        with self.assertRaisesRegex(ValueError, "mismatch"):
            fetcher.fetch(dict(self.asset, size=999), self.destination)
        self.assertEqual(list(self.destination.iterdir()), [])

    def test_failed_request_cleans_partial(self):
        with patch.object(fetcher, "urlopen", side_effect=OSError("offline")):
            with self.assertRaisesRegex(OSError, "offline"):
                fetcher.fetch(self.asset, self.destination)
        self.assertEqual(list(self.destination.iterdir()), [])

    def test_filename_cannot_escape_destination(self):
        with self.assertRaises(ValueError):
            fetcher.fetch(dict(self.asset, filename="../outside.mov"), self.destination)
        self.assertFalse((self.root / "outside.mov").exists())

    def test_cli_downloads_only_selected_resolution(self):
        (self.root / "wallpapers.json").write_text(json.dumps({"1080p": self.asset, "4k": {}}))
        with patch.object(fetcher, "THEME", self.root), patch.object(
            sys, "argv", ["fetch-wallpapers.py", "--size", "1080p", "--directory", str(self.destination)]
        ):
            fetcher.main()
        self.assertEqual((self.destination / self.asset["filename"]).read_bytes(), self.source.read_bytes())


if __name__ == "__main__":
    unittest.main()
