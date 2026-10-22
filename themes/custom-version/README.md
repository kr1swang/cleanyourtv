# Custom background library

The APK bundles the six-column Dock defaults in `config.json` and the pinned asset catalog in
`wallpaper-catalog.properties`. Images and movies are downloaded from
[wallpapers-2026-10-22](https://github.com/kr1swang/cleanyourtv/releases/tag/wallpapers-2026-10-22).
The previous Release remains available for older installations.

Open **Launcher settings → Background downloads**. Golden Gate offers static 1080p, static 4K,
1080p/30 fps, 1080p/60 fps, 4K/30 fps and 4K/60 fps. The picker shows download size, progress and
cached versions. A video profile also downloads its resolution's preview. The two frame rates share
that preview. All six files were uploaded to the new Release, including the original 60 fps files.

Downloads use HTTPS, stream on one background worker, check exact length and SHA-256, and atomically
publish a file only after verification. The current selection changes only after the entire profile
is available. Leaving the picker or pressing Cancel stops the job; verified assets remain reusable.
The next download removes abandoned partial files. Errors preserve the current background.

Files live under the app's private `files/wallpaper-library/` directory and survive normal updates.
**Clear unused downloads** preserves the active profile's files, including a shared preview. External
manually deployed files are never removed. Clearing app data removes downloads and selections.
On first use, Home downloads a static preview matching the physical display. Offline it uses the
plain background and retries on a later foreground visit, at most once every two minutes. An explicit
local image, None, or a supplied external wallpaper takes priority. No periodic update checker runs.

The selected profile uses the upstream wallpaper preference; external config, Dock order and other
preferences remain intact. A static profile disables legacy external video. A video profile plays its
fixed resolution/FPS; it never silently switches to another rendition. It starts from the beginning,
releases the decoder when Home stops, and keeps the verified preview as a fallback. The Home/YouTube/
Netflix service and Google Cast packages are not changed by this feature.

The catalog is versioned with the APK, not fetched from an unpinned latest Release. To add a background,
add its title, six profile entries and immutable asset URLs/sizes/hashes. The picker groups entries by
background. Upload assets before shipping the catalog. For another publisher, update the catalog's
allowed repository in `WallpaperCatalog.Asset` as well. The app now requests INTERNET permission for
these downloads; upstream descriptions of a network-free launcher do not apply to this fork.

## Manual or offline deployment

```bash
python3 tools/fetch-wallpapers.py --profile static-1080p
python3 tools/fetch-wallpapers.py --profile static-4k
python3 tools/fetch-wallpapers.py --profile 1080p-30
python3 tools/fetch-wallpapers.py --profile 1080p-60
python3 tools/fetch-wallpapers.py --profile 4k-30
python3 tools/fetch-wallpapers.py --profile 4k-60
```

The tool reads the same catalog as Android, verifies existing files and downloads only missing or
invalid assets. `--size 1080p`, `--size 4k` and `--size both` retain the legacy 60 fps filenames while
also fetching the matching previews. Downloaded media is ignored by Git.

Copy the chosen image to `/sdcard/Android/data/<package>/files/wallpaper.png` and, for video, copy the
movie alongside it. Merge only `video_wallpaper` in the existing external JSON: `[]` for static or a
one-element filename list for a fixed video profile. Keep unknown keys. Select Follow theme to leave
a downloaded on-TV selection; this resets other on-TV overrides too, so preserve them first.
Do not invoke `tools/theme.sh` just to switch a profile: it replaces the theme and resets choices.

## Verification

The JVM tests cover catalog validation, display-sized default selection, shared-preview cache reuse,
offline cache reads, cancellation, truncated/oversized responses, checksum failures and partial cleanup.
The Python tests check the shared six-profile catalog and command-line downloader. Hardware validation
of decoder performance, color transitions, remote navigation and Cast is still required before
claiming those results. The settings UI can be built without a connected TV.
