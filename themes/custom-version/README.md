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

- `wallpaper_3840x2160.mov`: original deployment copy, 3840×2160 HEVC at 60 fps.
- `wallpaper_1920x1080.mov`: 1920×1080 HEVC rendition at 60 fps. Both videos use Git LFS.
- `wallpaper.png`: the exact first frame of the deployment video, stored losslessly at 3840×2160,
  shown during preparation or if playback fails. Bundled at full resolution without conversion.
  The launcher uses ARGB_8888 to preserve gradients.

Install Git LFS before cloning, or run `git lfs install --local` and `git lfs pull`
in an existing checkout to download the video. A Git LFS pointer is not a playable
video; the 4K file is about 262 MiB before deployment.

Choose the deployment size using the [video deployment instructions](../../PROMPT.md):
for a fixed, known output, copy only the corresponding video; for a player that moves between
1080p and 4K displays, copy both. Ask about intended use if it is unknown. Do not use Android's
UI size alone to make this choice. Both filenames may remain listed when only one video is present.

Copy the selected video files to `/sdcard/Android/data/com.gathin.tvhome/files/` with ADB,
then apply this folder with `tools/theme.sh`. Keep `wallpaper.png` in this
folder when using that script, because theme deployment removes the
device's static wallpaper when the selected folder does not contain one.
The theme script does not copy video files. After clearing app storage, copy the selected videos back to
restore motion; the built-in settings already name them. Removing the external
theme returns to these bundled defaults. Set `wallpaper: false` to disable all backgrounds, or
`video_wallpaper: []` to use only the static image.
