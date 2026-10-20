# Custom theme

Six columns with YouTube, Netflix, Twitch, Spotify, Moonlight and Google Play Store.

`video_wallpaper` names a local video in the launcher's external files directory.
This custom extension plays it silently in a loop while Home is visible and resumed.
Leaving Home releases the player and decoder. App-colour glow and the sky layer are
suppressed while a video background is configured and its file exists.
The ordinary upstream appearance remains available by removing `video_wallpaper`.

The media files are versioned alongside this theme, but are not bundled in the APK:

- `golden-gate-wallpapers.mov`: deployment copy, 3840×2160 HEVC at 60 fps, tracked with Git LFS.
- `wallpaper.jpg`: a 4K frame from the same video, shown during preparation or if playback fails.

Install Git LFS before cloning, or run `git lfs install --local` and `git lfs pull`
in an existing checkout to download the video. A Git LFS pointer is not a playable
video; check that the downloaded file is about 262 MiB before deployment.

Copy both files to `/sdcard/Android/data/com.gathin.tvhome/files/` with ADB,
then apply this folder with `tools/theme.sh`. Keep a local `wallpaper.jpg` in this
folder when using that script, because upstream theme deployment removes the
device's static wallpaper when the selected folder does not contain one.
The upstream theme script does not copy video files.
