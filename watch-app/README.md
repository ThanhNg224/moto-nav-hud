# MotoNav watch app (Xiaomi Watch S5, Vela QuickApp)

The watch half of **MotoNav**, a motorcycle navigation HUD for the round 480x480 Xiaomi Watch S5 display. The phone app reads the Google Maps navigation notification and sends it over Bluetooth LE; this app shows the next maneuver arrow, distance, street name and a progress ring, and buzzes before turns.

```
Google Maps (navigation notification)
        -> MotoNav phone app (BLE peripheral)
        -> BLE GATT notify
        -> MotoNav watch app (this app, BLE central, HUD)
```

- Phone side: [`../phone-app/README.md`](../phone-app/README.md)
- Data contract between the two apps: [`../docs/protocol.md`](../docs/protocol.md) (protocol v1.5; written in Vietnamese)

Version 0.4.5 (`versionCode` 11), package `dev.motonavhud.app`, app name "MotoNav".

> **UI language.** The watch UI follows the system language: English if the watch is set to English, Vietnamese otherwise. Vietnamese on-screen strings are quoted below with an English gloss the first time they appear.

## Features

- Large turn arrow (13 maneuver arrows plus a roundabout exit badge "LỐI 2" / "EXIT 2"), distance, street name, ETA and remaining distance.
- Progress ring that changes colour as the turn gets closer: teal (over 200 m), amber (200 m or less), red-orange (50 m or less).
- **Haptics** before turns: 2 short pulses at the far threshold, 3 long pulses at the near threshold, once per turn. Quick settings offer four levels: "Chuẩn" (Standard, 200/50 m), "Phố" (City, 150/40 m), "Xa" (Far, 300/80 m) and "Tắt rung báo rẽ" (Mute turn haptics).
- **Brightness modes**: Auto (leave the system setting alone), Day (fixed 230) and Night (fixed 80). The original brightness is restored when the app leaves.
- Screen stays on while the app is open.
- Clear states: "Chờ chỉ đường" (No route yet), "Đang tính lại" (Rerouting), "Đã đến" (Arrived), "MẤT DỮ LIỆU" (DATA LOST).
- **Demo mode** that works without the phone.
- Settings are saved on the watch.

## Requirements

- Xiaomi Watch S5 (tested on the 46mm model, firmware 3.222.007). Other Vela QuickApp watches are untested.
- The MotoNav phone app (Android) running and notification access granted there.
- A way to sideload an `.rpk`, see below.

## Install

There is no store listing. Build the `.rpk` (see [For developers](#for-developers); output goes to `out/dev.motonavhud.app.{debug,release}.0.4.5.rpk`) and put it on the watch. The step-by-step sideloading guide is [`../docs/guides/S1-cai-rpk-len-s5.md`](../docs/guides/S1-cai-rpk-len-s5.md). On the verified setup the `.rpk` was installed with the Notify for Xiaomi app (Device > Third party app); the app then shows up as "MotoNav" with its icon. The watch keeps working alongside Mi Fitness in the same setup, but installing an `.rpk` through Mi Fitness itself was not possible there; see the guide.

Open the app on the watch, then start navigation on the phone. The watch scans for the phone automatically (by BLE service UUID), connects, and starts showing data.

## Using it

- **Waiting screen.** Shows a status banner and hint ("Mở MotoNav trên ĐT và bấm Bắt đầu", Open MotoNav on the phone and tap Start; "Bắt đầu chỉ đường trong Maps", Start navigation in Maps), plus two buttons: "Chạy thử" (Demo) and "Cài đặt" (Settings).
- **Quick settings.** Tap "Cài đặt", or swipe up. Cycle haptic level and brightness mode, start or stop demo, and close. The panel cannot be opened while live guidance is showing, while data is lost in the middle of a route, or while a haptic pulse is playing.
- **Demo mode.** Tap "Chạy thử" on the waiting screen (or from the settings panel). The HUD cycles through the 13 sample maneuvers, 4 seconds each, with the distance counting down within every step, through the same parsing and haptics path as real data. Tap the status line to stop. Real navigation data from the phone stops the demo automatically. When the phone itself runs its demo, the watch shows "CHẠY THỬ (ĐT)" (PHONE DEMO).

## What the HUD does with data

- Packets are accepted only if the protocol version is 1, the pairing code matches, values are in range, and the sequence number is newer. A bad packet never wipes the guidance that is already on screen.
- The navigation state `k` decides the screen: `k=0` waiting, `k=1` navigating, `k=2` "Đang tính lại" (Rerouting), `k=3` "Đã đến" (Arrived). Anything other than `k=1` clears the arrow immediately.
- **"MẤT DỮ LIỆU" (DATA LOST)** appears when no packet has arrived for more than 3 s (`STALE_MS`), when the channel drops, when the watch clock goes backwards, or when the data is too old: for `k=1`, source age above 660 s (`NAV_LOST_SOURCE_AGE_MS`); for `k=2` / `k=3`, above 10 s (`MAX_SOURCE_AGE_MS`).
- **Quiet period.** Google Maps does not repost its notification while you stand still or ride a long straight. For `k=1`, a source age between 10 s and 660 s keeps the arrow, distance and street and adds a faint "cũ Ns" ("old Ns") marker next to the distance. No banner, no data-lost buzz, no approach haptics, no colour change. The phone decides when to give up: it keeps guidance for up to 75 s without a new Maps post, or up to 600 s while it can confirm the Maps notification is still there (long red lights), then sends idle. The 660 s limit is only a safety net above that.
- Haptics fire only for `k=1`, a known maneuver, a known distance and fresh data (not while "old"). Each threshold fires once per turn step, and a more urgent pulse replaces a running one. Data-lost buzzes once per loss, only if the last state was `k=1`.
- The bottom status line shows a short rider-friendly connection state ("Đang tìm ĐT...", Searching phone; "Đang kết nối...", Connecting; "Chờ dữ liệu ĐT", Waiting for phone; "Đã kết nối", Connected; "Đang nối lại...", Reconnecting; "Bật Bluetooth trên đồng hồ", Turn on watch Bluetooth).

## Troubleshooting

- **Stuck on "Đang tìm ĐT..." (Searching phone).** Make sure the phone app is running (badge "ĐANG CHẠY") with Bluetooth on, and that the phone has notification access and Bluetooth permissions granted. The watch connects to the first device advertising the MotoNav service UUID; see `BLE_PINNED_ADDRESS` below if you have several phones nearby.
- **"Chờ dữ liệu ĐT" or "Không có dữ liệu BLE (MTU?)" (No BLE data).** Connected but no valid packet within 8 s of subscribing. The watch reconnects with backoff. Check the pairing code matches on both sides, and that the phone shows no MTU warning.
- **"Bật Bluetooth trên đồng hồ" (Turn on watch Bluetooth).** Bluetooth is off on the watch (Vela error `10001`).
- **No data although connected.** Pairing codes differ (`PAIR_TOKEN` vs the phone's pairing code).
- **"MẤT DỮ LIỆU" mid-ride.** Phone app killed by battery optimisation, phone Bluetooth turned off, or out of range. See the phone README for autostart and battery settings.
- **Detailed diagnostics.** Set `DEBUG_STATUS: true` in `src/common/config.js` and rebuild to see the full status line: `A wait/connect/error <code>` for the interconnect channel (`1001` phone app not installed, `1006` connection lost) and `C scan/seen xxxxx/connected/MTU requested/waiting data/connecting/disconnected retry Ns/No BLE data (MTU?)` for BLE, plus `scan|conn|svc|notify|MTU <code>` error codes (`10001` Bluetooth off, `10012` connect failure, `10014` device not found, `205` duplicate command, `203` not supported).

## Known limitations and not yet verified

- Long real-world rides (30 min or more), battery drain and long wrist-down / always-on-display behaviour have not been measured.
- The interconnect channel (`@system.interconnect` via Mi Fitness) is not implemented on the phone and is disabled on the watch (`ENABLE_INTERCONNECT: false`). Real interconnect would also need the app signed with the same certificate as the Android app; the keys in `sign/` are only for build and sideload.
- The `.rpk` has only been installed through a third-party sideloading app on the verified setup.
- Verified hardware: Xiaomi Watch S5 46mm fw 3.222.007 with an OPPO CPH2873 on Android 17. BLE auto-connect with `filters:[{}]`, stable receive of more than 1,000 frames, Vietnamese text with diacritics, all arrows, and both haptic levels worked.

## For developers

### Project layout

```
src/manifest.json              features: system.router, brightness, vibrator, storage,
                               interconnect, bluetooth.ble, configuration
src/app.ux
src/pages/index/index.ux       the HUD page (UI, state, menu, demo)
src/common/
  hud-logic.js                 pure logic: parse/validate, staleness, k priority, haptic steps,
                               distance format, arrow mapping, demo data (no framework imports)
  config.js                    all tunables, see below
  transport-ble.js             BLE central (channel C)
  transport-interconnect.js    interconnect (channel A), disabled
  haptics.js, status.js, i18n.js, log.js
  arrows/m00..m13.png          white arrow icons (m00 is not displayed)
tools/gen_arrows.py            regenerates the arrow PNGs (stdlib only)
test/                          node --test unit tests (hud-logic, robustness, ui-polish)
sign/debug, sign/release       self-signed keys for build/sideload (*.pem is git-ignored)
```

Both transports share the interface `create({onMessage, onStatus}) -> {start, stop}`. If both run, the first valid packet wins, through the same `z`/`q` filter.

Key `config.js` values: `STALE_MS` 3000, `MAX_SOURCE_AGE_MS` 10000, `NAV_LOST_SOURCE_AGE_MS` 660000, `VIBE_FAR_M` 200, `VIBE_NEAR_M` 50, `PAIR_TOKEN`, `BLE_MTU` 185, `BLE_FIRST_FRAME_MS` 8000, `BLE_BACKOFF_MS`, `BLE_PINNED_ADDRESS`, `FOLD_DIACRITICS` (default false), `DEBUG_STATUS`, `DEBUG_LOG`, `ENABLE_INTERCONNECT`. The manifest feature for BLE must be `system.bluetooth.ble`, not `system.bluetooth`.

### Build and test

Needs Node 18+.

```bash
cd watch-app
npm install
npm test                                 # node --test test/*.test.js
python3 tools/gen_arrows.py              # only after changing the arrows
npx aiot build   && cp dist/*.rpk out/   # debug
npx aiot release && cp dist/*.rpk out/   # release (each command clears dist/)
```

Output: `out/dev.motonavhud.app.{debug,release}.0.4.5.rpk`.

### BLE details

- The watch scans for the full service UUID `6e4f0001-b5a3-f393-e0a9-e50e24dcca9e`; every branch, including a pinned address, is filtered on results. It never relies on the device name.
- Order: connect, then only after the CONNECTED state event, request MTU 185, discover services and subscribe to notify characteristic `6e4f0002-b5a3-f393-e0a9-e50e24dcca9e`. The `connect` callback only confirms the command was sent. The MTU callback does not carry the negotiated value, so "ready" means the first valid packet.
- No packet within 8 s after subscribing triggers a reconnect with backoff (reset only after a valid packet); 8 s of silence afterwards also reconnects. Every callback is tied to an attempt generation and ignored once that attempt is closed.
- Set `BLE_PINNED_ADDRESS` (e.g. `'AA:BB:CC:DD:EE:FF'`) to connect only to one phone.

### Pairing code

`PAIR_TOKEN` defaults to `MOTONAV1`, a shared default pairing code. It is not a secret and not encryption; it only avoids accidental mix-ups. To change it, use the same value (1 to 8 characters, `A-Za-z0-9`) in `src/common/config.js` (then rebuild and reinstall) and in the phone app (Settings > Pairing code, "Mã ghép nối"). Packets with a different `p` are dropped.

### Log debugging

`DEBUG_LOG` (default `true`) writes `console.log` lines prefixed `[MotoNav]`: scan variants, matched device, connection state, setup steps, first frame, frame counter (at most one line per second), retry and backoff, data loss and haptics. The manifest sets `logLevel: log`. Firmware logs can be pulled via Mi Fitness > Debug on setups where that menu exists. On the phone side, see the event log in the phone README.
