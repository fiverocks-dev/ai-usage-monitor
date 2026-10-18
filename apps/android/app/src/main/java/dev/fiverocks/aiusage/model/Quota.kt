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
    fun snapshots(epochSeconds: Long, scenario: Int = 0): List<QuotaSnapshot> = listOf(
        QuotaSnapshot(ProviderId.CLAUDE_CODE, "5h", 0.76, epochSeconds + 3600, epochSeconds, QuotaStatus.FRESH),
        QuotaSnapshot(ProviderId.CLAUDE_CODE, "7d", 0.24, epochSeconds + 86400, epochSeconds, QuotaStatus.FRESH),
        QuotaSnapshot(ProviderId.CODEX, "5h", 0.0, epochSeconds + 4200, epochSeconds, QuotaStatus.FRESH),
        QuotaSnapshot(ProviderId.CODEX, "7d", 0.52, epochSeconds + 120000, epochSeconds, QuotaStatus.FRESH),
        QuotaSnapshot(ProviderId.ANTIGRAVITY_GEMINI, "model", 0.12, epochSeconds + 2400, epochSeconds - 7200, QuotaStatus.STALE),
        QuotaSnapshot(ProviderId.ANTIGRAVITY_CLAUDE, "model", null, null, null, QuotaStatus.UNSUPPORTED)
    ).let { if (scenario % 2 == 0) it else it.map { q -> if (q.provider == ProviderId.ANTIGRAVITY_GEMINI) q.copy(status = QuotaStatus.OFFLINE) else q } }
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
