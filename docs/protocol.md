# Phone-to-watch data contract (v1.5)

This is the single source of truth for the data contract between `phone-app/` (Android) and `watch-app/` (Xiaomi watch quick app). It is written for developers of either app. Any change to the contract must be made in this file first.

## Version history

- **v1.0/v1.1 (2026-11-23):** initial contract, revised after an external design review.
- **v1.2:** revised after an external code review. Added the pairing code `p`, made the NAV characteristic Notify-only, added effective source age on the watch, and set realistic byte budgets.
- **v1.3 (2026-11-23):** added the optional key `y`, which marks demo frames from the phone.
- **v1.4 (2026-11-25, app 0.4.2):** payload unchanged. Changed the source-age rules for `k=1` (10 to 65 s of silence is "stale", not data loss) and the deadline after which the phone falls back to `k=0`.
- **v1.5 (2026-11-25):** payload unchanged. Long stops (for example at a red light) no longer lose guidance: the phone keeps `k=1` for up to 600 s while the Maps notification is still present (confirmed every 3 s, without refreshing `a`), and the watch reports `k=1` data lost only when source age exceeds 660 s.

## Payload

JSON, UTF-8. The size limit is measured on the serialized string: at most `min(180, MTU-3)` bytes.

```json
{"v":1,"p":"MOTONAV1","z":48211,"q":42,"i":7,"a":900,"k":1,"m":3,"x":0,"d":180,"s":"Nguyễn Văn Linh","e":"12:45","r":4200}
```

| Key | Meaning | Type | Notes |
| --- | --- | --- | --- |
| `v` | contract version | int | always 1 |
| `z` | phone session | int | random number generated each time the service starts |
| `q` | sequence number within the session | int | strictly increasing for every message sent |
| `i` | step identifier | int | the phone increments it when `(m, x, s)` changes or when `d` jumps up after passing a step; used to vibrate exactly once per step |
| `a` | source age, ms | int | time from the most recent real Maps update (`postTime`) to the moment of sending; heartbeats and the notification-alive check do NOT refresh `a` |
| `k` | state | int | 0 idle / not navigating, 1 navigating, 2 rerouting, 3 arrived. **`k` takes precedence over `m`** |
| `m` | maneuver | int | see [Maneuvers](#maneuvers-m) |
| `x` | roundabout exit | int | 0 if not applicable |
| `d` | distance to the maneuver, meters | int | -1 if unknown |
| `s` | street name or instruction | string | truncated at a character (grapheme) boundary; the byte size of the JSON is re-measured after truncation; empty if absent |
| `e` | estimated arrival time `HH:mm` | string | empty if unknown |
| `r` | remaining distance, meters | int | -1 if unknown |
| `p` | pairing code | string | up to 8 characters `[A-Za-z0-9]`, set identically in `phone-app` (Prefs) and `watch-app/src/common/config.js`. The watch drops every message with a different `p`. It is sent in clear text, so it only guards against accidental or casual spoofing; it is not encryption |
| `y` | demo marker | int | OPTIONAL. Present (value 1) only when the frame comes from the phone's demo mode; absent for real navigation. It is part of the core and is never truncated. The watch shows a "demo" label when `y`=1; older watches ignore unknown keys and are unaffected (`v` stays 1). Real Google Maps navigation always wins over demo: when Maps is navigating, the phone stops demo and sends real frames (no `y`) |

`t` (phone timestamp) has been removed from the contract; measure latency with RTT when needed.

### Byte budget and truncation

When a message exceeds the byte budget, drop fields in this order: `e`, then shorten `s` progressively, then drop `s`, then drop `r` if the core still does not fit (order: e, s, r).

- The minimal core is `v,z,q,i,a,k,m,x,d,p`, plus `y` (`,"y":1`, +6 bytes) on demo frames. With realistic maximum values it is about 100 bytes, so a negotiated MTU of at least 107 is required (113 for demo frames at maximum values).
- If the core does not fit in `min(180, MTU-3)`, the phone does NOT send (it returns a "does not fit" result and shows a warning on the phone screen). It must never send a message over budget.
- The watch cannot know the real MTU (the API does not return the negotiated value). If no valid message arrives within 8 seconds after subscribing, it subscribes again. The rider sees the watch's localized connection phase; the string "Không có dữ liệu BLE (MTU?)" ("no BLE data (MTU?)") is a debug diagnostic only.

## Phone rules

- Send on change (`k`, `m`, `x`, `s`, or `d` differing by 10 m or more) and send a heartbeat every 1000 ms. A heartbeat resends the last state with `a` advanced by exactly the elapsed time and `q` incremented.
- When the Maps notification disappears, the listener disconnects, or parsing fails: send `k=0` immediately (or `k=2` if the route is being recalculated).
- Expiry by source age, measured from the last real Maps post:
  - `k=1` expires after 75 s without a new Maps post. Maps only reposts when the displayed text changes, so 30 to 60 s of silence on a straight road or while stopped is normal.
  - Exception: if the most recent notification-alive check (within the last 15 s) confirmed that the selected Maps notification is still present and ongoing, keep `k=1` for up to 600 s after the real post (for example, a long red light).
  - `k=3` expires after 15 s.
  - `k=2` does not expire by time.
  - On expiry, send `k=0`; never keep the old state.
- Every 3 s the phone checks `getActiveNotifications` to detect a disappeared notification immediately, or to pick up a post with a newer `postTime` whose callback was missed. This check does NOT refresh `a`; `a` only grows with time since the most recent real Maps post.
- Send `k=3` only when arrival is clearly recognized; cancelling navigation is `k=0`.
- Use a new `z` each time the service starts; `q` starts at 1.

## Watch rules

- **Accepting messages:** drop messages with a wrong `v`, malformed JSON, a missing core field, or a `q` that is not greater than the last accepted `q` of the same `z` (duplicates are dropped and do not refresh the timer). On a new `z`: accept it immediately, reset `q` and the vibration state, and hide guidance until the first valid message of the session arrives.
- **Staleness (watch side):** if no message is accepted for more than 3000 ms (`STALE_MS`), show "MẤT DỮ LIỆU" (data lost) immediately and hide the arrow, even when in a quiet state described below. Effective source age is `a + (now - time received)`. The thresholds are `MAX_SOURCE_AGE_MS` = 10000 ms and `NAV_LOST_SOURCE_AGE_MS` = 660000 ms, applied by `k`:
  - `k=1` (navigating): up to 10000 ms is **fresh**. Above 10000 ms and up to 660000 ms (`NAV_LOST_SOURCE_AGE_MS`) is **quiet (stale)**: keep the arrow, distance and street name, and show a dim "cũ Ns" ("old Ns" in English) next to the distance. There is no banner, no "data lost" vibration, no proximity vibration, and no change of approach color or arc. Above 660000 ms: "MẤT DỮ LIỆU" as above, arrow hidden. The phone is the real decision maker: it sends `k=0` when the Maps notification disappears or after 600 s, so the 660 s threshold is only a safety net.
  - `k=2` (rerouting) and `k=3` (arrived): limit of 10000 ms as before; beyond it, show "MẤT DỮ LIỆU".
  - If the watch clock goes backwards (now < time received), treat it as data lost until a new message arrives.
  - When the channel disconnects, clear the displayed guidance immediately (keep only the last `z`/`q` so a duplicate cannot restore it) and stay hidden until a new valid message arrives.
  - The watch remembers a few superseded `z` values and drops their messages.
- **Range checks before changing state:** `q` >= 1, `a` >= 0, `d` >= -1, `x` >= 0, every integer within JavaScript's safe range, `p` matches. Drop messages with invalid UTF-8.
- **`k` takes precedence:** when `k` is not 1, immediately clear the arrow and every turn warning. `k=0` shows the idle screen, `k=2` shows "Đang tính lại" (rerouting), and `k=3` shows "Đã đến" (arrived).
- **Vibration:** only when `k=1`, `m` is not 0, `d >= 0` and the source is fresh (no vibration while quiet/"cũ Ns"). Each step (`i`) vibrates only once, at the most urgent threshold that applies: `d <= 50` gives 3 long pulses, otherwise `d <= 200` gives 2 short pulses. If the first observation is already inside a threshold, it still vibrates (once, at the most urgent applicable threshold). When a more urgent threshold is reached, cancel any vibration in progress before starting the new one. Data loss vibrates once per loss episode; the idle screen, arrival, and the startup timeout do not vibrate.

## Maneuvers (`m`)

| Value | Meaning |
| --- | --- |
| 0 | unknown |
| 1 | straight |
| 2 | slight left |
| 3 | left |
| 4 | sharp left |
| 5 | slight right |
| 6 | right |
| 7 | sharp right |
| 8 | U-turn |
| 9 | roundabout (with exit `x`) |
| 10 | keep left |
| 11 | keep right |
| 12 | arrived |
| 13 | depart |

## Transports

- **A, interconnect:** `connect.send` from the phone (XMS `MessageApi.sendMessage(nodeId, bytes)`); the watch receives `onmessage({data})` as the JSON string above. Not yet implemented on the phone side in this iteration.
- **C, BLE GATT** (Android is the peripheral, the quick app is the central):
  - Service UUID `6e4f0001-b5a3-f393-e0a9-e50e24dcca9e`, NAV characteristic `6e4f0002-b5a3-f393-e0a9-e50e24dcca9e` (Notify only, with CCCD 0x2902; no Read, so strangers cannot read a snapshot). The CCCD accepts only 0x0001 (enable) or 0x0000 (disable) at offset 0.
  - **Advertising:** the primary packet contains the flags and the 128-bit service UUID (31 bytes total at most). The scan response carries only the device's Bluetooth name, when it fits; Android cannot set an arbitrary name. The watch does NOT depend on the name: every scan path filters by service UUID. If advertising fails with `ADVERTISE_FAILED_DATA_TOO_LARGE`, retry without the name. Check that `BluetoothLeAdvertiser` is non-null, that permissions are granted, and the start result.
  - **Watch-side order:** treat the `connect` callback only as acknowledgement that the command was issued; start MTU negotiation, service discovery and subscribe only after the CONNECTED state event.
  - **MTU:** the watch requests 185 after connecting; Android uses each peer's negotiated MTU to compute `min(180, MTU-3)`. The default MTU of 23 holds only 20 bytes, which is not enough.
  - **Notify:** send only to peers that have written the CCCD; send a snapshot immediately when a peer subscribes; allow one notification in flight at a time, keeping only the latest message until `onNotificationSent`; the watch resubscribes by itself after a reconnect.
  - **Choosing the right peer:** the watch filters by service UUID; it can also pin the address of the first successfully connected device in its configuration (`config.js`) to avoid connecting to another phone.
- The phone sends the same JSON on every transport; the watch accepts a message from whichever transport delivers a valid one first, and every message goes through the same `z`/`q` filter.
