# MotoNav – Moto Nav HUD

Turn-by-turn navigation on your wrist for motorcycle riders. MotoNav reads the navigation notification from **Google Maps** on your Android phone and shows a clear, glanceable heads-up display on a **Xiaomi Watch S5** (Vela QuickApp), sent over Bluetooth LE.

| Component | Latest version | What it is |
| --- | --- | --- |
| [`phone-app/`](phone-app/README.md) | **0.5.0** (code 7) | Android app: reads Google Maps notifications and broadcasts them over BLE |
| [`watch-app/`](watch-app/README.md) | **0.4.5** (code 11) | Xiaomi Watch S5 mini-app (`.rpk`): the HUD itself |
| [`docs/protocol.md`](docs/protocol.md) | **v1.5** | Phone → watch data contract (single source of truth) |

## How it works

```
Google Maps ──notification──▶ Phone app ──BLE GATT notify (1 Hz)──▶ Watch HUD
```

1. You start navigation in Google Maps.
2. The phone app parses the ongoing navigation notification (maneuver, distance, street, ETA).
3. It sends a small JSON message to the watch once per second over Bluetooth LE.
4. The watch shows the arrow, distance, street name and progress ring, and vibrates before each turn.

No account, no cloud, no internet connection needed by MotoNav itself. Everything stays between your phone and your watch.

## Features

**Watch HUD (480×480 round AMOLED)**
- 13 maneuver arrows (straight, slight/normal/sharp turns, keep left/right, U-turn, roundabout with exit badge, depart, arrive).
- 270° progress ring that changes color as you approach a turn (`> 200 m` teal → `≤ 200 m` amber → `≤ 50 m` red-orange).
- Haptic alerts per turn: two short pulses at ≤ 200 m, three long pulses at ≤ 50 m. Vibration level and screen brightness (Auto / Day / Night) are adjustable on the watch.
- Clear states: waiting for navigation, rerouting, arrived, "old Ns" when Maps goes quiet (for example at a long red light), and data lost.
- Built-in **Demo** mode to try it without riding.

**Phone app**
- Starts automatically when Google Maps begins navigating, and stops itself after an idle timeout (Off / 5 / 10 / 15 min).
- Quick places (Home, Work, and one more) that open Google Maps directly, with launcher shortcuts and a Quick Settings tile.
- Live preview of the watch face on the phone, one-tap permission prompts, and an event log for troubleshooting.
- Supports Google Maps Live Updates notifications.

## Requirements

- **Watch:** Xiaomi Watch S5 (tested on the 46 mm model, firmware 3.222.007).
- **Phone:** Android 10 or newer (minSdk 29) with Bluetooth LE and Google Maps. Tested on an OPPO phone running Android 17.
- A way to install an `.rpk` on the watch (see the [sideloading guide](docs/guides/S1-cai-rpk-len-s5.md)). MotoNav runs alongside Mi Fitness.

> Both apps follow the system language: English if your phone/watch is set to English, Vietnamese otherwise. Vietnamese on-screen strings quoted in the docs come with an English gloss.

## Quick start

1. **Phone app** – build and install it (see [`phone-app/README.md`](phone-app/README.md)):
   ```bash
   cd phone-app
   ./gradlew assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
   Then grant *Notification access*, Bluetooth and notification permissions when asked.
2. **Watch app** – build the `.rpk` and install it on the watch (see [`watch-app/README.md`](watch-app/README.md) and the [sideloading guide](docs/guides/S1-cai-rpk-len-s5.md)):
   ```bash
   cd watch-app
   npm install
   npx aiot release
   ```
3. Open the phone app, tap **Demo** to confirm the watch connects, then start navigation in Google Maps.

## Repository layout

| Path | Purpose |
| --- | --- |
| [`phone-app/`](phone-app/README.md) | Android app (Kotlin, Gradle Kotlin DSL) |
| [`watch-app/`](watch-app/README.md) | Watch mini-app (JavaScript / Vela UX, built with `aiot-toolkit`) |
| [`docs/protocol.md`](docs/protocol.md) | Wire protocol between phone and watch |
| [`docs/guides/`](docs/guides/S1-cai-rpk-len-s5.md) | Step-by-step guide: install the `.rpk` on the watch |
| [`spikes/`](spikes) | Small early experiments that de-risked the technology |

## Status

- Verified on real hardware: BLE link (1,000+ frames stable), Vietnamese text with diacritics, all arrows, vibration patterns, running next to Mi Fitness, Google Maps 26.47 Live Updates notifications.
- Not yet verified: long real-world rides (15–30+ minutes) for battery and long wrist-down behavior, and Vietnamese-language (`vi-VN`) Google Maps notification samples.
- Not implemented: the Mi Fitness interconnect channel (the BLE channel is the one in use).

## Privacy and security

- MotoNav only reads the navigation notification of Google Maps (`com.google.android.apps.maps`) and does not log notification text.
- The phone and watch share a default pairing code (`MOTONAV1`). It is not a secret; it only prevents accidental cross-talk between two nearby setups. You can change it (see the app READMEs).
- Signing keys (`*.pem`, `*.jks`) and `local.properties` are git-ignored. Generate your own keys when building the watch app.

## Contributing

Issues and pull requests are welcome. Read [`docs/protocol.md`](docs/protocol.md) first: protocol changes must be made there before touching either app.

## License

[MIT](LICENSE). MotoNav is an independent project and is not affiliated with Xiaomi or Google.
