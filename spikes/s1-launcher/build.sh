#!/bin/bash
# Biên dịch LaunchFragment.java -> build/dex/classes.dex
set -euo pipefail
cd "$(dirname "$0")"
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
rm -rf build; mkdir -p build/classes build/dex
javac -source 8 -target 8 -bootclasspath "$SDK/platforms/android-34/android.jar" LaunchFragment.java -d build/classes
"$SDK/build-tools/36.0.0/d8" build/classes/LaunchFragment.class --output build/dex/
ls -l build/dex/classes.dex
