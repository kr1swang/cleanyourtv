# The prompt

Paste this into Claude Code (or another coding assistant with shell access) on a computer that is
on the same Wi-Fi as the TV. Works on Android TV / Google TV sets (Xiaomi, TCL, Hisense, Sony,
OnePlus, Realme, Nvidia Shield and most others). It will not work on Samsung Tizen, LG webOS or
Fire TV. If you cloned this repo, mention that: the assistant can reuse `tools/` and `launcher/`
instead of writing them from scratch. Reuse the current branch's launcher and theme configuration;
custom theme defaults are in `themes/custom-version/`.

Before you start, on the TV: Settings → Device Preferences → About → press OK on "Build" 7 times,
then go back → Developer options → turn on USB debugging (and Network/ADB debugging if your TV
shows it). Keep the TV on. Some boxes cut power to their USB port while USB debugging is on, so a
USB speaker or drive plugged into it goes dead until you switch debugging off again.

---

I want to debloat my Android TV and replace its launcher with a clean Apple TV style home screen.
My TV model is: <MODEL NAME HERE>. Work on the real device, step by step, and verify everything you
do with screenshots pulled from the TV. Reuse preferences I have already stated; ask only for missing
choices. Preserve Google Cast, voice search, system updates and their dependencies unless I explicitly
request otherwise. Do not copy another device's disabled-package list.

Connect
- Find the TV on my network (hostname, ARP table, or ask me for the IP) and connect over ADB on port 5555.
  If the TV is in another home, I'll give you its Tailscale address instead (docs/REMOTE.md): use that,
  don't scan my local network.
- If the stock adb binary fails with "No route to host" on macOS even though the port is open, that is
  macOS Local Network privacy blocking adb. Don't debug it: use the Python `adb-shell` package in a venv
  as the ADB client instead. If its file push hangs on larger files, transfer files as base64 chunks
  through the shell and verify the md5.
- If your own commands can't reach the TV at all while it answers from an ordinary terminal (a sandbox that
  blocks the local network), ask me to run `tools/relay.py <tv ip>` in a terminal and leave it running, then
  use `TV_HOST=127.0.0.1 TV_PORT=15555` (`tools/relay.md`).
- If port 5555 stays closed, the TV may only offer Wireless debugging (Google TV sets often do): look for
  `_adb-tls-connect._tcp` with `dns-sd -B` or `adb mdns services`, ask me for the code and port under
  Developer options > Wireless debugging > Pair device with pairing code, then `adb pair`, `adb connect`
  and `adb tcpip 5555`. Android switches Wireless debugging off whenever Wi-Fi drops.
- Tell me when the "Allow USB debugging?" prompt will appear on the TV so I can accept it.
- If `adb install` hangs on Android 14 it is waiting for an on-screen confirmation; push the APK and run
  `pm install -r` over the shell instead.

Back up first
- Save the full package list, disabled-package list, getprop output and all settings to a backup folder
  before changing anything, and write a restore script that re-enables everything you disable.
- Ask me what is plugged into the TV's USB ports. Some devices cut their power while USB debugging
  is on, so warn me, and remind me to switch debugging off once you're done.
- When you finish, close network ADB instead of leaving it open: switch USB debugging and
  Network/ADB debugging off on the TV, revoke the USB debugging authorisations, reboot, and check
  with `tools/adb-closed.py <tv ip>` that the port refuses the connection. Don't trust `nc` on macOS:
  Local Network privacy can block it too, and its "No route to host" proves nothing; only "Connection
  refused" does. Port 5555 is plaintext, and on firmware with `ro.adb.secure=0` any device on the LAN
  can get a shell.

Debloat
- List installed packages and sort them into: manufacturer telemetry/analytics, manufacturer content
  and ad services (PatchWall, recommendation rows, content providers), background services I probably
  don't need (share/cast duplicates, cleaners), preloaded apps, and streaming apps.
- Disable the first categories with `pm disable-user --user 0`, never uninstall system packages.
- Never disable what the TV needs to work: the TV app and input services that play HDMI and the
  tuner, the input/source menu, remote control and Bluetooth pairing services, the on-screen
  keyboard, Google Play Store and Play Services. Manufacturer package names often don't say what a
  package does: check its activities and services with `dumpsys package` first, and ask me if it's
  still unclear.
- Ask which optional streaming apps or sharing services I want to remove if I have not already said.
  Keep Cast, voice search and system updates enabled by default; an unanswered checklist is not
  permission to disable them. Check dependencies before disabling any related package.
- Run `pm` commands one per shell call, chained commands may silently stop after the first.
- If `pm disable-user` is refused for every package, stop and tell me: some firmware (Fire OS 5 on a
  Fire TV Box, for one) refuses `pm disable-user` with a `SecurityException`, `pm uninstall --user 0`
  with `DELETE_FAILED_INTERNAL_ERROR`, and `pm hide` reports success while changing nothing. Check
  with `pm list packages -d` instead of the exit code, and don't burn the session retrying - there is
  nothing to debloat on such a set, so we fall back to its own privacy toggles and DNS blocking
  (`docs/TROUBLESHOOTING.md`).
- Don't launch a package whose name says factory, production, AT or fac menu (`com.cvte.tv.hkcfac`,
  `com.at.factory.*`, `mediatek.factorymenu.ui` and the like) to see what it does, even read-only:
  on Haier/CVTE boards opening one flips a factory-mode flag that kills the Input key and CEC, and
  makes the next boot disable Play services, Netflix and Chromecast - which the shell then cannot
  re-enable. `dumpsys package <name>` tells you what a package is without starting it, and
  `docs/TROUBLESHOOTING.md` has the way out if one has been opened.
- Disable in small batches and screenshot between them: a batch can take something load-bearing with
  it and you cannot tell which.
- After the last batch, reboot once and compare `pm list packages -d` with what you disabled: some
  firmware re-enables packages at every boot from its own list (TCL Android 9 does this for its user
  center). Note those in the list header instead of disabling them again after each boot.
- Offer me ad-blocking DNS as an optional extra: on Android 9 and newer, Private DNS set to an
  ad-blocking resolver (`settings put global private_dns_specifier dns.adguard-dns.com`, then
  `settings put global private_dns_mode hostname`). Tell me it doesn't block YouTube ads, check an app
  still plays afterwards, and note the undo (`private_dns_mode opportunistic`) in the README.

Launcher
- Check that the TV accepts APKs before building anything. Some China-market sets answer every
  sideload with `INSTALL_FAILED_VERIFICATION_FAILURE` — `adb install`, `pm install`, a hand-started
  installer and even the manufacturer's own APKs all fail — and others refuse any package that
  declares `android.intent.category.HOME` unless it carries the manufacturer's signature. If you
  hit either, do the debloat half, say plainly that the launcher half is impossible here, and stop
  retrying installs. The matching `examples/` header says which case your TV is.
- Check the TV's SDK level before promising the launcher, too: `tools/tv.py 'getprop
  ro.build.version.sdk'` against `grep minSdk launcher/app/build.gradle` (26 = Android 8.0). A set
  below it answers `INSTALL_FAILED_OLDER_SDK`; lowering `minSdk` is a source change you would then
  maintain yourself, and an APK rebuilt elsewhere is nothing this repo can verify. On those sets offer
  the debloat half and say the launcher cannot be installed.
- Don't install a third-party launcher that charges for basics like wallpapers. Build a small native
  Android TV launcher instead. Reuse this branch, which requires Android 8 / API 26 or newer; do not
  lower minSdk just to bypass installation checks. Install and test it before changing the default
  home. Disable the stock launcher only if needed for takeover and authorized for this device.
  If several apps claim the HOME intent, list them with `cmd package query-activities -a android.intent.action.MAIN -c
  android.intent.category.HOME` and set the default with `cmd package set-home-activity`. Those two
  commands can both name your launcher while Home still opens something else: on some Google TV builds
  `set-home-activity` prints `Success` and changes nothing, and setupwraith's `.RecoveryActivity`
  keeps the key. Confirm with `dumpsys activity activities | grep mFocusedApp` before telling me the
  takeover worked.
- Before disabling the stock launcher, check which other apps claim HOME: on Google TV the setup wizard
  (`com.google.android.tungsten.setupwraith`) takes over even after the stock launcher is disabled, and
  the matching `examples/` header says what to do. On Chromecast with Google TV, keep setupwraith enabled:
  it owns CastInfoProvider, which Cast uses to load device authentication credentials. On this device,
  disabling it caused empty authentication signatures and the sender's "untrusted device" error;
  restoring it and the stock launcher, then rebooting, restored casting. Use remote-key interception
  for custom Home instead, and verify physical Home and phone-to-TV casting after a reboot.
  On other devices, also check the saved Wi-Fi owner before considering disabling setupwraith: run
  `dumpsys wifi | grep -o 'cname=[^ ]*'`: a Wi-Fi network saved during first setup belongs to it, and
  Android deletes that network the moment it is disabled, which takes the TV off Wi-Fi and ADB. If it
  does, ask me to forget the network and re-join it in Settings first.
- On Google TV the stock launcher also answers the remote's app buttons (Netflix, YouTube and the like),
  Input and Settings/dashboard buttons, so after disabling it ask me to press each one, and remap any that
  do nothing with the launcher's key services: `input_key_keycodes` and `app_keys` with
  INPUT_KEY_SERVICE=1, `settings_key_keycodes` with HOME_KEY_SERVICE=1 (`config.xml` or the theme).
  A button the TV logs as `KEY_UNKNOWN` or `KEY_PROGRAM` cannot be matched by keycode, since every
  unmapped button shares it: read the scancode with `getevent -lt` and use `settings_key_scancodes`,
  which only `config.xml` carries (a theme can set keycodes, not scancodes).
- On Android 11 and newer the launcher must declare `<queries>` for `android.intent.category.LEANBACK_LAUNCHER`,
  or package visibility silently hides every app and the grid comes up empty; target a recent SDK, some
  Google TV 14 sets refuse older targets at install.
- Preserve this branch's existing custom layout, font, Dock order and focus behavior when deploying.
  The generic design below applies only when creating a new launcher; do not restore its four-column
  layout, top buttons, greeting or Dock title over the current custom version.
- Design, Apple TV 4K style: pitch black background; a dock row at the bottom of the screen inside a
  slightly lighter rounded band holding the four apps I pick (ask me which, and the order); the rest of
  the apps in a 4-column grid below that scrolls up when I press Down; one translucent "glass" highlight
  that springs from tile to tile instead of per-tile borders; the focused tile scales up slightly and
  shows its name underneath; above the dock, the focused dock app's name in large light type with an
  ambient glow in a colour sampled from its banner; frosted pill buttons top right for Inputs, Wi-Fi
  (showing the network name) and Settings; a greeting, a large thin clock and the date top left in a
  downloaded open-licence typeface such as Outfit. Inputs opens a compact glass dropdown under the pill
  listing the HDMI ports, AV and Live TV, switching via the TV input framework.
- Make D-pad navigation deterministic: Up/Down move exactly one row. Only navigate to a top bar
  when one is present. Verify both scrolling directions and the current branch's Home reset behavior.
- Keep it light: the TV may have about 1 GB of RAM. No blur or network polling. Use a static
  wallpaper unless I have requested video; for video, follow the deployment rules below.

Custom deployment profiles
- Use one APK with a device-specific external config and selected media files. These are deployment
  profiles, not separate Gradle build flavors. Offer `4k`, `full-hd`, or `static` when I have not already
  chosen. Reuse my stated preference instead of asking again. For a device that moves between displays,
  offer both video renditions as an optional automatic-selection profile.

  | Profile | Files to download and deploy | External `video_wallpaper` value |
  | --- | --- | --- |
  | `4k` | `wallpaper_3840x2160.mov` | `["wallpaper_3840x2160.mov"]` |
  | `full-hd` | `wallpaper_1920x1080.mov` | `["wallpaper_1920x1080.mov"]` |
  | `static` | No video download or transfer; use bundled `wallpaper.png` | `[]` |
  | Both, for changing displays | Both MOV files | `["wallpaper_3840x2160.mov", "wallpaper_1920x1080.mov"]` |

- Inspect the device model, Android version, RAM, free storage, active physical display mode and
  supported output modes, for example with `dumpsys display`. Distinguish the panel's native resolution,
  HDMI output and Android's UI render size. A 1080p UI does not prove that a 4K TV can only display
  1080p video. Do not infer permanent 1080p use from a Chromecast temporarily attached to a monitor.
  For a fixed TV with known output and an existing request for video, recommend the matching profile
  without asking for the resolution again. Explain any capability problem before changing that choice.
- Both current MOV assets are HEVC 10-bit at 60 fps. Check the target's decoder support for the actual
  codec, profile, dimensions and frame rate, then test playback on the device. Resolution alone is not
  a compatibility check, and a codec listing alone is not proof of smooth hardware decoding. The current
  player selects by filename dimensions and output mode; it does not check decoder capabilities.
  If decoding fails, it returns to the static poster, not another rendition. If playback is unsupported
  or sluggish, report the result and recommend Full HD or static as appropriate; do not silently reduce
  resolution or frame rate. Full HD uses the same codec/profile and may also be unsupported.
- Preserve the original 4K file and 60 fps. Use the separately prepared 1080p/60fps rendition; do not
  transcode on the TV. Check the selected files' actual dimensions, frame rate and colour metadata.
  Download with `python3 tools/fetch-wallpapers.py --size 4k`, `--size 1080p` for `full-hd`, or `--size both`.
  Do not invoke the downloader for `static`. The tool verifies the pinned Release URLs, sizes and SHA-256
  checksums in `themes/custom-version/wallpapers.json` and reuses valid local copies. Videos are ignored
  by Git and are not Git LFS objects.
- Back up the device's external config and on-TV choices before applying a profile. Merge only the
  selected `video_wallpaper` value into the existing JSON; retain unrelated keys, including unknown
  keys, columns, Dock order and app mappings. Keep the static wallpaper enabled and verify it is the
  intended image; an existing on-TV wallpaper choice can override the theme. Do not use a whole-theme
  reset to change one setting. `tools/theme.sh` replaces external config, clears wallpaper collections
  and resets on-TV choices through a revision marker; use it only for an intended full theme deployment
  after preserving the existing settings and media.
- Use a single filename in the array for a fixed profile. Merely copying the 1080p file is insufficient
  if an old 4K file remains listed and present. For `static`, explicitly set `video_wallpaper: []` even
  if videos remain on disk. Leave unselected files intact unless I request cleanup.
  For the optional two-file profile, suffixes `_WIDTHxHEIGHT.ext` must match actual dimensions. The player
  selects the smallest available rendition covering the physical output, or the largest if none covers
  it or the mode is unknown. Missing or empty files are skipped. A single string is also supported.
- The APK bundles `config.json` and full-resolution `wallpaper.png`; videos stay external. Push selected
  videos to `/sdcard/Android/data/<installed-package>/files/` using a temporary filename, verify the
  checksum, then rename. Reuse matching files and check storage for staging copies. `tools/theme.sh`
  does not transfer videos and hardcodes `com.gathin.tvhome`; check the actual installed package before
  using tools, especially with an alternate application ID. Some firmware blocks ADB writes to
  `Android/data`; the current ThemeProvider accepts config/images but not MOV. If no supported transfer
  route works, report the blocker and offer static instead of claiming video deployment succeeded.
- Record the selected profile in the device's deployment notes. Clearing app storage removes the
  external profile and movies; the APK restores its bundled six-column config and static poster.
  Reapply the saved profile and selected media to restore the device's configuration. The per-device
  profile does not change the defaults embedded in the shared APK.

Other-device compatibility
- Keep a stable signing key for future updates and record the package ID and certificate fingerprint.
  This branch currently signs release builds with the local debug key. A different computer may use a
  different key, and another APK with the same package name may have a different certificate. Compare
  signatures before replacing an installation. Do not uninstall or clear data to bypass a mismatch;
  preserve the original key or agree on a separate package identity. Never commit private signing keys.
- Inspect HOME handlers and OEM restrictions on this device before takeover. Do not blindly disable
  `com.google.android.apps.tv.launcherx` or `com.google.android.tungsten.setupwraith`. Preserve Cast's
  dependencies as described above, and keep a working route to the stock launcher.
- The custom `YouTubeKeyService` handles `HOME`, `BUTTON_3` and `BUTTON_4` in one accessibility service,
  opening the custom Home screen, YouTube and Netflix. Keep the existing service component name
  so upgrades preserve its accessibility setting. Do not automatically enable it on other devices.
  Chromecast firmware can consume the first Home press while waking from sleep. The service also
  redirects the exact stock `launcherx.home.HomeActivity` window while the screen is interactive;
  it does not redirect the whole package or poll the foreground app. This fallback may briefly show
  stock Home. Test physical Home from sleep and Cast while asleep as well as normal awake navigation.
  On service connection it also opens custom Home once per `Settings.Global.BOOT_COUNT` when the
  screen is interactive, because stock Home can appear before accessibility binds. The handled boot
  is stored in `remote_keys` preferences so service reconnects do not interrupt apps. First activation
  or clearing launcher data also resets that record. This is a post-boot redirect, not a replacement
  of the firmware's HOME resolver; stock Home may briefly appear. Verify a real reboot without
  pressing Home, and verify a service reconnect while another app is open.
  On the tested Chromecast, disabling only launcherx while leaving setupwraith enabled makes the
  system HOME resolve to `setupwraith/.RecoveryActivity`, which tries to open launcherx and logs
  `Launcherx Activity is not found`. This configuration was rolled back; keep both packages enabled
  with the key service. Cast was not tested in that configuration because the system Home path failed.
  Test the real remote, identify the key, and prefer the existing configurable key service when suitable.
  Preserve unrelated accessibility services and verify any enabled helper is bound after installation.
- Check which requested Dock packages are installed. Missing apps can be replaced by other grid apps
  under the default fill behavior; report the resulting order rather than claiming the exact six apps
  are present. Ask before installing replacement apps or changing the requested order.
- The custom layout has no upper-right Inputs/Settings buttons. On built-in TVs, verify that Settings,
  HDMI/source selection and live TV remain accessible through the remote or app grid. Check the actual
  display for overscan, clipped focus shadows, text scaling and four/six-column navigation.

Verify
- Establish a baseline before changing the device: real Home/app/voice keys and phone-to-TV Cast playback.
  After installation and any takeover, repeat those checks, including volume, power and source selection
  where the connected display permits. Keep update services enabled and verify their settings entry is
  reachable; do not trigger an OS update just to test it. Record anything that could not be tested.
- For video, verify the selected filename, actual decoder resolution, frame rate and dropped frames;
  leaving Home must release the decoder and returning must select the correct file. For static, confirm
  the poster appears and the launcher creates no video decoder. Compare idle memory and Dock/grid frame
  timing against static under the same conditions, with unrelated playback stopped when authorized.
  A screenshot alone cannot verify a hardware video layer. Report results only for the tested device
  and output mode; do not claim 4K-output validation from a 1080p monitor.
- After each change, inject remote key presses over ADB, take a screenshot from the TV and look at it:
  `tools/tv.py --screenshot shots/01-after.png` captures on the TV and pulls the PNG.
  Fix what you see before moving on.
- Live TV and HDMI screenshots come back empty (0 bytes); that is the TV, not a broken debloat, and
  `--screenshot` exits 1 with that message. Check
  `dumpsys activity activities | grep topResumedActivity` instead (see `docs/TROUBLESHOOTING.md`).
- If the screen looks enlarged and clipped after an install or a reboot, check the display overrides
  before editing the launcher grid: `wm size` and `wm density` show a physical value next to an
  override, and an override left over from an old resolution experiment, or a density that comes back
  wrong on some MediaTek Sony firmware, is not a launcher bug. An empty
  `settings get secure display_density_forced` is a normal default, not damage - read all three
  values first and put them back if you undo (`docs/TROUBLESHOOTING.md`).
- If a remote button relies on an accessibility helper, check that the service is enabled and bound
  for the current user, then ask me to press the real button; an injected key is not proof (see
  `docs/TROUBLESHOOTING.md`).
- When done, give me a restore script, a build-and-install script, and a README that explains how to
  re-enable any app and how to change the dock order or the wallpaper.

---

Expect questions only for choices not already supplied, such as the wallpaper profile or missing Dock
apps. Cast, voice search and system updates stay enabled by default. Keep backups and restore instructions
for every device-specific change.
