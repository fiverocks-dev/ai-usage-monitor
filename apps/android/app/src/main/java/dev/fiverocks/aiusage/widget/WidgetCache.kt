package dev.fiverocks.aiusage.widget

import android.content.Context
import dev.fiverocks.aiusage.model.*
import org.json.JSONArray
import org.json.JSONObject

/** UI-only, no credential fields. Private app storage; backup disabled in manifest. */
object WidgetCache {
    private const val STORE = "mock_widget_snapshots"
    private const val KEY = "snapshots"
    private const val REVISION = "mock_revision"
    private const val SOURCE = "data_source"

    fun source(context: Context): String =
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE).getString(SOURCE, "MOCK") ?: "MOCK"

    fun revision(context: Context): Int =
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE).getInt(REVISION, 0)

    fun save(context: Context, values: List<QuotaSnapshot>, revision: Int, source: String = "MOCK"): Boolean {
        val json = JSONArray()
        values.forEach { s ->
            json.put(JSONObject().apply {
                put("provider", s.provider.name)
                put("window", s.windowId)
                put("remaining", s.remainingFraction ?: JSONObject.NULL)
                put("reset", s.resetAtEpochSeconds ?: JSONObject.NULL)
                put("fetched", s.fetchedAtEpochSeconds ?: JSONObject.NULL)
                put("status", s.status.name)
            })
        }
        return context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit()
            .putString(KEY, json.toString())
            .putInt(REVISION, revision)
            .putString(SOURCE, source)
            .commit()
    }

    fun load(context: Context): List<QuotaSnapshot> = runCatching {
        val raw = context.getSharedPreferences(STORE, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return emptyList()
        val data = JSONArray(raw)
        (0 until data.length()).map { i ->
            val obj = data.getJSONObject(i)
            QuotaSnapshot(
                ProviderId.valueOf(obj.getString("provider")),
                obj.getString("window"),
                if (obj.isNull("remaining")) null else obj.getDouble("remaining"),
                if (obj.isNull("reset")) null else obj.getLong("reset"),
                if (obj.isNull("fetched")) null else obj.getLong("fetched"),
                QuotaStatus.valueOf(obj.getString("status"))
            )
        }
    }.getOrDefault(emptyList())
}
