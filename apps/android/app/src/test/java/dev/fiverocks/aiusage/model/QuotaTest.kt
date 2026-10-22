package dev.fiverocks.aiusage.model

import org.junit.Assert.*
import org.junit.Test

class QuotaTest {
    @Test fun expiredFreshDataIsMarkedStale() {
        val sample = QuotaSnapshot(ProviderId.CODEX, "5h", 0.5, null, 1000L, QuotaStatus.FRESH)
        assertEquals(QuotaStatus.FRESH, sample.displayStatus(1900L))
        assertEquals(QuotaStatus.STALE, sample.displayStatus(1901L))
    }
    @Test fun explicitFailureSurvivesExpiration() {
        val sample = QuotaSnapshot(ProviderId.CODEX, "5h", 0.5, null, 1000L, QuotaStatus.OFFLINE)
        assertEquals(QuotaStatus.OFFLINE, sample.displayStatus(100000L))
    }
    @Test fun missingFetchTimeIsNotFresh() {
        val sample = QuotaSnapshot(ProviderId.CODEX, "5h", null, null, null, QuotaStatus.FRESH)
        assertEquals(QuotaStatus.INVALID_RESPONSE, sample.displayStatus(1000L))
    }
    @Test fun widgetShowsStatusForEachWindow() {
        val now = 2000L
        val exhausted = QuotaSnapshot(ProviderId.CODEX, "5h", 0.0, null, now, QuotaStatus.FRESH)
        val unsupported = QuotaSnapshot(ProviderId.ANTIGRAVITY_CLAUDE, "model", null, null, null, QuotaStatus.UNSUPPORTED)
        val stale = QuotaSnapshot(ProviderId.CLAUDE_CODE, "7d", 0.45, null, 1000L, QuotaStatus.FRESH)
        assertEquals("5h: 0% (정상)", exhausted.widgetText(now))
        assertEquals("model: — (미지원)", unsupported.widgetText(now))
        assertEquals("7d: 45% (오래된 데이터)", stale.widgetText(now))
    }
    @Test fun fourProvidersRemainDistinct() {
        assertEquals(4, ProviderId.entries.size)
        assertEquals(6, MockQuota.snapshots(1000).size)
    }
    @Test fun absentIsDifferentFromExhausted() {
        val values = MockQuota.snapshots(1000)
        assertEquals("0%", values.first { it.provider == ProviderId.CODEX }.displayPercent())
        assertEquals("—", values.first { it.provider == ProviderId.ANTIGRAVITY_CLAUDE }.displayPercent())
    }
    @Test fun invalidValuesAreRejected() {
        listOf(-0.1, 1.2, Double.NaN, Double.POSITIVE_INFINITY).forEach { n ->
            try {
                QuotaSnapshot(ProviderId.CODEX, "5h", n, null, null, QuotaStatus.FRESH)
                fail("expected invalid fraction rejection: $n")
            } catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun everyRefreshChangesDisplayedQuota() {
        val states = (0..4).map { index -> MockQuota.snapshots(1000, index) }
        for (index in 1..4) {
            val before = states[index - 1].first { it.provider == ProviderId.CLAUDE_CODE }.remainingPercent
            val after = states[index].first { it.provider == ProviderId.CLAUDE_CODE }.remainingPercent
            assertNotEquals(before, after)
        }
    }
    @Test fun zeroOneAndThresholdValues() {
        listOf(0.0, 0.1, 1.0).forEach { n ->
            val s = QuotaSnapshot(ProviderId.CODEX, "5h", n, null, null, QuotaStatus.FRESH)
            assertEquals("${(n * 100).toInt()}%", s.displayPercent())
        }
    }
}
