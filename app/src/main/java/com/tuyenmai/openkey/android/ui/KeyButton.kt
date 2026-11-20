package com.tuyenmai.openkey.android.ui

import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.tuyenmai.openkey.core.ComputedKeyboardMetrics
import com.tuyenmai.openkey.core.GestureAction
import com.tuyenmai.openkey.core.KeyAction
import com.tuyenmai.openkey.core.KeyModel
import com.tuyenmai.openkey.core.KeyboardGestureController
import com.tuyenmai.openkey.core.ThemePalette
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Authentic Samsung One UI 6/7 Vector Icons for Keyboard Action and Modifier Keys (REQ-020, ADR-021).
 */
object KeyboardVectorIcons {

    @Composable
    fun ShiftIcon(
        isShifted: Boolean,
        isCapsLocked: Boolean,
        tint: Color,
        modifier: Modifier = Modifier
    ) {
        Canvas(modifier = modifier.size(22.dp)) {
            val w = size.width
            val h = size.height
            val strokeWidth = 1.8.dp.toPx()

            val arrowPath = Path().apply {
                moveTo(w * 0.50f, h * 0.16f)
                lineTo(w * 0.82f, h * 0.50f)
                lineTo(w * 0.64f, h * 0.50f)
                lineTo(w * 0.64f, if (isCapsLocked) h * 0.72f else h * 0.84f)
                lineTo(w * 0.36f, if (isCapsLocked) h * 0.72f else h * 0.84f)
                lineTo(w * 0.36f, h * 0.50f)
                lineTo(w * 0.18f, h * 0.50f)
                close()
            }

            if (isShifted || isCapsLocked) {
                drawPath(arrowPath, color = tint, style = Fill)
            } else {
                drawPath(
                    arrowPath,
                    color = tint,
                    style = Stroke(
                        width = strokeWidth,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }

            if (isCapsLocked) {
                val barY = h * 0.86f
                drawLine(
                    color = tint,
                    start = Offset(w * 0.28f, barY),
                    end = Offset(w * 0.72f, barY),
                    strokeWidth = 2.2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
    }

    @Composable
    fun BackspaceIcon(
        tint: Color,
        modifier: Modifier = Modifier
    ) {
        Canvas(modifier = modifier.size(22.dp)) {
            val w = size.width
            val h = size.height
            val strokeWidth = 1.8.dp.toPx()

            val path = Path().apply {
                moveTo(w * 0.12f, h * 0.50f)
                lineTo(w * 0.38f, h * 0.22f)
                lineTo(w * 0.88f, h * 0.22f)
                lineTo(w * 0.88f, h * 0.78f)
                lineTo(w * 0.38f, h * 0.78f)
                close()
            }

            drawPath(
                path,
                color = tint,
                style = Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            val crossSize = w * 0.12f
            val cx = w * 0.63f
            val cy = h * 0.50f
            drawLine(
                color = tint,
                start = Offset(cx - crossSize, cy - crossSize),
                end = Offset(cx + crossSize, cy + crossSize),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            drawLine(
                color = tint,
                start = Offset(cx + crossSize, cy - crossSize),
                end = Offset(cx - crossSize, cy + crossSize),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
    }

    @Composable
    fun GlobeLanguageIcon(
        languageBadge: String,
        tint: Color,
        accentTint: Color,
        modifier: Modifier = Modifier
    ) {
        Box(
            modifier = modifier.size(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val center = Offset(w * 0.44f, h * 0.46f)
                val radius = w * 0.34f
                val strokeWidth = 1.5.dp.toPx()

                drawCircle(
                    color = tint,
                    radius = radius,
                    center = center,
                    style = Stroke(width = strokeWidth)
                )

                drawLine(
                    color = tint,
                    start = Offset(center.x - radius, center.y),
                    end = Offset(center.x + radius, center.y),
                    strokeWidth = strokeWidth * 0.9f
                )

                drawOval(
                    color = tint,
                    topLeft = Offset(center.x - radius * 0.46f, center.y - radius),
                    size = Size(radius * 0.92f, radius * 2f),
                    style = Stroke(width = strokeWidth * 0.9f)
                )
            }

            Text(
                text = languageBadge,
                color = accentTint,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 1.dp, y = 2.dp)
            )
        }
    }

    @Composable
    fun EnterActionIcon(
        actionLabel: String,
        tint: Color = Color.White,
        modifier: Modifier = Modifier
    ) {
        Canvas(modifier = modifier.size(22.dp)) {
            val w = size.width
            val h = size.height
            val strokeWidth = 2.0.dp.toPx()

            when (actionLabel.lowercase()) {
                "search" -> {
                    val radius = w * 0.24f
                    val center = Offset(w * 0.42f, h * 0.42f)
                    drawCircle(
                        color = tint,
                        radius = radius,
                        center = center,
                        style = Stroke(width = strokeWidth)
                    )
                    drawLine(
                        color = tint,
                        start = Offset(w * 0.58f, h * 0.58f),
                        end = Offset(w * 0.84f, h * 0.84f),
                        strokeWidth = strokeWidth * 1.1f,
                        cap = StrokeCap.Round
                    )
                }
                "send" -> {
                    val path = Path().apply {
                        moveTo(w * 0.16f, h * 0.20f)
                        lineTo(w * 0.86f, h * 0.50f)
                        lineTo(w * 0.16f, h * 0.80f)
                        lineTo(w * 0.34f, h * 0.50f)
                        close()
                    }
                    drawPath(path, color = tint, style = Fill)
                }
                "go", "next" -> {
                    drawLine(
                        color = tint,
                        start = Offset(w * 0.20f, h * 0.50f),
                        end = Offset(w * 0.78f, h * 0.50f),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                    val arrowHead = Path().apply {
                        moveTo(w * 0.54f, h * 0.26f)
                        lineTo(w * 0.80f, h * 0.50f)
                        lineTo(w * 0.54f, h * 0.74f)
                    }
                    drawPath(
                        arrowHead,
                        color = tint,
                        style = Stroke(
                            width = strokeWidth,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
                "done" -> {
                    val checkPath = Path().apply {
                        moveTo(w * 0.20f, h * 0.52f)
                        lineTo(w * 0.42f, h * 0.74f)
                        lineTo(w * 0.82f, h * 0.26f)
                    }
                    drawPath(
                        checkPath,
                        color = tint,
                        style = Stroke(
                            width = strokeWidth * 1.1f,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
                else -> {
                    val returnPath = Path().apply {
                        moveTo(w * 0.78f, h * 0.32f)
                        lineTo(w * 0.78f, h * 0.62f)
                        lineTo(w * 0.28f, h * 0.62f)
                    }
                    drawPath(
                        returnPath,
                        color = tint,
                        style = Stroke(
                            width = strokeWidth,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                    val arrowHead = Path().apply {
                        moveTo(w * 0.48f, h * 0.44f)
                        lineTo(w * 0.26f, h * 0.62f)
                        lineTo(w * 0.48f, h * 0.80f)
                    }
                    drawPath(
                        arrowHead,
                        color = tint,
                        style = Stroke(
                            width = strokeWidth,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }
        }
    }
}

/**
 * Samsung Galaxy A37 One UI 6/7 Keycap Component (REQ-007, REQ-020, ADR-007, ADR-021).
 */
@Composable
fun KeyButton(
    keyItem: KeyModel,
    metrics: ComputedKeyboardMetrics,
    palette: ThemePalette,
    hapticFeedbackEnabled: Boolean,
    keyPopupPreviewEnabled: Boolean,
    gestureController: KeyboardGestureController,
    onKeyPressed: (KeyAction) -> Unit,
    onMoveCursor: (Int) -> Unit,
    onDeleteWord: () -> Unit,
    modifier: Modifier = Modifier,
    isShiftActive: Boolean = false,
    isCapsLocked: Boolean = false
) {
    val currentKey by rememberUpdatedState(keyItem)
    val key = keyItem.stableTouchIdentity
    val currentOnKeyPressed by rememberUpdatedState(onKeyPressed)
    val currentOnMoveCursor by rememberUpdatedState(onMoveCursor)
    val currentOnDeleteWord by rememberUpdatedState(onDeleteWord)
    val currentHaptics by rememberUpdatedState(hapticFeedbackEnabled)

    val theme = palette
    val view = LocalView.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    var isPressed by remember { mutableStateOf(false) }
    var showVariantsPopup by remember { mutableStateOf(false) }
    var variantOptions by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedVariantIndex by remember { mutableIntStateOf(0) }
    var spacebarTrackpadLabel by remember { mutableStateOf<String?>(null) }

    val cornerRadius = metrics.keyCornerRadiusDp.dp
    val gapH = (metrics.keyHorizontalGapDp / 2).dp
    val gapV = (metrics.keyVerticalGapDp / 2).dp

    val isEnterKey = currentKey.action is KeyAction.Enter
    val isShiftKey = currentKey.action is KeyAction.Shift
    val isBackspaceKey = currentKey.action is KeyAction.Backspace
    val isGlobeKey = currentKey.action is KeyAction.SwitchLanguage
    val isSpaceKey = currentKey.action is KeyAction.Space

    val accentColor = Color(theme.accentColor)

    val bgFillColor = when {
        isEnterKey -> {
            if (isPressed) accentColor.copy(alpha = 0.80f) else accentColor
        }
        currentKey.isFunctional -> {
            if (isPressed) {
                Color(theme.pressedFunctionalKeycapColor)
            } else if (isShiftActive && isShiftKey) {
                accentColor.copy(alpha = 0.18f)
            } else {
                Color(theme.functionalKeycapColor)
            }
        }
        else -> {
            if (isPressed) {
                Color(theme.pressedKeycapColor)
            } else {
                Color(theme.keycapColor)
            }
        }
    }

    val strokeWidthPx = with(density) { 1.dp.toPx() }
    val bevelHeightPx = with(density) { 1.2.dp.toPx() }
    val cornerRadiusPx = with(density) { cornerRadius.toPx() }
    val variantItemWidthPx = with(density) { 36.dp.toPx() }
    val longPressMoveSlopPx = with(density) { 12.dp.toPx() }
    val previewPopupOffsetY = with(density) { (-56).dp.roundToPx() }
    val variantsPopupOffsetY = with(density) { (-48).dp.roundToPx() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(key) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    val downX = down.position.x
                    val downY = down.position.y
                    selectedVariantIndex = 0

                    if (currentHaptics) {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    }

                    var longPressJob: Job? = null
                    var repeatDeleteJob: Job? = null
                    var longPressHandled = false
                    var didRepeatDelete = false
                    var tapCancelled = false
                    var rolloverToken = -1L
                    val isRolloverCharKey = !currentKey.isFunctional && currentKey.action is KeyAction.CommitChar
                    val capturedAction = currentKey.action

                    when (capturedAction) {
                        is KeyAction.Space -> {
                            gestureController.onSpacebarDown()
                            spacebarTrackpadLabel = null
                        }
                        is KeyAction.Backspace -> {
                            gestureController.onBackspaceDown()
                            repeatDeleteJob = scope.launch {
                                var repeatTick = 0
                                delay(400L)
                                while (isActive) {
                                    didRepeatDelete = true
                                    repeatTick++
                                    if (gestureController.resolveBackspaceRepeatAction(repeatTick) is GestureAction.DeletePreviousWord) {
                                        currentOnDeleteWord()
                                        delay(110L)
                                    } else {
                                        currentOnKeyPressed(KeyAction.Backspace)
                                        delay(50L)
                                    }
                                }
                            }
                        }
                        else -> {
                            if (isRolloverCharKey) {
                                rolloverToken = gestureController.onCharacterKeyDown(currentKey.primaryLabel) {
                                    longPressJob?.cancel()
                                    if (!longPressHandled && !tapCancelled) {
                                        currentOnKeyPressed(capturedAction)
                                    }
                                }
                            } else if (capturedAction !is KeyAction.Shift) {
                                gestureController.flushPendingCharacterKey()
                            }
                            longPressJob = scope.launch {
                                delay(KeyboardGestureController.LONG_PRESS_TIMEOUT_MS)
                                if (currentKey.action is KeyAction.SwitchLanguage) {
                                    if (currentHaptics) {
                                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    }
                                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                                    imm?.showInputMethodPicker()
                                    longPressHandled = true
                                } else {
                                    val variants = gestureController.resolveLongPressPopupItems(currentKey)
                                    if (variants.isNotEmpty()) {
                                        if (rolloverToken != -1L) {
                                            gestureController.markCharacterLongPressConsumed(rolloverToken)
                                        }
                                        if (currentHaptics) {
                                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                        }
                                        variantOptions = variants
                                        selectedVariantIndex = 0
                                        showVariantsPopup = true
                                        longPressHandled = true
                                    }
                                }
                            }
                        }
                    }

                    var pointer = down
                    try {
                        while (pointer.pressed) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointer.id } ?: break

                            if (change.pressed != pointer.pressed) {
                                pointer = change
                                break
                            }

                            if (showVariantsPopup && variantOptions.isNotEmpty()) {
                                selectedVariantIndex = gestureController.resolvePopupSlideIndex(
                                    dragOffsetX = change.position.x - downX,
                                    itemWidthPx = variantItemWidthPx,
                                    itemCount = variantOptions.size
                                )
                            } else {
                                val dragDeltaX = change.positionChange().x
                                val dragDeltaY = change.positionChange().y
                                if (dragDeltaX != 0f || dragDeltaY != 0f) {
                                    val deltaDp = with(density) { dragDeltaX.toDp().value }

                                    when (capturedAction) {
                                        is KeyAction.Space -> {
                                            val actions = gestureController.onSpacebarDrag(deltaDp)
                                            if (gestureController.isTrackpadActive) {
                                                spacebarTrackpadLabel = "<  ${currentKey.primaryLabel}  >"
                                                for (act in actions) {
                                                    if (act is GestureAction.CursorMove) {
                                                        currentOnMoveCursor(act.steps)
                                                    }
                                                }
                                            }
                                        }
                                        is KeyAction.Backspace -> {
                                            val actions = gestureController.onBackspaceDrag(deltaDp)
                                            if (gestureController.isSwipeDeleteActive) {
                                                repeatDeleteJob?.cancel()
                                                for (act in actions) {
                                                    if (act is GestureAction.DeletePreviousWord) {
                                                        currentOnDeleteWord()
                                                    }
                                                }
                                            }
                                        }
                                        else -> {
                                            if (kotlin.math.abs(change.position.x - downX) > longPressMoveSlopPx ||
                                                kotlin.math.abs(change.position.y - downY) > longPressMoveSlopPx
                                            ) {
                                                longPressJob?.cancel()
                                            }
                                            if (!tapCancelled && gestureController.shouldCancelTapOnDragOut(
                                                    offsetX = change.position.x,
                                                    offsetY = change.position.y,
                                                    keyWidthPx = size.width.toFloat(),
                                                    keyHeightPx = size.height.toFloat()
                                                )
                                            ) {
                                                tapCancelled = true
                                                isPressed = false
                                                if (rolloverToken != -1L) {
                                                    gestureController.cancelCharacterKey(rolloverToken)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            pointer = change
                        }
                    } finally {
                        isPressed = false
                        longPressJob?.cancel()
                        repeatDeleteJob?.cancel()
                        spacebarTrackpadLabel = null
                    }

                    when (capturedAction) {
                        is KeyAction.Space -> {
                            val result = gestureController.onSpacebarUp()
                            spacebarTrackpadLabel = null
                            if (result is GestureAction.CommitSpace) {
                                currentOnKeyPressed(KeyAction.Space)
                            }
                        }
                        is KeyAction.Backspace -> {
                            val result = gestureController.onBackspaceUp()
                            if (result is GestureAction.DeleteSingleChar && !didRepeatDelete) {
                                currentOnKeyPressed(KeyAction.Backspace)
                            }
                        }
                        else -> {
                            if (showVariantsPopup && variantOptions.isNotEmpty()) {
                                val chosen = variantOptions.getOrNull(selectedVariantIndex)
                                showVariantsPopup = false
                                variantOptions = emptyList()
                                if (chosen != null) {
                                    currentOnKeyPressed(KeyAction.CommitChar(chosen))
                                }
                            } else if (!longPressHandled && !tapCancelled) {
                                if (isRolloverCharKey) {
                                    if (gestureController.onCharacterKeyUp(rolloverToken)) {
                                        currentOnKeyPressed(capturedAction)
                                    }
                                } else {
                                    currentOnKeyPressed(capturedAction)
                                }
                            } else if (rolloverToken != -1L) {
                                gestureController.cancelCharacterKey(rolloverToken)
                            }
                        }
                    }
                }
            }
            .padding(horizontal = gapH, vertical = gapV)
            .drawBehind {
                val r = CornerRadius(cornerRadiusPx, cornerRadiusPx)
                if (!isPressed) {
                    drawRoundRect(
                        color = Color(theme.keyBorderColor).copy(alpha = 0.45f),
                        topLeft = Offset(0f, bevelHeightPx),
                        size = size,
                        cornerRadius = r
                    )
                }
                drawRoundRect(
                    color = bgFillColor,
                    topLeft = Offset.Zero,
                    size = size,
                    cornerRadius = r
                )
                if (!isEnterKey) {
                    drawRoundRect(
                        color = Color(theme.keyBorderColor).copy(alpha = 0.35f),
                        topLeft = Offset.Zero,
                        size = size,
                        cornerRadius = r,
                        style = Stroke(width = strokeWidthPx)
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (isEnterKey) {
            KeyboardVectorIcons.EnterActionIcon(actionLabel = currentKey.primaryLabel, tint = Color.White)
        } else if (isShiftKey) {
            KeyboardVectorIcons.ShiftIcon(
                isShifted = isShiftActive,
                isCapsLocked = isCapsLocked,
                tint = if (isShiftActive) accentColor else Color(theme.primaryTextColor)
            )
        } else if (isBackspaceKey) {
            KeyboardVectorIcons.BackspaceIcon(tint = Color(theme.primaryTextColor))
        } else if (isGlobeKey) {
            KeyboardVectorIcons.GlobeLanguageIcon(
                languageBadge = currentKey.secondaryLabel ?: "VI",
                tint = Color(theme.primaryTextColor),
                accentTint = accentColor
            )
        } else {
            // Secondary symbol sub-label
            val secondary = currentKey.secondaryLabel
            if (secondary != null) {
                Text(
                    text = secondary,
                    color = Color(theme.secondaryTextColor),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 1.5.dp, end = 3.5.dp)
                )
            }

            // Primary label
            if (isSpaceKey) {
                val spaceText = if (spacebarTrackpadLabel != null) spacebarTrackpadLabel!! else "‹ ${currentKey.primaryLabel} ›"
                Text(
                    text = spaceText,
                    color = Color(theme.secondaryTextColor),
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            } else if (currentKey.isFunctional) {
                Text(
                    text = currentKey.primaryLabel,
                    color = Color(theme.primaryTextColor),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            } else if (currentKey.primaryLabel.length == 1 && currentKey.primaryLabel[0].isDigit()) {
                val isLargeNumpad = currentKey.secondaryLabel == null && metrics.alphaRowHeightDp >= 56f
                Text(
                    text = currentKey.primaryLabel,
                    color = Color(theme.primaryTextColor),
                    fontSize = if (isLargeNumpad) 26.sp else 20.sp,
                    fontWeight = if (isLargeNumpad) FontWeight.Medium else FontWeight.Normal,
                    textAlign = TextAlign.Center
                )
            } else if (currentKey.primaryLabel == "," || currentKey.primaryLabel == ".") {
                Text(
                    text = currentKey.primaryLabel,
                    color = Color(theme.primaryTextColor),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            } else if (currentKey.primaryLabel.length == 1 && currentKey.primaryLabel[0].isUpperCase()) {
                Text(
                    text = currentKey.primaryLabel,
                    color = Color(theme.primaryTextColor),
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.offset(y = if (currentKey.secondaryLabel != null) 1.dp else 0.dp)
                )
            } else {
                Text(
                    text = currentKey.primaryLabel,
                    color = Color(theme.primaryTextColor),
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.offset(y = if (currentKey.secondaryLabel != null) 1.dp else 0.dp)
                )
            }
        }

        // Floating Key Popup Preview Bubble (One UI style)
        if (isPressed && keyPopupPreviewEnabled && !currentKey.isFunctional && currentKey.primaryLabel.length == 1) {
            Popup(
                alignment = Alignment.TopCenter,
                offset = IntOffset(0, previewPopupOffsetY),
                properties = PopupProperties(focusable = false)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(theme.keycapColor),
                    shadowElevation = 6.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(theme.keyBorderColor)),
                    modifier = Modifier
                        .width(54.dp)
                        .height(62.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = currentKey.primaryLabel,
                            color = Color(theme.primaryTextColor),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Long-Press Variant Options Popup (hold-slide-release)
        if (showVariantsPopup && variantOptions.isNotEmpty()) {
            Popup(
                alignment = Alignment.TopCenter,
                offset = IntOffset(0, variantsPopupOffsetY),
                onDismissRequest = { showVariantsPopup = false }
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(theme.keycapColor),
                    shadowElevation = 8.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(theme.keyBorderColor))
                ) {
                    Row(modifier = Modifier.padding(4.dp)) {
                        variantOptions.forEachIndexed { index, variant ->
                            val isSelected = index == selectedVariantIndex
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (isSelected) accentColor.copy(alpha = 0.20f) else Color.Transparent,
                                        RoundedCornerShape(6.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        currentOnKeyPressed(KeyAction.CommitChar(variant))
                                        showVariantsPopup = false
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = variant,
                                    fontSize = 20.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) accentColor else Color(theme.primaryTextColor)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
