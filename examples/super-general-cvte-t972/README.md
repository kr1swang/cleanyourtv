# Super General (CVTE TP.SK708D.PC821, Android 9): notes

Done remotely on one set. Debloat from `debloat.txt`; Home is a third-party launcher,
[Afnon](https://github.com/egyjs/afnon-android-tv-launcher), not the reference launcher.

## Remote buttons

The app buttons are wired in CVTE's hotkey service (`com.cvte.tv.api.impl`), which reads the read-only
`/vendor/menu_config_xml/hotkey.xml`. They work from any app and need no accessibility service:

| Button | Keycode | Opens |
|---|---|---|
| SETTING | 1110 | `com.cvte.settings` |
| YouTube | 1111 | YouTube |
| NFX | 1112 | `com.netflix.mediaclient`, else `com.stark.store` |
| ESHARE | 1113 | `com.ecloud.eshare.server` |
| SOURCE | `KEYCODE_TV_INPUT` | nothing: the firmware passes it to the app in front |

- **SOURCE**: the stock launcher opened `com.cvte.tv.globalui/.InputSourceActivity`. A replacement Home must do
  the same, or the button does nothing on the home screen.
- **Holding Volume**: an accessibility service that filters keys (`FLAG_REQUEST_FILTER_KEY_EVENTS`) makes Android
  re-inject them, and injected keys never repeat, so Volume moves one step per hold. On this firmware the hotkey
  service already answers the buttons, so a launcher's key filter is not needed; leave it off.
- **Testing**: `adb shell input keyevent 1112` reaches the hotkey service, so the app buttons can be tested over ADB.
- **NFX without Netflix**: the button is hard-wired to the package name. Uninstalling Netflix (a normal app here) and
  installing a tiny app with the same package name whose only activity opens another app repoints the button.
  ESHARE can't be repointed that way: EShare is a CVTE-signed system app.

## Other quirks

- Android 9 hides the Wi-Fi network name from apps without location: grant a launcher
  `android.permission.ACCESS_FINE_LOCATION` if it shows the network.
- EShare (v6.4.17, 2020) is a system app and can't be updated; newer EShare phone apps may fail to connect.
  The built-in Miracast receiver (`com.droidlogic.miracast`) works from a phone's Cast / Smart View tile.
  Its name comes from Settings.Global `wifi_p2p_device_name` and changes after a reboot.
- `screencap` via `exec-out` sometimes hangs over a slow link: `screencap -p /data/local/tmp/s.png`, then pull.
