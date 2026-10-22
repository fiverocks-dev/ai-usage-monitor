package dev.fiverocks.aiusage.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.compose.ui.unit.dp
import dev.fiverocks.aiusage.model.widgetText

class QuotaWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshots = WidgetCache.load(context)
        val revision = WidgetCache.revision(context)
        val source = WidgetCache.source(context)
        val now = System.currentTimeMillis() / 1000
        provideContent {
            Column(modifier = GlanceModifier.fillMaxSize().padding(12.dp)) {
                Text("AI Usage · $source #$revision")
                if (snapshots.isEmpty()) Text("데이터 없음")
                else snapshots.groupBy { it.provider }.forEach { (provider, windows) ->
                    val summary = windows.joinToString("  ") { it.widgetText(now) }
                    Text("${provider.title}  $summary")
                }
                Text(when (source) {
                    "MANUAL_CODEX" -> "사용자 수동 입력 · 자동 갱신 안 됨"
                    "LOCAL_JSON" -> "로컬 JSON · 외부 API 미연결"
                    else -> "예시 데이터 · 실제 사용량 아님"
                })
            }
        }
    }
}
class QuotaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuotaWidget()
}
