# S1 launcher (Route A)

Historical technical experiment (spike S1), kept for reference. It tried to open Mi Fitness's hidden `ThirdAppDebugFragment` page with `app_process` (user shell) so an `.rpk` could be installed on the watch. See `docs/guides/S1-cai-rpk-len-s5.md` for the current install guide.

It is a rewrite of the idea in oryonatan/xiaomi-band-development (`develop` branch, `scripts/deploy.sh`). The code only loads Mi Fitness classes with `PathClassLoader`, builds a `FragmentParams`, and calls `startActivity` to `CommonBaseActivity`. One small difference from the original: it accepts a `startActivity` with 10 or more parameters (newer Android).

## Build and run
```
./build.sh                       # -> build/dex/classes.dex
adb -s <SERIAL> push build/dex/classes.dex /data/local/tmp/launch.dex
APK=$(adb -s <SERIAL> shell pm path com.xiaomi.wearable | head -1 | sed 's/package://' | tr -d '\r')
adb -s <SERIAL> shell "CLASSPATH=/data/local/tmp/launch.dex app_process / LaunchFragment $APK"
```

## Result (2026-11-23)
- Tested on an OPPO CPH2873 (Android 17) with Mi Fitness 3.60.0i: `FragmentParams$b` loads, but the launch fails with `ClassNotFoundException: com.xiaomi.xms.wearable.ui.debug.ThirdAppDebugFragment`.
- Scanning the strings of all 12 dex files in the 3.60.0i `base.apk` showed that the `xms/wearable/ui/debug` package is gone, and neither "install third app" nor "input package name" appears. The remaining classes in `xms/wearable/ui` include `ThirdAppListFragment`, `ThirdAppShopFragment`, `ThirdAppLocalFragment` and `GuideWatchAppInstallFragment`.
- Conclusion: the debug page was removed from this Mi Fitness version, so renaming a class cannot fix it. Route A does not work on 3.60.0i.
- Next options: Route B or C from the install guide, or an older Mi Fitness build that still has the class (downgrading is a risk for the watch owner to decide).
