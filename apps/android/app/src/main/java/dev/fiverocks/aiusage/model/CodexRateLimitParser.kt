package dev.fiverocks.aiusage.model

import org.json.JSONObject

/**
 * Parser for the public Codex app-server account/rateLimits/read JSON-RPC response.
 * This does not authenticate, connect to an app-server, or call any private HTTP endpoint.
 */
object CodexRateLimitParser {
    private fun percent(value: JSONObject): Double? {
        val used = value.optDouble("usedPercent", Double.NaN)
        return if (used.isFinite() && used in 0.0..100.0) (100.0 - used) / 100.0 else null
    }

    private fun timestamp(value: JSONObject): Long? {
        val epoch = value.optLong("resetsAt", -1)
        return epoch.takeIf { it > 0 }
    }

    fun parse(raw: String, fetchedAtEpochSeconds: Long): List<QuotaSnapshot> {
        require(raw.toByteArray(Charsets.UTF_8).size <= 65536) { "Codex response too large" }
        val response = JSONObject(raw)
        require(!response.has("error")) { "Codex app-server returned error" }
        val result = response.optJSONObject("result") ?: error("Missing JSON-RPC result")
        // Backend denial is authoritative even when the percentage looks positive.
        val ordinaryUsageAllowed = if (result.isNull("ordinaryUsageAllowed")) null
            else if (result.has("ordinaryUsageAllowed")) result.getBoolean("ordinaryUsageAllowed")
            else null
        val limits = result.optJSONObject("rateLimits") ?: result
        val byLimit = result.optJSONObject("rateLimitsByLimitId")
        // Never report a different model's allowance as Codex quota.
        val selected = if (byLimit != null) {
            byLimit.optJSONObject("codex") ?: error("Codex limit ID is missing")
        } else limits
        val windows = listOf("primary", "secondary").mapNotNull { key ->
            val window = selected.optJSONObject(key) ?: return@mapNotNull null
            val minutes = window.optInt("windowDurationMins", 0)
            val name = when (minutes) {
                300 -> "5h"
                10080 -> "7d"
                else -> return@mapNotNull null
            }
            val remaining = percent(window) ?: return@mapNotNull null
            QuotaSnapshot(
                ProviderId.CODEX, name, remaining, timestamp(window),
                fetchedAtEpochSeconds,
                if (ordinaryUsageAllowed == false) QuotaStatus.RATE_LIMITED else QuotaStatus.FRESH
            )
        }
        require(windows.isNotEmpty()) { "No supported Codex quota windows" }
        require(windows.distinctBy { it.windowId }.size == windows.size) { "Duplicate Codex window" }
        return windows
    }
}
