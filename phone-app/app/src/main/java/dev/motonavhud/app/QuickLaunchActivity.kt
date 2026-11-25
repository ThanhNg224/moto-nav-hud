package dev.motonavhud.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast

/** Activity trong suốt cho shortcut/thông báo: bật HUD rồi nhảy sang Google Maps, tự đóng ngay. */
class QuickLaunchActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent) {
        if (!hasBluetoothPermissions()) {
            openMain()
            return finishQuietly()
        }
        if (!NavBus.serviceRunning) {
            // Hệ thống có thể từ chối bật service nền: báo rõ, không âm thầm mở Maps khi HUD không chạy.
            if (runCatching { NavForegroundService.startByUser(this, "quicklaunch") }.isFailure) {
                Toast.makeText(this, R.string.svc_hud_start_failed, Toast.LENGTH_LONG).show()
                openMain()
                return finishQuietly()
            }
        }
        val launched = when (intent.action) {
            ACTION_OPEN_MAPS -> MapsLauncher.openMaps(this)
            ACTION_NAV_PLACE -> {
                val place = intent.getStringExtra(EXTRA_PLACE_ID)?.let { PlacesStore.get(this, it) }
                if (place == null) {
                    openMain()
                    return finishQuietly()
                }
                MapsLauncher.navigateTo(this, place)
            }
            else -> true
        }
        if (!launched) {
            Toast.makeText(this, R.string.svc_maps_missing, Toast.LENGTH_SHORT).show()
            openMain()
        }
        finishQuietly()
    }

    private fun openMain() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun finishQuietly() {
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    private fun hasBluetoothPermissions(): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        return checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        const val ACTION_OPEN_MAPS = "dev.motonavhud.app.ACTION_OPEN_MAPS"
        const val ACTION_NAV_PLACE = "dev.motonavhud.app.ACTION_NAV_PLACE"
        const val EXTRA_PLACE_ID = "place_id"

        fun openMapsIntent(ctx: Context): Intent =
            Intent(ctx, QuickLaunchActivity::class.java).setAction(ACTION_OPEN_MAPS)

        fun navPlaceIntent(ctx: Context, placeId: String): Intent =
            Intent(ctx, QuickLaunchActivity::class.java).setAction(ACTION_NAV_PLACE).putExtra(EXTRA_PLACE_ID, placeId)
    }
}
