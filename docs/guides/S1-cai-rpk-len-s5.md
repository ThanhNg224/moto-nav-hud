# Sideloading the Moto Nav HUD `.rpk` onto a Xiaomi Watch S5

This guide is for the watch owner and does not assume expert knowledge. It explains how to install the Moto Nav HUD watch app (a Xiaomi Vela quick app, packaged as an `.rpk`) on a Xiaomi Watch S5 (global/Vietnam release, HyperOS) without going through an official store.

- Written: 2026-11-23, updated for watch app v0.4.5.
- File to install: `dev.motonavhud.app.release.0.4.5.rpk` (package `dev.motonavhud.app`). The build writes it to `watch-app/dist/`; copy it to `watch-app/out/`.
- Tested on: Xiaomi Watch S5 46mm, firmware 3.222.007, paired with an OPPO phone on Android 17 running Mi Fitness global 3.60.0i.
- Evidence tags: **[XM]** = verified from the cited URL; **[S5?]** = no direct evidence on the S5. Confidence: high / medium / low.

---

## 0. Which route should I pick?

**Short answer: use Route B (Notify for Xiaomi).** It is the route that actually worked on real hardware (see the note at the top of section 2 for why Route A did not).

| Rank | Route | Pros | Cons | Evidence on S5 |
|---|---|---|---|---|
| **1 (recommended)** | **B. Notify for Xiaomi** (Google Play) -> Device -> Third party app | Tap-through UI with official instructions; the vendor page lists the S5 as "Full"; **verified on the test watch** | Mi Fitness must be uninstalled or disabled (only one BLE connection at a time); may need the paid version; the third-party app holds your AuthKey | Worked on real hardware (Mi Fitness 3.60.0i, key taken from a Mi Fitness log export) |
| 2 | A. Original Mi Fitness + hidden debug page opened with `adb` | Uses Xiaomi's own install path; no AuthKey; no Mi Fitness removal; no pairing change | Depends on obfuscated class names in Mi Fitness; **does not work on Mi Fitness global 3.60.0i** (the debug page was removed); must run against the phone paired with the watch | One repo reports success on a Watch S5 with an older Mi Fitness (medium); failed on 3.60.0i |
| 3 | C. AstroBox (Android/desktop) | Free core features; supports "Xiaomi Watch S3 and later" | Chinese UI and docs; you sign in to your Xiaomi account inside a third-party app; the watch must "connect a new phone" | Indirect (S3 and later) (medium-low); not tested by us |
| Not recommended | Gadgetbridge | Open source | S5 "not heavily tested", uses Bluetooth Classic; nobody has reported installing an rpk on an S5; getting the key is complicated | low |
| Not available to most people | Official Mi Fitness Debug menu (Me -> About -> Debug) | Official | The Mi Fitness build with this menu is only given out through Xiaomi's business channel | n/a |

**First decision: which phone is the watch paired with?**
- **An Android phone with Mi Fitness:** use that same phone for everything below. Do not move the watch to another phone just to try this. Re-pairing to a new phone usually requires unlinking, and unlinking can erase data on the watch [S5?, medium].
- **An iPhone:** an `.rpk` cannot be installed from iOS (the rw4-cmatrix project states "iOS cannot install `.rpk`", medium). You need an Android phone. Think carefully before unlinking the watch.
- **Not paired with anything:** install Mi Fitness from the Play Store on an Android phone, pair the watch, then follow Route B.

---

## 1. Prerequisites and common steps

### 1.1 Record your current state (required, 2 minutes)
1. On the watch: Settings -> About, then photograph the **firmware version** and model.
2. On the phone: Mi Fitness -> Profile -> About, then note the **Mi Fitness version**. Or from a computer:
   `adb -s <SERIAL> shell dumpsys package com.xiaomi.wearable | grep versionName`
3. Keep this in a private note. Do not commit it to the repository.

### 1.2 Put the `.rpk` on the phone (from a computer with adb)
Run from the project root (`<project>/`):
```bash
adb devices -l                       # find the serial of the phone paired with the watch
adb -s <SERIAL> push watch-app/out/dev.motonavhud.app.release.0.4.5.rpk /sdcard/Download/
adb -s <SERIAL> shell ls -l /sdcard/Download/ | grep motonavhud   # the file should be listed
```
- Alternatively, copy the file to the phone any other way (USB file transfer, cloud drive).
- If the file picker does not show the `.rpk`, try a copy with a shorter name: `adb -s <SERIAL> shell cp /sdcard/Download/dev.motonavhud.app.release.0.4.5.rpk /sdcard/Download/hello.rpk`.

### 1.3 AuthKey rules (Routes B and C, Gadgetbridge)
- The AuthKey gives full control of the watch over Bluetooth. The bandbbs wiki calls it a sensitive credential and says not to screenshot or share it (https://wiki.bandbbs.cn/Guides/watchface_custom_tool/watchface_custom_tool-install.html, high).
- **Never paste the AuthKey into an AI chat or into any file in the repository** (including `.env`, notes, logs). If you must store it, use a password manager.
- If you think it has leaked: remove the device from the third-party tool, then re-bind through the official app to rotate the key (bandbbs wiki, medium).
- Route A does not need an AuthKey, which is one of its advantages.

---

## 2. Route B (recommended, verified): Notify for Xiaomi

> **Why this is recommended:** Route A does **not work** on Mi Fitness global 3.60.0i (the `ThirdAppDebugFragment` class is gone). The route that was actually used and verified is **Notify for Xiaomi** (`com.mc.xiaomi1`) on the phone paired with the watch, with the key taken from a Mi Fitness log export, installing the rpk through **Device -> Third party app**.

### Evidence
- The vendor compatibility page lists the **Xiaomi Watch S5 as "Full"**, with the official-app column saying **"Must Uninstall"**. The page also has a section on installing self-made `.rpk` apps (VelaJS). http://www.mibandnotify.com/xiaomi-mi-band/ **[XM]** (medium: the vendor's own claim).
- Official instructions: "Open Notify -> Device -> Third party app -> upload .rpk". Support "depends on firmware" and the S5 is not mentioned separately. http://www.mibandnotify.com/xiaomi-mi-band/notify-xms-app-instructions.php **[XM]** (medium).
- Price: Free $0, Pro $3 (one-time purchase), no trial seen. It is unclear whether "Third party app" needs Pro; rw4-cmatrix also says it is undocumented (low).
- The setup wizard can obtain the pairing key **through your Xiaomi account**, so Mi Fitness does not need to stay on the phone. https://github.com/hyperpostulate/rw4-cmatrix (medium; tested on Redmi Watch 4).

### Prerequisites
- An Android phone. The "Notify for Xiaomi/Mi Band 8-11" app (`com.mc.xiaomi1`) from Google Play.
- The Xiaomi account **the watch is currently bound to**, signed in inside Notify's wizard (or the key obtained as described above).
- Mi Fitness must not hold the BLE connection: uninstall it, or force-stop it and revoke its "Nearby devices" permission.
- The `.rpk` in the phone's Download folder (section 1.2).

### Steps
1. Complete sections 1.1 and 1.2.
2. **Do not tap "Unpair" in Mi Fitness.** Unpairing invalidates the key (Gadgetbridge docs, high; rw4-cmatrix: "do not un-pair... before uninstalling").
3. On the phone that has Mi Fitness: Settings -> Apps -> Mi Fitness -> **Force stop**, then Permissions -> **Nearby devices -> Don't allow**. Also disable Mi Fitness in battery/autostart settings if present. The vendor page says "Must Uninstall": if Notify still reports a conflict, uninstall Mi Fitness (without unpairing).
4. Install Notify from the Play Store, open it, run the wizard, choose the S5, and sign in to your Xiaomi account **inside the app**. Do not copy the key anywhere else.
5. Wait for the connected status. The watch may ask you to confirm pairing; accept.
6. In Notify: **Device** -> **Third party app** -> pick `dev.motonavhud.app.release.0.4.5.rpk` from **Download** -> follow the on-screen instructions, keeping the watch close to the phone.
7. On the watch, find **"Moto Nav HUD"** in the app list and open it (see section 6).

### Restoring Mi Fitness afterwards
1. In Notify: disconnect or force-stop Notify and revoke its Nearby devices permission. Do not remove the device from your account.
2. Re-enable the permission for Mi Fitness (or reinstall it and sign in with the same account), open it, and wait for the watch to reconnect.
3. Check that "Moto Nav HUD" is still on the watch and that health data still syncs. That reconnecting works without re-pairing is an **inference** [S5?, medium].

### Troubleshooting
- Cannot connect: Mi Fitness is still running in the background and holding BLE. Force-stop it again and check Nearby devices.
- No "Third party app" entry: the Notify app is outdated, or the feature needs Pro (unclear). Update the app and check the purchase screen.
- Install fails: the S5 firmware may block it. Write down the exact error text and report it.

---

## 3. Route A: original Mi Fitness + hidden debug page via adb

> **Status: does not work on Mi Fitness global 3.60.0i.** That build no longer contains `ThirdAppDebugFragment` (see `spikes/s1-launcher/README.md`). Use this route only if you have an older Mi Fitness build that still has the class.

### Evidence
- The international Mi Fitness (`com.xiaomi.wearable`) used to ship the class `com.xiaomi.xms.wearable.ui.debug.ThirdAppDebugFragment` with four buttons: "click to input package name", "install third app", "uninstall third app", "go third app list". No menu leads to it, so it must be opened with `app_process` run as the shell user through adb. Source: https://github.com/oryonatan/xiaomi-band-development/blob/develop/docs/deployment-real-device.md (MIT, updated 2026-04). **[XM]** on an international Mi Band 8 Pro; the script has special handling for the Samsung keyboard and file picker (medium).
- The repo https://github.com/CatWar2/xiaomi-watch-s5-notes-app (updated 2026-11-18) states "tested on real hardware (Xiaomi Watch S5 + Xiaomi 15 on HyperOS)" and installs via `ThirdAppDebugFragment`. **[XM, 1 repo, 1 star, AI-generated code]**: confidence **medium** for the S5.
- This matches Xiaomi's official flow in the Vela FAQ ("Mi Fitness -> Me -> About -> Debug -> third-party apps -> enter package -> install -> choose local rpk"). https://iot.mi.com/vela/quickapp/en/guide/other/faq.html (high). Route A is just a way to open the same screen without the business build of Mi Fitness.

### Prerequisites
- An Android phone **paired and connected** to the S5 through **international Mi Fitness** `com.xiaomi.wearable`. The China build `com.xiaomi.wearable.chn` does not work (per the oryonatan docs, medium). Whether the VN/global account region matters is unknown [S5?, low].
- USB debugging enabled on that phone, and `adb devices` lists it.
- A computer with the Android SDK (`build-tools` including d8, plus `platforms`) and `javac`.
- No root, no AuthKey, and no app removal needed.

### Steps
1. Complete sections 1.1 and 1.2 (the rpk is in `/sdcard/Download/`).
2. Open Mi Fitness and confirm the watch shows **Connected**. Keep the watch next to the phone with more than 30% battery.
3. Build the launcher `.dex` from the project's launcher spike: see `spikes/s1-launcher/README.md` (it is a rewrite of `LaunchFragment` from the oryonatan repo's `scripts/deploy.sh`). **Read the code before running it**: it only loads Mi Fitness classes and calls `startActivity` to `CommonBaseActivity`. Build in a temporary folder or use the spike's `build.sh`:
   ```bash
   SDK=~/Library/Android/sdk
   javac -source 8 -target 8 -bootclasspath $SDK/platforms/android-34/android.jar LaunchFragment.java -d classes
   $SDK/build-tools/36.0.0/d8 classes/LaunchFragment.class --output dex/
   adb -s <SERIAL> push dex/classes.dex /data/local/tmp/launch.dex
   ```
   (Adjust the SDK path and build-tools version to your machine.)
4. Open the hidden debug page:
   ```bash
   APK=$(adb -s <SERIAL> shell pm path com.xiaomi.wearable | head -1 | sed 's/package://' | tr -d '\r')
   adb -s <SERIAL> shell "CLASSPATH=/data/local/tmp/launch.dex app_process / LaunchFragment $APK"
   ```
   Expected: it prints `SUCCESS` and the phone shows a page with the four English buttons above.
5. On the phone, tap manually (no automation needed):
   1. **click to input package name** -> type `dev.motonavhud.app` -> OK. The Samsung keyboard may insert a space after the first dot; delete it before tapping OK.
   2. **install third app** -> the file picker opens -> go to **Downloads** -> choose `dev.motonavhud.app.release.0.4.5.rpk`.
   3. Wait for the watch to receive the file. Keep the phone screen on and Mi Fitness in the foreground.
6. On the watch: open the app list (press the top button), find **"Moto Nav HUD"** and open it (see section 6).
7. To uninstall: repeat step 4, enter the package name, then tap **uninstall third app**. To list apps: **go third app list**.
8. Cleanup (optional): `adb -s <SERIAL> shell rm /data/local/tmp/launch.dex`.

### Pairing / Mi Fitness
- Nothing changes: Mi Fitness keeps the connection and pushes the rpk itself. No force-stop or restore needed.

### Troubleshooting
| Symptom | Likely cause | Fix |
|---|---|---|
| `ClassNotFoundException` / `NoSuchMethodException` (`FragmentParams$b`, method `e`/`b`), or `ClassNotFoundException` for `ThirdAppDebugFragment` | Mi Fitness renamed its obfuscated classes, or removed the debug page (as in 3.60.0i) | Note the Mi Fitness version and use Route B instead |
| `ERROR: ... not found` / empty `pm path` | International Mi Fitness is not installed | Check the package `com.xiaomi.wearable` |
| `SecurityException` on `startActivity` | The Android ROM blocks the shell from opening non-exported activities | Use Route B |
| Tapping install does nothing on the watch | Watch disconnected, or Mi Fitness was killed | Reopen Mi Fitness, wait for Connected, retry |
| An old app version does not update | The watch caches the old version | Increase `versionCode` when rebuilding, or uninstall first (oryonatan, medium) |
| Signature error | Should not happen for an app that does not use interconnect | A debug-signed rpk is enough for apps that do not call interconnect (rw4-cmatrix, medium) |

---

## 4. Route C: AstroBox

### Evidence
- Official docs: "Xiaomi Watch S3 and later" is in the fully supported group. Connect by **Xiaomi account sync** (recommended) or by entering the AuthKey manually. The docs do **not** say how to get the AuthKey. https://abox.run/docs/usage/connection **[XM]**; S5 inferred from "S3 and later" (medium-low).
- Installing a local file: use the resource card on the device page or the "+" button in the queue, choose the file, and if it does not install automatically tap the start button ("开始"). https://abox.run/docs/usage/resource **[XM]**. That page does not mention `.rpk`/quick apps specifically, but the home page says quick apps can be installed (medium).
- Requires Android 10 or later, Location (precise), Bluetooth and Nearby devices permissions; download the APK from the official site (V2.2.6). https://abox.run/docs/usage/install **[XM]**. Source code: https://github.com/AstralSightStudios/AstroBox-NG (AGPL-3.0). Core features are free (https://astrobox.online).

### Steps (summary; the UI is in Chinese)
1. Complete sections 1.1 and 1.2. Force-stop Mi Fitness and revoke Nearby devices as in Route B step 3. The AstroBox docs do not require this; it is inferred from the one-connection rule [S5?, medium].
2. Install the AstroBox APK from the official site, grant permissions, and finish the first-run setup (OOBE).
3. Open the device card (the capsule at the bottom) -> device switcher -> **导入** (import) -> sign in to your Xiaomi account -> tap **登录** (log in). Two-step verification may appear.
4. On the watch: Settings -> **我的设备** (My device) -> **连接新手机** (Connect new phone) -> green tick. In AstroBox pick the S5; the watch vibrates; tap the green tick again; then on Android tap **配对与连接** (pair and connect) in the pairing notification.
   - Warning: it is unknown whether "Connect new phone" permanently disconnects the watch from Mi Fitness [S5?, low]. Do this last, only if Routes A and B fail.
5. Open the queue -> **+** -> choose the quick app (快应用) type -> pick `dev.motonavhud.app.release.0.4.5.rpk` in Download -> tap **开始** if needed.
6. On the watch, open "Moto Nav HUD" (section 6).
7. Restore Mi Fitness as in Route B. You may have to tap "连接新手机" again from Mi Fitness [S5?, low].

---

## 5. Routes not recommended (for reference)

- **Gadgetbridge**: the Watch S5 is listed as "Mostly supported", with the caveats "As we don't have that device it is not known if it works correctly" and "not heavily tested" (https://gadgetbridge.org/gadgets/wearables/xiaomi/, high for the page content). Issue #7197 shows the S5 connects over **Bluetooth Classic (RFCOMM)** and was once misdetected as an S4; there is no report of installing an rpk on an S5 (https://codeberg.org/Freeyourgadget/Gadgetbridge/issues/7197, medium). The protobuf docs list "Flashing (... rpk)" (https://gadgetbridge.org/basics/topics/xiaomi-protobuf/, high). Getting the key means digging through Mi Fitness logs or using "Xiaomi Cloud Tokens Extractor", i.e. handing your Xiaomi password to a third-party script (https://gadgetbridge.org/basics/pairing/huami-xiaomi-server/, high). The maintainers also warn of brick risk when installing unsupported resources. Only worth trying if you later want to drop Mi Fitness entirely.
- **bandbbs watch-face customization tool** (Chinese): lists the S5 and offers one-tap Bluetooth install using an AuthKey read from Mi Fitness (Mi Fitness -> Me -> About -> tap the orange logo repeatedly -> use the "AuthKey reader" in the tool). It mainly serves resources from its own catalog; whether it installs a local `.rpk` is unclear. It also uses "connect new phone" (bandbbs wiki, medium). The "tap the logo/version repeatedly" trick in the international Mi Fitness has **no confirming source** (low).
- **Official Debug menu** (Mi Fitness -> Me -> About -> Debug -> third-party apps): it exists, but the FAQ says the Mi Fitness build with this menu is issued "through business group" (https://iot.mi.com/vela/quickapp/en/guide/other/faq.html, high). No document confirms that tapping the version number in the Play Store build opens it (low). Trying 10 taps is harmless, but do not expect it to work.
- **MiWearBridge(-fixed)**: needs LSPosed and root. Not suitable.

---

## 6. After installing: what to check on the watch

Open "Moto Nav HUD" and confirm that:
1. It appears in the watch app list with its name and icon.
2. It opens quickly (under about 2 seconds) with no error message.
3. It exits and reopens normally.

If something looks wrong, write down any error text verbatim (including Chinese text) and take a screenshot of the watch (hold the top and bottom buttons together, or capture from the phone).

For reference, the historical S0/S1 "hello" spike (package `com.example.motonavhud`, `spikes/s0-hello-rpk/`) was verified with a longer visual checklist: black background; the text "Hello S5"; the Vietnamese test line "Đường Nguyễn Văn Linh ơ ư ế ộ" (a street name plus diacritic test characters) rendering with correct diacritics (no boxes or `?`); a white 128 px arrow rotated 45 degrees; centered layout not clipped at the round edge; the screen staying on; and no errors. If everything looks twice as large, clipped, or half-size, note it: another S5 repo uses `designWidth: 240` while the spike uses 480.

**Suggested report template:**
```
Route used: A / B / C
Phone: <model>, Android <x>; Mi Fitness version: <x>; Watch S5 firmware: <x>
Install: success / error "<verbatim text>"
Observations: <what you saw>
Screenshots: <attached>
```
Do not share your AuthKey, watch serial number, MAC address, or any screenshot that contains a key.

---

## 7. Safety

- **Do not factory-reset the watch and do not "Unpair" in Mi Fitness** to experiment. A reset changes the Bluetooth MAC and invalidates the key (Gadgetbridge docs, high), erases data, and can force a full re-pair.
- **Record the firmware and Mi Fitness versions first** (section 1.1). Temporarily **turn off auto-update** for Mi Fitness (Play Store -> Mi Fitness -> three-dot menu -> untick "Enable auto update") during development: Route A depends on internal class names, so a new version can break it. Postpone watch OTA firmware updates until you are done, because newer firmware may block sideloading (low-medium; inferred from reports that a global Band 10 Pro did not answer third-party app requests).
- **Community tool risk**: Notify, AstroBox and Gadgetbridge are not affiliated with Xiaomi. Signing in to your Xiaomi account in a third-party app hands them a token. Sideloading may violate Xiaomi's terms of service, and in theory Xiaomi could lock features or the account (no reports seen, low). Resources for the wrong model can brick the watch (Gadgetbridge maintainer warning).
- **Only install an rpk you built yourself** (from `spikes/` or `watch-app/out/`), never an unknown rpk.
- Route A runs `app_process` with shell privileges: only run code you have read. Root is neither needed nor appropriate.
- Everything here is done by the watch owner. This guide has not been automated or run on your device by anyone else.

---

## Summary

- Recommended route: **B, Notify for Xiaomi** (the S5 is "Full" per the vendor, it was verified on real hardware, but Mi Fitness must be uninstalled or disabled). Fallback: **C, AstroBox**.
- **A (original Mi Fitness + `ThirdAppDebugFragment` via adb)** is the cleanest on paper (no AuthKey, no pairing change) but does not work on Mi Fitness global 3.60.0i.

## Blocking risks
- Obfuscated Mi Fitness class names (`FragmentParams$b`, method `e`/`b`) may differ on your Mi Fitness version, or the debug page may be removed, which breaks Route A.
- A watch paired with an iPhone, or with a device without adb, requires changing the paired phone, with a risk of data loss.
- S5 global firmware may refuse third-party rpk installs (no evidence seen, low).

## Open questions
- Whether Notify's "Third party app" needs Pro.
- The correct `designWidth` for the S5 (480 or 240).
