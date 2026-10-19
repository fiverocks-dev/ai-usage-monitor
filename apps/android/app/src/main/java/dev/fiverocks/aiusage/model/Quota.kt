package dev.fiverocks.aiusage.model

enum class ProviderId(val title: String) {
    CLAUDE_CODE("Claude Code"),
    CODEX("Codex"),
    ANTIGRAVITY_GEMINI("Antigravity Gemini"),
    ANTIGRAVITY_CLAUDE("Antigravity Claude")
}

enum class QuotaStatus { FRESH, STALE, AUTH_REQUIRED, RATE_LIMITED, OFFLINE, UNSUPPORTED, INVALID_RESPONSE }

data class QuotaSnapshot(
    val provider: ProviderId,
    val windowId: String,
    val remainingFraction: Double?,
    val resetAtEpochSeconds: Long?,
    val fetchedAtEpochSeconds: Long?,
    val status: QuotaStatus
) {
    init { require(remainingFraction == null || (remainingFraction.isFinite() && remainingFraction in 0.0..1.0)) { "Invalid remaining fraction" } }
    val remainingPercent: Int? get() = remainingFraction?.let { (it * 100).toInt() }
}

object MockQuota {
    /** Each button press cycles deliberately different values; no network or real account involved. */
    fun snapshots(epochSeconds: Long, scenario: Int = 0): List<QuotaSnapshot> {
        val index = Math.floorMod(scenario, 4)
        val claude5h = listOf(0.76, 0.44, 0.10, 0.95)[index]
        val codex5h = listOf(0.0, 0.32, 0.67, 1.0)[index]
        val gemini = listOf(0.12, 0.55, 0.88, 0.22)[index]
        val geminiStatus = if (index % 2 == 0) QuotaStatus.STALE else QuotaStatus.OFFLINE
        return listOf(
            QuotaSnapshot(ProviderId.CLAUDE_CODE, "5h", claude5h, epochSeconds + 3600, epochSeconds, QuotaStatus.FRESH),
            QuotaSnapshot(ProviderId.CLAUDE_CODE, "7d", listOf(0.24, 0.38, 0.61, 0.83)[index], epochSeconds + 86400, epochSeconds, QuotaStatus.FRESH),
            QuotaSnapshot(ProviderId.CODEX, "5h", codex5h, epochSeconds + 4200, epochSeconds, QuotaStatus.FRESH),
            QuotaSnapshot(ProviderId.CODEX, "7d", listOf(0.52, 0.18, 0.79, 0.45)[index], epochSeconds + 120000, epochSeconds, QuotaStatus.FRESH),
            QuotaSnapshot(ProviderId.ANTIGRAVITY_GEMINI, "model", gemini, epochSeconds + 2400, epochSeconds - 7200, geminiStatus),
            QuotaSnapshot(ProviderId.ANTIGRAVITY_CLAUDE, "model", null, null, null, QuotaStatus.UNSUPPORTED)
        )
    }
}

fun QuotaStatus.label(): String = when (this) {
    QuotaStatus.FRESH -> "정상"
    QuotaStatus.STALE -> "오래된 데이터"
    QuotaStatus.AUTH_REQUIRED -> "로그인 필요"
    QuotaStatus.RATE_LIMITED -> "요청 제한"
    QuotaStatus.OFFLINE -> "연결 불가"
    QuotaStatus.UNSUPPORTED -> "미지원"
    QuotaStatus.INVALID_RESPONSE -> "응답 오류"
}

fun QuotaSnapshot.displayPercent(): String = remainingPercent?.let { "$it%" } ?: "—"
