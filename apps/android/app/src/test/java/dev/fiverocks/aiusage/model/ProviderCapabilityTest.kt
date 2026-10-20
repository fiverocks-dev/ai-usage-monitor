package dev.fiverocks.aiusage.model

import org.junit.Assert.*
import org.junit.Test

class ProviderCapabilityTest {
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
