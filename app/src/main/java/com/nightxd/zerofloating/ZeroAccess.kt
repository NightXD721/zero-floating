package com.nightxd.zerofloating

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

/**
 * Accessibility helper so Overlay mode can tap/type into Chrome (or any app)
 * when filling Google Forms outside Zero Floating.
 */
class ZeroAccess : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.eventTypes = 0
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.flags = (AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            or AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS)
        info.notificationTimeout = 100
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    fun clickText(target: String, partial: Boolean = true): Boolean {
        val root = rootInActiveWindow ?: return false
        val want = target.trim().lowercase()
        if (want.isEmpty()) return false
        val hit = findNode(root, want, partial)
        if (hit != null) {
            val ok = hit.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            if (!ok) {
                // try parent clickable
                var p = hit.parent
                var depth = 0
                while (p != null && depth < 6) {
                    if (p.isClickable) {
                        if (p.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
                    }
                    p = p.parent
                    depth++
                }
                // gesture tap center
                val r = Rect()
                hit.getBoundsInScreen(r)
                if (r.width() > 2 && r.height() > 2) {
                    return tap(r.centerX().toFloat(), r.centerY().toFloat())
                }
                return false
            }
            return true
        }
        return false
    }

    fun typeText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val focus = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: findEditable(root)
            ?: return false
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        return focus.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    fun scrollForward(): Boolean {
        val root = rootInActiveWindow ?: return false
        return scrollNode(root)
    }

    private fun scrollNode(n: AccessibilityNodeInfo?): Boolean {
        if (n == null) return false
        if (n.isScrollable && n.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) return true
        for (i in 0 until n.childCount) {
            if (scrollNode(n.getChild(i))) return true
        }
        return false
    }

    private fun findEditable(n: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (n == null) return null
        if (n.isEditable) return n
        for (i in 0 until n.childCount) {
            val c = findEditable(n.getChild(i))
            if (c != null) return c
        }
        return null
    }

    private fun findNode(n: AccessibilityNodeInfo?, want: String, partial: Boolean): AccessibilityNodeInfo? {
        if (n == null) return null
        val t = (n.text?.toString() ?: "") + " " + (n.contentDescription?.toString() ?: "")
        val low = t.lowercase().replace("\\s+".toRegex(), " ").trim()
        if (low.isNotEmpty()) {
            if (partial) {
                if (low.contains(want) || want.contains(low.take(20))) {
                    if (n.isClickable || n.isCheckable || n.isEditable) return n
                    // still return for parent walk
                    return n
                }
            } else if (low == want) {
                return n
            }
        }
        for (i in 0 until n.childCount) {
            val c = findNode(n.getChild(i), want, partial)
            if (c != null) return c
        }
        return null
    }

    private fun tap(x: Float, y: Float): Boolean {
        if (android.os.Build.VERSION.SDK_INT < 24) return false
        val path = Path()
        path.moveTo(x, y)
        val stroke = GestureDescription.StrokeDescription(path, 0, 60)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, null, null)
    }

    companion object {
        @Volatile
        var instance: ZeroAccess? = null

        fun enabled(ctx: Context): Boolean {
            val expected = ctx.packageName + "/" + ZeroAccess::class.java.canonicalName
            val enabled = Settings.Secure.getString(
                ctx.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabled.split(':').any { it.equals(expected, true) || it.endsWith("/" + ZeroAccess::class.java.simpleName) }
        }

        fun openSettings(ctx: Context) {
            try {
                ctx.startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) {
            }
        }

        fun click(text: String): Boolean = instance?.clickText(text) == true
        fun type(text: String): Boolean = instance?.typeText(text) == true
        fun scroll(): Boolean = instance?.scrollForward() == true
    }
}
