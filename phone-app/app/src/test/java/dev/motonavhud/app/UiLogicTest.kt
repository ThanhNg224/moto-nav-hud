package dev.motonavhud.app

import dev.motonavhud.core.NavState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiLogicTest {
    private fun auto(
        running: Boolean = false, autoOn: Boolean = true, listener: Boolean = true, connected: Boolean = true,
        btPerm: Boolean = true, btOn: Boolean = true, suppressed: Boolean = false,
    ) = autoStartStatus(running, autoOn, listener, connected, btPerm, btOn, suppressed)

    @Test
    fun autoStartOnlyReadyWhenNothingBlocks() {
        assertEquals(AutoStartStatus.READY, auto())
        assertEquals(AutoStartStatus.RUNNING, auto(running = true, suppressed = true))
        assertEquals(AutoStartStatus.SUPPRESSED, auto(suppressed = true))
        assertEquals(AutoStartStatus.BT_OFF, auto(btOn = false))
        assertEquals(AutoStartStatus.NO_BT_PERMISSION, auto(btPerm = false, btOn = false))
        assertEquals(AutoStartStatus.NO_LISTENER, auto(listener = false))
        assertEquals(AutoStartStatus.DISABLED, auto(autoOn = false))
        assertEquals(AutoStartStatus.LISTENER_CONNECTING, auto(connected = false))
        assertEquals(AutoStartStatus.NO_LISTENER, auto(listener = false, connected = false))
    }

    @Test
    fun notifStepSeparatesGrantAppAndChannel() {
        assertEquals(NotifStep.REQUEST, notifStep(false, false, false))
        assertEquals(NotifStep.APP_BLOCKED, notifStep(true, false, false))
        assertEquals(NotifStep.CHANNEL_BLOCKED, notifStep(true, true, true))
        assertEquals(NotifStep.OK, notifStep(true, true, false))
    }

    @Test
    fun chipColumnsWrapWhenNarrow() {
        assertEquals(4, chipColumns(300, 4))
        assertEquals(4, chipColumns(210, 4))
        assertEquals(2, chipColumns(209, 4))
    }

    @Test
    fun mapsStatusDistinguishesStates() {
        assertEquals(MapsStatus.NO_LISTENER, mapsStatus(false, true, false, NavState.K_NAVIGATING))
        assertEquals(MapsStatus.CONNECTING, mapsStatus(true, false, false, NavState.K_IDLE))
        assertEquals(MapsStatus.IDLE, mapsStatus(true, true, false, NavState.K_IDLE))
        assertEquals(MapsStatus.NAVIGATING, mapsStatus(true, true, false, NavState.K_NAVIGATING))
        assertEquals(MapsStatus.REROUTING, mapsStatus(true, true, false, NavState.K_REROUTING))
        assertEquals(MapsStatus.ARRIVED, mapsStatus(true, true, false, NavState.K_ARRIVED))
        assertEquals(MapsStatus.DEMO, mapsStatus(true, true, true, NavState.K_IDLE))
    }

    @Test
    fun subscriberWithMtuWarningIsDeliveryProblem() {
        assertEquals(WatchStatus.DELIVERY_PROBLEM, watchStatus(true, true, true, 1, BleState.CONNECTED, WarnCode.MTU_TOO_SMALL))
        assertEquals(WatchStatus.CONNECTED, watchStatus(true, true, true, 1, BleState.CONNECTED, WarnCode.NONE))
        assertEquals(WatchStatus.NO_PERMISSION, watchStatus(false, true, true, 1, BleState.CONNECTED, WarnCode.NONE))
    }

    @Test
    fun watchStatusFromBleState() {
        assertEquals(WatchStatus.OFF, watchStatus(true, true, false, 0, BleState.STOPPED, WarnCode.NONE))
        assertEquals(WatchStatus.WAITING, watchStatus(true, true, true, 0, BleState.ADVERTISING, WarnCode.NONE))
        assertEquals(WatchStatus.WAITING_SAVER, watchStatus(true, true, true, 0, BleState.ADVERTISING_SAVER, WarnCode.NONE))
        assertEquals(WatchStatus.BT_OFF, watchStatus(true, false, false, 0, BleState.BT_OFF, WarnCode.NONE))
        // Bluetooth đã bật lại sau khi service dừng: không còn báo Bluetooth tắt
        assertEquals(WatchStatus.OFF, watchStatus(true, true, false, 0, BleState.BT_OFF, WarnCode.NONE))
        assertEquals(WatchStatus.BT_OFF, watchStatus(true, true, true, 0, BleState.BT_OFF, WarnCode.NONE))
        assertEquals(WatchStatus.ERROR, watchStatus(true, true, false, 0, BleState.ERROR_STOPPED, WarnCode.NONE))
        assertEquals(WatchStatus.RETRYING, watchStatus(true, true, true, 0, BleState.ERROR_RETRY, WarnCode.NONE))
    }

    @Test
    fun pulseOnlyWhileWaiting() {
        assertTrue(isWaitingForWatch(WatchStatus.WAITING))
        assertTrue(isWaitingForWatch(WatchStatus.STARTING))
        assertFalse(isWaitingForWatch(WatchStatus.CONNECTED))
        assertFalse(isWaitingForWatch(WatchStatus.OFF))
    }

    @Test
    fun permissionMemory() {
        // Lần đầu đóng hộp thoại: mơ hồ, chưa vĩnh viễn nhưng đã từng bị từ chối (hiện lối mở cài đặt)
        val dismissed = permAfterResult(PermMemory(), granted = false, rationaleBefore = false, rationaleNow = false)
        assertEquals(PermMemory(deniedOnce = true, permanent = false), dismissed)
        // Lần đầu từ chối rõ ràng: có rationale
        val first = permAfterResult(PermMemory(), granted = false, rationaleBefore = false, rationaleNow = true)
        assertEquals(PermMemory(deniedOnce = true, permanent = false), first)
        // Lần hai từ chối: trước có rationale, sau hết
        val second = permAfterResult(first, granted = false, rationaleBefore = true, rationaleNow = false)
        assertEquals(PermMemory(deniedOnce = true, permanent = true), second)
        // Cấp quyền xóa trạng thái
        assertEquals(PermMemory(), permAfterResult(second, granted = true, rationaleBefore = false, rationaleNow = false))
    }
}
