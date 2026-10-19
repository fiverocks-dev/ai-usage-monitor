package dev.fiverocks.aiusage

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.updateAll
import dev.fiverocks.aiusage.model.*
import dev.fiverocks.aiusage.widget.QuotaWidget
import dev.fiverocks.aiusage.widget.WidgetCache
import kotlinx.coroutines.launch
import android.util.Log
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var sequence by remember { mutableIntStateOf(WidgetCache.revision(this@MainActivity)) }
            var snapshots by remember { mutableStateOf(WidgetCache.load(this@MainActivity)) }
            val scope = rememberCoroutineScope()
            var widgetSyncStatus by remember { mutableStateOf("위젯 갱신 대기") }

            fun refresh() {
                sequence++
                val requestedRevision = sequence
                snapshots = MockQuota.snapshots(System.currentTimeMillis() / 1000, requestedRevision)
                val persisted = WidgetCache.save(this@MainActivity, snapshots, requestedRevision)
                if (!persisted) {
                    widgetSyncStatus = "위젯 데이터 저장 실패"
                    return
                }
                widgetSyncStatus = "위젯 갱신 요청 중…"
                scope.launch {
                    try {
                        QuotaWidget().updateAll(this@MainActivity)
                        widgetSyncStatus = "위젯 갱신 요청 완료 · Mock #$requestedRevision"
                    } catch (error: Exception) {
                        Log.e("AIUsageWidget", "Widget refresh failed", error)
                        widgetSyncStatus = "위젯 갱신 실패 · 앱을 다시 열어 확인"
                    }
                }
            }

            LaunchedEffect(Unit) {
                if (snapshots.isEmpty()) refresh()
            }
            MaterialTheme {
                Scaffold { padding ->
                    Column(Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
                        Text("AI Usage", style = MaterialTheme.typography.headlineLarge)
                        Text("샘플 데이터 · 실제 계정에 연결되지 않음", color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { refresh() }) { Text("Mock 갱신") }
                        Text("Mock 갱신 횟수 #$sequence · 누를 때마다 잔여량 변경", style = MaterialTheme.typography.bodySmall)
                        Text(widgetSyncStatus, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(16.dp))
                        ProviderId.entries.forEach { provider ->
                            ElevatedCard(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                                Column(Modifier.padding(16.dp)) {
                                    Text(provider.title, style = MaterialTheme.typography.titleMedium)
                                    val windows = snapshots.filter { it.provider == provider }
                                    if (windows.isEmpty()) Text("데이터 없음")
                                    windows.forEach { q ->
                                        Text("${q.windowId} · 남은 사용량 ${q.displayPercent()} · ${q.displayStatus(System.currentTimeMillis() / 1000).label()}", modifier = Modifier.semantics { contentDescription = "${provider.title}, ${q.windowId}, 남은 사용량 ${q.displayPercent()}, 상태 ${q.displayStatus(System.currentTimeMillis() / 1000).label()}" })
                                        q.resetAtEpochSeconds?.let { Text("초기화: ${date(it)}") }
                                        q.fetchedAtEpochSeconds?.let { Text("마지막 갱신: ${date(it)}") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun date(epochSeconds: Long): String =
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(epochSeconds * 1000))
}
