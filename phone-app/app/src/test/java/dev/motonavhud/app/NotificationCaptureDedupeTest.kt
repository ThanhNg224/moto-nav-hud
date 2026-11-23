package dev.motonavhud.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NotificationCaptureDedupeTest {
    private fun json(postTime: Long, title: String = "200 m") =
        JSONObject().put("package", "maps").put("id", 1).put("postTime", postTime).put("title", title)

    @Test fun postTimeDoesNotAffectKey() {
        val icon = byteArrayOf(1, 2, 3)
        assertEquals(
            NotificationCapture.dedupeKey(json(1000), icon, null),
            NotificationCapture.dedupeKey(json(2000), icon, null),
        )
    }

    @Test fun contentAndIconStillAffectKey() {
        val base = NotificationCapture.dedupeKey(json(1000), byteArrayOf(1), null)
        assertNotEquals(base, NotificationCapture.dedupeKey(json(1000, "300 m"), byteArrayOf(1), null))
        assertNotEquals(base, NotificationCapture.dedupeKey(json(1000), byteArrayOf(2), null))
    }
}
