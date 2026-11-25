package dev.motonavhud.app

import android.content.Context
import dev.motonavhud.core.MAX_QUICK_PLACES
import dev.motonavhud.core.PlaceKind
import dev.motonavhud.core.PlacesCodec
import dev.motonavhud.core.QuickPlace
import java.util.UUID

/** Điểm đến nhanh (Nhà/Cơ quan/khác) lưu trong SharedPreferences "places". */
object PlacesStore {
    private const val FILE = "places"
    private const val KEY_LIST = "list"

    fun all(ctx: Context): List<QuickPlace> {
        val list = PlacesCodec.decode(ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_LIST, null))
        return list.sortedBy { it.kind.ordinal }
    }

    fun get(ctx: Context, id: String): QuickPlace? = all(ctx).firstOrNull { it.id == id }

    fun forKind(ctx: Context, kind: PlaceKind): QuickPlace? = all(ctx).firstOrNull { it.kind == kind }

    /** Upsert theo id; HOME/WORK thay điểm cùng loại cũ. Trả false nếu vượt [MAX_QUICK_PLACES]. */
    fun save(ctx: Context, place: QuickPlace): Boolean {
        val list = all(ctx).filter {
            it.id != place.id && (place.kind == PlaceKind.OTHER || it.kind != place.kind)
        } + place
        if (list.size > MAX_QUICK_PLACES) return false
        write(ctx, list)
        return true
    }

    fun remove(ctx: Context, id: String) {
        write(ctx, all(ctx).filter { it.id != id })
    }

    fun newId(): String = UUID.randomUUID().toString()

    private fun write(ctx: Context, list: List<QuickPlace>) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY_LIST, PlacesCodec.encode(list)).apply()
    }
}
