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
        self.assertEqual(set(catalog), {
            "fhd-static", "fhd-30", "fhd-60", "4k-static", "4k-30", "4k-60",
        })
        assets = {}
        for variant, entries in catalog.items():
            self.assertEqual(len(entries), 1 if variant.endswith("static") else 2)
            for asset in entries:
                self.assertEqual(Path(asset["filename"]).name, asset["filename"])
                self.assertEqual(asset["url"].rsplit("/", 1)[1], asset["filename"])
                self.assertRegex(asset["sha256"], r"^[0-9a-f]{64}$")
                self.assertGreater(asset["size"], 0)
                self.assertIn("/releases/download/wallpapers-2026-10-22/", asset["url"])
                assets[asset["filename"]] = asset
        self.assertEqual(len(assets), 6)
        for resolution in ("fhd", "4k"):
            self.assertEqual(catalog[f"{resolution}-static"][0], catalog[f"{resolution}-30"][0])
            self.assertEqual(catalog[f"{resolution}-30"][0], catalog[f"{resolution}-60"][0])

    def test_each_cli_profile_downloads_its_poster_and_only_its_movie(self):
        for profile in fetcher.catalog():
            with self.subTest(profile=profile), patch("sys.argv", ["fetch-wallpapers.py", "--profile", profile]), patch.object(fetcher, "fetch") as download:
                fetcher.main()
                got = [call.args[0] for call in download.call_args_list]
                self.assertEqual(got, fetcher.catalog()[profile])

    def test_corrupt_download_does_not_overwrite_existing_file(self):
        asset = fetcher.catalog()["fhd-static"][0]
        with tempfile.TemporaryDirectory() as tmp:
            folder = Path(tmp)
            target = folder / asset["filename"]
            target.write_bytes(b"old")
            with patch.object(fetcher, "urlopen", return_value=io.BytesIO(b"wrong")):
                with self.assertRaises(ValueError):
                    fetcher.fetch(asset, folder)
            self.assertEqual(target.read_bytes(), b"old")
            self.assertEqual(list(folder.glob("*.part")), [])
