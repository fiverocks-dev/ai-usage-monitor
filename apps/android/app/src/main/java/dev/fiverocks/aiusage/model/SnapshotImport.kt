package dev.fiverocks.aiusage.model

import org.json.JSONObject

/** Strict, local-only interchange format. No credential fields or arbitrary provider IDs. */
object SnapshotImport {
    private val rootKeys = setOf("schemaVersion", "snapshots")
    private val rowKeys = setOf("provider", "windowId", "remainingFraction", "resetAtEpochSeconds", "fetchedAtEpochSeconds", "status")
    private const val MAX_ROWS = 32
    private const val MAX_BYTES = 65536

    fun parse(raw: String): List<QuotaSnapshot> {
        require(raw.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "파일 크기 제한 64KB 초과" }
        val root = JSONObject(raw)
        require(root.keys().asSequence().all { it in rootKeys }) { "허용되지 않은 최상위 필드" }
        require(root.getInt("schemaVersion") == 1) { "지원하지 않는 스키마 버전" }
        val rows = root.getJSONArray("snapshots")
        require(rows.length() in 1..MAX_ROWS) { "스냅샷 개수는 1~32개여야 합니다" }
        val seen = mutableSetOf<Pair<ProviderId, String>>()
        return (0 until rows.length()).map { index ->
            val item = rows.getJSONObject(index)
            require(item.keys().asSequence().all { it in rowKeys }) { "허용되지 않은 필드" }
            val provider = ProviderId.valueOf(item.getString("provider"))
            val window = item.getString("windowId")
            require(window.isNotBlank() && window.length <= 32) { "잘못된 기간 이름" }
            require(seen.add(provider to window)) { "중복 Provider/기간" }
            val fraction = if (item.isNull("remainingFraction")) null else item.getDouble("remainingFraction")
            val reset = if (item.isNull("resetAtEpochSeconds")) null else item.getLong("resetAtEpochSeconds")
            val fetched = if (item.isNull("fetchedAtEpochSeconds")) null else item.getLong("fetchedAtEpochSeconds")
            val status = QuotaStatus.valueOf(item.getString("status"))
            QuotaSnapshot(provider, window, fraction, reset, fetched, status)
        }
    }
}
