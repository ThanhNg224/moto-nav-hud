package dev.motonavhud.app

import android.content.Context
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon

/** Đẩy các điểm đến đã lưu thành shortcut động (nhấn giữ icon app) để mở Maps dẫn đường 1 chạm. */
object ShortcutPublisher {
    private const val MAX_DYNAMIC = 3

    fun publish(ctx: Context) {
        val sm = ctx.getSystemService(ShortcutManager::class.java) ?: return
        val places = PlacesStore.all(ctx)
        // Bỏ shortcut đã ghim ra màn hình chính của điểm vừa xóa, tránh bấm vào rồi mới biết điểm không còn.
        val live = places.map { "place_${it.id}" }.toSet()
        val stale = (sm.pinnedShortcuts.map { it.id } + sm.dynamicShortcuts.map { it.id }).filter { it.startsWith("place_") && it !in live }
        if (stale.isNotEmpty()) runCatching { sm.disableShortcuts(stale) }
        val limit = minOf(MAX_DYNAMIC, sm.maxShortcutCountPerActivity - sm.manifestShortcuts.size).coerceAtLeast(0)
        val shortcuts = places.take(limit).map { p ->
            ShortcutInfo.Builder(ctx, "place_${p.id}")
                .setShortLabel(p.name)
                .setLongLabel(p.name)
                .setIcon(Icon.createWithResource(ctx, R.drawable.ic_hud_arrow))
                .setIntent(QuickLaunchActivity.navPlaceIntent(ctx, p.id))
                .build()
        }
        try {
            sm.dynamicShortcuts = shortcuts
        } catch (_: IllegalStateException) {
        } catch (_: RuntimeException) {
        }
    }
}
