# TCL 55P71B Pro (G09, Google TV 12): a bound key helper costs remote key auto-repeat

Owner report: [#763](https://github.com/Gathin23/cleanyourtv/issues/763). One unit, one session: TCL
55P71B Pro "Smart TV Pro", device `G09`, Android 12 (Google TV), firmware `STT2.230203.001.AS23`,
Amlogic.

## The symptom

Hold volume up or down and the volume moves one step, on the home screen and inside an app. Pressing
the button that many times gives that many steps. The remote is not necessarily the problem and neither
is the debloat: the same remote ramps normally again as soon as no key-filtering accessibility service is
bound, and where the repeats go is one `getevent -lt` away - if the kernel still prints them while the
button is held, they are being dropped above the driver.

## The bisect

The owner enabled the launcher's two optional helpers one at a time and then together:

| `enabled_accessibility_services` | holding volume up/down |
| --- | --- |
| both `HomeKeyService` and `InputKeyService` | one step per press |
| `HomeKeyService` alone | one step per press |
| `InputKeyService` alone | one step per press |
| `""`, with `accessibility_enabled 0` | ramps continuously |

All or nothing. This is not one helper mishandling `VOLUME_UP`: the dump while broken lists both as
`Service[label=Home button, capabilities=8]` and `Service[label=Home, capabilities=8]`, and capability
8 is `CAPABILITY_CAN_FILTER_KEY_EVENTS`. Any bound service that asks to filter key events puts this
firmware's input path in a mode where the first press arrives and the repeats that follow are dropped.

Read the state without changing anything:

```sh
tools/tv.py 'settings get secure enabled_accessibility_services'
tools/tv.py 'settings get secure accessibility_enabled'
tools/tv.py 'dumpsys accessibility'
```

To get the repeat back for a test, and to put the helpers back afterwards:

```sh
# keep the string the first read printed; the empty list also unbinds TalkBack and any other service
tools/tv.py "settings put secure enabled_accessibility_services ''; settings put secure accessibility_enabled 0"
tools/tv.py "settings put secure enabled_accessibility_services com.gathin.tvhome/com.gathin.tvhome.HomeKeyService:com.gathin.tvhome/com.gathin.tvhome.InputKeyService; settings put secure accessibility_enabled 1"
```

That second line is the launcher's two helpers, not "the value": write back what the read printed.
`theme.sh` already re-puts the list after a theme push, `am force-stop com.gathin.tvhome` drops it on
its own (see [TROUBLESHOOTING](../TROUBLESHOOTING.md)), and re-running `launcher/build-and-install.sh`
without `INPUT_KEY_SERVICE=1`/`HOME_KEY_SERVICE=1` leaves the helpers off.

## The launcher is not the one eating the repeats

Both helpers return `false` for keys they do not map, so a volume key never reaches launcher code and
is not consumed there: `InputKeyService.onKeyEvent` passes anything that is not in `app_keys`,
`input_key_keycodes`, `KEYCODE_TV` or `KEYCODE_TV_INPUT`, and `HomeKeyService` passes everything except
`KEYCODE_ALL_APPS` and the settings keycodes/scancodes. The repeat is lost before the app sees the
event. What the report does not contain is an `onKeyEvent` line for a held volume key, so "the
platform's filter drops the repeat" is the reporter's reading of the bisect, not a captured log. A
future report that logs the repeat event arriving at a helper and being passed through would move the
blame back into the launcher.

## What the helpers buy, and what this costs

The buttons a helper exists for on this remote:

| Button | What arrives | Helper |
| --- | --- | --- |
| Gear (TV settings) | `KEYCODE_NOTIFICATION` 83, scancode 561 | `settings_key_keycodes` / `settings_key_scancodes`, `HOME_KEY_SERVICE=1` |
| Netflix | vendor keycode 4062, scancode 472 | `app_keys`, `INPUT_KEY_SERVICE=1` |
| Prime Video | vendor keycode 4099 | `app_keys` |
| YouTube | vendor keycode 4065 | `app_keys` |
| Input | `KEYCODE_TV` / `KEYCODE_TV_INPUT` | `InputKeyService`, and TCL draws that panel itself on this set |

Vendor keycodes reach nothing on their own: `com.tcl.ttvs`'s `KeyDownBehaviorReceiver` logs
`keyString=4062 keyCode=4062 type=IR` and acts on nothing. These buttons were answered by the stock
home, and the [G09 example list](../../examples/tcl-g09-google-tv/debloat.txt) disables launcherx, so
after the cleanup only a helper can answer them. That is where the trade comes from: on this firmware
the buttons and the volume ramp cannot both be had, because both need the same filter capability.

## Choices

- **Keep the repeat and skip the helpers.** Do not pass `INPUT_KEY_SERVICE=1` or `HOME_KEY_SERVICE=1`
  when installing. The price is the gear button and the remote's app buttons: see the
  [G09 example](../../examples/tcl-g09-google-tv/README.md), whose install line passes
  `INPUT_KEY_SERVICE=1` for exactly those buttons, so a set that behaves like this one pays for them
  with the volume ramp. The Input button keeps working there without any helper.
- **Keep the buttons and accept stepwise volume.** The volume keys still change the volume, one step
  per press; the launcher cannot make the platform ramp it again. Setting volume from a phone app or
  through an AVR/soundbar remote was not compared on this set.
- **Keep launcherx enabled and bind no helper.** The stock home answers those buttons, so the repeat
  survives. This is the only configuration that has both, and the price is the stock home screen the
  cleanup was replacing.

It is a toggle, not a fix: two one-line `settings` writes switch between the two behaviours whenever
you want, but never both at once.

## Limits

One unit, one firmware build (`STT2.230203.001.AS23`), one session. Only volume up/down was held:
channel up/down, a held D-pad (seek) and the media keys were not tried, so "auto-repeat" here means the
key that was measured. Whether a filter service that is not the launcher's (TalkBack, a remote app)
does the same is untested; the capability in the dump is the only common factor seen. No cold boot, no
second remote. The two other G09 units in this repo (UK `G09_4K_GB` AS24 in Brazil, and
`G09_4K_GB_32BIT` AS10) never looked at this, so the claim belongs to the build, not to "G09". No
launcher-side fix is offered, and none should be expected: an app can decline the key - these two
already do - but cannot hand a repeat that the platform dropped back to the input stack, and putting
`VOLUME_UP` in `app_keys` would open an app rather than ramp the volume.
