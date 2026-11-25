package dev.motonavhud.app

import dev.motonavhud.core.NavState
import dev.motonavhud.core.PlaceKind
import dev.motonavhud.core.QuickPlace

/** Logic thuần của UI (không phụ thuộc Android) để test trên JVM. */

enum class AutoStartStatus { RUNNING, READY, DISABLED, SUPPRESSED, NO_BT_PERMISSION, NO_LISTENER, LISTENER_CONNECTING, BT_OFF }

/** Trạng thái tự bật thật: READY chỉ nghĩa là mọi điều kiện đã đủ để thử tự bật khi Maps bắt đầu dẫn đường (Android vẫn có thể chặn start nền). */
fun autoStartStatus(
    running: Boolean,
    autoOn: Boolean,
    listenerGranted: Boolean,
    listenerConnected: Boolean,
    btPermOk: Boolean,
    btOn: Boolean,
    suppressed: Boolean,
): AutoStartStatus = when {
    running -> AutoStartStatus.RUNNING
    !btPermOk -> AutoStartStatus.NO_BT_PERMISSION
    !listenerGranted -> AutoStartStatus.NO_LISTENER
    !listenerConnected -> AutoStartStatus.LISTENER_CONNECTING
    !btOn -> AutoStartStatus.BT_OFF
    !autoOn -> AutoStartStatus.DISABLED
    suppressed -> AutoStartStatus.SUPPRESSED
    else -> AutoStartStatus.READY
}

enum class MapsStatus { NO_LISTENER, CONNECTING, IDLE, NAVIGATING, REROUTING, ARRIVED, DEMO }

fun mapsStatus(listenerGranted: Boolean, listenerConnected: Boolean, demo: Boolean, k: Int): MapsStatus = when {
    !listenerGranted -> MapsStatus.NO_LISTENER
    demo -> MapsStatus.DEMO
    k == NavState.K_NAVIGATING -> MapsStatus.NAVIGATING
    k == NavState.K_REROUTING -> MapsStatus.REROUTING
    k == NavState.K_ARRIVED -> MapsStatus.ARRIVED
    listenerConnected -> MapsStatus.IDLE
    else -> MapsStatus.CONNECTING
}

enum class WatchStatus {
    NO_PERMISSION, DELIVERY_PROBLEM, CONNECTED, OFF, STARTING, WAITING, WAITING_SAVER,
    BT_OFF, RETRYING, ERROR, AUTO_STOPPED,
}

/** [btOn] là trạng thái adapter thật: khi service đã dừng, BT_OFF cũ được giữ lại không còn đúng nếu Bluetooth đã bật. */
fun watchStatus(btPermOk: Boolean, btOn: Boolean, running: Boolean, subs: Int, ble: BleState, warn: WarnCode): WatchStatus = when {
    !btPermOk -> WatchStatus.NO_PERMISSION
    subs > 0 ->
        if (warn == WarnCode.MTU_TOO_SMALL || ble == BleState.NOTIFY_ERROR) WatchStatus.DELIVERY_PROBLEM
        else WatchStatus.CONNECTED
    else -> when (ble) {
        BleState.BT_OFF -> if (!running && btOn) WatchStatus.OFF else WatchStatus.BT_OFF
        BleState.ERROR_RETRY -> WatchStatus.RETRYING
        BleState.ERROR_STOPPED -> WatchStatus.ERROR
        BleState.AUTO_STOPPED -> WatchStatus.AUTO_STOPPED
        BleState.NOTIFY_ERROR -> WatchStatus.DELIVERY_PROBLEM
        BleState.STARTING -> if (running) WatchStatus.STARTING else WatchStatus.OFF
        BleState.ADVERTISING, BleState.CONNECTED -> if (running) WatchStatus.WAITING else WatchStatus.OFF
        BleState.ADVERTISING_SAVER -> if (running) WatchStatus.WAITING_SAVER else WatchStatus.OFF
        BleState.STOPPED -> WatchStatus.OFF
    }
}

/** Chỉ nhấp nháy chấm trạng thái khi đang chờ đồng hồ kết nối. */
fun isWaitingForWatch(s: WatchStatus) =
    s == WatchStatus.STARTING || s == WatchStatus.WAITING || s == WatchStatus.WAITING_SAVER

/** Ký ức về một quyền runtime: [deniedOnce] = đã bị từ chối ít nhất một lần; [permanent] = chắc chắn không còn hộp thoại. */
data class PermMemory(val deniedOnce: Boolean = false, val permanent: Boolean = false)

/**
 * Cập nhật sau kết quả xin quyền. Chỉ coi là vĩnh viễn khi trước lúc xin đã có rationale (từng từ chối)
 * và giờ hết rationale. Lần xin đầu mà rationale vẫn false là mơ hồ (có thể chỉ đóng hộp thoại): giữ đường xin quyền.
 */
fun permAfterResult(prev: PermMemory, granted: Boolean, rationaleBefore: Boolean, rationaleNow: Boolean) =
    if (granted) PermMemory()
    else PermMemory(deniedOnce = true, permanent = prev.permanent || (rationaleBefore && !rationaleNow))

enum class NotifStep { OK, REQUEST, APP_BLOCKED, CHANNEL_BLOCKED }

/** Thông báo của MotoNav (tùy chọn): tách quyền runtime, công tắc thông báo của app và kênh service. */
fun notifStep(permGranted: Boolean, appEnabled: Boolean, channelBlocked: Boolean) = when {
    !permGranted -> NotifStep.REQUEST
    !appEnabled -> NotifStep.APP_BLOCKED
    channelBlocked -> NotifStep.CHANNEL_BLOCKED
    else -> NotifStep.OK
}

/** Số cột chip: 4 nếu mỗi chip còn >= 48dp, ngược lại 2 để chip không hẹp hơn mục tiêu chạm. */
fun chipColumns(availableDp: Int, chips: Int, gapDp: Int = 6, minDp: Int = 48) =
    if ((availableDp - gapDp * (chips - 1)) / chips >= minDp) chips else 2

/** Chế độ nội dung của màn chính: SETUP thay đồng hồ bằng danh sách quyền; các chế độ còn lại theo trạng thái dẫn đường. */
enum class HomeMode { SETUP, IDLE, NAVIGATING, REROUTING, ARRIVED }

/** SETUP chỉ khi service chưa chạy và thiếu quyền bắt buộc (Bluetooth hoặc đọc thông báo). Service đang chạy thì luôn hiện trạng thái thật. */
fun homeMode(setupNeeded: Boolean, running: Boolean, k: Int): HomeMode = when {
    !running -> if (setupNeeded) HomeMode.SETUP else HomeMode.IDLE
    k == NavState.K_NAVIGATING -> HomeMode.NAVIGATING
    k == NavState.K_REROUTING -> HomeMode.REROUTING
    k == NavState.K_ARRIVED -> HomeMode.ARRIVED
    else -> HomeMode.IDLE
}

/** Bố cục thanh nút dưới cùng: IDLE = [cài đặt][Mở Maps], RUNNING = [Dừng][Về Maps], DEMO = [Dừng][Dừng chạy thử], SETUP = nút chính bị khóa. */
enum class DockMode { SETUP, IDLE, RUNNING, DEMO }

fun dockMode(setupNeeded: Boolean, running: Boolean, demo: Boolean): DockMode = when {
    demo -> DockMode.DEMO
    running -> DockMode.RUNNING
    setupNeeded -> DockMode.SETUP
    else -> DockMode.IDLE
}

/** Số bước thiết lập còn thiếu (mỗi phần tử = một bước đã xong hay chưa). */
fun setupStepsLeft(vararg done: Boolean) = done.count { !it }

/** Che mã ghép: chỉ lộ 4 ký tự cuối khi mã dài hơn 4; mã ngắn che hết. */
fun maskToken(t: String) = if (t.length <= 4) "\u2022".repeat(t.length) else "\u2022\u2022\u2022\u2022 " + t.takeLast(4)

/** Một ô "Đi nhanh": [kind] cố định theo vị trí (Nhà, Cơ quan, ô thứ ba); [place] null nghĩa là chưa lưu. */
data class QuickSlot(val kind: PlaceKind, val place: QuickPlace?)

/** Luôn đúng 3 ô: Nhà, Cơ quan, rồi điểm khác đã lưu (nếu chưa có thì ô "Thêm"). */
fun quickSlots(places: List<QuickPlace>) = listOf(PlaceKind.HOME, PlaceKind.WORK, PlaceKind.OTHER)
    .map { kind -> QuickSlot(kind, places.firstOrNull { it.kind == kind }) }
