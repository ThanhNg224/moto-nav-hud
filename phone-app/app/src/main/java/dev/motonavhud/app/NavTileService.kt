package dev.motonavhud.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Ô bật/tắt nhanh Moto Nav HUD ngay trên thanh Quick Settings Android (1 chạm trước khi đi xe). */
class NavTileService : TileService() {

    private val onBusChange: () -> Unit = { syncTile() }

    override fun onStartListening() {
        super.onStartListening()
        NavBus.addListener(onBusChange)
        syncTile()
    }

    override fun onStopListening() {
        NavBus.removeListener(onBusChange)
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        if (NavBus.serviceRunning) {
            NavForegroundService.stopByUser(this)
            syncTile()
            return
        }
        if (!hasBluetoothPermissions()) {
            openApp()
            return
        }
        val ok = runCatching { NavForegroundService.startByUser(this, "tile") }.isSuccess
        if (!ok) openApp() else if (Prefs.isOpenMapsOnStart(this)) openMaps()
        syncTile()
    }

    private fun openMaps() {
        val intent = packageManager.getLaunchIntentForPackage(MapsLauncher.MAPS_PACKAGE) ?: return
        launchAndCollapse(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun openApp() {
        launchAndCollapse(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun launchAndCollapse(intent: Intent) {
        if (Build.VERSION.SDK_INT >= 34) {
            val pi = PendingIntent.getActivity(this, 2, intent, PendingIntent.FLAG_IMMUTABLE)
            startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun hasBluetoothPermissions(): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        return checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    }

    private fun syncTile() {
        val tile = qsTile ?: return
        val running = NavBus.serviceRunning
        tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "MotoNav"
        val btOn = getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true
        val sub = if (running) {
            if (!btOn) R.string.svc_tile_bt_off else when (NavBus.serviceStatus()) {
                NavBus.ServiceStatus.BT_OFF -> R.string.svc_tile_bt_off
                NavBus.ServiceStatus.DELIVERY_PROBLEM -> R.string.svc_tile_error
                NavBus.ServiceStatus.WAITING -> R.string.svc_tile_waiting
                NavBus.ServiceStatus.DEMO -> R.string.svc_tile_demo
                NavBus.ServiceStatus.NAVIGATING -> R.string.svc_tile_navigating
                NavBus.ServiceStatus.ARRIVED -> R.string.svc_tile_arrived
                NavBus.ServiceStatus.CONNECTED -> R.string.svc_tile_connected
            }
        } else {
            val auto = Prefs.isAutoStart(this)
            when {
                !hasBluetoothPermissions() -> R.string.svc_tile_setup
                !btOn -> R.string.svc_tile_bt_off
                auto && !NavBus.listenerConnected -> R.string.svc_tile_setup
                auto && NavBus.autoStartGate.isSuppressed() -> R.string.svc_tile_auto_paused
                auto -> R.string.svc_tile_auto
                else -> R.string.svc_tile_tap_start
            }
        }
        tile.subtitle = getString(sub)
        tile.updateTile()
    }

    companion object {
        fun requestRefresh(ctx: Context) {
            runCatching {
                requestListeningState(ctx, ComponentName(ctx, NavTileService::class.java))
            }
        }
    }
}
