package com.ext.custom_pin_view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.text.InputFilter
import android.text.InputType
import android.util.AttributeSet
import android.util.TypedValue
import android.view.KeyEvent
import android.view.animation.CycleInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import androidx.core.content.res.ResourcesCompat
import kotlinx.coroutines.*

/**
 * PinView - A highly customizable PIN/OTP input view for Android
 *
 * Features:
 * - Multiple styles (Box, Circle, Underline)
 * - Fully customizable via XML
 * - Auto-focus management
 * - Error states with animations
 * - Masked input support
 * - Completion callback
 */
class PinView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    // ===== ENUMS =====
    enum class ViewType { BOX, CIRCLE, UNDERLINE }
    enum class BoxState { NORMAL, FOCUSED, FILLED, ERROR }

    // ===== CONFIGURATION =====
    private var pinLength: Int = 4
    private var viewType: ViewType = ViewType.BOX

    // Dimensions
    private var boxWidth: Float = dpToPx(50f)
    private var boxHeight: Float = dpToPx(50f)
    private var boxSpacing: Float = dpToPx(12f)
    private var cornerRadius: Float = dpToPx(8f)

    // Border
    private var borderWidth: Float = dpToPx(2f)
    private var normalBorderColor: Int = Color.parseColor("#CCCCCC")
    private var focusedBorderColor: Int = Color.parseColor("#2196F3")
    private var errorBorderColor: Int = Color.parseColor("#F44336")
    private var filledBorderColor: Int = Color.parseColor("#4CAF50")

    // Background & Text
    private var boxBackgroundColor: Int = Color.WHITE
    private var textColor: Int = Color.BLACK
    private var textSize: Float = spToPx(20f)
    private var fontFamily: String? = null
    private var typeface: Typeface = Typeface.DEFAULT

    // Cursor
    private var cursorColor: Int = Color.parseColor("#2196F3")
    private var cursorWidth: Float = dpToPx(2f)
    private var cursorHeight: Float = dpToPx(24f)
    private var cursorVisible: Boolean = true

    // Masking
    private var maskEnabled: Boolean = false
    private var maskCharacter: String = "●"
    private var maskDelay: Long = 500L

    // Animation
    private var animationEnabled: Boolean = true
    private var errorShakeEnabled: Boolean = true
    private var scaleAnimationEnabled: Boolean = true

    // Behavior
    private var clearOnError: Boolean = false
    private var autoSubmit: Boolean = false
    private var secureInput: Boolean = true

    // State
    private var disabledAlpha: Float = 0.5f
    private var filledTextColor: Int = Color.BLACK

    // ===== INTERNAL STATE =====
    private var pinText: String = ""
    private var isError: Boolean = false
    private val boxStates = mutableListOf<BoxState>()
    private var currentFocusIndex: Int = 0

    // Paint objects
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Hidden EditText for input
    private val hiddenEditText: EditText

    // Animation
    private var cursorAnimator: ValueAnimator? = null
    private var isCursorVisible: Boolean = true
    private val scaleValues = mutableMapOf<Int, Float>()

    // Masking coroutines
    private val maskJobs = mutableMapOf<Int, Job>()
    private val maskScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Listeners
    private var onPinEnteredListener: ((String) -> Unit)? = null
    private var onTextChangedListener: ((String) -> Unit)? = null

    init {
        setWillNotDraw(false)

        // Initialize hidden EditText
        hiddenEditText = EditText(context).apply {
            alpha = 0f
            isFocusable = true
            isFocusableInTouchMode = true
            inputType = InputType.TYPE_CLASS_NUMBER
            imeOptions = EditorInfo.IME_ACTION_DONE
            filters = arrayOf(InputFilter.LengthFilter(pinLength))

            // Disable copy/paste for security
            if (secureInput) {
                customSelectionActionModeCallback = object : android.view.ActionMode.Callback {
                    override fun onCreateActionMode(
                        mode: android.view.ActionMode?,
                        menu: android.view.Menu?
                    ) = false

                    override fun onPrepareActionMode(
                        mode: android.view.ActionMode?,
                        menu: android.view.Menu?
                    ) = false

                    override fun onActionItemClicked(
                        mode: android.view.ActionMode?,
                        item: android.view.MenuItem?
                    ) = false

                    override fun onDestroyActionMode(mode: android.view.ActionMode?) {}
                }
                isLongClickable = false
            }
        }

        addView(hiddenEditText, LayoutParams(1, 1))

        // Load attributes
        attrs?.let { loadAttributes(it) }

        // Initialize box states
        for (i in 0 until pinLength) {
            boxStates.add(BoxState.NORMAL)
            scaleValues[i] = 1f
        }

        setupEditTextListener()
        setupPaints()
        startCursorAnimation()

        // Auto-focus on attach
        post {
            if (isEnabled) {
                requestFocusForInput()
            }
        }
    }

    private fun loadAttributes(attrs: AttributeSet) {
        val ta = context.obtainStyledAttributes(attrs, R.styleable.PinView)

        try {
            // PIN Configuration
            pinLength = ta.getInteger(R.styleable.PinView_pinLength, 4)
            viewType = ViewType.values()[ta.getInt(R.styleable.PinView_pinViewType, 0)]

            // Dimensions
            boxWidth = ta.getDimension(R.styleable.PinView_boxWidth, boxWidth)
            boxHeight = ta.getDimension(R.styleable.PinView_boxHeight, boxHeight)
            boxSpacing = ta.getDimension(R.styleable.PinView_boxSpacing, boxSpacing)
            cornerRadius = ta.getDimension(R.styleable.PinView_cornerRadius, cornerRadius)

            // Border
            borderWidth = ta.getDimension(R.styleable.PinView_borderWidth, borderWidth)
            normalBorderColor =
                ta.getColor(R.styleable.PinView_normalBorderColor, normalBorderColor)
            focusedBorderColor =
                ta.getColor(R.styleable.PinView_focusedBorderColor, focusedBorderColor)
            errorBorderColor = ta.getColor(R.styleable.PinView_errorBorderColor, errorBorderColor)
            filledBorderColor =
                ta.getColor(R.styleable.PinView_filledBorderColor, filledBorderColor)

            // Background & Text
            boxBackgroundColor =
                ta.getColor(R.styleable.PinView_boxBackgroundColor, boxBackgroundColor)
            textColor = ta.getColor(R.styleable.PinView_textColor, textColor)
            textSize = ta.getDimension(R.styleable.PinView_textSize, textSize)
            filledTextColor = ta.getColor(R.styleable.PinView_filledTextColor, textColor)

            fontFamily?.let {
                try {
                    typeface = ResourcesCompat.getFont(
                        context,
                        context.resources.getIdentifier(it, "font", context.packageName)
                    ) ?: Typeface.DEFAULT
                } catch (e: Exception) {
                    typeface = Typeface.DEFAULT
                }
            }

            // Cursor
            cursorColor = ta.getColor(R.styleable.PinView_cursorColor, cursorColor)
            cursorWidth = ta.getDimension(R.styleable.PinView_cursorWidth, cursorWidth)
            cursorHeight = ta.getDimension(R.styleable.PinView_cursorHeight, cursorHeight)
            cursorVisible = ta.getBoolean(R.styleable.PinView_cursorVisible, true)

            // Masking
            maskEnabled = ta.getBoolean(R.styleable.PinView_maskEnabled, false)
            maskCharacter = ta.getString(R.styleable.PinView_maskCharacter) ?: "●"
            maskDelay = ta.getInteger(R.styleable.PinView_maskDelay, 500).toLong()

            // Animation
            animationEnabled = ta.getBoolean(R.styleable.PinView_animationEnabled, true)
            errorShakeEnabled = ta.getBoolean(R.styleable.PinView_errorShakeEnabled, true)
            scaleAnimationEnabled = ta.getBoolean(R.styleable.PinView_scaleAnimationEnabled, true)

            // Behavior
            clearOnError = ta.getBoolean(R.styleable.PinView_clearOnError, false)
            autoSubmit = ta.getBoolean(R.styleable.PinView_autoSubmit, false)
            secureInput = ta.getBoolean(R.styleable.PinView_secureInput, true)

            // State
            disabledAlpha = ta.getFloat(R.styleable.PinView_disabledAlpha, 0.5f)

        } finally {
            ta.recycle()
        }

        // Update EditText filter
        hiddenEditText.filters = arrayOf(InputFilter.LengthFilter(pinLength))
    }

    private fun setupPaints() {
        borderPaint.apply {
            style = Paint.Style.STROKE
            strokeWidth = borderWidth
        }

        backgroundPaint.apply {
            style = Paint.Style.FILL
            color = boxBackgroundColor
        }

        textPaint.apply {
            textAlign = Paint.Align.CENTER
            textSize = this@PinView.textSize
            color = textColor
            typeface = this@PinView.typeface
        }

        cursorPaint.apply {
            style = Paint.Style.FILL
            color = cursorColor
            strokeWidth = cursorWidth
        }
    }

    private fun setupEditTextListener() {
        hiddenEditText.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DEL) {
                if (pinText.isEmpty()) {
                    return@setOnKeyListener false
                }
                handleBackspace()
                return@setOnKeyListener true
            }
            false
        }

        hiddenEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val newText = s?.toString() ?: ""

                if (newText.length > pinText.length) {
                    // New character added
                    val newChar = newText.last()
                    if (newChar.isDigit() && pinText.length < pinLength) {
                        addCharacter(newChar)
                    }
                }
            }

            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    private fun addCharacter(char: Char) {
        if (pinText.length >= pinLength) return

        isError = false
        pinText += char
        currentFocusIndex = pinText.length

        updateBoxStates()

        // Animate scale
        if (scaleAnimationEnabled && animationEnabled) {
            animateBoxScale(pinText.length - 1)
        }

        // Handle masking with delay
        if (maskEnabled) {
            handleMasking(pinText.length - 1)
        }

        onTextChangedListener?.invoke(pinText)

        // Check completion
        if (pinText.length == pinLength) {
            if (autoSubmit) {
                postDelayed({ onPinEnteredListener?.invoke(pinText) }, 100)
            } else {
                onPinEnteredListener?.invoke(pinText)
            }
        }

        invalidate()
    }

    private fun handleBackspace() {
        if (pinText.isEmpty()) return

        isError = false

        // Cancel mask job for this position
        maskJobs[pinText.length - 1]?.cancel()
        maskJobs.remove(pinText.length - 1)

        pinText = pinText.dropLast(1)
        currentFocusIndex = pinText.length

        updateBoxStates()
        onTextChangedListener?.invoke(pinText)
        invalidate()
    }

    private fun handleMasking(index: Int) {
        // Cancel previous mask job for this index
        maskJobs[index]?.cancel()

        maskJobs[index] = maskScope.launch {
            delay(maskDelay)
            invalidate()
        }
    }

    private fun updateBoxStates() {
        for (i in 0 until pinLength) {
            boxStates[i] = when {
                isError -> BoxState.ERROR
                i < pinText.length -> BoxState.FILLED
                i == pinText.length -> BoxState.FOCUSED
                else -> BoxState.NORMAL
            }
        }
    }

    private fun animateBoxScale(index: Int) {
        ValueAnimator.ofFloat(1f, 1.1f, 1f).apply {
            duration = 200
            addUpdateListener {
                scaleValues[index] = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun startCursorAnimation() {
        if (!cursorVisible) return

        cursorAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 530
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener {
                isCursorVisible = it.animatedValue as Float > 0.5f
                invalidate()
            }
            start()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val totalWidth =
            (boxWidth * pinLength + boxSpacing * (pinLength - 1)).toInt() + paddingLeft + paddingRight
        val totalHeight = boxHeight.toInt() + paddingTop + paddingBottom

        setMeasuredDimension(
            resolveSize(totalWidth, widthMeasureSpec),
            resolveSize(totalHeight, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val startX = paddingLeft.toFloat()
        val startY = paddingTop.toFloat()

        for (i in 0 until pinLength) {
            val x = startX + (boxWidth + boxSpacing) * i
            val scale = scaleValues[i] ?: 1f

            canvas.save()

            // Apply scale animation
            if (scale != 1f) {
                val centerX = x + boxWidth / 2
                val centerY = startY + boxHeight / 2
                canvas.scale(scale, scale, centerX, centerY)
            }

            drawBox(canvas, x, startY, i)

            canvas.restore()
        }

        // Apply disabled alpha
        if (!isEnabled) {
            canvas.drawColor(Color.argb((255 * (1 - disabledAlpha)).toInt(), 255, 255, 255))
        }
    }

    private fun drawBox(canvas: Canvas, x: Float, y: Float, index: Int) {
        val state = boxStates.getOrNull(index) ?: BoxState.NORMAL

        // Determine border color
        borderPaint.color = when (state) {
            BoxState.ERROR -> errorBorderColor
            BoxState.FOCUSED -> focusedBorderColor
            BoxState.FILLED -> filledBorderColor
            BoxState.NORMAL -> normalBorderColor
        }

        // Draw based on view type
        when (viewType) {
            ViewType.BOX -> drawBoxStyle(canvas, x, y)
            ViewType.CIRCLE -> drawCircleStyle(canvas, x, y)
            ViewType.UNDERLINE -> drawUnderlineStyle(canvas, x, y)
        }

        // Draw text or cursor
        if (index < pinText.length) {
            drawText(canvas, x, y, index)
        } else if (index == currentFocusIndex && isCursorVisible && cursorVisible && !isError) {
            drawCursor(canvas, x, y)
        }
    }

    private fun drawBoxStyle(canvas: Canvas, x: Float, y: Float) {
        // Background
        canvas.drawRoundRect(
            x, y, x + boxWidth, y + boxHeight,
            cornerRadius, cornerRadius,
            backgroundPaint
        )

        // Border
        canvas.drawRoundRect(
            x + borderWidth / 2,
            y + borderWidth / 2,
            x + boxWidth - borderWidth / 2,
            y + boxHeight - borderWidth / 2,
            cornerRadius,
            cornerRadius,
            borderPaint
        )
    }

    private fun drawCircleStyle(canvas: Canvas, x: Float, y: Float) {
        val centerX = x + boxWidth / 2
        val centerY = y + boxHeight / 2
        val radius = minOf(boxWidth, boxHeight) / 2 - borderWidth

        // Background
        canvas.drawCircle(centerX, centerY, radius, backgroundPaint)

        // Border
        canvas.drawCircle(centerX, centerY, radius, borderPaint)
    }

    private fun drawUnderlineStyle(canvas: Canvas, x: Float, y: Float) {
        // Background (optional)
        canvas.drawRect(x, y, x + boxWidth, y + boxHeight, backgroundPaint)

        // Underline
        val lineY = y + boxHeight - borderWidth / 2
        canvas.drawLine(x, lineY, x + boxWidth, lineY, borderPaint)
    }

    private fun drawText(canvas: Canvas, x: Float, y: Float, index: Int) {
        val char = pinText.getOrNull(index) ?: return

        // Determine if we should show masked character
        val displayChar = if (maskEnabled && shouldMaskCharacter(index)) {
            maskCharacter
        } else {
            char.toString()
        }

        // Use filled text color for filled boxes
        textPaint.color = if (boxStates[index] == BoxState.FILLED) filledTextColor else textColor

        val centerX = x + boxWidth / 2
        val centerY = y + boxHeight / 2 - (textPaint.descent() + textPaint.ascent()) / 2

        canvas.drawText(displayChar, centerX, centerY, textPaint)
    }

    private fun shouldMaskCharacter(index: Int): Boolean {
        // Don't mask if job is still active (showing briefly)
        return maskJobs[index]?.isActive != true
    }

    private fun drawCursor(canvas: Canvas, x: Float, y: Float) {
        val centerX = x + boxWidth / 2
        val centerY = y + boxHeight / 2
        val top = centerY - cursorHeight / 2
        val bottom = centerY + cursorHeight / 2

        canvas.drawLine(centerX, top, centerX, bottom, cursorPaint)
    }

    // ===== PUBLIC API =====

    fun setOnPinEnteredListener(listener: (String) -> Unit) {
        onPinEnteredListener = listener
    }

    fun setOnTextChangedListener(listener: (String) -> Unit) {
        onTextChangedListener = listener
    }

    fun getText(): String = pinText

    fun setText(text: String) {
        pinText = text.take(pinLength)
        hiddenEditText.setText(pinText)
        currentFocusIndex = pinText.length
        updateBoxStates()
        invalidate()
    }

    fun clear() {
        pinText = ""
        hiddenEditText.setText("")
        currentFocusIndex = 0
        isError = false
        updateBoxStates()
        maskJobs.values.forEach { it.cancel() }
        maskJobs.clear()
        invalidate()
    }

    fun showError() {
        isError = true
        updateBoxStates()

        if (errorShakeEnabled && animationEnabled) {
            startShakeAnimation()
        }

        if (clearOnError) {
            postDelayed({ clear() }, 500)
        } else {
            invalidate()
        }
    }

    private fun startShakeAnimation() {
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 400
            interpolator = CycleInterpolator(3f)
            addUpdateListener {
                val value = it.animatedValue as Float
                translationX = (value - 0.5f) * 20f
            }
            start()
        }
    }

    fun requestFocusForInput() {
        hiddenEditText.requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(hiddenEditText, InputMethodManager.SHOW_IMPLICIT)
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        hiddenEditText.isEnabled = enabled
        if (enabled) {
            requestFocusForInput()
        }
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cursorAnimator?.cancel()
        maskScope.cancel()
    }

    // ===== UTILITY FUNCTIONS =====

    private fun dpToPx(dp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        )
    }

    private fun spToPx(sp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            sp,
            context.resources.displayMetrics
        )
    }
}