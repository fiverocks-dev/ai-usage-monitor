package dev.fiverocks.aiusage.model

import org.junit.Assert.*
import org.junit.Test

class ProviderCapabilityTest {
    @Test fun approvalAloneDoesNotClaimWorkingAdapter() {
        val allowed = ProviderCapability(
            ProviderId.CODEX, ApprovalState.ALLOWED, ApprovalState.ALLOWED,
            ApprovalState.ALLOWED, null, "2026-10-20"
        )
        assertEquals("연동 가능성 확인 · 실제 어댑터 미구현", allowed.connectionLabel())
        assertEquals("직접 조회 미연결 · 공식 권한 미확인",
            ProviderCapabilities.mockResearch().getValue(ProviderId.CODEX).connectionLabel())
        assertEquals("직접 조회 불가",
            allowed.copy(quotaReadApproval = ApprovalState.BLOCKED).connectionLabel())
    }

    @Test fun mockProvidersCannotReadLiveQuota() {
        val capabilities = ProviderCapabilities.mockResearch()
        assertEquals(4, capabilities.size)
        assertTrue(capabilities.values.none { it.liveQuotaEnabled })
    }

    @Test fun signInApprovalAloneCannotUnlockQuotaAccess() {
        val candidate = ProviderCapability(
            ProviderId.CODEX,
            ApprovalState.ALLOWED,
            ApprovalState.UNKNOWN,
            ApprovalState.UNKNOWN,
            null,
            "2026-10-20"
        )
        assertFalse(candidate.liveQuotaEnabled)
    }

    @Test fun allThreeIndependentPermissionsRequired() {
        val candidate = ProviderCapability(
            ProviderId.CODEX,
            ApprovalState.ALLOWED,
            ApprovalState.ALLOWED,
            ApprovalState.ALLOWED,
            "https://example.invalid/evidence",
            "2026-10-20"
        )
        assertTrue(candidate.liveQuotaEnabled)
        assertFalse(candidate.copy(distributionApproval = ApprovalState.BLOCKED).liveQuotaEnabled)
    }
}
