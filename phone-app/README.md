# MotoNav phone app (Android)

The Android half of **MotoNav**, a motorcycle navigation HUD for the Xiaomi Watch S5. The app reads the ongoing Google Maps navigation notification, turns it into a small data packet (next maneuver, distance, street, ETA), and sends it to the watch over Bluetooth LE. The watch app draws it as a glanceable HUD.

```
Google Maps (navigation notification)
        -> MotoNav phone app (this app, BLE peripheral)
        -> BLE GATT notify
        -> MotoNav watch app (HUD, BLE central)
```

- Watch side: [`../watch-app/README.md`](../watch-app/README.md)
- Data contract between the two apps: [`../docs/protocol.md`](../docs/protocol.md) (protocol v1.5; written in Vietnamese)

Versions: phone app **0.5.0** (`versionCode` 7), watch app 0.4.5 (`versionCode` 11).

> **UI language.** Both apps follow the system language: English if the device is set to English, Vietnamese otherwise. Vietnamese on-screen strings are quoted below with an English gloss the first time they appear.

## Features

- **Automatic HUD.** When Google Maps starts navigating, MotoNav starts itself and connects to the watch (can be turned off in Settings).
- **Quick places** ("Đi nhanh", Quick go). Save up to 3 places (e.g. "Về nhà" Home, "Cơ quan" Work, plus one more) and open Google Maps navigation to them in one tap, using your preferred travel mode (default: two-wheeler; also car, bicycle, walking).
- **Launcher shortcuts** for your saved places (long-press the app icon), and a static "Mở Maps và bắt đầu" (Open Maps and start) shortcut.
- **Quick Settings tile** ("MotoNav"): one tap starts the HUD service and, by default, opens Google Maps.
- **"Về Maps" (Back to Maps) button** on the service notification.
- **Demo mode** ("Chạy thử"): drives the watch HUD with fake maneuvers, no Google Maps needed.
- **Auto-stop when idle** (off / 5 / 10 / 15 min, default 10) to save battery.
- **Live preview** of the watch face on the phone, last packet, and a diagnostics view.
- **Optional location**: only used to fill in "current location" when saving a quick place. Not required, and no Google Play services are needed.

## Requirements

- Android 10 or newer (`minSdk` 29).
- Google Maps with navigation notifications. Verified with Google Maps 26.47 (Live Updates style notification).
- Bluetooth LE.
- A MotoNav watch app on the watch (see the watch README). Tested on Xiaomi Watch S5 46mm (fw 3.222.007) with an OPPO phone on Android 17.

## Install and set up

There is no Play Store release; build the APK yourself (see [For developers](#for-developers)) and install it with `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

On first launch the app shows a setup card ("Thiết lập lần đầu", First-time setup) with the permissions below.

1. **Notification access** (required). Tap "Mở cài đặt đọc thông báo" (Open notification access settings) and switch MotoNav on. This lets the app read the Google Maps navigation notification, and only that.
   - **Android 13+, app installed outside the Play Store:** if the MotoNav entry is greyed out or says "Restricted setting", leave that screen and go to Settings > Apps > MotoNav > menu (three dots, top right) > **Allow restricted settings**. Then return and enable notification access. Android 15+ may ask for an extra confirmation.
2. **Bluetooth** (required). Grant the "Nearby devices" permission with "Cấp quyền" (Grant). It is used to advertise to the watch.
3. **MotoNav notifications** (optional). Shows a notification while the service runs so you can stop it quickly. Navigation works without it.
4. **Location** (optional). Only for "use current location" when saving a quick place.
5. **Battery optimisation** (recommended). Set MotoNav to "Unrestricted" (Settings > Battery > MotoNav), or use the "Không tối ưu pin" (No battery optimisation) row in the app. On **Xiaomi / HyperOS**, also enable **Autostart**, set battery saver to "No restrictions", and lock the app in the recent-apps screen. Otherwise the system may kill the service in the middle of a ride.

## Using it

1. Make sure the MotoNav watch app is open on the watch (it scans for the phone on its own).
2. Tap "Bắt đầu" (Start), or just start navigation in Google Maps. With auto-start on, the HUD connects by itself. You can also use the Quick Settings tile (add the "MotoNav" tile to the quick settings panel) or the launcher shortcut.
3. Pick a quick place on the home screen to open Google Maps navigation straight to it.

Settings ("Cài đặt") cover: auto-start, auto-stop timeout, open Maps when pressing Start, travel mode, quick places, permissions, the pairing code, and advanced items (demo, diagnostics, notification capture).

### Demo mode

Tap "Chạy thử" (Demo). The service starts if needed and cycles through the 13 maneuvers, 4 seconds each, with the distance counting down from about 260 m to 20 m in every step. That is enough time to see each arrow and feel both haptic levels on the watch (short pulses at 200 m or less, long urgent pulses at 50 m or less). Frames carry a demo flag, so the watch shows a "demo" label. Leaving demo mode makes the phone resend the real state. Demo is blocked while Google Maps is navigating: real navigation always wins.

## Troubleshooting

Status badges on the home screen:

| Badge (Vietnamese) | Meaning |
| --- | --- |
| "ĐANG CHẠY" | Running |
| "TỰ BẬT" | Armed, waiting for Maps to start navigating (auto-start) |
| "ĐÃ DỪNG" | Stopped (manually, or by auto-stop) |
| "THIẾU QUYỀN" | A required permission is missing, see the cards below |
| "ĐANG NỐI MAPS" | Android is still binding the notification listener |
| "BLUETOOTH TẮT" | Bluetooth is off on the phone |

Common problems:

- **"Chưa được đọc thông báo Google Maps" (Not allowed to read Google Maps notifications).** Enable notification access. If the toggle is blocked, use "Allow restricted settings" (see above). If it stays stuck on connecting, switch the permission off and on, or tap Start.
- **Watch does not find the phone.** Check the Bluetooth badge, that the service is running ("ĐANG CHẠY"), and that the watch app is open. The watch filters by the service UUID, not by device name; the advertised name is the phone's system Bluetooth name.
- **"Gửi tin lỗi" (Delivery error) or an MTU warning.** The watch is connected but cannot receive full packets. The negotiated MTU is too small (a packet needs MTU 107 or more). Disconnect and reconnect the watch, or restart the watch app.
- **Watch shows no data after the pairing code was changed.** The pairing code must be identical on both sides, see [Pairing code](#pairing-code).
- **Service killed during a ride.** Battery optimisation / autostart, see Install and set up.
- **Nothing is recognised from Maps.** The app sends "idle" rather than guessing. Use the capture tool to collect samples (see [For developers](#for-developers)).

## Known limitations and not yet verified

- Verified against a real Google Maps 26.47 notification on OPPO Android 17 (English, two-wheeler mode). More Vietnamese (`vi-VN`) samples from real roads are still needed to extend the parsing rules. Unrecognised notifications are sent as idle (`k=0`).
- **Long stops.** Maps may not repost its notification for a minute or more (straight road, red light). The phone keeps sending navigation state (`k=1`) up to 75 s after the last real Maps post. If the 3-second alive check confirms that the Maps notification is still present, it keeps it for up to 600 s so long red lights do not lose guidance. After that it sends idle. The age field `a` is never refreshed by the alive check. On the watch, data older than 10 s shows an "old Ns" marker, and "data lost" appears only above 660 s (see the watch README).
- The large notification icon is only a fallback and recognises left, right and straight, and only when the text has no direction. U-turns, roundabouts, slight/sharp turns, depart and arrive come from text.
- Not yet verified on real devices in version 0.5.0: the "Back to Maps" notification button, launcher shortcuts, the Quick Settings tile, current location with a real GPS fix, large font sizes, landscape. It is unknown whether Google Maps honours the two-wheeler mode in Vietnam.
- Only the BLE channel is implemented. The Mi Fitness / XMS interconnect channel is not (a new `Transport` would be enough).
- Long real-world rides (30 min or more, battery use) have not been measured.

## For developers

### Project layout

```
app/src/main/java/dev/motonavhud/
  core/   Plain Kotlin, no Android imports, unit-tested
    NavState.kt                 state and maneuver constants
    PayloadBuilder.kt           JSON payload, byte budget min(180, MTU-3)
    Throttle.kt                 send-on-change, 1 s heartbeat, per-k expiry
    MapsNotificationParser.kt   Vietnamese + English rule table
    ManeuverIconClassifier.kt   large-icon fallback
    AutoStartGate.kt            auto-start decision
    Transport.kt                channel interface (BLE today)
    PlacesCodec.kt, QuickPlace.kt, TravelMode.kt
  app/    Android layer
    BlePeripheralServer.kt      GATT server + advertising
    NavForegroundService.kt     foreground service (connectedDevice), 200 ms loop, demo
    NavNotificationListener.kt  only com.google.android.apps.maps, ongoing notifications
    NavBus.kt, EventLog.kt, NavLog.kt, NotificationCapture.kt
    MainActivity.kt, SettingsActivity.kt, PlacesActivity.kt, QuickLaunchActivity.kt
    NavTileService.kt, ShortcutPublisher.kt, MapsLauncher.kt, LocationHelper.kt
    Prefs.kt, PlacesStore.kt, PermissionFlow.kt, Ui.kt, UiLogic.kt, Palette.kt
    WatchBezelView.kt, ArrowPreviewView.kt
app/src/main/res/xml/shortcuts.xml   static launcher shortcut
app/src/test/java/dev/motonavhud/    JVM unit tests for core/ and app/ logic
```

The UI is built in code with plain Views (no Compose). Strings live in `res/values` (Vietnamese) and `res/values-en`.

### Build and test

Needs JDK 17+ and the Android SDK (`local.properties` with `sdk.dir`, git-ignored). AGP 8.13.2, Gradle 8.14.6 (wrapper), Kotlin 2.2.21, `minSdk` 29, `targetSdk` 34.

```bash
./gradlew testDebugUnitTest assembleDebug lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n dev.motonavhud.app/.MainActivity
```

Builds are signed with the default debug keystore. `*.jks` is git-ignored.

### BLE service

- Service UUID `6e4f0001-b5a3-f393-e0a9-e50e24dcca9e`; NAV characteristic `6e4f0002-b5a3-f393-e0a9-e50e24dcca9e`, **Notify only** (no Read; the CCCD `0x2902` accepts only `0x0001` / `0x0000` at offset 0; prepared writes are rejected).
- The main advertising packet carries flags and the service UUID. The scan response carries the phone's system Bluetooth name (Android cannot advertise an arbitrary name without renaming the adapter). If advertising fails with `DATA_TOO_LARGE`, it retries without the name. Watches must filter by service UUID.
- Notify goes only to peers that wrote the CCCD, with an immediate snapshot on subscribe. One notify is in flight server-wide and each peer keeps only its latest packet. Payloads are built at send time, so `a` and expiry are always current.
- Packets never exceed `min(180, MTU-3)`. The worst-case core is about 102 bytes, so MTU 107 or more is required; otherwise the phone does not send and shows a warning.
- While running, the service holds a `PARTIAL_WAKE_LOCK` (10 min timeout, renewed) so the 1 Hz heartbeat survives screen-off. If Bluetooth is off at start the service stops with a reason; toggling Bluetooth mid-session rebuilds the GATT server.

### Pairing code

The payload key `p` is a shared default pairing code, `MOTONAV1`. It is not a secret and not encryption; it only prevents accidental or casual mix-ups when two setups are nearby. To change it, set the same value (1 to 8 characters, `A-Za-z0-9`) in both places:

1. Phone: in the app under Settings > Pairing code ("Mã ghép nối"); it takes effect on the next packet. The default is `Prefs.DEFAULT_TOKEN`.
2. Watch: `PAIR_TOKEN` in `watch-app/src/common/config.js`, then rebuild and reinstall the `.rpk`.

The watch drops every packet whose `p` differs.

### Event log

The app writes `files/events.log` (rotating to `events.log.1`) for post-ride analysis. It records, among others, `maps_post gap_ms=` (silence between two Maps posts) and `src_age_cross bucket=` (the source age crossing its thresholds). Read it with:

```bash
adb shell run-as dev.motonavhud.app cat files/events.log.1 files/events.log
```

Short events are also logged to logcat under the tag `MotoNav` (never the raw notification text).

### Capture tool (spike S4: real notification samples)

1. Turn on the "Ghi mẫu thông báo Maps (gỡ lỗi)" (Record Maps notification samples, debug) switch under Advanced.
2. Start navigation in Google Maps.
3. Each distinct Maps notification is saved to `/sdcard/Android/data/dev.motonavhud.app/files/captures/` as `capture-NNNN-<ms>.json` (all extras as strings, tag, channel, flags) plus `-large.png` / `-small.png`. Writing happens off the main thread, capped at 500 sets, and survives restarts. "Xóa mẫu đã lưu" (Delete saved samples) removes them all.
4. Pull them: `adb pull /sdcard/Android/data/dev.motonavhud.app/files/captures ./captures`, then add real strings to the table in `MapsNotificationParserTest`.

Captures contain whatever Maps shows in the notification (street names, destinations), so do not publish them unredacted.
