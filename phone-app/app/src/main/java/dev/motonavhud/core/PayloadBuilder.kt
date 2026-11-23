package dev.motonavhud.core

import java.text.BreakIterator

/** Tạo JSON theo docs/protocol.md v1.3. */
object PayloadBuilder {
    const val MAX_BYTES = 180

    sealed class Result {
        class Ok(val bytes: ByteArray) : Result()

        /** Lõi (v,z,q,i,a,k,m,x,d,p và y nếu demo) cũng không vừa [cap]; KHÔNG được gửi gì. */
        class Unfittable(val coreBytes: Int, val cap: Int) : Result()
    }

    fun bytesOrNull(r: Result): ByteArray? = (r as? Result.Ok)?.bytes

    /**
     * [mtuLimit] là số byte dữ liệu tối đa của peer (MTU - 3); giới hạn thực là min(180, mtuLimit),
     * đo trên JSON đã serialize (ký tự thoát như `\"` tính đủ). Thứ tự bỏ: `e`, rút `s` theo grapheme
     * rồi bỏ `s`, cuối cùng `r`; `y` (chỉ khi [demo]) là một phần lõi, không bị cắt. Khóa bị bỏ thì vắng mặt hẳn. Lõi không vừa -> [Result.Unfittable].
     */
    fun build(state: NavState, z: Int, q: Int, i: Int, a: Int, p: String, mtuLimit: Int = MAX_BYTES, demo: Boolean = false): Result {
        val cap = minOf(MAX_BYTES, mtuLimit)
        fun ser(s: String?, e: String?, withR: Boolean) = json(state, z, q, i, a, p, s, e, withR, demo).toByteArray(Charsets.UTF_8)

        ser(state.s, state.e, true).let { if (it.size <= cap) return Result.Ok(it) }
        ser(state.s, null, true).let { if (it.size <= cap) return Result.Ok(it) }

        val cuts = graphemeEnds(state.s)
        for (n in cuts.indices.reversed()) {
            ser(state.s.substring(0, cuts[n]), null, true).let { if (it.size <= cap) return Result.Ok(it) }
        }
        ser(null, null, true).let { if (it.size <= cap) return Result.Ok(it) }
        val core = ser(null, null, false)
        return if (core.size <= cap) Result.Ok(core) else Result.Unfittable(core.size, cap)
    }

    /** Vị trí kết thúc của từng grapheme, theo thứ tự tăng dần. */
    private fun graphemeEnds(s: String): List<Int> {
        val it = BreakIterator.getCharacterInstance()
        it.setText(s)
        val ends = ArrayList<Int>()
        var end = it.next()
        while (end != BreakIterator.DONE) {
            ends.add(end)
            end = it.next()
        }
        return ends
    }

    private fun json(st: NavState, z: Int, q: Int, i: Int, a: Int, p: String, s: String?, e: String?, withR: Boolean, demo: Boolean): String {
        val sb = StringBuilder(160)
        sb.append("{\"v\":1,\"z\":").append(z).append(",\"q\":").append(q).append(",\"i\":").append(i)
            .append(",\"a\":").append(a).append(",\"k\":").append(st.k).append(",\"m\":").append(st.m)
            .append(",\"x\":").append(st.x).append(",\"d\":").append(st.d)
            .append(",\"p\":\"").append(escape(p)).append('"')
        if (s != null) sb.append(",\"s\":\"").append(escape(s)).append('"')
        if (e != null) sb.append(",\"e\":\"").append(escape(e)).append('"')
        if (withR) sb.append(",\"r\":").append(st.r)
        if (demo) sb.append(",\"y\":1") // đánh dấu demo: thuộc lõi, không bao giờ bị cắt
        return sb.append('}').toString()
    }

    private fun escape(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c < ' ' -> sb.append("\\u%04x".format(c.code))
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }
}
