package com.example.jarvis_personalassistant

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.KeyEvent
import kotlinx.coroutines.flow.MutableStateFlow
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class JarvisAccessibilityService : AccessibilityService() {

    private var windowManager: WindowManager? = null
    private var ambientLightView: AmbientLightView? = null
    private var serviceJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main)

    companion object {
        private const val TAG = "JarvisAccessService"
        
        private var sInstance: JarvisAccessibilityService? = null
        
        val isRunning = MutableStateFlow(false)

        fun getInstance(): JarvisAccessibilityService? = sInstance

        fun performBack(): Boolean {
            return sInstance?.performGlobalAction(GLOBAL_ACTION_BACK) ?: false
        }

        fun performHome(): Boolean {
            return sInstance?.performGlobalAction(GLOBAL_ACTION_HOME) ?: false
        }

        private fun cleanClickTarget(text: String): String {
            return text.lowercase()
                .replace(Regex("""\b(the|button|icon|link|image|menu|field|input|box)\b"""), "")
                .trim()
        }

        fun performClickOnText(text: String): Boolean {
            val service = sInstance ?: return false
            
            val prefs = service.getSharedPreferences("jarvis_prefs", MODE_PRIVATE)
            val recognizeCommonIcons = prefs.getBoolean("recognize_common_icons", true)

            // Build candidates list in order of specificity
            val candidates = java.util.LinkedHashSet<String>()
            candidates.add(text)
            
            val cleaned = cleanClickTarget(text)
            if (cleaned.isNotBlank()) {
                candidates.add(cleaned)
            }
            
            if (recognizeCommonIcons) {
                val lower = text.lowercase()
                if (lower.contains("record") || lower.contains("mic") || lower.contains("voice") || lower.contains("audio")) {
                    candidates.add("voice message")
                    candidates.add("record")
                    candidates.add("microphone")
                    candidates.add("audio")
                }
                if (lower.contains("send") || lower.contains("submit") || lower.contains("post")) {
                    candidates.add("send")
                    candidates.add("submit")
                }
                if (lower.contains("search") || lower.contains("find")) {
                    candidates.add("search")
                    candidates.add("find")
                }
            }

            // Try active window first
            val root = service.rootInActiveWindow
            if (root != null) {
                for (candidate in candidates) {
                    val success = clickInNodeTree(root, candidate)
                    if (success) {
                        root.recycle()
                        return true
                    }
                }
                root.recycle()
            }
            
            // Fall back to all interactive windows
            val windows = service.windows
            if (!windows.isNullOrEmpty()) {
                for (window in windows) {
                    val wRoot = window.root
                    if (wRoot != null) {
                        for (candidate in candidates) {
                            val success = clickInNodeTree(wRoot, candidate)
                            if (success) {
                                wRoot.recycle()
                                return true
                            }
                        }
                        wRoot.recycle()
                    }
                }
            }
            
            Log.d(TAG, "No nodes found matching candidates: $candidates")
            return false
        }

        fun performLongClickOnText(text: String): Boolean {
            val service = sInstance ?: return false
            val candidates = java.util.LinkedHashSet<String>()
            candidates.add(text)
            val cleaned = cleanClickTarget(text)
            if (cleaned.isNotBlank()) candidates.add(cleaned)
            
            val root = service.rootInActiveWindow
            if (root != null) {
                for (candidate in candidates) {
                    val success = longClickInNodeTree(root, candidate)
                    if (success) {
                        root.recycle()
                        return true
                    }
                }
                root.recycle()
            }
            val windows = service.windows
            if (!windows.isNullOrEmpty()) {
                for (window in windows) {
                    val wRoot = window.root
                    if (wRoot != null) {
                        for (candidate in candidates) {
                            val success = longClickInNodeTree(wRoot, candidate)
                            if (success) {
                                wRoot.recycle()
                                return true
                            }
                        }
                        wRoot.recycle()
                    }
                }
            }
            return false
        }

        private fun longClickInNodeTree(root: AccessibilityNodeInfo, text: String): Boolean {
            val nodes = root.findAccessibilityNodeInfosByText(text)
            if (nodes.isNullOrEmpty()) return false
            var clickedAny = false
            for (node in nodes) {
                val clicked = node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
                if (clicked) {
                    Log.d(TAG, "Long clicked node: $text")
                    clickedAny = true
                }
                if (!clickedAny) {
                    var parent = node.parent
                    while (parent != null) {
                        val pClicked = parent.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
                        if (pClicked) {
                            Log.d(TAG, "Long clicked parent of node: $text")
                            clickedAny = true
                            parent.recycle()
                            break
                        }
                        val temp = parent.parent
                        parent.recycle()
                        parent = temp
                    }
                }
                node.recycle()
                if (clickedAny) break
            }
            return clickedAny
        }

        fun performCheckOnText(text: String, check: Boolean): Boolean {
            val service = sInstance ?: return false
            val candidates = java.util.LinkedHashSet<String>()
            candidates.add(text)
            val cleaned = cleanClickTarget(text)
            if (cleaned.isNotBlank()) candidates.add(cleaned)
            
            val root = service.rootInActiveWindow
            if (root != null) {
                for (candidate in candidates) {
                    val success = checkInNodeTree(root, candidate, check)
                    if (success) {
                        root.recycle()
                        return true
                    }
                }
                root.recycle()
            }
            val windows = service.windows
            if (!windows.isNullOrEmpty()) {
                for (window in windows) {
                    val wRoot = window.root
                    if (wRoot != null) {
                        for (candidate in candidates) {
                            val success = checkInNodeTree(wRoot, candidate, check)
                            if (success) {
                                wRoot.recycle()
                                return true
                            }
                        }
                        wRoot.recycle()
                    }
                }
            }
            return false
        }

        private fun checkInNodeTree(root: AccessibilityNodeInfo, text: String, check: Boolean): Boolean {
            val nodes = root.findAccessibilityNodeInfosByText(text)
            if (nodes.isNullOrEmpty()) return false
            var success = false
            for (node in nodes) {
                var targetNode: AccessibilityNodeInfo? = node
                while (targetNode != null) {
                    if (targetNode.isCheckable) {
                        val isChecked = targetNode.isChecked
                        if (isChecked != check) {
                            success = targetNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            Log.d(TAG, "Toggled checkable node state to $check")
                        } else {
                            success = true
                        }
                        break
                    }
                    val parent = targetNode.parent
                    if (targetNode != node) targetNode.recycle()
                    targetNode = parent
                }
                if (targetNode != node && targetNode != null) targetNode.recycle()
                node.recycle()
                if (success) break
            }
            return success
        }

        fun performScrollDirection(direction: String): Boolean {
            val service = sInstance ?: return false
            val metrics = service.resources.displayMetrics
            val width = metrics.widthPixels.toFloat()
            val height = metrics.heightPixels.toFloat()
            return when (direction.lowercase().trim()) {
                "down" -> swipeGesture(width / 2, height * 0.7f, width / 2, height * 0.2f)
                "up" -> swipeGesture(width / 2, height * 0.3f, width / 2, height * 0.8f)
                "right" -> swipeGesture(width * 0.8f, height / 2, width * 0.2f, height / 2)
                "left" -> swipeGesture(width * 0.2f, height / 2, width * 0.8f, height / 2)
                else -> false
            }
        }

        fun performMagnification(action: String): Boolean {
            val service = sInstance ?: return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val controller = service.magnificationController ?: return false
                val currentScale = controller.scale
                val currentX = controller.centerX
                val currentY = controller.centerY
                val metrics = service.resources.displayMetrics
                val width = metrics.widthPixels.toFloat()
                val height = metrics.heightPixels.toFloat()
                
                return when (action.lowercase().trim()) {
                    "start zooming" -> controller.setScale(2.0f, true)
                    "zoom in" -> controller.setScale((currentScale + 1.0f).coerceAtMost(8.0f), true)
                    "zoom out" -> {
                        val target = (currentScale - 1.0f).coerceAtLeast(1.0f)
                        if (target == 1.0f) controller.reset(true) else controller.setScale(target, true)
                    }
                    "stop zooming" -> controller.reset(true)
                    "pan left" -> {
                        val step = 200f / currentScale
                        controller.setCenter((currentX - step).coerceAtLeast(0f), currentY, true)
                    }
                    "pan right" -> {
                        val step = 200f / currentScale
                        controller.setCenter((currentX + step).coerceAtMost(width), currentY, true)
                    }
                    "pan up" -> {
                        val step = 200f / currentScale
                        controller.setCenter(currentX, (currentY - step).coerceAtLeast(0f), true)
                    }
                    "pan down" -> {
                        val step = 200f / currentScale
                        controller.setCenter(currentX, (currentY + step).coerceAtMost(height), true)
                    }
                    else -> false
                }
            }
            return false
        }

        fun performTextEditAction(command: String): Boolean {
            val service = sInstance ?: return false
            
            var focusedNode: AccessibilityNodeInfo? = null
            val root = service.rootInActiveWindow
            if (root != null) {
                focusedNode = findFocusedNode(root)
                root.recycle()
            }
            if (focusedNode == null) {
                val windows = service.windows
                if (!windows.isNullOrEmpty()) {
                    for (window in windows) {
                        val wRoot = window.root
                        if (wRoot != null) {
                            focusedNode = findFocusedNode(wRoot)
                            wRoot.recycle()
                            if (focusedNode != null) break
                        }
                    }
                }
            }
            if (focusedNode == null) return false
            
            val currentText = focusedNode.text?.toString() ?: ""
            val selStart = focusedNode.textSelectionStart
            val selEnd = focusedNode.textSelectionEnd
            
            var newText = currentText
            var newSelStart = selStart
            var newSelEnd = selEnd
            var actionTaken = false
            
            val clean = command.lowercase().trim()
            
            if (clean == "delete all text" || clean == "delete all") {
                newText = ""
                newSelStart = 0
                newSelEnd = 0
                actionTaken = true
            } else if (clean.startsWith("delete ") && clean != "delete selected text") {
                val target = command.substring("delete ".length).trim()
                if (currentText.contains(target, ignoreCase = true)) {
                    newText = currentText.replace(target, "", ignoreCase = true)
                    actionTaken = true
                }
            } else if (clean == "delete selected text") {
                if (selStart in 0..selEnd) {
                    newText = currentText.substring(0, selStart) + currentText.substring(selEnd)
                    newSelStart = selStart
                    newSelEnd = selStart
                    actionTaken = true
                }
            } else if (clean == "delete to the beginning") {
                if (selStart >= 0) {
                    newText = currentText.substring(selStart)
                    newSelStart = 0
                    newSelEnd = 0
                    actionTaken = true
                }
            } else if (clean == "delete to the end") {
                if (selStart >= 0) {
                    newText = currentText.substring(0, selStart)
                    newSelStart = newText.length
                    newSelEnd = newText.length
                    actionTaken = true
                }
            } else if (clean == "select all text" || clean == "select all") {
                newSelStart = 0
                newSelEnd = currentText.length
                actionTaken = true
            } else if (clean == "unselect text") {
                newSelStart = currentText.length
                newSelEnd = currentText.length
                actionTaken = true
            } else if (clean == "go to the beginning") {
                newSelStart = 0
                newSelEnd = 0
                actionTaken = true
            } else if (clean == "go to the end") {
                newSelStart = currentText.length
                newSelEnd = currentText.length
                actionTaken = true
            } else if (clean.startsWith("replace ") && clean.contains(" with ")) {
                val indexWith = clean.indexOf(" with ")
                val wordToReplace = command.substring("replace ".length, indexWith).trim()
                val replacement = command.substring(indexWith + " with ".length).trim()
                if (currentText.contains(wordToReplace, ignoreCase = true)) {
                    newText = currentText.replace(wordToReplace, replacement, ignoreCase = true)
                    actionTaken = true
                }
            } else if (clean.startsWith("uppercase ")) {
                val target = command.substring("uppercase ".length).trim()
                if (currentText.contains(target, ignoreCase = true)) {
                    newText = currentText.replace(target, target.uppercase(), ignoreCase = true)
                    actionTaken = true
                }
            } else if (clean.startsWith("lowercase ")) {
                val target = command.substring("lowercase ".length).trim()
                if (currentText.contains(target, ignoreCase = true)) {
                    newText = currentText.replace(target, target.lowercase(), ignoreCase = true)
                    actionTaken = true
                }
            } else if (clean.startsWith("capitalize ")) {
                val target = command.substring("capitalize ".length).trim()
                if (currentText.contains(target, ignoreCase = true)) {
                    val capitalized = target.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                    newText = currentText.replace(target, capitalized, ignoreCase = true)
                    actionTaken = true
                }
            } else if (clean.startsWith("insert ") && clean.contains(" before ")) {
                val indexBefore = clean.indexOf(" before ")
                val textToInsert = command.substring("insert ".length, indexBefore).trim()
                val anchor = command.substring(indexBefore + " before ".length).trim()
                val anchorIndex = currentText.indexOf(anchor, ignoreCase = true)
                if (anchorIndex >= 0) {
                    newText = currentText.substring(0, anchorIndex) + textToInsert + " " + currentText.substring(anchorIndex)
                    actionTaken = true
                }
            } else if (clean.startsWith("insert ") && clean.contains(" after ")) {
                val indexAfter = clean.indexOf(" after ")
                val textToInsert = command.substring("insert ".length, indexAfter).trim()
                val anchor = command.substring(indexAfter + " after ".length).trim()
                val anchorIndex = currentText.indexOf(anchor, ignoreCase = true)
                if (anchorIndex >= 0) {
                    val insertPos = anchorIndex + anchor.length
                    newText = currentText.substring(0, insertPos) + " " + textToInsert + currentText.substring(insertPos)
                    actionTaken = true
                }
            }
            
            var success = false
            if (actionTaken) {
                if (newText != currentText) {
                    val arguments = android.os.Bundle()
                    arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
                    success = focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                } else {
                    success = true
                }
                if (success && (newSelStart != selStart || newSelEnd != selEnd)) {
                    val selectionArgs = android.os.Bundle().apply {
                        putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, newSelStart)
                        putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, newSelEnd)
                    }
                    focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selectionArgs)
                }
            }
            focusedNode.recycle()
            return success
        }

        fun performNotifications(): Boolean {
            return sInstance?.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS) ?: false
        }
        
        fun performQuickSettings(): Boolean {
            return sInstance?.performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS) ?: false
        }
        
        fun performRecents(): Boolean {
            return sInstance?.performGlobalAction(GLOBAL_ACTION_RECENTS) ?: false
        }
        
        fun performPowerMenu(): Boolean {
            return sInstance?.performGlobalAction(GLOBAL_ACTION_POWER_DIALOG) ?: false
        }
        
        fun performLockScreen(): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                sInstance?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) ?: false
            } else false
        }
        
        fun performScreenshot(): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                sInstance?.performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT) ?: false
            } else false
        }

        private fun clickInNodeTree(root: AccessibilityNodeInfo, text: String): Boolean {
            val nodes = root.findAccessibilityNodeInfosByText(text)
            if (nodes.isNullOrEmpty()) return false
            
            var clickedAny = false
            for (node in nodes) {
                if (!clickedAny) {
                    if (node.isClickable) {
                        val clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        if (clicked) {
                            Log.d(TAG, "Clicked node with text: $text")
                            clickedAny = true
                        }
                    }
                    if (!clickedAny) {
                        // Try parent nodes if node itself isn't clickable
                        var parent = node.parent
                        while (parent != null) {
                            if (parent.isClickable) {
                                val clicked = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                                if (clicked) {
                                    Log.d(TAG, "Clicked parent node with text: $text")
                                    clickedAny = true
                                    parent.recycle()
                                    break
                                }
                            }
                            val temp = parent.parent
                            parent.recycle()
                            parent = temp
                        }
                    }
                }
                node.recycle()
            }
            return clickedAny
        }

        fun getScreenContent(): String {
            val service = sInstance ?: return "Accessibility Service is not running."
            val sb = StringBuilder()
            
            // Try rootInActiveWindow first
            val root = service.rootInActiveWindow
            if (root != null) {
                parseNodeTree(root, sb, 0)
                root.recycle()
            }
            
            // If empty, fall back to searching all interactive windows
            if (sb.isEmpty()) {
                val windows = service.windows
                if (!windows.isNullOrEmpty()) {
                    for (window in windows) {
                        val wRoot = window.root
                        if (wRoot != null) {
                            parseNodeTree(wRoot, sb, 0)
                            wRoot.recycle()
                        }
                    }
                }
            }
            
            val result = sb.toString()
            return if (result.isNotBlank()) result else "Screen is empty or not accessible."
        }

        private fun parseNodeTree(node: AccessibilityNodeInfo?, sb: StringBuilder, depth: Int) {
            if (node == null) return
            val text = node.text?.toString() ?: node.contentDescription?.toString()
            if (!text.isNullOrBlank()) {
                sb.append("  ".repeat(depth))
                sb.append("- ")
                sb.append(text)
                if (node.isClickable) sb.append(" [Clickable]")
                sb.append("\n")
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                parseNodeTree(child, sb, depth + 1)
                child?.recycle()
            }
        }

        fun clickAtCoordinates(x: Float, y: Float): Boolean {
            val service = sInstance ?: return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val path = Path().apply { moveTo(x, y) }
                val gestureBuilder = GestureDescription.Builder()
                gestureBuilder.addStroke(GestureDescription.StrokeDescription(path, 0, 100))
                return service.dispatchGesture(gestureBuilder.build(), null, null)
            }
            return false
        }

        fun longClickAtCoordinates(x: Float, y: Float): Boolean {
            val service = sInstance ?: return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val path = Path().apply { moveTo(x, y) }
                val gestureBuilder = GestureDescription.Builder()
                gestureBuilder.addStroke(GestureDescription.StrokeDescription(path, 0, 600))
                return service.dispatchGesture(gestureBuilder.build(), null, null)
            }
            return false
        }

        fun swipeGesture(startX: Float, startY: Float, endX: Float, endY: Float): Boolean {
            val service = sInstance ?: return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val path = Path().apply {
                    moveTo(startX, startY)
                    lineTo(endX, endY)
                }
                val gestureBuilder = GestureDescription.Builder()
                gestureBuilder.addStroke(GestureDescription.StrokeDescription(path, 0, 300))
                return service.dispatchGesture(gestureBuilder.build(), null, null)
            }
            return false
        }

        private fun findFocusedNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (node.isFocused) {
                return AccessibilityNodeInfo.obtain(node)
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    val found = findFocusedNode(child)
                    child.recycle()
                    if (found != null) {
                        return found
                    }
                }
            }
            return null
        }

        fun enterLockScreenPin(pin: String): Boolean {
            val service = sInstance ?: return false
            val root = service.rootInActiveWindow ?: return false
            var success = true
            
            for (char in pin) {
                if (!char.isDigit()) continue
                val digit = char.toString()
                var clicked = false
                val nodes = root.findAccessibilityNodeInfosByText(digit)
                if (!nodes.isNullOrEmpty()) {
                    for (node in nodes) {
                        if (node.isClickable && (node.text?.toString() == digit || node.contentDescription?.toString()?.contains(digit) == true)) {
                            clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            if (clicked) {
                                node.recycle()
                                break
                            }
                        }
                        node.recycle()
                    }
                }
                if (!clicked) {
                    success = false
                    Log.d(TAG, "Failed to click digit $digit")
                }
                try { Thread.sleep(250) } catch (e: Exception) {}
            }
            
            val actionTexts = listOf("OK", "Enter", "Done", "Go", "Next", "✓")
            for (actionText in actionTexts) {
                val actionNodes = root.findAccessibilityNodeInfosByText(actionText)
                if (!actionNodes.isNullOrEmpty()) {
                    for (node in actionNodes) {
                        if (node.isClickable) {
                            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            node.recycle()
                            break
                        }
                        node.recycle()
                    }
                }
            }
            root.recycle()
            return success
        }

        private var mGridView: GridView? = null
        private val mHandler = android.os.Handler(android.os.Looper.getMainLooper())

        fun showGridOverlay() {
            val service = sInstance ?: return
            mHandler.post {
                val wm = service.windowManager ?: return@post
                if (mGridView == null) {
                    mGridView = GridView(service)
                    val params = WindowManager.LayoutParams().apply {
                        type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                        format = PixelFormat.TRANSLUCENT
                        flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        width = WindowManager.LayoutParams.MATCH_PARENT
                        height = WindowManager.LayoutParams.MATCH_PARENT
                    }
                    try {
                        wm.addView(mGridView, params)
                    } catch (e: java.lang.Exception) {
                        Log.e(TAG, "Failed to add grid view overlay", e)
                    }
                }
                mGridView?.show()
            }
        }

        fun hideGridOverlay() {
            mHandler.post {
                mGridView?.hide()
            }
        }

        fun selectGridCell(index: Int): Boolean {
            val grid = mGridView ?: return false
            if (grid.visibility != View.VISIBLE) return false
            mHandler.post {
                grid.zoomToCell(index)
            }
            return true
        }

        fun clickGridCell(index: Int): Boolean {
            val grid = mGridView ?: return false
            if (grid.visibility != View.VISIBLE) return false
            val center = grid.getCellCenter(index) ?: return false
            val success = clickAtCoordinates(center.first, center.second)
            hideGridOverlay()
            return success
        }

        fun longClickGridCell(index: Int): Boolean {
            val grid = mGridView ?: return false
            if (grid.visibility != View.VISIBLE) return false
            val center = grid.getCellCenter(index) ?: return false
            val success = longClickAtCoordinates(center.first, center.second)
            hideGridOverlay()
            return success
        }

        fun swipeFromGridCell(index: Int, direction: String): Boolean {
            val grid = mGridView ?: return false
            if (grid.visibility != View.VISIBLE) return false
            val center = grid.getCellCenter(index) ?: return false
            val cx = center.first
            val cy = center.second
            
            val success = when (direction.lowercase().trim()) {
                "up" -> swipeGesture(cx, cy, cx, cy - 300f)
                "down" -> swipeGesture(cx, cy, cx, cy + 300f)
                "left" -> swipeGesture(cx, cy, cx - 300f, cy)
                "right" -> swipeGesture(cx, cy, cx + 300f, cy)
                else -> false
            }
            hideGridOverlay()
            return success
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility Service Connected")
        sInstance = this
        isRunning.value = true
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        android.os.Handler(android.os.Looper.getMainLooper()).post {
            JarvisSpeechListener.getInstance(applicationContext).startListening()
        }

        serviceJob?.cancel()
        serviceJob = serviceScope.launch {
            combine(
                JarvisSpeechListener.isListening,
                JarvisEngine.isConversing,
                JarvisEngine.isTtsSpeaking,
                JarvisEngine.ambientLightColor
            ) { isListening, isConversing, isSpeaking, ambientColor ->
                val shouldShow = (isConversing && isListening) || isSpeaking
                Pair(shouldShow, ambientColor)
            }.collect { (shouldShow, colorHex) ->
                updateAmbientLight(shouldShow, colorHex)
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString()
            if (packageName != null) {
                Log.d(TAG, "Active App Changed: $packageName")
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility Service Interrupted")
        JarvisSpeechListener.getInstance()?.stopListening()
        JarvisSpeechListener.getInstance()?.close()
        serviceJob?.cancel()
        updateAmbientLight(false, "#00FFFF")
        sInstance = null
        isRunning.value = false
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val prefs = getSharedPreferences("jarvis_prefs", MODE_PRIVATE)
        val activationKeyEnabled = prefs.getBoolean("activation_key_enabled", false)
        if (activationKeyEnabled && event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                val listener = JarvisSpeechListener.getInstance(applicationContext)
                if (JarvisSpeechListener.isListening.value) {
                    listener.stopListening()
                    JarvisEngine.speak("Voice Access inactive.")
                } else {
                    listener.startListening()
                    JarvisEngine.speak("Listening.")
                }
            }
            return true
        }
        return super.onKeyEvent(event)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Accessibility Service Destroyed")
        JarvisSpeechListener.getInstance()?.stopListening()
        JarvisSpeechListener.getInstance()?.close()
        serviceJob?.cancel()
        updateAmbientLight(false, "#00FFFF")

        mHandler.post {
            mGridView?.let {
                try {
                    windowManager?.removeView(it)
                } catch (e: Exception) {}
                mGridView = null
            }
        }

        sInstance = null
        isRunning.value = false
    }

    private fun updateAmbientLight(show: Boolean, colorHex: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            val wm = windowManager ?: return@post
            if (show) {
                val colorInt = try {
                    android.graphics.Color.parseColor(colorHex)
                } catch (e: Exception) {
                    android.graphics.Color.CYAN
                }
                if (ambientLightView == null) {
                    ambientLightView = AmbientLightView(this).apply {
                        setColor(colorInt)
                        startPulsing()
                    }
                    val params = WindowManager.LayoutParams().apply {
                        width = WindowManager.LayoutParams.MATCH_PARENT
                        height = WindowManager.LayoutParams.MATCH_PARENT
                        type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                        flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        format = PixelFormat.TRANSLUCENT
                        gravity = Gravity.CENTER
                    }
                    try {
                        wm.addView(ambientLightView, params)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to add ambient light view", e)
                    }
                } else {
                    ambientLightView?.setColor(colorInt)
                }
            } else {
                ambientLightView?.let {
                    it.stopPulsing()
                    try {
                        wm.removeView(it)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to remove ambient light view", e)
                    }
                    ambientLightView = null
                }
            }
        }
    }
}

class AmbientLightView(context: android.content.Context) : View(context) {
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 24f
    }

    private var glowColor: Int = android.graphics.Color.CYAN
    private var pulseAlpha = 255
    private var pulseDirection = -5
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val pulseRunnable = object : Runnable {
        override fun run() {
            pulseAlpha += pulseDirection
            if (pulseAlpha <= 100) {
                pulseAlpha = 100
                pulseDirection = 5
            } else if (pulseAlpha >= 255) {
                pulseAlpha = 255
                pulseDirection = -5
            }
            invalidate()
            handler.postDelayed(this, 30)
        }
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        paint.maskFilter = android.graphics.BlurMaskFilter(16f, android.graphics.BlurMaskFilter.Blur.NORMAL)
    }

    fun setColor(color: Int) {
        glowColor = color
        invalidate()
    }

    fun startPulsing() {
        handler.removeCallbacks(pulseRunnable)
        handler.post(pulseRunnable)
    }

    fun stopPulsing() {
        handler.removeCallbacks(pulseRunnable)
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        paint.color = glowColor
        paint.alpha = pulseAlpha
        
        val margin = paint.strokeWidth / 2f
        // Left side vertical glow line
        canvas.drawLine(
            margin,
            0f,
            margin,
            height.toFloat(),
            paint
        )
        // Right side vertical glow line
        canvas.drawLine(
            width.toFloat() - margin,
            0f,
            width.toFloat() - margin,
            height.toFloat(),
            paint
        )
    }
}
