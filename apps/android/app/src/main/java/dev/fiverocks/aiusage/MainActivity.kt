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
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.updateAll
import dev.fiverocks.aiusage.model.*
import dev.fiverocks.aiusage.widget.QuotaWidget
import dev.fiverocks.aiusage.widget.WidgetCache
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var sequence by remember { mutableIntStateOf(WidgetCache.revision(this@MainActivity)) }
            var snapshots by remember { mutableStateOf(WidgetCache.load(this@MainActivity)) }
            val scope = rememberCoroutineScope()

            fun refresh() {
                sequence++
                snapshots = MockQuota.snapshots(System.currentTimeMillis() / 1000, sequence)
                WidgetCache.save(this@MainActivity, snapshots, sequence)
                scope.launch { QuotaWidget().updateAll(this@MainActivity) }
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
                        Spacer(Modifier.height(16.dp))
                        ProviderId.entries.forEach { provider ->
                            ElevatedCard(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                                Column(Modifier.padding(16.dp)) {
                                    Text(provider.title, style = MaterialTheme.typography.titleMedium)
                                    val windows = snapshots.filter { it.provider == provider }
                                    if (windows.isEmpty()) Text("데이터 없음")
                                    windows.forEach { q ->
                                        Text("${q.windowId} · 남은 사용량 ${q.displayPercent()} · ${q.status.label()}")
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
