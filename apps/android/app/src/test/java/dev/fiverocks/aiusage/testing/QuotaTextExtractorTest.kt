package dev.fiverocks.aiusage.testing

import org.junit.Assert.*
import org.junit.Test

class QuotaTextExtractorTest {
    @Test fun relevantCodexLinesAreShown() {
        val page = "Settings\nCodex Usage\n5시간 남은 비율 70%\n주간 남은 비율 80%\nBilling address"
        val diagnostic = QuotaTextExtractor.summary(page)
        assertTrue(diagnostic.contains("5시간 남은 비율 70%"))
        assertTrue(diagnostic.contains("주간 남은 비율 80%"))
        assertFalse(diagnostic.contains("Billing address"))
    }

    @Test fun irrelevantTextIsNotPrinted() {
        val diagnostic = QuotaTextExtractor.summary("Password: secret\nPrivate settings")
        assertEquals("사용량 관련 행을 찾지 못했습니다", diagnostic)
    }
}
