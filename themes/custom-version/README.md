# Custom theme

Six columns with YouTube, Netflix, Twitch, Spotify, Moonlight and Google Play Store.

`video_wallpaper` lists local video renditions in the launcher's external files directory:

```json
"video_wallpaper": ["wallpaper_3840x2160.mov", "wallpaper_1920x1080.mov"]
```

Filename suffixes `_WIDTHxHEIGHT.ext` declare the resolution and must match the actual video.
Array order does not set priority. The player chooses the smallest available rendition covering
the physical output mode; if none is large enough or the mode is unknown, it uses the largest.
It uses physical dimensions, not Android's possibly scaled UI size. Selection is checked when
playback starts and when the display mode changes. An unchanged selection keeps playing.
Missing or empty files are skipped; if none are available, the static wallpaper remains.
A single string is also supported. A filename without dimensions is used as a fallback when
no sized rendition is available; if several such files exist, the first in the list is used.
This custom extension plays it silently in a loop while Home is visible and resumed.
Leaving Home releases the player and decoder. App-colour glow and the sky layer are
suppressed while a video background is configured and its file exists.
Set `video_wallpaper` to `[]` (or an empty string) to disable video playback.

The build packages `config.json` and `wallpaper.png` directly from this folder as defaults.
Clearing app storage restores six columns, the Dock order and the static wallpaper on next launch.
External config keys, wallpaper files and on-TV choices can still override those defaults.
The video files remain outside the APK:

- `wallpaper_3840x2160.mov`: original deployment copy, 3840×2160 HEVC 10-bit at 60 fps.
- `wallpaper_1920x1080.mov`: 1920×1080 HEVC 10-bit rendition at 60 fps.
- `wallpaper.png`: the exact first frame of the deployment video, stored losslessly at 3840×2160,
  shown during preparation or if playback fails. Bundled at full resolution without conversion.
  The launcher uses ARGB_8888 to preserve gradients.

Videos are [Release attachments](https://github.com/kr1swang/cleanyourtv/releases/tag/wallpapers-2026-10-21),
not Git or Git LFS files. `wallpapers.json` pins their URLs, byte sizes and SHA-256 checksums.
The 4K file is about 262 MiB; the 1080p file is about 117 MiB. From the repository root:

```sh
python3 tools/fetch-wallpapers.py --size 1080p
python3 tools/fetch-wallpapers.py --size 4k
# Use this instead when both resolutions are needed:
python3 tools/fetch-wallpapers.py --size both
```

The tool downloads only the selected files, verifies them before replacing local copies, and skips
existing files whose size and checksum match. Downloads stay ignored by Git. Building the APK does
not require either video; `config.json` and `wallpaper.png` remain in the repository.

Choose a deployment profile using the [deployment instructions](../../PROMPT.md). All profiles use
one APK; only the external config and deployed media differ.

| Profile | Download option | External `video_wallpaper` |
| --- | --- | --- |
| `4k` | `--size 4k` | `["wallpaper_3840x2160.mov"]` |
| `full-hd` | `--size 1080p` | `["wallpaper_1920x1080.mov"]` |
| `static` | No download | `[]` |
| Both, for changing displays | `--size both` | `["wallpaper_3840x2160.mov", "wallpaper_1920x1080.mov"]` |

For a fixed TV, use the intended physical output and verified decoder capability to choose the video
profile. Do not use Android's UI size alone. Both files require HEVC 10-bit decoding at 60 fps; the
player does not check codec capabilities or try a different rendition after a decoding error. Such
an error falls back to the static poster. Test smooth playback on each target device.

Back up the external config and merge only the profile's video setting, preserving unrelated keys.
A single-entry array prevents an old higher-resolution file from being selected. `static` explicitly
disables playback even when MOV files remain on disk; keep the static wallpaper enabled. Record the
profile per device. Clearing app data removes the external override and videos and restores the
shared APK defaults, so reapply the profile after a reset.

After downloading, copy the selected video files to `/sdcard/Android/data/<installed-package>/files/`
with ADB and verify their checksums. The default package is `com.gathin.tvhome`. Some firmware blocks
writes there; the current ThemeProvider has no MOV transfer fallback.
For a full theme deployment, `tools/theme.sh` uses the default package and replaces external config,
clears wallpaper collections and resets on-TV choices. Back those up first; do not use a full theme
replacement just to change the profile. Keep `wallpaper.png` in the staged theme folder when using
that script, because it removes the external static wallpaper when the folder does not contain one.
The theme script does not copy video files. After clearing app storage, reapply the saved profile and
copy its selected videos back to restore motion. Removing the external theme returns to the bundled
defaults, which list both video renditions. Set `wallpaper: false` to disable all backgrounds, or
`video_wallpaper: []` to use only the static image.
