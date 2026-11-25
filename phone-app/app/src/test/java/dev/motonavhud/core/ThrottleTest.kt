package dev.motonavhud.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThrottleTest {
    private var now = 1_000L
    private val th = Throttle(clock = { now }, z = 777)
    private val nav = NavState(1, 3, 0, 200, "Nguyễn Văn Linh", "12:45", 4000)

    @Test fun firstPollSendsIdleWithQ1AndZ() {
        val e = th.poll()!!
        assertEquals(0, e.state.k); assertEquals(1, e.q); assertEquals(777, e.z); assertEquals(0, e.render(now).a)
    }

    @Test fun silentBetweenHeartbeatsWhenUnchanged() {
        th.offer(nav); th.poll()
        now += 400
        assertNull(th.poll())
        now += 599
        assertNull(th.poll())
        now += 1
        assertNotNull(th.poll())
    }

    @Test fun qStrictlyIncreasesFromOne() {
        th.offer(nav)
        val qs = mutableListOf<Int>()
        repeat(5) { th.poll()?.let { e -> qs += e.q }; now += 1000 }
        assertEquals(listOf(1, 2, 3, 4, 5), qs)
    }

    @Test fun sendsOnDistanceChangeOfTenOrMoreOnly() {
        th.offer(nav); th.poll()
        now += 100; th.offer(nav.copy(d = 192)); assertNull(th.poll())
        now += 100; th.offer(nav.copy(d = 190)); assertNotNull(th.poll())
        now += 100; th.offer(nav.copy(d = 181)); assertNull(th.poll())
    }

    @Test fun sendsOnMStreetKChange() {
        th.offer(nav); th.poll()
        now += 10; th.offer(nav.copy(m = 6)); assertEquals(6, th.poll()!!.state.m)
        now += 10; th.offer(nav.copy(m = 6, s = "Khác")); assertEquals("Khác", th.poll()!!.state.s)
        now += 10; th.offer(nav.copy(m = 6, s = "Khác", k = 2)); assertEquals(2, th.poll()!!.state.k)
    }

    @Test fun keepsOnlyLatestOffer() {
        th.offer(nav); th.poll()
        now += 10
        th.offer(nav.copy(m = 6)); th.offer(nav.copy(m = 7))
        assertEquals(7, th.poll()!!.state.m)
        now += 10
        assertNull(th.poll())
    }

    @Test fun heartbeatDoesNotRefreshAge() {
        th.offer(nav)
        assertEquals(0, th.poll()!!.render(now).a)
        now += 1000; assertEquals(1000, th.poll()!!.render(now).a)
        now += 1000; assertEquals(2000, th.poll()!!.render(now).a)
        now += 1000; th.offer(nav); // cập nhật thật làm mới
        assertEquals(0, th.poll()!!.render(now).a)
    }

    @Test fun staleSourceSendsIdleNeverStaleK1() {
        th.offer(nav); th.poll()
        now += 75_000
        assertEquals(1, th.poll()!!.state.k) // đúng ngưỡng 75 s vẫn chưa quá
        now += 1_000
        val e = th.poll()!!
        assertEquals(0, e.state.k); assertEquals(0, e.render(now).a)
        now += 1_000
        assertEquals(0, th.poll()!!.state.k)
    }

    @Test fun k1SurvivesPastFifteenSecondsWithoutRepost() {
        th.offer(nav); th.poll()
        now += 15_000
        assertEquals(1, th.poll()!!.state.k) // Maps im lặng trên đường thẳng: vẫn k=1 tới 75 s
    }

    @Test fun arrivedExpiresAtFifteenSeconds() {
        th.offer(nav.copy(k = 3)); th.poll()
        now += 15_000
        assertEquals(3, th.poll()!!.state.k) // đúng ngưỡng 15 s vẫn chưa quá
        now += 1_000
        val e = th.poll()!!
        assertEquals(0, e.state.k); assertEquals(0, e.render(now).a)
    }

    @Test fun staleReroutingStaysK2() {
        th.offer(nav.copy(k = 2)); th.poll()
        now += 20_000
        assertEquals(2, th.poll()!!.state.k)
    }

    @Test fun reroutingNeverExpiresViaPoll() {
        th.offer(nav.copy(k = 2)); th.poll()
        now += 600_000
        assertEquals(2, th.poll()!!.state.k)
    }

    @Test fun explicitIdleOfferGivesK0() {
        th.offer(nav); th.poll()
        now += 10; th.offer(NavState.IDLE)
        assertEquals(0, th.poll()!!.state.k)
    }

    @Test fun stepIdIncrementsOnManeuverStreetOrDistanceJump() {
        th.offer(nav); val i0 = th.poll()!!.i
        now += 10; th.offer(nav.copy(d = 100)); th.poll()
        now += 1000; assertEquals(i0, th.poll()!!.i) // d giảm: cùng bước
        th.offer(nav.copy(m = 6, d = 100)); now += 10
        val i1 = th.poll()!!.i; assertTrue(i1 > i0)
        th.offer(nav.copy(m = 6, d = 80)); now += 10
        assertEquals(i1, th.poll()!!.i)
        th.offer(nav.copy(m = 6, d = 400)); now += 10 // d tăng vọt: bước mới
        assertTrue(th.poll()!!.i > i1)
    }

    @Test fun stepIdIncrementsWhenNavigationStarts() {
        th.offer(NavState.IDLE); val i0 = th.poll()!!.i
        now += 10; th.offer(nav)
        assertTrue(th.poll()!!.i > i0)
    }

    @Test fun ageAndExpiryAreRecomputedAtRenderTime() {
        th.offer(nav)
        val e = th.poll()!!
        assertEquals(0, e.render(now).a)
        assertEquals(5000, e.render(now + 5000).a) // snapshot xếp hàng: tuổi tăng theo thời gian thật
        assertEquals(1, e.render(now + 75_000).state.k)
        val late = e.render(now + 75_001)
        assertEquals(0, late.state.k); assertEquals(0, late.a) // hết hạn lúc gửi thật
    }

    @Test fun k1ExpiryBoundaryAtRenderTime() {
        th.offer(nav)
        val e = th.poll()!!
        assertEquals(1, e.render(now + 14_999).state.k)
        assertEquals(1, e.render(now + 74_999).state.k)
        assertEquals(1, e.render(now + 75_000).state.k)
        val expired = e.render(now + 75_001)
        assertEquals(0, expired.state.k); assertEquals(0, expired.a)
    }

    @Test fun queuedK1EmissionGoesIdleOncePastNavStale() {
        th.offer(nav)
        val queued = th.poll()!! // xếp hàng, đọc muộn
        assertEquals(1, queued.render(now + 74_000).state.k)
        assertEquals(0, queued.render(now + 80_000).state.k)
    }

    @Test fun rerouteSnapshotDoesNotExpireAtRender() {
        th.offer(nav.copy(k = 2))
        val e = th.poll()!!
        assertEquals(2, e.render(now + 60_000).state.k)
    }

    @Test fun offerWithOlderSourceTimeKeepsRealAge() {
        th.offer(nav, atMs = now - 4000) // seed từ postTime
        assertEquals(4000, th.poll()!!.render(now).a)
        now += 68_000
        assertEquals(1, th.poll()!!.state.k) // 72 s kể từ nguồn thật (-3000): còn trong 75 s
        now += 4_000
        assertEquals(0, th.poll()!!.state.k) // 76 s kể từ nguồn thật -> hết hạn
    }

    @Test fun reofferingRealIdleAfterSimulatedStateForcesIdle() {
        th.offer(nav.copy(m = 9, d = 30)); th.poll()  // trạng thái giả lập
        now += 10
        th.offer(NavState.IDLE, atMs = 0)             // thoát Demo: ép offer snapshot thật (không có nguồn)
        val e = th.poll()!!
        assertEquals(0, e.state.k)
    }

    @Test fun refreshingUnchangedActiveNotificationKeepsStepIdAndResetsAge() {
        th.offer(nav)
        val first = th.poll()!!
        assertEquals(0, first.render(now).a)
        now += 3_000
        th.offer(nav, atMs = now) // mô phỏng verifySelectedAlive() xác nhận notification ongoing còn sống
        val e = th.poll()!!
        assertEquals(1, e.state.k)
        assertEquals(first.i, e.i) // không tăng stepId -> không rung lặp
        assertEquals(0, e.render(now).a)
    }

    @Test fun confirmAliveHoldsK1PastNavStaleWithoutRefreshingAge() {
        th.offer(nav)
        now += 100_000
        th.confirmAlive()
        val e = th.poll()!!
        assertEquals(1, e.state.k)
        assertEquals(100_000, e.render(now).a) // xác nhận không làm mới tuổi
    }

    @Test fun k1WithoutConfirmAliveExpiresAfterNavStale() {
        th.offer(nav)
        now += 75_001
        assertEquals(0, th.poll()!!.state.k)
    }

    @Test fun staleConfirmAliveDoesNotHoldK1() {
        th.offer(nav)
        now += 80_000
        th.confirmAlive()
        now += 16_000 // 96 s; xác nhận cách 16 s > 15 s
        assertEquals(0, th.poll()!!.state.k)
    }

    @Test fun aliveCapsK1AtTenMinutesFromRealOffer() {
        th.offer(nav)
        repeat(200) { now += 3_000; th.confirmAlive() } // t0 + 600 s, xác nhận mỗi 3 s
        assertEquals(1, th.poll()!!.state.k)
        now += 1
        assertEquals(0, th.poll()!!.state.k)
    }

    @Test fun aliveDoesNotExtendArrivedK3() {
        th.offer(nav.copy(k = 3))
        th.confirmAlive()
        now += 15_001
        assertEquals(0, th.poll()!!.state.k)
    }

    @Test fun newOfferDropsPreviousAliveConfirmation() {
        th.offer(nav)
        now += 70_000
        th.confirmAlive()
        th.offer(nav.copy(d = 150), now - 80_000) // nguồn mới đã cũ 80 s: xác nhận sống cũ không được gia hạn nó
        assertEquals(0, th.poll()!!.state.k)
    }

    @Test fun aliveEmissionReRenderedAfterConfirmGoesIdle() {
        th.offer(nav)
        now += 100_000
        th.confirmAlive()
        val e = th.poll()!!
        assertEquals(1, e.state.k)
        assertEquals(100_000, e.render(now).a)
        val later = e.render(now + 30_000) // 130 s; xác nhận cũ 30 s
        assertEquals(0, later.state.k); assertEquals(0, later.a)
    }
}
