# The prompt

Paste this into Claude Code (or another coding assistant with shell access) on a computer that is
on the same Wi-Fi as the TV. Works on Android TV / Google TV sets (Xiaomi, TCL, Hisense, Sony,
OnePlus, Realme, Nvidia Shield and most others). It will not work on Samsung Tizen, LG webOS or
Fire TV. If you cloned this repo, mention that: the assistant can reuse `tools/` and `launcher/`
instead of writing them from scratch. Reuse the current branch's launcher and theme configuration;
custom theme defaults are in `themes/apple-style/`.

Before you start, on the TV: Settings → Device Preferences → About → press OK on "Build" 7 times,
then go back → Developer options → turn on USB debugging (and Network/ADB debugging if your TV
shows it). Keep the TV on. Some boxes cut power to their USB port while USB debugging is on, so a
USB speaker or drive plugged into it goes dead until you switch debugging off again. A USB Ethernet
adapter on that port can drop the same way, so plan to do the whole session in one sitting.

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
- Ask me what is plugged into the TV's USB ports, and ask separately whether the TV's network comes
  through a USB Ethernet adapter: "nothing plugged in" can be true while the network itself is on that
  port. Some devices cut their power while USB debugging is on, so warn me, and remind me to switch
  debugging off once you're done. On a set that reaches the network over USB Ethernet, check before you
  change anything: `dumpsys usb | grep host_connected` (`host_connected=false` means the adapter is
  already gone) and `dumpsys connectivity | grep -o 'Transports: [A-Z]*'` (`WIFI` on a TV I call wired
  means the box fell back, and ADB is running over Wi-Fi). Finish the whole job in one sitting, not
  two: there the port only returns with USB debugging off, and that closes port 5555 as well. Prefer
  Wireless debugging if the TV has it.
- When you finish, close network ADB instead of leaving it open: switch USB debugging and
  Network/ADB debugging off on the TV, revoke the USB debugging authorisations, reboot, and check
  with `tools/adb-closed.py <tv ip>` that the port refuses the connection. If the TV's network came
  over a USB Ethernet adapter, unplug and replug the adapter once debugging is off - the port comes
  back with it - and have me confirm the TV is on Ethernet again, not Wi-Fi. Don't trust `nc` on macOS:
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
- Never disable Android's own core packages, or the TV may not boot again: the permission controller,
  the package installer, ext services and ext shared, module metadata (each as `com.android.*` or
  `com.google.android.*`), `com.google.android.overlay.modules*`, `com.android.providers.*` (calendar,
  contacts, user dictionary and blocked numbers aside), `com.android.systemui`, `com.android.shell`,
  `com.android.phone`, `com.android.settings`, `com.android.tv.settings` and `android`. An original Mi TV
  Stick never booted again after a list took the first six
  ([#884](https://github.com/Gathin23/cleanyourtv/issues/884)); `tools/disable.sh` refuses such a list.
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
  `com.cvte.tv.fac.menu`, `com.at.factory.*`, `mediatek.factorymenu.ui` and the like) to see what it does, even read-only:
  on Haier/CVTE boards opening one flips a factory-mode flag that kills the Input key and CEC, and
  makes the next boot disable Play services, Netflix and Chromecast - which the shell then cannot
  re-enable. `dumpsys package <name>` tells you what a package is without starting it, and
  `docs/TROUBLESHOOTING.md` has the way out if one has been opened.
- Disable in small batches and screenshot between them: a batch can take something load-bearing with
  it and you cannot tell which.
- After the last batch, reboot once and compare `pm list packages -d` with what you disabled: some
  firmware re-enables packages at every boot from its own list (TCL Android 9 does this for its user
  center). Note those in the list header instead of disabling them again after each boot. If
  setupwraith or the stock launcher came back and Home is black, check `logcat | grep
  RecoveryActivity` and re-enable the stock launcher before anything else
  (`docs/devices/acer-r4g.md`).
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
  the debloat half and say the launcher cannot be installed. With no launcher to hand over to, the
  stock one stays: `cmd package` does not exist before Android 7, so `tools/disable.sh` asks `dumpsys
  activity` for Home there and warns when neither answers - that warning means nothing was checked,
  and a set with no recovery has no way back (#795).
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
- On a CVTE board (any `com.cvte.*` package), leave its stock launcher (`com.cvte.tv.launcher.*`) enabled
  unless the `examples/` folder for that exact board disables it: make your launcher Home with
  `set-home-activity`, reboot once, and confirm Home and ADB come back before anything else. Don't enable a
  remote-key helper in the same pass. A JVC LT-43N7115 (`TP.SK706S.PC822`, Android 11) hung on its boot
  animation with ADB gone after both were done before one reboot (#741).
- On Google TV the stock launcher also answers the remote's app buttons (Netflix, YouTube and the like),
  Input and Settings/dashboard buttons, so after disabling it ask me to press each one, and remap any that
  do nothing with the launcher's key services: `input_key_keycodes` and `app_keys` with
  INPUT_KEY_SERVICE=1, `settings_key_keycodes` with HOME_KEY_SERVICE=1 (`config.xml` or the theme).
  Enabling either helper can cost more than the buttons it maps: on a TCL G09 build (Google TV 12) ask me
  to hold volume up before and after, because a bound key-filtering service there drops the key repeats -
  a ramping volume becomes one step per press, one helper or both, all or nothing - so say which one I am
  giving up instead of enabling it quietly (`docs/devices/tcl-55p71b-g09-key-repeat.md` has the bisect and
  the three ways out). On other sets this was not measured; if holding a button stops repeating once a
  helper is on, it is the same trade and not a broken remote.
  A button the TV logs as `KEY_UNKNOWN` or `KEY_PROGRAM` cannot be matched by keycode, since every
  unmapped button shares it: read the scancode with `getevent -lt` and use `settings_key_scancodes`,
  which only `config.xml` carries (a theme can set keycodes, not scancodes).
- On Android 11 and newer the launcher must declare `<queries>` for `android.intent.category.LEANBACK_LAUNCHER`,
  or package visibility silently hides every app and the grid comes up empty; target a recent SDK, some
  Google TV 14 sets refuse older targets at install.
- Preserve this branch's existing custom layout, font, Dock order and focus behavior when deploying.
  The generic design below applies only when creating a new launcher; do not restore its four-column
  layout, top buttons, greeting or Dock title over the current custom version.
- For a new device without supplied preferences, ask how many dock slots I want (4, 5, 6 or 7), which
  apps, and their order. Use the existing `columns` and `dock_apps` config rather than a new layout.
- Design, Apple TV 4K style: pitch black background; a dock row at the bottom of the screen inside a
  slightly lighter rounded band holding the apps I pick; the rest of the apps in a grid with the same
  number of columns below that scrolls up when I press Down; one translucent "glass" highlight
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
- The preferred route is Launcher settings → Background downloads. Offer six profiles: static 1080p,
  static 4K, 1080p/30fps, 1080p/60fps, 4K/30fps and 4K/60fps. Reuse a supplied preference.
  Detect the physical output resolution when suggesting a profile; do not silently lower resolution or FPS.
- This fork has INTERNET permission solely for background assets. Its APK bundles config.json and the
  pinned wallpaper-catalog.properties, not images or movies. The catalog specifies release URLs, sizes
  and SHA-256 hashes. Do not follow "latest" or replace published assets in place.
- A clean install or cleared app data first tries the FHD static background. A failed attempt keeps a plain
  background; later foreground visits can retry after a two-minute cooldown. Existing external images
  and explicit local/None choices take priority. UI selection downloads only the selected profile and
  its matching preview. Downloads run off the UI thread and apply only after all assets are verified.
- Downloaded media stays in app-private persistent storage across normal APK upgrades. Leaving the
  background picker cancels its download. Interrupted temporary files are removed before the next job.
  Clear unused downloads preserves the active image/video pair. Clearing app data removes downloads
  and selections, but retains the APK's six-column and Dock defaults; the default preview needs network again.
- Preserve Cast, voice search, update packages, the Home/app-key service and existing device settings.
  A background selection must not replace external config or alter unrelated keys.
- For offline/manual deployment, use tools/fetch-wallpapers.py --profile fhd-static, 4k-static,
  fhd-30, fhd-60, 4k-30 or 4k-60. It downloads the corresponding preview plus video, if any.
  Push the chosen image as wallpaper.png; merge only video_wallpaper into the device's config.
  Use an empty list for static, or the one selected movie's filename for a fixed video profile.
  An existing downloaded on-TV choice overrides the theme: choose Follow theme when intentionally
  returning to a manual deployment. Back up all other on-TV choices before resetting them.
- Verify file checksums, available storage, Home launch, the selected decoder dimensions/FPS, returning
  from apps, repeated Home, and phone Cast when a physical device is available. Without hardware, report
  build and automated test results separately; do not claim actual FPS, memory savings or Cast compatibility.

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
  `tools/tv.py --screenshot shots/01-after.png DPAD_RIGHT` presses the key, then captures on the TV and
  pulls the PNG (leave the keys off to capture what is already on screen).
  Fix what you see before moving on.
- Live TV, HDMI and some streaming apps (Netflix) give empty screenshots (0 bytes); that is the TV, not a
  broken debloat, and `--screenshot` exits 1 with that message. Check
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
