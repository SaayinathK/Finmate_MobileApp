package com.example.financetracker.ui

import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.financetracker.R
import com.example.financetracker.utils.PlannerRules
import com.example.financetracker.utils.ReportPeriod
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.time.LocalDate
import java.util.Locale

abstract class WalletScreen : AppCompatActivity() {
    val dark get() = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
    val ink get() = androidx.core.content.ContextCompat.getColor(this, R.color.ink_primary)
    val muted get() = androidx.core.content.ContextCompat.getColor(this, R.color.ink_secondary)
    val blue get() = androidx.core.content.ContextCompat.getColor(this, R.color.blue_700)
    val actionBlue = Color.rgb(0, 105, 224)
    val green get() = if (dark) Color.rgb(92, 219, 175) else Color.rgb(16, 130, 101)
    val red get() = if (dark) Color.rgb(255, 145, 162) else Color.rgb(196, 67, 87)
    val surface get() = androidx.core.content.ContextCompat.getColor(this, R.color.wallet_surface)
    val backgroundColor get() = androidx.core.content.ContextCompat.getColor(this, R.color.app_background)
    val border get() = androidx.core.content.ContextCompat.getColor(this, R.color.wallet_border)
    val softBlue get() = androidx.core.content.ContextCompat.getColor(this, R.color.wallet_soft_blue)
    fun soft(color: Int): Int = if (dark && Color.red(color) > 200 && Color.green(color) > 200 && Color.blue(color) > 200)
        androidx.core.graphics.ColorUtils.blendARGB(surface, color, 0.07f) else color
    lateinit var content: LinearLayout
    private var lastScroll: ScrollView? = null
    private var lastTitle = ""
    private var entranceShown = false
    fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    fun shape(color: Int, radius: Int = 14) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    fun text(value: String, size: Int = 14, color: Int = ink, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size.toFloat(); setTextColor(color)
        typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
        includeFontPadding = false
        setPadding(0, dp(2), 0, dp(2))
    }
    fun gap(parent: LinearLayout = content, height: Int = 8) { parent.addView(View(this), LinearLayout.LayoutParams(1, dp(height))) }
    fun card(parent: LinearLayout = content, color: Int = surface): LinearLayout = column().apply {
        background = shape(soft(color)).apply { setStroke(dp(1), if (color == surface || color == Color.WHITE) border else Color.TRANSPARENT) }
        setPadding(dp(12), dp(12), dp(12), dp(12))
        parent.addView(this, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
    }
    fun button(label: String, parent: LinearLayout = content, outlined: Boolean = false, action: () -> Unit): MaterialButton {
        return MaterialButton(this, null, if (outlined) com.google.android.material.R.attr.materialButtonOutlinedStyle else com.google.android.material.R.attr.materialButtonStyle).apply {
            text = label; textSize = 14f; letterSpacing = 0f; isAllCaps = false; cornerRadius = dp(10); minHeight = dp(48)
            minimumHeight = dp(48); insetTop = dp(3); insetBottom = dp(3)
            setPadding(dp(12), dp(4), dp(12), dp(4))
            if (outlined) { setTextColor(blue); strokeColor = android.content.res.ColorStateList.valueOf(border); strokeWidth = dp(1) }
            else { backgroundTintList = android.content.res.ColorStateList.valueOf(actionBlue); setTextColor(Color.WHITE) }
            setOnClickListener { action() }; parent.addView(this, LinearLayout.LayoutParams(-1, -2))
            WalletMotion.press(this)
        }
    }
    fun field(parent: LinearLayout, label: String, value: String = "", numeric: Boolean = false, multiline: Boolean = false): TextInputEditText {
        val wrapper = TextInputLayout(this).apply {
            hint = label; boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            boxBackgroundColor = surface; boxStrokeColor = blue
            defaultHintTextColor = android.content.res.ColorStateList.valueOf(muted)
            setBoxCornerRadii(dp(10).toFloat(), dp(10).toFloat(), dp(10).toFloat(), dp(10).toFloat())
        }
        val input = TextInputEditText(wrapper.context).apply {
            id = View.generateViewId(); setText(value); setTextColor(ink); textSize = 15f
            inputType = if (numeric) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                else InputType.TYPE_CLASS_TEXT or (if (multiline) InputType.TYPE_TEXT_FLAG_MULTI_LINE else InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
            minHeight = dp(48); setPadding(dp(12), dp(12), dp(12), dp(10)); if (multiline) minLines = 2
        }
        wrapper.addView(input, LinearLayout.LayoutParams(-1, -2))
        parent.addView(wrapper, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6); bottomMargin = dp(2) })
        return input
    }
    fun choices(parent: LinearLayout, options: List<String>, selected: Int = 0, changed: (Int) -> Unit): ChipGroup {
        return ChipGroup(this).apply {
            isSingleSelection = true; isSelectionRequired = true
            chipSpacingHorizontal = dp(4); chipSpacingVertical = 0
            options.forEachIndexed { index, label ->
                addView(Chip(this@WalletScreen).apply {
                    id = View.generateViewId(); text = label; isCheckable = true; isChecked = index == selected
                    minHeight = dp(48); setEnsureMinTouchTargetSize(true)
                    textSize = 13f; isCheckedIconVisible = false; chipCornerRadius = dp(8).toFloat()
                    chipStartPadding = dp(8).toFloat(); chipEndPadding = dp(8).toFloat()
                    textStartPadding = dp(3).toFloat(); textEndPadding = dp(3).toFloat()
                    val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
                    chipBackgroundColor = android.content.res.ColorStateList(states, intArrayOf(softBlue, soft(Color.rgb(239,243,248))))
                    setTextColor(android.content.res.ColorStateList(states, intArrayOf(blue, muted)))
                    checkedIconTint = android.content.res.ColorStateList.valueOf(blue)
                    setOnCheckedChangeListener { _, checked -> if (checked) changed(index) }
                    WalletMotion.press(this)
                })
            }; parent.addView(this)
        }
    }
    fun section(title: String, subtitle: String? = null, parent: LinearLayout = content) {
        parent.addView(text(title, 17, ink, true)); subtitle?.takeIf { it.isNotBlank() }?.let { parent.addView(text(it, 12, muted)) }; gap(parent, 4)
    }
    fun actions(parent: LinearLayout = content, vararg items: Pair<String, () -> Unit>) {
        val actions = row()
        items.forEachIndexed { index, (label, action) ->
            button(label, actions, true, action).layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply {
                if (index > 0) marginStart = dp(6)
            }
        }
        parent.addView(actions)
    }
    fun entry(parent: LinearLayout = content, title: String, detail: String, amount: String, incoming: Boolean, action: () -> Unit) {
        val item = card(parent)
        val line = row()
        line.addView(badge(if (incoming) R.drawable.ic_arrow_in else R.drawable.ic_arrow_out, if (incoming) green else red, if (incoming) Color.rgb(231,247,241) else Color.rgb(255,239,241)), LinearLayout.LayoutParams(dp(32), dp(32)).apply { marginEnd = dp(10) })
        line.addView(column().apply {
            addView(text(title, 15, ink, true)); addView(text(detail, 11, muted))
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(8) })
        line.addView(text(amount, 14, if (incoming) green else red, true))
        item.addView(line)
        item.minimumHeight = dp(56); item.isFocusable = true
        item.contentDescription = "$title, $detail, $amount. Open details"
        val ripple = android.util.TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)
        item.foreground = androidx.core.content.ContextCompat.getDrawable(this, ripple.resourceId)
        item.setOnClickListener { action() }
        WalletMotion.press(item)
    }
    fun badge(resource: Int, tint: Int = blue, backgroundColor: Int = Color.rgb(230,239,255)): ImageView = ImageView(this).apply {
        setImageResource(resource); setColorFilter(tint); background = shape(soft(backgroundColor), 10)
        setPadding(dp(6), dp(6), dp(6), dp(6)); contentDescription = null
    }
    fun illustration(resource: Int, width: Int = 80, height: Int = width) = ImageView(this).apply {
        setImageResource(resource); scaleType = ImageView.ScaleType.FIT_CENTER; contentDescription = null
        layoutParams = LinearLayout.LayoutParams(dp(width), dp(height))
    }
    fun guide(gesture: WalletGuideView.Gesture, size: Int = 112) = WalletGuideView(this).apply {
        this.gesture = gesture
        layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
    }
    fun guideMessage(title: String, detail: String, gesture: WalletGuideView.Gesture, parent: LinearLayout = content) {
        val panel = card(parent, softBlue)
        val line = row()
        line.addView(guide(gesture), LinearLayout.LayoutParams(dp(112), dp(112)).apply { marginEnd = dp(8) })
        line.addView(column().apply {
            addView(text(title, 17, ink, true))
            if (detail.isNotBlank()) addView(text(detail, 13, muted))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        panel.addView(line)
    }
    fun emptyState(resource: Int, title: String, detail: String = "", parent: LinearLayout = content) {
        val gesture = when (resource) {
            R.drawable.art_wallet -> WalletGuideView.Gesture.WELCOME
            R.drawable.art_checklist -> WalletGuideView.Gesture.PLAN
            R.drawable.swallet_logo -> WalletGuideView.Gesture.ENCOURAGE
            else -> null
        }
        if (gesture != null) { guideMessage(title, detail, gesture, parent); return }
        val empty = card(parent)
        val line = row()
        line.addView(illustration(resource, 72), LinearLayout.LayoutParams(dp(72), dp(72)).apply { marginEnd = dp(12) })
        line.addView(column().apply { addView(text(title, 16, ink, true)); if (detail.isNotBlank()) addView(text(detail, 12, muted)) }, LinearLayout.LayoutParams(0, -2, 1f))
        empty.addView(line)
    }
    fun money(amount: Double): String {
        val currency = getSharedPreferences("finmate_settings", MODE_PRIVATE).getString("currency", "LKR") ?: "LKR"
        return "$currency ${String.format(Locale.getDefault(), "%,.2f", amount)}"
    }
    fun open(clazz: Class<*>) { startActivity(Intent(this, clazz)) }
    fun toast(message: String) { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
    fun pickDate(date: LocalDate, selected: (LocalDate) -> Unit) {
        DatePickerDialog(this, { _, y, m, d -> selected(LocalDate.of(y, m + 1, d)) }, date.year, date.monthValue - 1, date.dayOfMonth).show()
    }
    fun iconButton(resource: Int, label: String, action: () -> Unit) = androidx.appcompat.widget.AppCompatImageButton(this).apply {
        setImageResource(resource); imageTintList = android.content.res.ColorStateList.valueOf(blue)
        contentDescription = label; androidx.appcompat.widget.TooltipCompat.setTooltipText(this, label)
        background = android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(Color.rgb(216,231,252)), shape(softBlue, 10), null)
        setPadding(dp(13), dp(13), dp(13), dp(13)); minimumWidth = dp(48); minimumHeight = dp(48)
        setOnClickListener { action() }; WalletMotion.press(this)
    }
    private fun pickMonthYear(anchor: LocalDate, selected: (LocalDate) -> Unit) {
        val form = column().apply { setPadding(dp(20), 0, dp(20), dp(8)) }
        val yearRow = row()
        yearRow.addView(text("Year", 14, muted), LinearLayout.LayoutParams(0, -2, 1f))
        val year = NumberPicker(this).apply {
            id = R.id.monthYearPickerYear; minValue = 1; maxValue = 9999; value = anchor.year.coerceIn(1, 9999)
            wrapSelectorWheel = false; contentDescription = "Year"
        }
        yearRow.addView(year, LinearLayout.LayoutParams(dp(112), dp(112))); form.addView(yearRow)
        var selectedMonth = anchor.monthValue
        val months = mutableListOf<MaterialButton>()
        val monthLabels = java.text.DateFormatSymbols.getInstance().shortMonths
        fun updateSelection() {
            months.forEachIndexed { index, month ->
                val checked = index + 1 == selectedMonth
                month.isSelected = checked
                month.backgroundTintList = android.content.res.ColorStateList.valueOf(if (checked) actionBlue else softBlue)
                month.setTextColor(if (checked) Color.WHITE else ink)
                month.contentDescription = "${java.time.Month.of(index + 1).getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault())}${if (checked) ", selected" else ""}"
            }
        }
        repeat(4) { rowIndex ->
            val monthRow = row()
            repeat(3) { columnIndex ->
                val index = rowIndex * 3 + columnIndex
                val month = button(monthLabels[index], monthRow, true) { selectedMonth = index + 1; updateSelection() }
                month.strokeWidth = 0
                month.layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f).apply { if (columnIndex > 0) marginStart = dp(6) }
                months.add(month)
            }
            form.addView(monthRow)
        }
        updateSelection()
        val scroll = ScrollView(this).apply { addView(form) }
        MaterialAlertDialogBuilder(this).setTitle("Month & year").setView(scroll)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Apply") { _, _ ->
                year.clearFocus()
                val month = java.time.YearMonth.of(year.value, selectedMonth)
                selected(month.atDay(minOf(anchor.dayOfMonth, month.lengthOfMonth())))
            }.show()
    }
    fun periodPicker(period: ReportPeriod, anchor: LocalDate, changed: (ReportPeriod, LocalDate) -> Unit) {
        val panel = card().apply { id = R.id.periodSelector; setPadding(dp(8), dp(6), dp(8), dp(6)) }
        val segments = MaterialButtonToggleGroup(this).apply { isSingleSelection = true; isSelectionRequired = true }
        val segmentIds = listOf(R.id.periodDay, R.id.periodWeek, R.id.periodMonth)
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        listOf("Day", "Week", "Month").forEachIndexed { index, label ->
            val segment = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = label; textSize = 13f; letterSpacing = 0f; isAllCaps = false
                minHeight = dp(48); minimumHeight = dp(48); insetTop = dp(4); insetBottom = dp(4)
                setPadding(dp(6), 0, dp(6), 0); WalletMotion.press(this)
            }
            segment.apply {
                id = segmentIds[index]; isCheckable = true; cornerRadius = dp(10); strokeWidth = 0
                backgroundTintList = android.content.res.ColorStateList(states, intArrayOf(actionBlue, softBlue))
                setTextColor(android.content.res.ColorStateList(states, intArrayOf(Color.WHITE, muted)))
            }
            segments.addView(segment, LinearLayout.LayoutParams(0, dp(48), 1f))
        }
        segments.check(segmentIds[period.ordinal])
        segments.addOnButtonCheckedListener { _, id, checked ->
            if (checked) { val selected = segmentIds.indexOf(id); if (selected != period.ordinal) changed(ReportPeriod.values()[selected], anchor) }
        }
        panel.addView(segments, LinearLayout.LayoutParams(-1, dp(48)))
        val (start, end) = PlannerRules.bounds(anchor, period)
        val dates = row()
        val dateLabel = when (period) {
            ReportPeriod.DAY -> PlannerRules.displayDate(start)
            ReportPeriod.MONTH -> start.format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy"))
            ReportPeriod.WEEK -> if (start.month == end.month && start.year == end.year) "${start.dayOfMonth}–${end.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy"))}" else "${start.format(java.time.format.DateTimeFormatter.ofPattern("d MMM"))} – ${end.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy"))}"
        }
        val selectedDate = row().apply {
            id = R.id.periodDate; minimumHeight = dp(48); isFocusable = true
            contentDescription = if (period == ReportPeriod.MONTH) "Select month and year, $dateLabel" else "Select date, $dateLabel"
            val ripple = android.util.TypedValue(); theme.resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)
            foreground = androidx.core.content.ContextCompat.getDrawable(this@WalletScreen, ripple.resourceId)
            setOnClickListener { if (period == ReportPeriod.MONTH) pickMonthYear(anchor) { changed(period, it) } else pickDate(anchor) { changed(period, it) } }
        }
        selectedDate.addView(ImageView(this).apply { setImageResource(R.drawable.ic_calendar_outline); setColorFilter(blue); contentDescription = null }, LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginStart = dp(6); marginEnd = dp(8) })
        selectedDate.addView(text(dateLabel, 13, ink, true), LinearLayout.LayoutParams(0, -2, 1f))
        selectedDate.addView(ImageView(this).apply { setImageResource(R.drawable.ic_chevron_down); setColorFilter(blue); contentDescription = null }, LinearLayout.LayoutParams(dp(18), dp(18)).apply { marginEnd = dp(4) })
        dates.addView(selectedDate, LinearLayout.LayoutParams(0, -2, 1f))
        dates.addView(iconButton(R.drawable.ic_chevron_left, "Previous ${period.name.lowercase()}") {
            changed(period, when (period) { ReportPeriod.DAY -> anchor.minusDays(1); ReportPeriod.WEEK -> anchor.minusWeeks(1); ReportPeriod.MONTH -> anchor.minusMonths(1) })
        }, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginStart = dp(4) })
        dates.addView(iconButton(R.drawable.ic_chevron_right, "Next ${period.name.lowercase()}") {
            changed(period, when (period) { ReportPeriod.DAY -> anchor.plusDays(1); ReportPeriod.WEEK -> anchor.plusWeeks(1); ReportPeriod.MONTH -> anchor.plusMonths(1) })
        }, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginStart = dp(4) })
        panel.addView(dates, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(4) })
    }
    fun screen(title: String, subtitle: String = "", nav: Boolean = true, floatingAction: Pair<String, () -> Unit>? = null) {
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark
        }
        val previousScroll = if (title == lastTitle) lastScroll?.scrollY ?: 0 else 0
        lastTitle = title
        val shell = column().apply {
            val floating = android.util.TypedValue(); theme.resolveAttribute(android.R.attr.windowIsFloating, floating, true)
            if (floating.data != 0) { background = shape(surface, 20); clipToOutline = true }
            else setBackgroundColor(surface)
        }
        ViewCompat.setOnApplyWindowInsetsListener(shell) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        val toolbar = row().apply { setPadding(dp(12), dp(4), dp(12), dp(4)); setBackgroundColor(surface); minimumHeight = dp(60) }
        if (nav) toolbar.addView(ImageView(this).apply {
            setImageResource(R.drawable.swallet_logo); contentDescription = "SWallet"; scaleType = ImageView.ScaleType.FIT_CENTER
            background = shape(softBlue, 12); setPadding(dp(2), dp(2), dp(2), dp(2))
        }, LinearLayout.LayoutParams(dp(40), dp(40)))
        else toolbar.addView(iconButton(R.drawable.ic_back_arrow, "Back") { finish() }, LinearLayout.LayoutParams(dp(48), dp(48)))
        toolbar.addView(column().apply {
            setPadding(dp(10), 0, 0, 0); addView(text(title, 19, ink, true).apply {
                id = R.id.screenTitle; ViewCompat.setAccessibilityHeading(this, true)
                typeface = androidx.core.content.res.ResourcesCompat.getFont(this@WalletScreen, R.font.inter_semibold)
            })
            if (subtitle.isNotBlank()) addView(text(subtitle, 11, muted))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        shell.addView(toolbar)
        shell.addView(View(this).apply { setBackgroundColor(border) }, LinearLayout.LayoutParams(-1, dp(1)))
        content = column().apply { setPadding(dp(16), dp(10), dp(16), dp(if (floatingAction != null) 88 else 16)) }
        val scroll = ScrollView(this).apply { id = R.id.screenScroll; isFillViewport = true; clipToPadding = false; addView(content) }
        lastScroll = scroll
        if (previousScroll > 0) scroll.post { scroll.scrollTo(0, previousScroll) }
        val body = FrameLayout(this).apply { setBackgroundColor(backgroundColor); addView(scroll, FrameLayout.LayoutParams(-1, -1)) }
        var fab: FloatingActionButton? = null
        if (floatingAction != null) {
            fab = FloatingActionButton(this).apply {
                id = R.id.addAction; contentDescription = floatingAction.first
                androidx.appcompat.widget.TooltipCompat.setTooltipText(this, floatingAction.first)
                setImageResource(R.drawable.ic_add); imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
                backgroundTintList = android.content.res.ColorStateList.valueOf(actionBlue); compatElevation = dp(6).toFloat()
                shapeAppearanceModel = ShapeAppearanceModel.builder().setAllCornerSizes(dp(16).toFloat()).build()
                size = FloatingActionButton.SIZE_NORMAL; setMaxImageSize(dp(24))
                setOnClickListener { floatingAction.second() }; WalletMotion.press(this)
            }
            body.addView(fab, FrameLayout.LayoutParams(dp(56), dp(56), Gravity.BOTTOM or Gravity.END).apply { marginEnd = dp(16); bottomMargin = dp(16) })
        }
        shell.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        if (nav) shell.addView(BottomNavBar(this).apply { id = R.id.bottomNav })
        setContentView(shell)
        if (nav) BottomNavHelper.attach(this)
        ViewCompat.requestApplyInsets(shell)
        val visibleContent = content
        content.post {
            if (visibleContent === content && !entranceShown) {
                entranceShown = true
                WalletMotion.revealChildren(visibleContent)
                fab?.let { WalletMotion.confirm(it) }
            }
        }
    }
    fun confirmDeletion(title: String, detail: String, action: () -> Unit) {
        MaterialAlertDialogBuilder(this).setTitle(title).setMessage(detail)
            .setNegativeButton("Cancel", null).setPositiveButton("Continue") { _, _ ->
                val form = column().apply { setPadding(dp(24), 0, dp(24), dp(8)) }
                val verify = field(form, "Type DELETE to confirm").apply {
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                    id = R.id.deleteVerification
                }
                val dialog = MaterialAlertDialogBuilder(this).setTitle("Confirm deletion (2 of 2)")
                    .setView(form).setNegativeButton("Cancel", null).setPositiveButton("Delete", null).create()
                dialog.setOnShowListener {
                    dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        if (verify.text.toString().trim() != "DELETE") verify.error = "Type DELETE to continue"
                        else { dialog.dismiss(); action() }
                    }
                }
                dialog.show()
            }.show()
    }
    fun animateEntrance(view: View) {
        WalletMotion.reveal(view)
    }
}
