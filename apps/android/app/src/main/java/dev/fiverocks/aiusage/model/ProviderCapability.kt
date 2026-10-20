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
