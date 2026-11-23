import android.content.Intent;
import android.os.Looper;
import android.os.Parcelable;

/**
 * Launcher tối thiểu (viết lại, dựa trên ý tưởng của oryonatan/xiaomi-band-development).
 * Chạy bằng app_process dưới user shell. CHỈ làm 2 việc:
 *  1. nạp class của Mi Fitness từ APK của nó (PathClassLoader, không thực thi gì ngoài việc dựng 1 object tham số);
 *  2. gọi startActivity tới CommonBaseActivity (của Mi Fitness) kèm tham số trỏ tới ThirdAppDebugFragment.
 * Không ghi file, không mạng, không đổi cài đặt.
 */
public class LaunchFragment {
    public static void main(String[] args) {
        try {
            // Cần Looper để các class Android khởi tạo được trong tiến trình app_process.
            Looper.prepareMainLooper();
            String apkPath = args[0]; // đường dẫn base.apk của com.xiaomi.wearable

            // Bước 1: nạp class từ APK Mi Fitness.
            dalvik.system.PathClassLoader cl =
                new dalvik.system.PathClassLoader(apkPath, ClassLoader.getSystemClassLoader());
            // Builder của FragmentParams (tên bị obfuscate: có thể đổi theo version Mi Fitness).
            Class<?> builderClass = cl.loadClass("com.xiaomi.fitness.baseui.common.FragmentParams$b");
            Object builder = builderClass.newInstance();
            Class<?> fragmentClass = cl.loadClass("com.xiaomi.xms.wearable.ui.debug.ThirdAppDebugFragment");

            // Bước 2: builder.e(fragmentClass) rồi builder.b() -> FragmentParams (Parcelable).
            builderClass.getMethod("e", Class.class).invoke(builder, fragmentClass);
            Object fp = builderClass.getMethod("b").invoke(builder);

            // Bước 3: Intent mở CommonBaseActivity, nhét tham số vào extra "fragment_param".
            Intent intent = new Intent();
            intent.setClassName("com.xiaomi.wearable", "com.xiaomi.fitness.baseui.common.CommonBaseActivity");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra("fragment_param", (Parcelable) fp);

            // Bước 4: gọi ActivityManager.getService().startActivity(...) với caller là "com.android.shell".
            Class<?> amClass = Class.forName("android.app.ActivityManager");
            java.lang.reflect.Method getService = amClass.getDeclaredMethod("getService");
            getService.setAccessible(true);
            Object am = getService.invoke(null);
            for (java.lang.reflect.Method m : am.getClass().getMethods()) {
                if (m.getName().equals("startActivity") && m.getParameterTypes().length >= 10) {
                    Class<?>[] params = m.getParameterTypes();
                    Object[] callArgs = new Object[params.length];
                    for (int i = 0; i < params.length; i++) {
                        if (params[i] == int.class) callArgs[i] = 0;
                        else if (params[i] == Intent.class) callArgs[i] = intent;
                        else if (params[i] == String.class && i == 1) callArgs[i] = "com.android.shell";
                        else callArgs[i] = null;
                    }
                    m.invoke(am, callArgs);
                    System.out.println("SUCCESS");
                    return;
                }
            }
            System.out.println("FAILED: no matching startActivity method");
        } catch (Throwable e) {
            System.err.println("ERROR: " + e);
            e.printStackTrace();
        }
    }
}
