package dev.motonavhud.app

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat

/**
 * Quyền và hành động dùng chung cho MainActivity và SettingsActivity (chuyển nguyên văn từ MainActivity cũ).
 * Activity chuyển tiếp onRequestPermissionsResult sang [handleResult]; [onChanged] để màn vẽ lại.
 */
class PermissionFlow(private val a: Activity, private val onChanged: () -> Unit) {
    private var pendingAfterPermission: (() -> Unit)? = null
    private val rationaleBefore = HashMap<String, Boolean>()

    private val uiPrefs get() = a.getSharedPreferences("ui_state", Context.MODE_PRIVATE)

    fun listenerGranted() = NotificationManagerCompat.getEnabledListenerPackages(a).contains(a.packageName)

    fun batteryIgnored() = a.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(a.packageName) == true

    fun missingBluetoothPermissions(): List<String> {
        if (Build.VERSION.SDK_INT < 31) return emptyList()
        return listOf(Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT)
            .filter { a.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
    }

    fun notificationsMissing() =
        Build.VERSION.SDK_INT >= 33 && a.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    /** Kênh thông báo của service (id khớp NavForegroundService.CHANNEL_ID); null nghĩa là chưa tạo, không coi là bị chặn. */
    fun serviceChannelBlocked() =
        a.getSystemService(NotificationManager::class.java).getNotificationChannel(NOTIF_CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE

    fun notifState() = notifStep(!notificationsMissing(), NotificationManagerCompat.from(a).areNotificationsEnabled(), serviceChannelBlocked())

    val postNotifications = listOf(Manifest.permission.POST_NOTIFICATIONS)

    private fun memory(p: String) = PermMemory(uiPrefs.getBoolean("denied_$p", false), uiPrefs.getBoolean("perm_$p", false))

    /** Chụp rationale TRƯỚC khi xin để phân biệt từ chối lần hai (vĩnh viễn) với đóng hộp thoại lần đầu (mơ hồ). */
    private fun launchRequest(perms: List<String>, code: Int) {
        perms.forEach { rationaleBefore[it] = a.shouldShowRequestPermissionRationale(it) }
        a.requestPermissions(perms.toTypedArray(), code)
    }

    private fun recordResult(perms: Array<out String>, results: IntArray) {
        if (perms.isEmpty() || results.size != perms.size) return
        uiPrefs.edit().apply {
            perms.forEachIndexed { i, p ->
                val m = permAfterResult(
                    memory(p), results[i] == PackageManager.PERMISSION_GRANTED,
                    rationaleBefore[p] == true, a.shouldShowRequestPermissionRationale(p),
                )
                putBoolean("denied_$p", m.deniedOnce)
                putBoolean("perm_$p", m.permanent)
            }
        }.apply()
    }

    /** Chắc chắn không còn hộp thoại: nút chính mở cài đặt ứng dụng. */
    fun isPermPermanent(perms: Collection<String>) = perms.any { memory(it).permanent }

    /** Đã bị từ chối ít nhất một lần: luôn hiện thêm lối mở cài đặt ứng dụng bên cạnh nút xin quyền. */
    fun wasDeniedOnce(perms: Collection<String>) = perms.any { memory(it).deniedOnce }

    fun openAppSettings() {
        a.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${a.packageName}")))
    }

    fun openListenerSettings() {
        a.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    fun openBatterySettings() {
        runCatching { a.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
            .onFailure { Toast.makeText(a, R.string.ui_perm_battery_fallback, Toast.LENGTH_SHORT).show() }
    }

    /** Bluetooth là quyền bắt buộc, xin riêng; bị từ chối vĩnh viễn thì mở cài đặt ứng dụng. */
    fun ensureBluetooth(then: () -> Unit) {
        val missing = missingBluetoothPermissions()
        when {
            missing.isEmpty() -> then()
            isPermPermanent(missing) -> {
                Toast.makeText(a, R.string.ui_perm_bt_denied_toast, Toast.LENGTH_LONG).show()
                openAppSettings()
            }
            else -> {
                pendingAfterPermission = then
                launchRequest(missing, REQ_BT)
            }
        }
    }

    /** Thông báo của MotoNav là tùy chọn: xin quyền, hoặc mở cài đặt thông báo app/kênh khi bị tắt. */
    fun onNotificationsClick() {
        when (notifState()) {
            NotifStep.OK -> Unit
            NotifStep.APP_BLOCKED ->
                a.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, a.packageName))
            NotifStep.CHANNEL_BLOCKED ->
                a.startActivity(
                    Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, a.packageName)
                        .putExtra(Settings.EXTRA_CHANNEL_ID, NOTIF_CHANNEL_ID),
                )
            NotifStep.REQUEST -> if (isPermPermanent(postNotifications)) openAppSettings() else launchRequest(postNotifications, REQ_NOTIF)
        }
    }

    /** Gọi từ Activity.onRequestPermissionsResult (sau super). */
    fun handleResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        recordResult(permissions, grantResults)
        if (requestCode == REQ_BT) {
            val then = pendingAfterPermission
            pendingAfterPermission = null
            if (then != null && missingBluetoothPermissions().isEmpty()) then()
        }
        onChanged()
    }

    /** Bật/tắt chạy thử HUD (chuyển nguyên văn từ toggleDemo cũ); [after] chạy khi trạng thái demo đã đổi hoặc bị chặn. */
    fun toggleDemo(after: () -> Unit) {
        if (NavBus.demo) {
            NavBus.demo = false
            NavBus.onServiceWake?.invoke()
            NavLog.i("demo off")
            after()
            return
        }
        if (!NavBus.canStartDemo()) {
            Toast.makeText(a, R.string.ui_demo_blocked, Toast.LENGTH_LONG).show()
            return
        }
        ensureBluetooth {
            if (!NavBus.serviceRunning) NavForegroundService.startByUser(a)
            NavBus.demo = true
            NavBus.onServiceWake?.invoke()
            NavLog.i("demo on")
            after()
        }
    }

    companion object {
        const val REQ_BT = 1
        const val REQ_NOTIF = 2
        const val NOTIF_CHANNEL_ID = "nav_service"
    }
}
