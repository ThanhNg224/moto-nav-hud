package dev.motonavhud.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import dev.motonavhud.core.MapsUri
import dev.motonavhud.core.QuickPlace

/** Mở Google Maps (app) hoặc dẫn đường tới điểm đã lưu, theo phương tiện trong cài đặt (mặc định xe máy). Không bao giờ ném lỗi. */
object MapsLauncher {
    const val MAPS_PACKAGE = "com.google.android.apps.maps"

    fun isInstalled(ctx: Context): Boolean = ctx.packageManager.getLaunchIntentForPackage(MAPS_PACKAGE) != null

    fun openMaps(ctx: Context): Boolean {
        val intent = ctx.packageManager.getLaunchIntentForPackage(MAPS_PACKAGE) ?: return false
        return start(ctx, intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun navigateTo(ctx: Context, place: QuickPlace): Boolean {
        val mode = Prefs.travelMode(ctx)
        val nav = Intent(Intent.ACTION_VIEW, Uri.parse(MapsUri.navigationUri(place, mode)))
            .setPackage(MAPS_PACKAGE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (start(ctx, nav)) return true
        val web = Intent(Intent.ACTION_VIEW, Uri.parse(MapsUri.webUri(place, mode))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return start(ctx, web)
    }

    private fun start(ctx: Context, intent: Intent): Boolean = try {
        ctx.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
