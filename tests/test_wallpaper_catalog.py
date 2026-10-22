"""The Android picker and offline deployment share one six-profile release catalog."""
import importlib.util
import io
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("fetch_wallpapers", ROOT / "tools/fetch-wallpapers.py")
fetcher = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fetcher)


class WallpaperCatalogTest(unittest.TestCase):
    def test_six_profiles_share_pinned_release_and_resolution_posters(self):
        catalog = fetcher.catalog()
        self.assertEqual(len(catalog), 6)
        assets = {}
        for variant, entries in catalog.items():
            self.assertEqual(len(entries), 1 if variant.endswith("static") else 2)
            for asset in entries:
                self.assertEqual(Path(asset["filename"]).name, asset["filename"])
                self.assertRegex(asset["sha256"], r"^[0-9a-f]{64}$")
                self.assertGreater(asset["size"], 0)
                self.assertIn("/releases/download/wallpapers-2026-10-22/", asset["url"])
                assets[asset["filename"]] = asset
        self.assertEqual(len(assets), 6)
        for resolution in ("1080", "4k"):
            self.assertEqual(catalog[f"gg-{resolution}-static"][0], catalog[f"gg-{resolution}-30"][0])
            self.assertEqual(catalog[f"gg-{resolution}-30"][0], catalog[f"gg-{resolution}-60"][0])

    def test_each_cli_profile_downloads_its_poster_and_only_its_movie(self):
        for profile in fetcher.PROFILES:
            with self.subTest(profile=profile), patch("sys.argv", ["fetch-wallpapers.py", "--profile", profile]), patch.object(fetcher, "fetch") as download:
                fetcher.main()
                got = [call.args[0] for call in download.call_args_list]
                self.assertEqual(got, fetcher.catalog()[fetcher.PROFILES[profile]])

    def test_legacy_cli_preserves_60fps_filenames(self):
        with patch("sys.argv", ["fetch-wallpapers.py", "--size", "both"]), patch.object(fetcher, "fetch") as download:
            fetcher.main()
            movies = [c.args[0] for c in download.call_args_list if c.args[0]["filename"].endswith(".mov")]
            self.assertEqual([m["filename"] for m in movies], ["wallpaper_1920x1080.mov", "wallpaper_3840x2160.mov"])
            self.assertTrue(all(m["url"].endswith("_60fps.mov") for m in movies))

    def test_corrupt_download_does_not_overwrite_existing_file(self):
        asset = fetcher.catalog()["gg-1080-static"][0]
        with tempfile.TemporaryDirectory() as tmp:
            folder = Path(tmp)
            target = folder / asset["filename"]
            target.write_bytes(b"old")
            with patch.object(fetcher, "urlopen", return_value=io.BytesIO(b"wrong")):
                with self.assertRaises(ValueError):
                    fetcher.fetch(asset, folder)
            self.assertEqual(target.read_bytes(), b"old")
            self.assertEqual(list(folder.glob("*.part")), [])
