package com.example.service

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import com.example.MainActivity
import com.example.clipboard.MasterClipboardEngine
import com.example.model.ClipboardItem
import com.example.model.KeyboardSettings
import com.example.util.SettingsRepository

class KalkanIME : InputMethodService() {

    private lateinit var clipboardEngine: MasterClipboardEngine
    private lateinit var settingsRepo: SettingsRepository
    private var vibrator: Vibrator? = null
    private var audioManager: AudioManager? = null
    private var clipboardManager: ClipboardManager? = null

    private lateinit var rootContainer: LinearLayout
    private lateinit var headerBar: LinearLayout
    private lateinit var editToolbar: LinearLayout
    private lateinit var quickChipsScroll: HorizontalScrollView
    private lateinit var quickChipsContainer: LinearLayout
    private lateinit var keyboardViewContainer: FrameLayout

    // Panels
    private lateinit var mainKeyboardView: LinearLayout
    private lateinit var symbolsKeyboardView: LinearLayout
    private lateinit var symbols2KeyboardView: LinearLayout
    private lateinit var emojiKeyboardView: LinearLayout
    private lateinit var clipboardSheetView: LinearLayout

    private var isShifted = false
    private var isCapsLock = false
    private var currentMode = KeyboardMode.LETTERS
    private var lastShiftClickTime = 0L

    private enum class KeyboardMode {
        LETTERS, SYMBOLS, SYMBOLS2, EMOJI, CLIPBOARD
    }

    private val primaryClipListener = ClipboardManager.OnPrimaryClipChangedListener {
        captureClipboardContent("OnPrimaryClipChanged")
    }

    override fun onCreate() {
        super.onCreate()
        clipboardEngine = MasterClipboardEngine.getInstance(this)
        settingsRepo = SettingsRepository.getInstance(this)
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager

        clipboardManager?.addPrimaryClipChangedListener(primaryClipListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        clipboardManager?.removePrimaryClipChangedListener(primaryClipListener)
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        // Dual-trigger guarantee: also capture on keyboard opening
        captureClipboardContent("OnStartInputView")
        refreshQuickChips()
        showMode(KeyboardMode.LETTERS)
    }

    private fun captureClipboardContent(source: String) {
        try {
            val clip = clipboardManager?.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).coerceToText(this)?.toString()
                if (!text.isNullOrBlank()) {
                    clipboardEngine.addClip(text, sourceApp = source)
                }
            }
        } catch (e: Exception) {
            // Android 10+ background restriction fallback
        }
    }

    override fun onCreateInputView(): View {
        val theme = getThemeColors()

        rootContainer = LinearLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(theme.background)
            setPadding(dp(4), dp(4), dp(4), dp(8))
        }

        // 1. Header Bar (Quick action icons & Recent Clipboard Chips)
        buildHeaderBar()
        rootContainer.addView(headerBar)

        // 2. Keyboard View Container
        keyboardViewContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        rootContainer.addView(keyboardViewContainer)

        buildAllViews()
        showMode(KeyboardMode.LETTERS)

        return rootContainer
    }

    private fun buildHeaderBar() {
        val theme = getThemeColors()

        // Outer header: two rows — edit tools + mode/chips
        headerBar = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), dp(2), dp(2), dp(4))
        }

        // ------------------------------------------------------------------
        // ROW 1: Select All / Cut / Copy / Paste
        // ------------------------------------------------------------------
        editToolbar = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(40)
            )
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(2), dp(2), dp(2))
            background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(Color.parseColor("#121826"))
                setStroke(dp(1), theme.chipBorder)
            }
        }

        // Equal-weight edit action buttons across the full width
        editToolbar.addView(
            createEditToolButton(
                icon = "☑",
                label = "Seç",
                contentDescription = "Tümünü seç"
            ) { performSelectAll() }
        )
        editToolbar.addView(
            createEditToolButton(
                icon = "✂",
                label = "Kes",
                contentDescription = "Kes"
            ) { performCut() }
        )
        editToolbar.addView(
            createEditToolButton(
                icon = "⧉",
                label = "Kopya",
                contentDescription = "Kopyala"
            ) { performCopy() }
        )
        editToolbar.addView(
            createEditToolButton(
                icon = "📋",
                label = "Yapıştır",
                contentDescription = "Yapıştır"
            ) { performPaste() }
        )

        headerBar.addView(editToolbar)

        // ------------------------------------------------------------------
        // ROW 2: Mode buttons + quick clipboard chips + hide
        // ------------------------------------------------------------------
        val modeRow = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(42)
            ).apply {
                topMargin = dp(4)
            }
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), 0, dp(2), 0)
        }

        val actionButtons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        // Pano sheet toggle
        actionButtons.addView(
            createHeaderIconButton("🗂") {
                if (currentMode == KeyboardMode.CLIPBOARD) {
                    showMode(KeyboardMode.LETTERS)
                } else {
                    showMode(KeyboardMode.CLIPBOARD)
                }
            }
        )

        // Emoji toggle
        actionButtons.addView(
            createHeaderIconButton("😀") {
                if (currentMode == KeyboardMode.EMOJI) {
                    showMode(KeyboardMode.LETTERS)
                } else {
                    showMode(KeyboardMode.EMOJI)
                }
            }
        )

        // App / settings
        actionButtons.addView(
            createHeaderIconButton("🛡") {
                val intent = Intent(this@KalkanIME, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                startActivity(intent)
            }
        )

        // Hide keyboard
        actionButtons.addView(
            createHeaderIconButton("▼") {
                requestHideSelf(0)
            }
        )

        modeRow.addView(actionButtons)

        // Quick chips
        quickChipsScroll = HorizontalScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                1f
            ).apply {
                marginStart = dp(6)
            }
            isHorizontalScrollBarEnabled = false
        }

        quickChipsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        quickChipsScroll.addView(quickChipsContainer)
        modeRow.addView(quickChipsScroll)

        headerBar.addView(modeRow)

        refreshQuickChips()
    }

    /**
     * Compact labeled tool button used in the Select/Cut/Copy/Paste toolbar.
     * weight=1 so four actions share the full keyboard width evenly.
     */
    private fun createEditToolButton(
        icon: String,
        label: String,
        desc: String,
        onClick: () -> Unit
    ): LinearLayout {
        val theme = getThemeColors()
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                marginStart = dp(2)
                marginEnd = dp(2)
            }
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(theme.keySpecial)
            }
            isClickable = true
            isFocusable = true
            contentDescription = desc
            setOnClickListener {
                performFeedback()
                onClick()
            }
            // Pressed-state flash
            setOnTouchListener { v, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        v.alpha = 0.65f
                    }
                    android.view.MotionEvent.ACTION_UP,
                    android.view.MotionEvent.ACTION_CANCEL -> {
                        v.alpha = 1f
                    }
                }
                false
            }

            val iconView = TextView(this@KalkanIME).apply {
                text = icon
                textSize = 14f
                setTextColor(theme.keySpecialText)
                gravity = Gravity.CENTER
                includeFontPadding = false
            }
            addView(iconView)

            val labelView = TextView(this@KalkanIME).apply {
                text = label
                setTextColor(theme.keyText)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                includeFontPadding = false
                setPadding(dp(4), 0, 0, 0)
            }
            addView(labelView)
        }
    }

    // =========================================================================
    // EDIT ACTIONS: Select All / Cut / Copy / Paste
    // =========================================================================

    private fun performSelectAll() {
        val ic = currentInputConnection ?: return
        // Prefer framework context-menu action (works with most editors).
        val handled = ic.performContextMenuAction(android.R.id.selectAll)
        if (!handled) {
            // Fallback: expand selection across before+after cursor text.
            val before = ic.getTextBeforeCursor(MAX_EDIT_CHARS, 0)?.length ?: 0
            val after = ic.getTextAfterCursor(MAX_EDIT_CHARS, 0)?.length ?: 0
            ic.setSelection(0, before + after)
        }
    }

    private fun performCopy() {
        val ic = currentInputConnection ?: return
        val selected = ic.getSelectedText(0)?.toString()
        if (!selected.isNullOrEmpty()) {
            // Save into master clipboard first, then system clipboard via context action.
            clipboardEngine.addClip(selected, sourceApp = "KalkanIME-Copy")
            val handled = ic.performContextMenuAction(android.R.id.copy)
            if (!handled) {
                clipboardManager?.setPrimaryClip(ClipData.newPlainText("Kalkan", selected))
            }
            refreshQuickChips()
            return
        }
        // Nothing selected → select all then copy (common keyboard UX)
        performSelectAll()
        val allSelected = ic.getSelectedText(0)?.toString()
        if (!allSelected.isNullOrEmpty()) {
            clipboardEngine.addClip(allSelected, sourceApp = "KalkanIME-CopyAll")
            if (!ic.performContextMenuAction(android.R.id.copy)) {
                clipboardManager?.setPrimaryClip(ClipData.newPlainText("Kalkan", allSelected))
            }
            refreshQuickChips()
        }
    }

    private fun performCut() {
        val ic = currentInputConnection ?: return
        val selected = ic.getSelectedText(0)?.toString()
        if (!selected.isNullOrEmpty()) {
            clipboardEngine.addClip(selected, sourceApp = "KalkanIME-Cut")
            val handled = ic.performContextMenuAction(android.R.id.cut)
            if (!handled) {
                clipboardManager?.setPrimaryClip(ClipData.newPlainText("Kalkan", selected))
                ic.commitText("", 1) // delete selection
            }
            refreshQuickChips()
            return
        }
        // Nothing selected → select all then cut
        performSelectAll()
        val allSelected = ic.getSelectedText(0)?.toString()
        if (!allSelected.isNullOrEmpty()) {
            clipboardEngine.addClip(allSelected, sourceApp = "KalkanIME-CutAll")
            if (!ic.performContextMenuAction(android.R.id.cut)) {
                clipboardManager?.setPrimaryClip(ClipData.newPlainText("Kalkan", allSelected))
                ic.commitText("", 1)
            }
            refreshQuickChips()
        }
    }

    private fun performPaste() {
        val ic = currentInputConnection ?: return

        // 1) Try system paste (respects the real clipboard)
        val handled = ic.performContextMenuAction(android.R.id.paste)
        if (handled) {
            // Also capture whatever is on the system clipboard into master pano
            captureClipboardContent("KalkanIME-Paste")
            refreshQuickChips()
            return
        }

        // 2) Fallback: system clipboard text
        try {
            val clip = clipboardManager?.primaryClip
            val sysText = clip?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)
                ?.coerceToText(this)
                ?.toString()
            if (!sysText.isNullOrBlank()) {
                commitText(sysText)
                clipboardEngine.addClip(sysText, sourceApp = "KalkanIME-Paste")
                refreshQuickChips()
                return
            }
        } catch (_: Exception) {
            // ignore
        }

        // 3) Last resort: latest item from Master Clipboard engine
        val latest = clipboardEngine.itemsFlow.value.firstOrNull()
        if (latest != null) {
            commitText(latest.text)
        }
    }

    companion object {
        /** Guard for getTextBefore/AfterCursor fallbacks (Select All). */
        private const val MAX_EDIT_CHARS = 100_000
    }

    private fun refreshQuickChips() {
        if (!::quickChipsContainer.isInitialized) return
        quickChipsContainer.removeAllViews()

        val items = clipboardEngine.itemsFlow.value.take(6)
        if (items.isEmpty()) {
            val placeholder = TextView(this).apply {
                text = "Pano boş"
                setTextColor(Color.parseColor("#8892B0"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(dp(8), 0, dp(8), 0)
            }
            quickChipsContainer.addView(placeholder)
            return
        }

        val theme = getThemeColors()
        for (item in items) {
            val chip = TextView(this).apply {
                val preview = if (item.text.length > 25) item.text.take(25) + "..." else item.text
                text = "${if (item.isPinned) "⭐ " else ""}${preview.replace("\n", " ")}"
                setTextColor(theme.keyText)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(10), dp(4), dp(10), dp(4))

                background = GradientDrawable().apply {
                    cornerRadius = dp(14).toFloat()
                    setColor(theme.chipBackground)
                    setStroke(dp(1), theme.chipBorder)
                }

                setOnClickListener {
                    performFeedback()
                    commitText(item.text)
                }
            }

            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dp(6)
            }
            quickChipsContainer.addView(chip, params)
        }
    }

    private fun createHeaderIconButton(symbol: String, onClick: () -> Unit): TextView {
        val theme = getThemeColors()
        return TextView(this).apply {
            text = symbol
            textSize = 15f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(34), dp(34)).apply {
                marginEnd = dp(4)
            }
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(theme.keySpecial)
            }
            setOnClickListener {
                performFeedback()
                onClick()
            }
        }
    }

    private fun buildAllViews() {
        keyboardViewContainer.removeAllViews()

        mainKeyboardView = buildLettersKeyboard()
        symbolsKeyboardView = buildSymbolsKeyboard(page = 1)
        symbols2KeyboardView = buildSymbolsKeyboard(page = 2)
        emojiKeyboardView = buildEmojiKeyboard()
        clipboardSheetView = buildClipboardSheet()

        keyboardViewContainer.addView(mainKeyboardView)
        keyboardViewContainer.addView(symbolsKeyboardView)
        keyboardViewContainer.addView(symbols2KeyboardView)
        keyboardViewContainer.addView(emojiKeyboardView)
        keyboardViewContainer.addView(clipboardSheetView)
    }

    private fun showMode(mode: KeyboardMode) {
        currentMode = mode
        mainKeyboardView.visibility = if (mode == KeyboardMode.LETTERS) View.VISIBLE else View.GONE
        symbolsKeyboardView.visibility = if (mode == KeyboardMode.SYMBOLS) View.VISIBLE else View.GONE
        symbols2KeyboardView.visibility = if (mode == KeyboardMode.SYMBOLS2) View.VISIBLE else View.GONE
        emojiKeyboardView.visibility = if (mode == KeyboardMode.EMOJI) View.VISIBLE else View.GONE
        clipboardSheetView.visibility = if (mode == KeyboardMode.CLIPBOARD) View.VISIBLE else View.GONE

        if (mode == KeyboardMode.CLIPBOARD) {
            populateClipboardSheet()
        }
        refreshQuickChips()
    }

    // =========================================================================
    // LETTERS KEYBOARD (Turkish QWERTY with dedicated / long-press Turkish keys)
    // =========================================================================
    private fun buildLettersKeyboard(): LinearLayout {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val settings = settingsRepo.settingsFlow.value

        // Optional Number Row
        if (settings.showNumberRow) {
            val numRow = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
            layout.addView(createRow(numRow, keyHeight = dp(38), isNumberRow = true))
        }

        // Turkish QWERTY Rows
        // Row 1: Q W E R T Y U I O P Ğ Ü
        val row1 = if (settings.turkishSpecialKeys) {
            listOf("q", "w", "e", "r", "t", "y", "u", "ı", "o", "p", "ğ", "ü")
        } else {
            listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
        }
        layout.addView(createRow(row1))

        // Row 2: A S D F G H J K L Ş İ
        val row2 = if (settings.turkishSpecialKeys) {
            listOf("a", "s", "d", "f", "g", "h", "j", "k", "l", "ş", "i")
        } else {
            listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")
        }
        layout.addView(createRow(row2))

        // Row 3: Shift + Z X C V B N M Ö Ç + Backspace
        val row3Letters = if (settings.turkishSpecialKeys) {
            listOf("z", "x", "c", "v", "b", "n", "m", "ö", "ç")
        } else {
            listOf("z", "x", "c", "v", "b", "n", "m")
        }
        layout.addView(createRowWithModifiers(row3Letters))

        // Bottom Row: ?123, Emoji, Space, '.', Enter
        layout.addView(createBottomRow())

        return layout
    }

    private fun createRow(keys: List<String>, keyHeight: Int = dp(46), isNumberRow: Boolean = false): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(4)
            }
            gravity = Gravity.CENTER
        }

        for (key in keys) {
            val keyView = createKeyView(key, keyHeight, isSpecial = isNumberRow) {
                val charToSend = if (isShifted || isCapsLock) key.uppercase() else key.lowercase()
                commitText(charToSend)
                if (isShifted && !isCapsLock) {
                    toggleShift(false)
                }
            }
            row.addView(keyView)
        }
        return row
    }

    private fun createRowWithModifiers(letters: List<String>): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(4)
            }
            gravity = Gravity.CENTER
        }

        // Shift Key
        val shiftKey = createSpecialKeyView(if (isCapsLock) "⇪" else if (isShifted) "⬆" else "⇧", weight = 1.4f) {
            val now = System.currentTimeMillis()
            if (now - lastShiftClickTime < 350) {
                // Double tap -> CapsLock
                isCapsLock = !isCapsLock
                isShifted = isCapsLock
            } else {
                if (isCapsLock) {
                    isCapsLock = false
                    isShifted = false
                } else {
                    isShifted = !isShifted
                }
            }
            lastShiftClickTime = now
            updateKeyboardLabels()
        }
        row.addView(shiftKey)

        // Middle Letter Keys
        for (key in letters) {
            val keyView = createKeyView(key, dp(46), isSpecial = false) {
                val charToSend = if (isShifted || isCapsLock) key.uppercase() else key.lowercase()
                commitText(charToSend)
                if (isShifted && !isCapsLock) {
                    toggleShift(false)
                }
            }
            row.addView(keyView)
        }

        // Backspace Key
        val backspaceKey = createSpecialKeyView("⌫", weight = 1.4f) {
            handleBackspace()
        }
        row.addView(backspaceKey)

        return row
    }

    private fun createBottomRow(): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            gravity = Gravity.CENTER
        }

        // Symbols switch key '?123'
        val symKey = createSpecialKeyView("?123", weight = 1.5f) {
            showMode(KeyboardMode.SYMBOLS)
        }
        row.addView(symKey)

        // Comma key ','
        val commaKey = createKeyView(",", dp(46), isSpecial = false, weight = 1.0f) {
            commitText(",")
        }
        row.addView(commaKey)

        // Space bar
        val spaceKey = createSpaceKeyView(weight = 4.5f)
        row.addView(spaceKey)

        // Dot key '.'
        val dotKey = createKeyView(".", dp(46), isSpecial = false, weight = 1.0f) {
            commitText(".")
        }
        row.addView(dotKey)

        // Enter / Action key
        val enterKey = createEnterKeyView(weight = 1.8f)
        row.addView(enterKey)

        return row
    }

    // =========================================================================
    // SYMBOLS KEYBOARDS
    // =========================================================================
    private fun buildSymbolsKeyboard(page: Int): LinearLayout {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        if (page == 1) {
            // Row 1: 1 2 3 4 5 6 7 8 9 0
            layout.addView(createRow(listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")))
            // Row 2: @ # $ % & - + ( ) /
            layout.addView(createRow(listOf("@", "#", "₺", "$", "%", "&", "-", "+", "(", ")")))
            // Row 3: =< toggle, * " ' : ; ! ? Backspace
            val row3 = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(4) }
            }
            val switch2Btn = createSpecialKeyView("=\\<", weight = 1.5f) {
                showMode(KeyboardMode.SYMBOLS2)
            }
            row3.addView(switch2Btn)

            val symbolsRow3 = listOf("*", "\"", "'", ":", ";", "!", "?", "/")
            for (s in symbolsRow3) {
                row3.addView(createKeyView(s, dp(46)) { commitText(s) })
            }

            val delBtn = createSpecialKeyView("⌫", weight = 1.5f) { handleBackspace() }
            row3.addView(delBtn)
            layout.addView(row3)
        } else {
            // Symbols Page 2: Math & Code symbols
            // Row 1: ~ ` | • √ π ÷ × ¶ ∆
            layout.addView(createRow(listOf("~", "`", "|", "•", "√", "π", "÷", "×", "¶", "∆")))
            // Row 2: £ € ¥ ¢ ^ ° = { } \
            layout.addView(createRow(listOf("£", "€", "¥", "¢", "^", "°", "=", "{", "}", "\\")))
            // Row 3: ?123 switch, % © ® ™ [ ] < > Backspace
            val row3 = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(4) }
            }
            val switch1Btn = createSpecialKeyView("?123", weight = 1.5f) {
                showMode(KeyboardMode.SYMBOLS)
            }
            row3.addView(switch1Btn)

            val symbolsRow3Page2 = listOf("©", "®", "™", "[", "]", "<", ">", "_")
            for (s in symbolsRow3Page2) {
                row3.addView(createKeyView(s, dp(46)) { commitText(s) })
            }

            val delBtn = createSpecialKeyView("⌫", weight = 1.5f) { handleBackspace() }
            row3.addView(delBtn)
            layout.addView(row3)
        }

        // Bottom row for symbols
        val bottomRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val abcBtn = createSpecialKeyView("ABC", weight = 1.5f) {
            showMode(KeyboardMode.LETTERS)
        }
        bottomRow.addView(abcBtn)

        val comma = createKeyView(",", dp(46), weight = 1.0f) { commitText(",") }
        bottomRow.addView(comma)

        val space = createSpaceKeyView(weight = 4.5f)
        bottomRow.addView(space)

        val dot = createKeyView(".", dp(46), weight = 1.0f) { commitText(".") }
        bottomRow.addView(dot)

        val enter = createEnterKeyView(weight = 1.8f)
        bottomRow.addView(enter)

        layout.addView(bottomRow)
        return layout
    }

    // =========================================================================
    // EMOJI KEYBOARD
    // =========================================================================
    private fun buildEmojiKeyboard(): LinearLayout {
        val theme = getThemeColors()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(240)
            )
        }

        // Category Tabs
        val tabScroll = HorizontalScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(36)
            )
            isHorizontalScrollBarEnabled = false
        }
        val tabsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        tabScroll.addView(tabsLayout)
        layout.addView(tabScroll)

        // Emoji grid container
        val emojiScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        val emojiContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        emojiScroll.addView(emojiContent)
        layout.addView(emojiScroll)

        val emojiCategories = listOf(
            "😀" to listOf("😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "🥲", "🥹", "☺️", "😊", "😇", "🙂", "🙃", "😉", "😌", "😍", "🥰", "😘", "😗", "😙", "😚", "😋", "😛", "😝", "😜", "🤪", "🤨", "🧐", "🤓", "😎", "🥸", "🤩", "🥳", "😏", "😒", "😞", "😔", "😟", "😕", "🙁", "☹️", "😣", "😖", "😫", "😩", "🥺", "😢", "😭", "😮‍💨", "😤", "😠", "😡", "🤬", "🤯", "😳", "🥵", "🥶", "😱", "😨", "😰", "😥", "😓", "🫣", "🤗", "🫡", "🤫", "🫠", "🤥", "😶", "😐", "😑", "😬", "🫨", "🙄", "😯", "😦", "😧", "😮", "😲", "🥱", "😴", "🤤", "😪", "😵", "😵‍💫", "🫥", "🤐", "🥴", "🤢", "🤮", "🤧", "😷", "🤒", "🤕", "🤑", "🤠", "😈", "👿", "👺", "🤡", "💩", "👻", "💀", "☠️", "👽", "👾", "🤖"),
            "👍" to listOf("👍", "👎", "👌", "🤌", "🤏", "✌️", "🤞", "🫰", "🤟", "🤘", "🤙", "👈", "👉", "👆", "🖕", "👇", "☝️", "🫵", "👋", "🤚", "🖐️", "✋", "🖖", "🫱", "🫲", "🤝", "👏", "🙌", "👐", "🤲", "🤜", "🤛", "✊", "👊", "💪", "🦾", "🦿", "🦵", "🦶", "👂", "🦻", "👃", "🫀", "🫁", "🧠", "👀", "👁️", "👅", "👄", "🫦", "💋", "🩸"),
            "❤️" to listOf("❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "❤️‍🔥", "❤️‍🩹", "💔", "❣️", "💕", "💞", "💓", "💗", "💖", "💘", "💝", "💟", "💌", "💐", "🌸", "💮", "🪷", "🏵️", "🌹", "🥀", "🌺", "🌻", "🌼", "🌷", "🌱", "🪴", "🌲", "🌳", "🌴", "🌵", "🌾", "🌿", "☘️", "🍀", "🍁", "🍂", "🍃"),
            "🔥" to listOf("🔥", "⚡", "✨", "⭐", "🌟", "💫", "💥", "🚀", "🛡️", "⚔️", "💎", "👑", "🎯", "🏆", "🥇", "🥈", "🥉", "💰", "💵", "💸", "💳", "📈", "📉", "💻", "📱", "⌨️", "🖥️", "🔒", "🔑", "🛠️", "⚙️", "🔋", "💡", "📡", "🛰️", "🧪", "🧬", "🔮", "🧿", "🚩", "🏁", "🚀", "🪐", "🌍", "🌎", "🌏"),
            "🐶" to listOf("🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐨", "🐯", "🦁", "🐮", "🐷", "🐸", "🐵", "🐔", "🐧", "🐦", "🐤", "🦆", "🦅", "🦉", "🦇", "🐺", "🐗", "🐴", "🦄", "🐝", "🪱", "🐛", "🦋", "🐌", "🐞", "🐜", "🪰", "🪲", "🪳", "🦟", "🦗", "🕷️", "🦂", "🐢", "🐍", "🦎", "🦖", "🦕", "🐙", "🦑", "🦐", "🦞", "🦀", "🐡", "🐠", "🐟", "🐬", "🐳", "🦈", "🦭", "🐊", "🐅", "🐆", "🦓", "🦍", "🦧", "🦣", "🐘", "🦛", "🦏", "🐪", "🐫", "🦒", "🦘", "🦬", "🐃", "🐂", "🐄", "🐎", "🐖", "🐏", "🐑", "🦙", "🐐", "🦌", "🐕", "🐩", "🦮", "🐕‍🦺", "🐈", "🐈‍⬛", "🪶", "🐓", "🦃", "🦤", "🦚", "🦜", "🦢", "🦩", "🕊️", "🐇", "🦝", "🦨", "🦡", "🦦", "🦥", "🐁", "🐀", "🐿️", "🦔")
        )

        fun loadCategory(emojis: List<String>) {
            emojiContent.removeAllViews()
            var currentRow: LinearLayout? = null
            val perRow = 8
            emojis.forEachIndexed { index, emoji ->
                if (index % perRow == 0) {
                    currentRow = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                        gravity = Gravity.CENTER
                    }
                    emojiContent.addView(currentRow)
                }

                val emojiBtn = TextView(this).apply {
                    text = emoji
                    textSize = 22f
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f)
                    setOnClickListener {
                        performFeedback()
                        commitText(emoji)
                    }
                }
                currentRow?.addView(emojiBtn)
            }
        }

        // Initialize category buttons
        emojiCategories.forEachIndexed { index, pair ->
            val catTab = TextView(this).apply {
                text = pair.first
                textSize = 18f
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(4), dp(12), dp(4))
                setOnClickListener {
                    performFeedback()
                    loadCategory(pair.second)
                }
            }
            tabsLayout.addView(catTab)
        }

        // Load first category by default
        loadCategory(emojiCategories[0].second)

        // Bottom Switch Bar
        val bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val abcBtn = createSpecialKeyView("ABC", weight = 2f) {
            showMode(KeyboardMode.LETTERS)
        }
        bottomBar.addView(abcBtn)

        val space = createSpaceKeyView(weight = 5f)
        bottomBar.addView(space)

        val backspace = createSpecialKeyView("⌫", weight = 2f) {
            handleBackspace()
        }
        bottomBar.addView(backspace)

        layout.addView(bottomBar)

        return layout
    }

    // =========================================================================
    // EMBEDDED MASTER CLIPBOARD SHEET (Search, View, 1-Tap Paste, Pin/Unpin)
    // =========================================================================
    private fun buildClipboardSheet(): LinearLayout {
        val theme = getThemeColors()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(250)
            )
            setBackgroundColor(theme.background)
        }

        // Top Search & Close Bar
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(40)
            ).apply {
                bottomMargin = dp(4)
            }
        }

        val searchInput = EditText(this).apply {
            hint = "🔍 Panoda Ara (Sınırsız Kayıt)..."
            setHintTextColor(Color.parseColor("#8892B0"))
            setTextColor(theme.keyText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(dp(8), dp(4), dp(8), dp(4))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(theme.keySpecial)
                setStroke(dp(1), theme.chipBorder)
            }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    populateClipboardSheet(s?.toString() ?: "")
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        topBar.addView(searchInput)

        val closeBtn = TextView(this).apply {
            text = "✕ Kapat"
            setTextColor(Color.parseColor("#00E5FF"))
            typeface = Typeface.DEFAULT_BOLD
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(4), dp(10), dp(4))
            setOnClickListener {
                performFeedback()
                showMode(KeyboardMode.LETTERS)
            }
        }
        topBar.addView(closeBtn)
        layout.addView(topBar)

        // Scrollable List of Clips
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        val itemsList = LinearLayout(this).apply {
            tag = "clipboard_items_container"
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(itemsList)
        layout.addView(scroll)

        return layout
    }

    private fun populateClipboardSheet(query: String = "") {
        val container = clipboardSheetView.findViewWithTag<LinearLayout>("clipboard_items_container") ?: return
        container.removeAllViews()

        val allClips = clipboardEngine.itemsFlow.value
        val filtered = if (query.isBlank()) {
            allClips
        } else {
            allClips.filter { it.text.contains(query, ignoreCase = true) }
        }

        if (filtered.isEmpty()) {
            val emptyNotice = TextView(this).apply {
                text = if (query.isBlank()) "Panoda henüz kayıt yok." else "'$query' ile eşleşen öğe bulunamadı."
                setTextColor(Color.parseColor("#8892B0"))
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(24), dp(16), dp(24))
            }
            container.addView(emptyNotice)
            return
        }

        val theme = getThemeColors()
        for (item in filtered) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(6)
                }
                background = GradientDrawable().apply {
                    cornerRadius = dp(8).toFloat()
                    setColor(if (item.isPinned) Color.parseColor("#152238") else theme.chipBackground)
                    setStroke(dp(1), if (item.isPinned) Color.parseColor("#00E5FF") else theme.chipBorder)
                }
                setPadding(dp(10), dp(8), dp(10), dp(8))
            }

            // Top meta row
            val metaRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            val dateText = TextView(this).apply {
                text = "${item.formattedDate} • ${item.charCount} Karakter"
                setTextColor(Color.parseColor("#00E5FF"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            metaRow.addView(dateText)

            val pinBtn = TextView(this).apply {
                text = if (item.isPinned) "⭐ Sabit" else "☆ Sabitle"
                setTextColor(if (item.isPinned) Color.parseColor("#FFD700") else Color.parseColor("#8892B0"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setPadding(dp(6), dp(2), dp(6), dp(2))
                setOnClickListener {
                    performFeedback()
                    clipboardEngine.togglePin(item.id)
                    populateClipboardSheet(query)
                }
            }
            metaRow.addView(pinBtn)
            card.addView(metaRow)

            // Text Content (preview up to 4 lines with full click paste)
            val clipText = TextView(this).apply {
                text = item.text
                maxLines = 4
                setTextColor(theme.keyText)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(0, dp(4), 0, dp(2))
            }
            card.addView(clipText)

            // Tap card to paste
            card.setOnClickListener {
                performFeedback()
                commitText(item.text)
                showMode(KeyboardMode.LETTERS)
            }

            container.addView(card)
        }
    }

    // =========================================================================
    // KEY VIEW BUILDERS & FEEDBACK
    // =========================================================================
    private fun createKeyView(
        label: String,
        heightDp: Int = dp(46),
        isSpecial: Boolean = false,
        weight: Float = 1.0f,
        onClick: () -> Unit
    ): TextView {
        val theme = getThemeColors()
        return TextView(this).apply {
            text = label
            tag = "key_$label"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(theme.keyText)
            typeface = Typeface.DEFAULT

            val keyDrawable = GradientDrawable().apply {
                cornerRadius = dp(6).toFloat()
                setColor(if (isSpecial) theme.keySpecial else theme.keyNormal)
            }
            background = keyDrawable

            layoutParams = LinearLayout.LayoutParams(0, heightDp, weight).apply {
                marginStart = dp(2)
                marginEnd = dp(2)
            }

            setOnClickListener {
                performFeedback()
                onClick()
            }

            // Long press support for popups (e.g. accented letters, Turkish chars)
            setOnLongClickListener {
                showLongPressPopup(this, label)
                true
            }
        }
    }

    private fun createSpecialKeyView(
        label: String,
        heightDp: Int = dp(46),
        weight: Float = 1.0f,
        onClick: () -> Unit
    ): TextView {
        val theme = getThemeColors()
        return TextView(this).apply {
            text = label
            tag = "key_$label"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(theme.keySpecialText)
            typeface = Typeface.DEFAULT_BOLD

            val keyDrawable = GradientDrawable().apply {
                cornerRadius = dp(6).toFloat()
                setColor(theme.keySpecial)
            }
            background = keyDrawable

            layoutParams = LinearLayout.LayoutParams(0, heightDp, weight).apply {
                marginStart = dp(2)
                marginEnd = dp(2)
            }

            setOnClickListener {
                performFeedback()
                onClick()
            }
        }
    }

    private fun createSpaceKeyView(weight: Float = 4.5f): TextView {
        val theme = getThemeColors()
        return TextView(this).apply {
            text = "Türkçe (Q)"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#8892B0"))

            val keyDrawable = GradientDrawable().apply {
                cornerRadius = dp(6).toFloat()
                setColor(theme.keyNormal)
            }
            background = keyDrawable

            layoutParams = LinearLayout.LayoutParams(0, dp(46), weight).apply {
                marginStart = dp(2)
                marginEnd = dp(2)
            }

            setOnClickListener {
                performFeedback()
                commitText(" ")
            }
        }
    }

    private fun createEnterKeyView(weight: Float = 1.8f): TextView {
        return TextView(this).apply {
            text = "⏎"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD

            val keyDrawable = GradientDrawable().apply {
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#00E5FF")) // Cyber Cyan Accent
            }
            background = keyDrawable

            layoutParams = LinearLayout.LayoutParams(0, dp(46), weight).apply {
                marginStart = dp(2)
                marginEnd = dp(2)
            }

            setOnClickListener {
                performFeedback()
                handleEnter()
            }
        }
    }

    private fun showLongPressPopup(anchor: View, baseKey: String) {
        val alternates = when (baseKey.lowercase()) {
            "a" -> listOf("â", "á", "à", "ä", "ã", "å")
            "e" -> listOf("é", "è", "ê", "ë", "ē")
            "i", "ı" -> listOf("ı", "i", "î", "í", "ì", "ï")
            "o" -> listOf("ö", "ô", "ó", "ò", "õ")
            "u" -> listOf("ü", "û", "ú", "ù")
            "c" -> listOf("ç")
            "s" -> listOf("ş", "ß")
            "g" -> listOf("ğ")
            "1" -> listOf("¹", "½", "⅓")
            "2" -> listOf("²", "⅔")
            "3" -> listOf("³", "¾")
            "t", "l" -> listOf("₺")
            "$" -> listOf("₺", "€", "£", "¥", "₽")
            else -> emptyList()
        }

        if (alternates.isEmpty()) return
        performFeedback()

        val theme = getThemeColors()
        val popupLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(6), dp(4), dp(6), dp(4))
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#161B22"))
                setStroke(dp(1), Color.parseColor("#00E5FF"))
            }
        }

        val popupWindow = PopupWindow(
            popupLayout,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )

        for (alt in alternates) {
            val displayed = if (isShifted || isCapsLock) alt.uppercase() else alt.lowercase()
            val btn = TextView(this).apply {
                text = displayed
                textSize = 18f
                setTextColor(Color.WHITE)
                setPadding(dp(10), dp(6), dp(10), dp(6))
                setOnClickListener {
                    performFeedback()
                    commitText(displayed)
                    popupWindow.dismiss()
                }
            }
            popupLayout.addView(btn)
        }

        popupWindow.showAsDropDown(anchor, 0, -dp(90))
    }

    private fun toggleShift(state: Boolean) {
        isShifted = state
        updateKeyboardLabels()
    }

    private fun updateKeyboardLabels() {
        if (!::mainKeyboardView.isInitialized) return
        updateChildLabels(mainKeyboardView)
    }

    private fun updateChildLabels(view: View) {
        if (view is TextView && view.tag != null && view.tag.toString().startsWith("key_")) {
            val key = view.tag.toString().removePrefix("key_")
            if (key.length == 1 && key[0].isLetter()) {
                view.text = if (isShifted || isCapsLock) key.uppercase() else key.lowercase()
            } else if (key == "⇧" || key == "⬆" || key == "⇪") {
                view.text = if (isCapsLock) "⇪" else if (isShifted) "⬆" else "⇧"
            }
        } else if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                updateChildLabels(view.getChildAt(i))
            }
        }
    }

    private fun commitText(text: String) {
        val ic = currentInputConnection ?: return
        ic.commitText(text, 1)
    }

    private fun handleBackspace() {
        val ic = currentInputConnection ?: return
        val selected = ic.getSelectedText(0)
        if (selected != null && selected.isNotEmpty()) {
            ic.commitText("", 1)
        } else {
            ic.deleteSurroundingText(1, 0)
        }
    }

    private fun handleEnter() {
        val ic = currentInputConnection ?: return
        val editorInfo = currentInputEditorInfo
        if (editorInfo != null) {
            val action = editorInfo.imeOptions and EditorInfo.IME_MASK_ACTION
            if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
                ic.performEditorAction(action)
                return
            }
        }
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
    }

    private fun performFeedback() {
        val settings = settingsRepo.settingsFlow.value
        if (settings.hapticFeedback && vibrator?.hasVibrator() == true) {
            val duration = settings.hapticDuration.toLong().coerceIn(5L, 100L)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(duration)
            }
        }
        if (settings.soundFeedback) {
            audioManager?.playSoundEffect(AudioManager.FX_KEY_CLICK, 0.5f)
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun getThemeColors(): ThemeColorSet {
        return ThemeColorSet(
            background = Color.parseColor("#090D16"),
            keyNormal = Color.parseColor("#1B2234"),
            keySpecial = Color.parseColor("#26304A"),
            keyText = Color.parseColor("#E6EDF3"),
            keySpecialText = Color.parseColor("#00E5FF"),
            chipBackground = Color.parseColor("#161F33"),
            chipBorder = Color.parseColor("#2F3D5E")
        )
    }

    data class ThemeColorSet(
        val background: Int,
        val keyNormal: Int,
        val keySpecial: Int,
        val keyText: Int,
        val keySpecialText: Int,
        val chipBackground: Int,
        val chipBorder: Int
    )
}
