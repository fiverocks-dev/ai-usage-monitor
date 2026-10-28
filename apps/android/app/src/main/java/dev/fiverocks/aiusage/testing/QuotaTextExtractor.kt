package dev.fiverocks.aiusage.testing

/** Display-only diagnostic. Never persist the full page or image. */
object QuotaTextExtractor {
    fun summary(text: String): String {
        val lines = text.take(12000).lines().map { it.trim() }.filter { it.isNotBlank() }
        val relevant = lines.filter {
            it.contains("5시간") || it.contains("5-hour", true) ||
            it.contains("weekly", true) || it.contains("주간") ||
            it.contains("사용량") || it.contains("usage", true) ||
            it.contains("remaining", true)
        }.take(12)
        return if (relevant.isEmpty()) "사용량 관련 행을 찾지 못했습니다"
            else "사용량 관련 행 ${relevant.size}개 (값은 검증 필요)\n" +
                relevant.joinToString("\n").take(1200)
    }
}
