# Smart Auto Clicker Agent Notes

## Always-Run Checks

Run these before handing off changes that affect Smart Auto Clicker, overlays, MediaProjection, the frame broker, or Throwlet integration:

1. `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :smartautoclicker:assembleFDroidDebug`
2. Emulator smoke test with Smart Auto Clicker and Throwlet installed.

Smart Auto Clicker requires Java 21 or newer for local builds.

## Emulator Setup

Use the local Android SDK emulator when available:

```sh
~/Library/Android/sdk/emulator/emulator -avd SmartPoGoTestApi36
adb wait-for-device
```

Build and install Smart Auto Clicker:

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :smartautoclicker:assembleFDroidDebug
adb install -r smartautoclicker/build/outputs/apk/fDroid/debug/smartautoclicker-fDroid-debug.apk
```

Build and install Throwlet from the sibling checkout:

```sh
cd ../throwlet
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Enable SAC accessibility and overlays:

```sh
adb shell settings put secure enabled_accessibility_services com.buzbuz.smartautoclicker.debug/com.buzbuz.smartautoclicker.SmartAutoClickerService
adb shell settings put secure accessibility_enabled 1
adb shell appops set com.buzbuz.smartautoclicker.debug SYSTEM_ALERT_WINDOW allow
adb shell appops set dev.nicospz.throwlet SYSTEM_ALERT_WINDOW allow
```

## Localhost Port Map

SAC and Throwlet both install Shizuku gesture helpers. Keep these ports and binaries separate:

| Service | App | Port | Remote binary |
| --- | --- | --- | --- |
| Precision gesture helper | SAC | `49323` | `/data/local/tmp/sac-gesture-helper` |
| Frame broker | SAC | `49322` | in-process (`ScreenFrameBroker`) |
| Gesture helper | Throwlet | `49321` | `/data/local/tmp/throwlet-gesture-helper` |

If SAC precision gesture recording returns `unknown command` while settings show the helper running, Throwlet's helper is likely bound to the old shared port/binary. Restart the SAC helper from Settings after updating both apps.

## Throwlet Integration Smoke Test

Throwlet talks to SAC through the localhost frame broker:

- Host: `127.0.0.1`
- Port: `49322`
- Token: `throwlet-frame-v1`
- Commands: `STATUS`, `FRAME`, `CROP_PICK token=throwlet-frame-v1 left=<l> top=<t> right=<r> bottom=<b>`

For SAC changes touching MediaProjection, scenarios, overlays, display capture, or the Throwlet crop picker:

1. Start SAC on the emulator.
2. Start a smart scenario so SAC requests MediaProjection.
3. Accept the Android MediaProjection prompt with screen sharing enabled.
4. Confirm the broker is recording:

   ```sh
   adb shell 'toybox nc 127.0.0.1 49322 <<EOF
   STATUS token=throwlet-frame-v1
   EOF'
   ```

   Expected first line: `OK RECORDING`.

5. Confirm frame serving works:

   ```sh
   adb shell 'toybox nc 127.0.0.1 49322 <<EOF
   FRAME token=throwlet-frame-v1
   EOF'
   ```

   Expected first line starts with `OK width=<w> height=<h> format=png len=<n>`.

6. Start Throwlet buddy mode through its command router:

   ```sh
   adb shell am start -a dev.nicospz.catchhelper.action.START_BUDDY_FULL \
     -n dev.nicospz.throwlet/.CommandRouterActivity
   ```

7. Tap the Throwlet buddy crop button on the rail.
8. Verify SAC receives `CROP_PICK`, opens `ThrowletCropPickerMenu`, and seeds the selector with Throwlet's default rectangle.
9. Confirm the crop in SAC. Throwlet should open `BuddyCropSaveActivity`.
10. Save a valid Pokemon name in Throwlet, then verify logs and persistence:

   ```sh
   adb logcat -d | rg 'CROP_PICK|ThrowletCropPicker|ScreenFrameBroker|sac-crop|buddy crop'
   adb shell run-as dev.nicospz.throwlet ls -l files/needles/buddy
   adb shell "run-as dev.nicospz.throwlet sh -c 'echo \"select pokemonKey,pokemonName,sourceLane,cropLeft,cropTop,cropRight,cropBottom,thresholdPercent,enabled from buddy_crops;\" | sqlite3 databases/throwlet.db'"
   ```

Required signal:

- SAC log has `client command=CROP_PICK`.
- SAC log has `ThrowletCropPickerMenu: onCreateMenu`, `onCreateOverlayView`, `onStart complete`, and `selector valid=true`.
- SAC log has `crop served frame=<w>x<h> rect=<l>,<t>,<r>,<b> bytes=<n>`.
- Throwlet log has `sac-crop pick success` and `buddy crop SAC success`.
- Throwlet has a saved PNG under `files/needles/buddy`.
- Throwlet has an enabled `buddy_crops` row for the saved Pokemon.

## Debugging Rules

- If `STATUS` is connection refused, SAC's broker is not running. Restart the scenario and accept MediaProjection.
- If `STATUS` is not `OK RECORDING`, fix SAC MediaProjection/scenario startup before debugging Throwlet.
- If `FRAME` fails, inspect `ScreenFrameBroker` and the active display recorder.
- If `CROP_PICK` returns `ERROR UNKNOWN_COMMAND`, the installed SAC build does not include Throwlet crop support.
- If the crop picker crashes with `Resources$NotFoundException`, check that `ThrowletCropPickerMenu` uses `ScenarioConfigTheme`.
- If the crop picker re-enters lifecycle repeatedly, check that `ThrowletCropPickerMenu.onStart()` does not call `show()`.
- If the picker opens without the default rectangle, inspect `ThrowletCropPickerMenu` and `ConditionSelectorView.showCapture(bitmap, defaultSelection)`.
- If SAC serves the crop but Throwlet does not save it, debug Throwlet's `SacCropSource`, `BuddyCropSaveActivity`, and `BuddyCropStorage`.
