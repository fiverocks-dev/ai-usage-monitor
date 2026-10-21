package dev.fiverocks.aiusage

import android.os.Bundle
import android.content.Intent
import android.content.ActivityNotFoundException
import android.net.Uri
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
            var browserStatus by remember { mutableStateOf("") }
            var codexFiveHour by remember { mutableStateOf("") }
            var codexWeekly by remember { mutableStateOf("") }
            var manualStatus by remember { mutableStateOf("") }
            fun saveManualCodex() {
                val five = codexFiveHour.trim().toIntOrNull()
                val week = codexWeekly.trim().toIntOrNull()
                if (five == null || week == null || five !in 0..100 || week !in 0..100) {
                    manualStatus = "두 항목 모두 남은 비율 0~100 사이의 정수로 입력하세요"
                    return
                }
                val now = System.currentTimeMillis() / 1000
                val nextRevision = sequence + 1
                val manual = listOf(
                    QuotaSnapshot(ProviderId.CODEX, "5h", five / 100.0, null, now, QuotaStatus.FRESH),
                    QuotaSnapshot(ProviderId.CODEX, "7d", week / 100.0, null, now, QuotaStatus.FRESH)
                )
                if (!WidgetCache.save(this@MainActivity, manual, nextRevision, "MANUAL_CODEX")) {
                    manualStatus = "수동 입력 저장 실패"
                    return
                }
                sequence = nextRevision
                snapshots = manual
                dataSource = "MANUAL_CODEX"
                manualStatus = "수동 입력 저장됨 · 자동 동기화 아님"
                scope.launch {
                    try {
                        QuotaWidget().updateAll(this@MainActivity)
                        manualStatus = "Codex 수동 입력 위젯 갱신 요청 완료"
                    } catch (error: Exception) {
                        manualStatus = "저장 완료 · 위젯 갱신 요청 실패"
                        Log.e("AIUsageWidget", "Manual Codex refresh failed", error)
                    }
                }
            }
            fun openOfficialSite(url: String) {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE))
                    browserStatus = "브라우저 열기 요청 완료 · 사용량 화면 이동은 직접 확인 필요"
                } catch (error: ActivityNotFoundException) {
                    browserStatus = "웹 브라우저 앱을 찾을 수 없습니다"
                } catch (error: SecurityException) {
                    browserStatus = "브라우저 실행이 기기 정책에 의해 차단됐습니다"
                }
            }
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
                        Text("Codex 실제 화면값 수동 입력", style = MaterialTheme.typography.titleMedium)
                        Text("Codex 사용량 화면에 나온 '남은 비율(%)'을 입력하세요. 사용한 비율이 표시되면 100에서 빼야 합니다. 이 값은 자동 갱신되지 않습니다.", style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(
                            value = codexFiveHour,
                            onValueChange = { codexFiveHour = it.take(3).filter(Char::isDigit) },
                            label = { Text("Codex 5시간 남은 비율 (0~100)") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = codexWeekly,
                            onValueChange = { codexWeekly = it.take(3).filter(Char::isDigit) },
                            label = { Text("Codex 주간 남은 비율 (0~100)") },
                            singleLine = true
                        )
                        Button(onClick = { saveManualCodex() }) { Text("Codex 수동값 위젯에 적용") }
                        if (manualStatus.isNotEmpty()) Text(manualStatus, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(12.dp))
                        Text("실제 사용량 확인 (휴대폰 브라우저)")
                        Text("불안정한 사용량 딥링크 대신 공식 서비스 첫 화면을 엽니다. 로그인 후 설정 → 사용량으로 이동하세요. 앱·위젯 자동 동기화는 지원하지 않습니다.", style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = {
                            openOfficialSite("https://claude.ai/")
                        }) { Text("Claude 열기 · 설정 → 사용량") }
                        OutlinedButton(onClick = {
                            openOfficialSite("https://chatgpt.com/")
                        }) { Text("ChatGPT 열기 · 설정 → 사용량") }
                        if (browserStatus.isNotEmpty()) Text(browserStatus, style = MaterialTheme.typography.bodySmall)
                        Text("Antigravity: 공식 Android 웹 quota 화면 URL은 검증되지 않았습니다.", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                        Text("Android 직접 연동: 공식 quota 조회 권한 검증 중")
                        Text("현재 값은 Mock 또는 사용자가 가져온 로컬 JSON이며 실계정 자동 조회가 아닙니다.")
                        Spacer(Modifier.height(12.dp))
                        ProviderId.entries.forEach { provider ->
                            ElevatedCard(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                                Column(Modifier.padding(16.dp)) {
                                    Text(provider.title, style = MaterialTheme.typography.titleMedium)
                                    val capability = ProviderCapabilities.mockResearch().getValue(provider)
                                    Text(
                                        capability.connectionLabel(),
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
