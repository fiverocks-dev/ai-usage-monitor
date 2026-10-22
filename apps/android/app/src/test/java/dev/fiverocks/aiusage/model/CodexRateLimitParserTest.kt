package dev.fiverocks.aiusage.model

import org.junit.Assert.*
import org.junit.Test

class CodexRateLimitParserTest {
    @Test fun readsFiveHourAndWeeklyByDurationNotPosition() {
        val raw = """
          {"result":{"rateLimits":{"primary":{"usedPercent":30,"windowDurationMins":10080,"resetsAt":1800000000},
            "secondary":{"usedPercent":80,"windowDurationMins":300,"resetsAt":1799990000}}}}
        """.trimIndent()
        val parsed = CodexRateLimitParser.parse(raw, 1799980000)
        assertEquals(2, parsed.size)
        assertEquals(0.2, parsed.first { it.windowId == "5h" }.remainingFraction!!, 0.00001)
        assertEquals(0.7, parsed.first { it.windowId == "7d" }.remainingFraction!!, 0.00001)
        assertEquals(1800000000L, parsed.first { it.windowId == "7d" }.resetAtEpochSeconds)
    }

    @Test fun readsCodexBucketAndIgnoresOtherModels() {
        val raw = """
          {"result":{"rateLimitsByLimitId":{"codex":{"primary":{"usedPercent":0,"windowDurationMins":300}},
            "other":{"primary":{"usedPercent":100,"windowDurationMins":300}}}}}
        """.trimIndent()
        val parsed = CodexRateLimitParser.parse(raw, 1700000000L)
        assertEquals(1.0, parsed.single().remainingFraction!!, 0.0)
    }

    @Test fun prefersCodexBucketOverLegacyView() {
        val raw = """
          {"result":{"rateLimits":{"primary":{"usedPercent":95,"windowDurationMins":300}},
            "rateLimitsByLimitId":{"codex":{"primary":{"usedPercent":40,"windowDurationMins":300},
              "secondary":{"usedPercent":10,"windowDurationMins":10080}}}}}
        """.trimIndent()
        val result = CodexRateLimitParser.parse(raw, 1700000000L)
        assertEquals(2, result.size)
        assertEquals(0.6, result.first { it.windowId == "5h" }.remainingFraction!!, 0.00001)
        assertEquals(0.9, result.first { it.windowId == "7d" }.remainingFraction!!, 0.00001)
    }

    @Test fun cannotUseOtherModelQuotaIfCodexBucketMissing() {
        val raw = """
          {"result":{"rateLimits":{"primary":{"usedPercent":2,"windowDurationMins":300}},
            "rateLimitsByLimitId":{"other":{"primary":{"usedPercent":3,"windowDurationMins":300}}}}}
        """.trimIndent()
        assertThrows(IllegalStateException::class.java) {
            CodexRateLimitParser.parse(raw, 1700000000L)
        }
    }

    @Test fun explicitBackendDenialOverridesRemainingPercentage() {
        val denied = """{"result":{"ordinaryUsageAllowed":false,"rateLimits":{"primary":{"usedPercent":5,"windowDurationMins":300}}}}"""
        val parsed = CodexRateLimitParser.parse(denied, 1700000000L)
        assertEquals(0.95, parsed.single().remainingFraction!!, 0.00001)
        assertEquals(QuotaStatus.RATE_LIMITED, parsed.single().status)

        val unknown = """{"result":{"ordinaryUsageAllowed":null,"rateLimits":{"primary":{"usedPercent":5,"windowDurationMins":300}}}}"""
        assertEquals(QuotaStatus.FRESH, CodexRateLimitParser.parse(unknown, 1700000000L).single().status)
    }

    @Test fun refusesUnknownWindowsAndInvalidPercent() {
        assertThrows(IllegalArgumentException::class.java) {
            CodexRateLimitParser.parse("""{"result":{"primary":{"usedPercent":110,"windowDurationMins":300}}}""", 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CodexRateLimitParser.parse("""{"result":{"primary":{"usedPercent":80,"windowDurationMins":60}}}""", 1)
        }
    }
}
