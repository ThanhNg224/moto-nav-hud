package dev.motonavhud.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoStartGateTest {

    @Test
    fun acquiresOnceWhileStartInFlight() {
        val gate = AutoStartGate()
        assertTrue(gate.tryAcquireAutoStart(enabled = true, serviceRunning = false, btReady = true))
        assertTrue(gate.isStartInFlight())
        // Nhịp Maps tiếp theo khi service chưa kịp onCreate không được gọi trùng
        assertFalse(gate.tryAcquireAutoStart(enabled = true, serviceRunning = false, btReady = true))
        gate.onServiceStarted()
        assertFalse(gate.isStartInFlight())
    }

    @Test
    fun respectsDisabledAndMissingBt() {
        val gate = AutoStartGate()
        assertFalse(gate.tryAcquireAutoStart(enabled = false, serviceRunning = false, btReady = true))
        assertFalse(gate.tryAcquireAutoStart(enabled = true, serviceRunning = false, btReady = false))
        assertFalse(gate.tryAcquireAutoStart(enabled = true, serviceRunning = true, btReady = true))
    }

    @Test
    fun manualStopDuringTripSuppressesMidTripRepostAndRearmsAfterIdleWindow() {
        val gate = AutoStartGate(rearmIdleMs = 30_000L)
        gate.onManualStop(mapsActive = true)
        assertTrue(gate.isSuppressed())
        assertFalse(gate.tryAcquireAutoStart(enabled = true, serviceRunning = false, btReady = true))

        // Maps gỡ notification 5 giây rồi đăng lại giữa chuyến -> vẫn giữ tạm ngưng
        gate.onMapsIdle(1_000L)
        gate.onMapsActive(6_000L)
        assertTrue(gate.isSuppressed())
        assertFalse(gate.tryAcquireAutoStart(enabled = true, serviceRunning = false, btReady = true))

        // Maps thực sự kết thúc chuyến và không có sự kiện nào suốt 5 phút; chuyến mới bắt đầu -> tự mở khóa
        gate.onMapsIdle(10_000L)
        gate.onMapsActive(310_000L)
        assertFalse(gate.isSuppressed())
        assertTrue(gate.tryAcquireAutoStart(enabled = true, serviceRunning = false, btReady = true))
    }

    @Test
    fun manualStopWhileIdleDoesNotSuppressFutureTrip() {
        val gate = AutoStartGate()
        gate.onManualStop(mapsActive = false)
        assertFalse(gate.isSuppressed())
        assertTrue(gate.tryAcquireAutoStart(enabled = true, serviceRunning = false, btReady = true))
    }

    @Test
    fun blockedLaunchAndBleExhaustedSuppressUntilManualStartOrNewTrip() {
        val gate = AutoStartGate(rearmIdleMs = 30_000L)
        assertTrue(gate.tryAcquireAutoStart(enabled = true, serviceRunning = false, btReady = true))
        gate.onAutoStartBlocked()
        assertFalse(gate.isStartInFlight())
        assertTrue(gate.isSuppressed())
        assertFalse(gate.tryAcquireAutoStart(enabled = true, serviceRunning = false, btReady = true))

        // Bắt đầu thủ công xóa tạm ngưng ngay
        gate.onManualStartRequested()
        assertFalse(gate.isSuppressed())
        assertTrue(gate.tryAcquireAutoStart(enabled = true, serviceRunning = false, btReady = true))

        // BLE hết retry trong chuyến đi -> không tự khởi động lại liên tục
        gate.onBleExhausted(mapsActive = true)
        assertTrue(gate.isSuppressed())
        assertFalse(gate.tryAcquireAutoStart(enabled = true, serviceRunning = false, btReady = true))
    }
}
