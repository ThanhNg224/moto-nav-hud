package dev.motonavhud.core

/** Kênh truyền payload JSON UTF-8 sang đồng hồ (BLE GATT hiện tại, XMS interconnect sau này). */
interface Transport {
    fun send(payload: ByteArray)

    /**
     * Kênh có giới hạn riêng từng peer (BLE MTU) override hàm này. [build] được gọi LÚC GỬI THẬT
     * (không phải lúc tạo closure) với `mtuLimit` của peer, và trả null nếu không có gì hợp lệ để gửi.
     */
    fun send(build: (mtuLimit: Int) -> ByteArray?) {
        build(PayloadBuilder.MAX_BYTES)?.let { send(it) }
    }
}
