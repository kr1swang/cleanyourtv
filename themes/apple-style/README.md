# Apple-style launcher

The APK bundles the six-column Dock defaults in `config.json` and the pinned asset catalog in
`wallpaper-catalog.properties`. Images and movies are downloaded from
[wallpapers-2026-10-22](https://github.com/kr1swang/cleanyourtv/releases/tag/wallpapers-2026-10-22).
Release filenames use `wallpaper_FHD` and `wallpaper_4K`, with `_30fps` or `_60fps` for movies.
Release, downloader and cache filenames share the same FHD / 4K naming.

Press **Up** on a Dock app to open the control center, then choose **Background settings**.
The center also offers Input sources, System settings, Network settings and Reset order.
Input sources appear only when an input route is available, such as a visible passthrough input,
Live TV or a configured vendor panel. This does not mean an HDMI cable has a live signal.
Sources reuse the existing TV/CEC and vendor fallbacks. Reset order requires confirmation and
restores the theme Dock and default app ordering without changing the background.
Back returns through the lists to the previously focused Dock app. With the custom key helper enabled,
Home closes the overlay, collapses the grid and selects the first Dock app without restarting Home.
The former flower card and standalone download settings page have been removed.

Main and child pages have no title and share 288dp-wide rows. Opening, Back/Home closing, child-page
changes and the dim backdrop use 180ms transitions; reduced motion uses a 60ms fade without sliding.
Dock expansion and collapse use 360ms scrolling. The clock remains visible behind the control center.

Holding OK on an app opens Reorder followed by App info, with the supplied outline icons. These actions share the
control center's colors, Outfit type, rounded rows and focused shadow. The menu stays centered on
the app, opens above it unless there is insufficient space, and fits its content with a minimum visible width equal
to the focused card. Rows have an 8dp gap, with 8dp between their visible edge and the app. Reorder finishes after placing that app once. The upstream Remove from Home action
is still available when the app library is enabled.
During reorder, only the carried tile and its name slide from the previous position over 180ms.
Rapid direction changes continue from the current visual position; placing the tile ends the motion.
With animations disabled or reduced motion enabled, placement is immediate. Moving within a row reuses the bound
tiles; crossing the Dock edge updates only the two tiles whose height/name visibility changes.
The menu dims Home by the same 50% as the control center. The selected app, its visible name and
cached shadow are drawn above that dim layer; Dock labels remain hidden. The layer fades over 180ms
and is removed on dismissal. Reopening during the fade reuses the same layer and current opacity.
It draws the existing tile directly, without capturing the screen or
allocating a full-screen bitmap.
The expanded-grid shade is reduced during this transition so the two shades together stay at 50%.
The grid uses an off-screen hardware layer only while its top-edge fade is visible; at rest,
card animations do not redraw a full-screen fade texture.

Opacity uses four levels: the theme's 30% Dock, 50% full-screen dim layers, 75% unselected rows and
confirmation/download surfaces, and 100% selected rows. Control-center dialogs, module-card prompts
and the app library also use the shared 50% dim amount. Borders and shadows are separate accents.

Background choices use single-line cards: the focus fill identifies the remote target, while a
check marks the selected profile. Size and download status follow the name.
The overlay uses no blur, background capture, local ADB, polling or extra dependencies.
Opening it does not pause Home; applying a different background replaces only the background
layer, preserving the grid and panel focus.

Golden Gate offers static 1080p, static 4K,
1080p/30 fps, 1080p/60 fps, 4K/30 fps and 4K/60 fps. The picker shows download size, progress and
cached versions. A video profile also downloads its resolution's preview. The two frame rates share
that preview. All six files were uploaded to the new Release, including the original 60 fps files.

Downloads use HTTPS, stream on one background worker, check exact length and SHA-256, and atomically
publish a file only after verification. The current selection changes only after the entire profile
is available. Leaving the picker or pressing Cancel stops the job; verified assets remain reusable.
The next download removes abandoned partial files. Errors preserve the current background.

Files live under the app's private `files/wallpaper-library/` directory and survive normal updates.
**Clear unused files** preserves the active profile's files, including a shared preview. External
manually deployed files are never removed. Clearing app data removes downloads and selections.
On first use or after app data is cleared, Home downloads the FHD static background.
Returning Home also retries if its downloaded files are missing. Offline it uses the
plain background and retries on a later foreground visit, at most once every two minutes. An explicit
local image, None, or a supplied external wallpaper takes priority. No periodic update checker runs.

The selected profile uses the upstream wallpaper preference; external config, Dock order and other
preferences remain intact. A static profile creates no video player. A video profile plays its
fixed resolution/FPS; it never silently switches to another rendition. It starts from the beginning,
releases the decoder when Home stops, and keeps the verified preview as a fallback. The Home/YouTube/
Netflix service and Google Cast packages are not changed by this feature.
When hidden Home receives a low-memory trim, upstream's memory policy releases the wallpaper too.
The custom static surface drops its bitmap reference with the ImageView; a video preview released
by the same trim is loaded again when Home returns. Ordinary app switches keep the cached preview.

The catalog is versioned with the APK, not fetched from an unpinned latest Release. To add a background,
add its title, six profile entries and immutable asset URLs/sizes/hashes. The picker groups entries by
background. Upload assets before shipping the catalog. For another publisher, update the catalog's
allowed repository in `WallpaperCatalog.Asset` as well. The app now requests INTERNET permission for
these downloads; upstream descriptions of a network-free launcher do not apply to this fork.

The Dock uses upstream `dock_even_padding` for its spacing and concentric corners instead of custom
fixed band dimensions. Six columns, the YouTube → Netflix → Twitch → Spotify → Moonlight → Play Store
order and 30% opacity are defaults in `config.json`, read through upstream configuration handling.
There is no separate code enforcing those values; external config and saved on-TV choices can override
them, and a saved app order takes priority until Reset order is used.

The upstream Launcher settings activity remains available through its existing shortcuts. Its local
wallpaper picker is separate from the control center's download catalog. A small display adapter shows
downloaded profile names there instead of internal IDs. Keep that page when merging upstream.

## Remote keys

The installed `YouTubeKeyService` component extends upstream `HomeKeyService`, sharing its
boot probe, stock-home detection, service repair and configurable app-key handling. The bundled
`app_keys` map Chromecast BUTTON_3 to YouTube and BUTTON_4 to Netflix; `stock_home_redirect`
names Google TV. External config can override these for another remote or launcher.
The helper must still be enabled by the owner; these settings alone do not enable a service.
Clearing app data removes saved service-repair records too. Bundled key mappings remain, but they do
not restore Android accessibility authorization; check that the helper is enabled and bound afterward.

The custom override consumes Home immediately. While Home is visible, it closes the control center
and returns focus to the first Dock app with animations, without restarting the wallpaper.
Unlike upstream's short-press redirect, holding Home is also consumed by this helper.
At boot, upstream probes once during the first three minutes and redirects only when a stock Home
window is observed, leaving another app or an HDMI input alone. No launcher or Cast package is disabled.

## Maintenance

- Prefer upstream implementations when they cover the same behavior. Remove the superseded custom
  implementation, while preserving the documented Apple-style behavior.
- Keep configurable defaults in `config.json`, including six columns, Dock order and 30% Dock opacity.
  Reuse upstream configuration handling instead of enforcing these values again in Java.
- Keep custom behavior in focused components and reuse upstream settings, input-source, key-handling
  and memory-management paths. Avoid unrelated refactoring during upstream merges.
- Keep this guide as the detailed custom reference. Keep the fork notices in the root README,
  AGENTS and CONTRIBUTING short; do not rewrite upstream documentation or generated tables.
- Preserve Cast, voice search and system updates during deployment. The configured Google TV helper
  leaves the stock launcher and setup services enabled; do not disable them to make Home redirection work.
  Another TV needs its own input and remote mapping checks.
- The release APK must remain below 589,824 bytes, the upstream 576 KiB gate. Do not bundle downloaded
  wallpaper images or movies in the APK. Do not raise the gate to accommodate a change without agreement.
- Run the repository's Python suite and launcher unit tests, lint and release build for code changes.
  For Home/key/lifecycle changes, also verify physical remote keys, sleep/wake, reboot and phone Cast on
  a connected TV. For layout changes, check navigation and screenshots. For performance claims, use
  the [performance workflow](../../docs/workflows/perf-ab.md) and report measured results.
  If no TV is available, state which hardware checks remain unverified.

## Verification

The JVM tests cover catalog validation, FHD static default selection, shared-preview cache reuse,
offline cache reads, cancellation, truncated/oversized responses, checksum failures and partial cleanup.
The JVM catalog tests read the shipped six-profile catalog. Hardware validation
of decoder performance, color transitions, remote navigation and Cast is still required before
claiming those results. The settings UI can be built without a connected TV.
