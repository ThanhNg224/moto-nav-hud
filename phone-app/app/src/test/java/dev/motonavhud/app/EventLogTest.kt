package dev.motonavhud.app

import java.io.File
import java.nio.file.Files
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventLogTest {
    private fun tmp(): File = Files.createTempDirectory("eventlog").toFile()

    @Test
    fun formatLine_prefixesTimestamp() {
        val fmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        assertEquals("01-01 00:00:01.500 hello t=1", EventLogWriter.formatLine(fmt, 1500, "hello t=1"))
    }

    @Test
    fun isExpired_olderThanMaxAge() {
        assertFalse(EventLog.isExpired(1000, 1100, 100))
        assertTrue(EventLog.isExpired(1000, 1101, 100))
    }

    @Test
    fun rotateDecision() {
        assertFalse(EventLogWriter.shouldRotate(100, 100))
        assertTrue(EventLogWriter.shouldRotate(101, 100))
    }

    @Test
    fun payloadDedupe_ignoresQ_andRepeatsAfterInterval() {
        val d = PayloadDedupe(30_000)
        assertTrue(d.shouldPersist("payload q=1 k=a m=1 d=2 i=3 a=4", 0))
        assertFalse(d.shouldPersist("payload q=2 k=a m=1 d=2 i=3 a=4", 1000))
        assertFalse(d.shouldPersist("payload q=2 k=a m=1 d=2 i=3 a=9999", 1500))
        assertTrue(d.shouldPersist("payload q=3 k=b m=1 d=2 i=3 a=4", 2000))
        assertFalse(d.shouldPersist("payload q=4 k=b m=1 d=2 i=3 a=4", 31_000))
        assertTrue(d.shouldPersist("payload q=5 k=b m=1 d=2 i=3 a=4", 32_000))
        assertTrue(d.shouldPersist("ble connected x=1", 32_001))
    }

    @Test
    fun writer_appendsDedupesAndRotates() {
        val dir = tmp()
        val w = EventLogWriter(dir, clock = { 0L }, maxBytes = 200, maxPending = 500)
        w.offer("payload q=1 k=a")
        w.offer("payload q=2 k=a")
        w.offer("event x=1")
        w.awaitIdle()
        val lines = File(dir, "events.log").readLines()
        assertEquals(2, lines.size)
        assertTrue(lines[1].endsWith(" event x=1"))
        repeat(20) { w.offer("event filler=$it") ; w.awaitIdle() }
        assertTrue(File(dir, "events.log.1").exists())
        assertTrue(File(dir, "events.log").length() <= 200 + 64)
    }
}
