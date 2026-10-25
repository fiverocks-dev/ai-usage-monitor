package dev.fiverocks.aiusage.testing

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.content.Context

/** Explicit opt-in diagnostic service; observes only Chrome's visible usage page. */
class QuotaAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!getSharedPreferences("quota_diagnostics", Context.MODE_PRIVATE)
                .getBoolean("a11y_armed", false)) return
        if (event?.packageName?.toString() != "com.android.chrome") return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val root = rootInActiveWindow ?: return
        val lines = mutableListOf<String>()
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)
        while (stack.isNotEmpty() && lines.size < 180) {
            val node = stack.removeLast()
            node.text?.toString()?.takeIf { it.isNotBlank() }?.let(lines::add)
            val count = node.childCount.coerceAtMost(40)
            for (i in 0 until count) node.getChild(i)?.let(stack::add)
        }
        val joined = lines.joinToString("\n")
        // Never persist arbitrary page text, cookies, form fields, or credentials.
        if (!joined.contains("사용량") && !joined.contains("Usage", true) &&
            !joined.contains("5시간") && !joined.contains("Weekly", true)) return
        val diagnostic = QuotaTextExtractor.summary(joined)
        getSharedPreferences("quota_diagnostics", Context.MODE_PRIVATE)
            .edit().putString("a11y_result", diagnostic).apply()
    }
    override fun onInterrupt() {}
}
