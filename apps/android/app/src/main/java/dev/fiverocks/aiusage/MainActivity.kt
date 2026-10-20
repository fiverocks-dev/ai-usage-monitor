package dev.fiverocks.aiusage

import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
            var dataSource by remember { mutableStateOf(WidgetCache.source(this@MainActivity)) }
            val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) {
                    try {
                        val raw = contentResolver.openInputStream(uri)?.use { input ->
                            val bytes = input.readNBytes(65537)
                            require(bytes.size <= 65536) { "파일 크기 제한 초과" }
                            String(bytes, Charsets.UTF_8)
                        } ?: error("파일 읽기 실패")
                        val imported = SnapshotImport.parse(raw)
                        val revision = sequence + 1
                        if (WidgetCache.save(this@MainActivity, imported, revision, "LOCAL_JSON")) {
                            sequence = revision
                            snapshots = imported
                            dataSource = "LOCAL_JSON"
                            widgetSyncStatus = "파일 적용 완료 · 위젯 갱신 중"
                            scope.launch {
                                try {
                                    QuotaWidget().updateAll(this@MainActivity)
                                    widgetSyncStatus = "파일 적용 완료 · 위젯 요청 완료"
                                } catch (error: Exception) {
                                    Log.e("AIUsageWidget", "Widget refresh failed", error)
                                    widgetSyncStatus = "파일 적용 완료 · 위젯 갱신 실패"
                                }
                            }
                        } else widgetSyncStatus = "저장 실패"
                    } catch (error: Exception) {
                        Log.w("AIUsageImport", "Rejected JSON file: " + error.javaClass.simpleName)
                        widgetSyncStatus = "가져오기 실패 · JSON 형식 확인"
                    }
                }
            }

            fun refresh() {
                sequence++
                val requestedRevision = sequence
                snapshots = MockQuota.snapshots(System.currentTimeMillis() / 1000, requestedRevision)
                dataSource = "MOCK"
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
                        Text("로컬 데이터 · 실제 계정에 연결되지 않음", color = MaterialTheme.colorScheme.error)
                        Text("데이터 출처: $dataSource")
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { refresh() }) { Text("Mock 갱신") }
                            OutlinedButton(onClick = { filePicker.launch(arrayOf("application/json", "text/plain")) }) { Text("JSON 가져오기") }
                        }
                        Text("Mock 갱신 횟수 #$sequence · 누를 때마다 잔여량 변경", style = MaterialTheme.typography.bodySmall)
                        Text(widgetSyncStatus, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(16.dp))
                        Text("Android 직접 연동: 공식 quota 조회 권한 검증 중")
                        Text("현재 값은 Mock 또는 사용자가 가져온 로컬 JSON이며 실계정 자동 조회가 아닙니다.")
                        Spacer(Modifier.height(12.dp))
                        ProviderId.entries.forEach { provider ->
                            ElevatedCard(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                                Column(Modifier.padding(16.dp)) {
                                    Text(provider.title, style = MaterialTheme.typography.titleMedium)
                                    val capability = ProviderCapabilities.mockResearch().getValue(provider)
                                    Text(
                                        if (capability.liveQuotaEnabled) "직접 조회 가능" else "직접 조회 미연결 · 공식 quota API 미확인",
                                        style = MaterialTheme.typography.bodySmall
                                    )
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
