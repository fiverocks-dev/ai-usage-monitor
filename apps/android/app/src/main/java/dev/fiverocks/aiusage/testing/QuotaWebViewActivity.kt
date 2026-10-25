package dev.fiverocks.aiusage.testing

import android.app.Activity
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.Button
import android.widget.TextView
import org.json.JSONObject

/** Isolated WebView cookie jar. Do not bypass provider login restrictions. */
class QuotaWebViewActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val output = TextView(this).apply { text = "WebView 실험 · 로그인 제약 가능 · 데이터는 로컬에서만 처리" }
        val web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = object : WebViewClient() {}
        }
        val button = Button(this).apply {
            text = "현재 보이는 페이지 텍스트 진단"
            setOnClickListener {
                if (web.url?.startsWith("https://chatgpt.com/settings/usage") != true) {
                    output.text = "ChatGPT 사용량 페이지에서만 읽기 실행 가능"
                    return@setOnClickListener
                }
                web.evaluateJavascript(
                    "(function(){return document.body ? document.body.innerText.slice(0,12000) : '';})()"
                ) { result ->
                    runCatching {
                        val raw = JSONObject("{\"x\":$result}").getString("x")
                        output.text = QuotaTextExtractor.summary(raw)
                    }.onFailure { output.text = "화면 텍스트 분석 실패" }
                }
            }
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(output)
            addView(button)
            addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        setContentView(layout)
        web.loadUrl("https://chatgpt.com/settings/usage?tab=overview")
    }
}
