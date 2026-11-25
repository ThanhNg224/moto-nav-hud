# S0 - Hello RPK (Xiaomi Watch S5, 480x480)

Historical technical experiment (spike S0), kept for reference. Goal: show that a hello-world `.rpk` can be built from the command line so it can be sideloaded onto a real watch in S1. It is not the shipping app; see `watch-app/` for that.

## What it verified
- Both the debug and release builds exit 0, and the rpk contains the manifest, `app.js`, `pages/index/index.js`, `arrow.png` and `META-INF/CERT`.

## Not verified by this spike
- Rendering on a watch or simulator (text, Vietnamese diacritics, image rotation, keep-screen-on) was left to S1.
- No simulator was run (it needs the AIoT-IDE GUI and the Vela image) and no watch was connected.
- Whether the `package` conflicts with a "native app" was not confirmed.

## Versions
- Node v22.23.3, npm 10.9.9 (macOS)
- `aiot-toolkit` 2.0.5, `@aiot-toolkit/jsc` ^1.0.3 (local devDependencies, not installed globally)

## Build
```
cd spikes/s0-hello-rpk
npm install
npx aiot build      # debug   -> dist/com.example.motonavhud.debug.0.0.1.rpk   (~9 KB)
npx aiot release    # release -> dist/com.example.motonavhud.release.0.0.1.rpk (~8 KB)
```
Each build command clears `dist/` first, so copy the built file to `out/` (gitignored) if you want to keep it.

Signing keys are not included: create your own self-signed RSA 2048 key and certificate in `sign/debug` and `sign/release` (openssl); `sign/**/*.pem` is gitignored.

## Layout
- `src/manifest.json`: package `com.example.motonavhud` (placeholder), designWidth 480, features: router, brightness, vibrator, interconnect (declared, unused).
- `src/pages/index/index.ux`: black background, "Hello S5", a Vietnamese test line, `arrow.png` rotated 45 degrees, `setKeepScreenOn(true)` in `onShow`.
- `src/common/arrow.png`: white arrow, 128x128 RGBA (generated with plain Python, no Pillow needed).

## Notes
- The CLI prints a "please use AIoT-IDE" warning, but the build still succeeds.
- `aiot-toolkit` creates a temporary folder `spikes/.temp_s0-hello-rpk` during the build and removes it afterwards.
- `npm install` prints some audit warnings (devDependencies only, ignored).
