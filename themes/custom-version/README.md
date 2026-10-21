# Custom theme

Six columns with YouTube, Netflix, Twitch, Spotify, Moonlight and Google Play Store.

`video_wallpaper` names a local video in the launcher's external files directory.
An optional `video_wallpaper_1080p` names a smaller rendition. Displays currently outputting
1920×1080 or less use that file; larger or unknown modes use the original. Selection uses
the physical display mode, not Android's possibly scaled UI size. It is checked when
playback starts and when the display mode changes. An unchanged selection keeps playing.
If one file is missing or empty, the other is used; if both are unavailable, the static
wallpaper remains. A theme with only `video_wallpaper` still works as before.
This custom extension plays it silently in a loop while Home is visible and resumed.
Leaving Home releases the player and decoder. App-colour glow and the sky layer are
suppressed while a video background is configured and its file exists.
Set `video_wallpaper` to an empty string to disable video playback.

The build packages `config.json` and `wallpaper.png` directly from this folder as defaults.
Clearing app storage restores six columns, the Dock order and the static wallpaper on next launch.
External config keys, wallpaper files and on-TV choices can still override those defaults.
Only the video remains outside the APK:

- `wallpaper_3840x2160.mov`: original deployment copy, 3840×2160 HEVC at 60 fps.
- `wallpaper_1920x1080.mov`: 1920×1080 HEVC rendition at 60 fps. Both videos use Git LFS.
- `wallpaper.png`: the exact first frame of the deployment video, stored losslessly at 3840×2160,
  shown during preparation or if playback fails. Bundled at full resolution without conversion.
  The launcher uses ARGB_8888 to preserve gradients.

Install Git LFS before cloning, or run `git lfs install --local` and `git lfs pull`
in an existing checkout to download the video. A Git LFS pointer is not a playable
video; the 4K file is about 262 MiB before deployment.

Copy both video files to `/sdcard/Android/data/com.gathin.tvhome/files/` with ADB,
then apply this folder with `tools/theme.sh`. Keep `wallpaper.png` in this
folder when using that script, because theme deployment removes the
device's static wallpaper when the selected folder does not contain one.
The theme script does not copy video files. After clearing app storage, copy the videos back to
restore motion; the built-in settings already name them. Removing the external
theme returns to these bundled defaults. Set `wallpaper: false` to disable all backgrounds, or
`video_wallpaper: ""` to use only the static image.
