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
import dev.fiverocks.aiusage.model.displayPercent
import dev.fiverocks.aiusage.model.label

class QuotaWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshots = WidgetCache.load(context)
        val revision = WidgetCache.revision(context)
        provideContent {
            Column(modifier = GlanceModifier.fillMaxSize().padding(12.dp)) {
                Text("AI Usage · Mock #$revision")
                if (snapshots.isEmpty()) Text("데이터 없음")
                else snapshots.groupBy { it.provider }.forEach { (provider, windows) ->
                    val summary = windows.joinToString("  ") { "${it.windowId}: ${it.displayPercent()}" }
                    Text("${provider.title}  $summary")
                    if (windows.any { it.status != dev.fiverocks.aiusage.model.QuotaStatus.FRESH })
                        Text(windows.first { it.status != dev.fiverocks.aiusage.model.QuotaStatus.FRESH }.status.label())
                }
                Text("예시 데이터 · 실제 사용량 아님")
            }
        }
    }
}
class QuotaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuotaWidget()
}
