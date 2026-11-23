# S1 launcher (Route A)

Mở trang debug ẩn `ThirdAppDebugFragment` của Mi Fitness bằng `app_process` (user shell).
Viết lại từ ý tưởng của oryonatan/xiaomi-band-development (nhánh `develop`, `scripts/deploy.sh`). Code chỉ: nạp class Mi Fitness bằng `PathClassLoader`, dựng `FragmentParams`, gọi `startActivity` tới `CommonBaseActivity`.
Sai khác nhỏ so với bản gốc: chấp nhận `startActivity` có >= 10 tham số (Android mới).

## Dùng
```
./build.sh                       # -> build/dex/classes.dex
adb -s <SERIAL> push build/dex/classes.dex /data/local/tmp/launch.dex
APK=$(adb -s <SERIAL> shell pm path com.xiaomi.wearable | head -1 | sed 's/package://' | tr -d '\r')
adb -s <SERIAL> shell "CLASSPATH=/data/local/tmp/launch.dex app_process / LaunchFragment $APK"
```

## Kết quả thử (2026-11-23)
- OPPO CPH2873, Android 17, Mi Fitness 3.60.0i: `FragmentParams$b` nạp được nhưng
  `ClassNotFoundException: com.xiaomi.xms.wearable.ui.debug.ThirdAppDebugFragment`.
- Đã quét 12 file dex của base.apk 3.60.0i (strings): **không còn** package `xms/wearable/ui/debug`,
  không có chuỗi "install third app" / "input package name". Lớp còn lại trong `xms/wearable/ui`:
  ThirdAppListFragment, ThirdAppShopFragment, ThirdAppLocalFragment, GuideWatchAppInstallFragment...
  => Trang debug đã bị gỡ khỏi bản này; không thể "đổi tên class" để sửa. Route A không chạy trên 3.60.0i.
- Hướng tiếp: Route B/C, hoặc tìm bản Mi Fitness cũ hơn có lớp này (cần hỏi chủ máy trước khi đổi/hạ bản).
