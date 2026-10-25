package dev.fiverocks.aiusage.testing

/** Diagnostic only: never infer a percentage from unrelated account information. */
object QuotaTextExtractor {
    private val percentage = Regex("""(?<![\\d.])(?:100|[1-9]?\\d)(?:\\.\\d+)?\\s*%""")
    fun summary(text: String): String {
        val normalized = text.replace(Regex("""[\\t ]+"""), " ").take(12000)
        val lines = normalized.lines().map { it.trim() }.filter { it.isNotBlank() }
        val relevant = lines.filter {
            it.contains("5시간") || it.contains("5-hour", true) ||
            it.contains("weekly", true) || it.contains("주간") ||
            it.contains("사용량") || it.contains("usage", true)
        }.take(12)
        val sample = relevant.joinToString("\n").take(1500)
        val values = percentage.findAll(sample).map { it.value }.toList()
        return "관련 행 ${relevant.size}개 · 비율 후보 ${values.size}개\n" +
            if (sample.isBlank()) "관련 텍스트를 찾지 못했습니다" else sample
    }
}
