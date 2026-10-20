package dev.fiverocks.aiusage.model

/** Provider authentication permission is distinct from entitlement to read subscription quota. */
enum class ApprovalState { UNKNOWN, ALLOWED, BLOCKED }

data class ProviderCapability(
    val provider: ProviderId,
    val authApproval: ApprovalState,
    val quotaReadApproval: ApprovalState,
    val distributionApproval: ApprovalState,
    val evidenceUrl: String?,
    val checkedAt: String
) {
    /** This is a policy gate, not proof that a working adapter exists. */
    val liveQuotaEnabled: Boolean
        get() = authApproval == ApprovalState.ALLOWED &&
            quotaReadApproval == ApprovalState.ALLOWED &&
            distributionApproval == ApprovalState.ALLOWED
}

/** Evidence entries are informational only; they never enable live adapters. */
object ProviderCapabilities {
    fun mockResearch(): Map<ProviderId, ProviderCapability> = ProviderId.entries.associateWith { provider ->
        ProviderCapability(
            provider = provider,
            authApproval = ApprovalState.UNKNOWN,
            quotaReadApproval = ApprovalState.UNKNOWN,
            distributionApproval = ApprovalState.UNKNOWN,
            evidenceUrl = when (provider) {
                ProviderId.CLAUDE_CODE -> "https://docs.anthropic.com/en/docs/claude-code/getting-started"
                ProviderId.CODEX -> "https://developers.openai.com/siwc/quickstart"
                ProviderId.ANTIGRAVITY_GEMINI, ProviderId.ANTIGRAVITY_CLAUDE ->
                    "https://www.antigravity.google/docs/cli/commands/usage"
            },
            checkedAt = "2026-10-20"
        )
    }
}

/**
 * Read-only Android integration eligibility. UNKNOWN must not degrade into a
 * live-looking percentage; the UI must offer an explicit source label instead.
 */
fun ProviderCapability.connectionLabel(): String = when {
    quotaReadApproval == ApprovalState.BLOCKED || authApproval == ApprovalState.BLOCKED ||
        distributionApproval == ApprovalState.BLOCKED -> "직접 조회 불가"
    !liveQuotaEnabled -> "직접 조회 미연결 · 공식 권한 미확인"
    else -> "연동 가능성 확인 · 실제 어댑터 미구현"
}
