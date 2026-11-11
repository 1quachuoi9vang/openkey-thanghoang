package com.tuyenmai.openkey.android.ui

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuyenmai.openkey.core.AutoWordingEngine
import com.tuyenmai.openkey.core.BatchReplacementCoordinator
import com.tuyenmai.openkey.core.EditorUiState
import com.tuyenmai.openkey.core.KeyAction
import com.tuyenmai.openkey.core.KeyModel
import com.tuyenmai.openkey.core.KeyboardGestureController
import com.tuyenmai.openkey.core.KeyboardLayer
import com.tuyenmai.openkey.core.KeyboardLayoutDefinition
import com.tuyenmai.openkey.core.KeyboardSessionController
import com.tuyenmai.openkey.core.PhoneLayoutConfig
import com.tuyenmai.openkey.core.PhoneLayoutMetrics
import com.tuyenmai.openkey.core.OneHandedMode
import com.tuyenmai.openkey.core.OpenKeySettingsStore
import com.tuyenmai.openkey.core.TextCommitterTarget
import com.tuyenmai.openkey.core.VietnameseEngine
import com.tuyenmai.openkey.core.WordCandidate

/**
 * Samsung Galaxy A37 One UI 5-Row Keyboard Screen (REQ-007, ADR-007).
 * Renders:
 * - Top 42dp CandidateStripView (3 candidate slots or idle toolbar)
 * - Row 1 (dedicated number row when enabled or VNI mode)
 * - Row 2 (q..p / symbols)
 * - Row 3 (a..l with exact 5% left and right insets)
 * - Row 4 (⇧ [15%] + 7 keys [10% each] + ⌫ [15%])
 * - Row 5 (!#1/ABC [15%] + , [10%] + Spacebar [50%] + . [10%] + Enter [15%])
 * - 82% One-Handed dock container with 18% side gutter (⇄ swap, ⤢ expand)
 * - Effective bottom chin padding (14dp in portrait)
 */
@Composable
fun KeyboardScreen(
    settingsStore: OpenKeySettingsStore,
    committer: TextCommitterTarget,
    batchCoordinator: BatchReplacementCoordinator,
    autoWordingEngine: AutoWordingEngine,
    sessionController: KeyboardSessionController? = null,
    enterActionLabel: String = "↵",
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val screenWidthDp = configuration.screenWidthDp
    val screenHeightDp = configuration.screenHeightDp

    val isDark = isSystemInDarkTheme()
    val palette = if (isDark) KeyboardLayoutDefinition.OneUiDarkTheme else KeyboardLayoutDefinition.OneUiLightTheme

    val controller = remember(sessionController, settingsStore, committer, batchCoordinator, autoWordingEngine) {
        sessionController ?: KeyboardSessionController(
            settingsStore = settingsStore,
            committer = committer,
            batchCoordinator = batchCoordinator,
            autoWordingEngine = autoWordingEngine
        )
    }

    var editorUiState by remember(controller) { mutableStateOf(controller.editorUiState) }
    DisposableEffect(controller) {
        val listener: (EditorUiState) -> Unit = { newState ->
            editorUiState = newState
        }
        controller.addUiStateListener(listener)
        onDispose {
            controller.removeUiStateListener(listener)
        }
    }

    val effectiveEnterLabel = if (sessionController != null) editorUiState.enterActionLabel else enterActionLabel

    var language by remember { mutableStateOf(settingsStore.language) }
    val effectiveLanguage = if (sessionController != null) editorUiState.language else language
    var inputType by remember { mutableStateOf(settingsStore.inputType) }
    var oneHandedMode by remember { mutableStateOf(settingsStore.oneHandedMode) }
    var showNumberRow by remember { mutableStateOf(settingsStore.showNumberRow) }
    var heightScalePercent by remember { mutableStateOf(settingsStore.heightScalePercent) }
    var bottomChinPaddingDp by remember { mutableStateOf(settingsStore.bottomChinPaddingDp) }
    var hapticFeedbackEnabled by remember { mutableStateOf(settingsStore.hapticFeedbackEnabled) }
    var keyPopupPreviewEnabled by remember { mutableStateOf(settingsStore.keyPopupPreviewEnabled) }

    DisposableEffect(settingsStore) {
        val listener: (String) -> Unit = { key ->
            when (key) {
                OpenKeySettingsStore.KEY_LANGUAGE -> language = settingsStore.language
                OpenKeySettingsStore.KEY_INPUT_TYPE -> inputType = settingsStore.inputType
                OpenKeySettingsStore.KEY_ONE_HANDED_MODE -> oneHandedMode = settingsStore.oneHandedMode
                OpenKeySettingsStore.KEY_SHOW_NUMBER_ROW -> showNumberRow = settingsStore.showNumberRow
                OpenKeySettingsStore.KEY_HEIGHT_SCALE_PERCENT -> heightScalePercent = settingsStore.heightScalePercent
                OpenKeySettingsStore.KEY_BOTTOM_CHIN_PADDING_DP -> bottomChinPaddingDp = settingsStore.bottomChinPaddingDp
                OpenKeySettingsStore.KEY_HAPTIC_FEEDBACK_ENABLED -> hapticFeedbackEnabled = settingsStore.hapticFeedbackEnabled
                OpenKeySettingsStore.KEY_KEY_POPUP_PREVIEW_ENABLED -> keyPopupPreviewEnabled = settingsStore.keyPopupPreviewEnabled
            }
        }
        settingsStore.addListener(listener)
        onDispose {
            settingsStore.removeListener(listener)
        }
    }

    val gestureController = remember { KeyboardGestureController() }
    var currentLayer by remember { mutableStateOf(KeyboardLayer.QWERTY) }
    var isShifted by remember { mutableStateOf(false) }
    var isCapsLock by remember { mutableStateOf(false) }

    val layoutConfig = PhoneLayoutConfig(
        screenWidthDp = screenWidthDp,
        screenHeightDp = screenHeightDp,
        isLandscape = isLandscape,
        showNumberRow = showNumberRow,
        inputType = inputType,
        heightScalePercent = heightScalePercent,
        bottomChinPaddingDp = bottomChinPaddingDp,
        oneHandedMode = oneHandedMode
    )
    val metrics = PhoneLayoutMetrics.compute(layoutConfig)

    val effectiveLayer = if (editorUiState.isNumericField) KeyboardLayer.NUMPAD else currentLayer
    val layerDef = KeyboardLayoutDefinition.getLayer(
        layer = effectiveLayer,
        isShifted = isShifted || isCapsLock,
        language = effectiveLanguage,
        imeActionLabel = effectiveEnterLabel
    )

    val handleKeyAction: (KeyAction, KeyModel) -> Unit = { action, keyModel ->
        when (action) {
            is KeyAction.CommitChar -> {
                controller.onKeyAction(action)
                if (isShifted && !isCapsLock) {
                    isShifted = false
                }
            }
            is KeyAction.Backspace,
            is KeyAction.Space,
            is KeyAction.SwitchLanguage,
            is KeyAction.Enter -> {
                controller.onKeyAction(action)
            }
            is KeyAction.Shift -> {
                if (!isShifted && !isCapsLock) {
                    isShifted = true
                } else if (isShifted && !isCapsLock) {
                    isCapsLock = true
                } else {
                    isShifted = false
                    isCapsLock = false
                }
            }
            is KeyAction.SwitchLayer -> {
                currentLayer = when (currentLayer) {
                    KeyboardLayer.QWERTY -> KeyboardLayer.SYMBOLS_1
                    KeyboardLayer.SYMBOLS_1 -> {
                        if (keyModel.primaryLabel == "1/2") KeyboardLayer.SYMBOLS_2 else KeyboardLayer.QWERTY
                    }
                    KeyboardLayer.SYMBOLS_2 -> {
                        if (keyModel.primaryLabel == "2/2") KeyboardLayer.SYMBOLS_1 else KeyboardLayer.QWERTY
                    }
                    KeyboardLayer.NUMPAD -> KeyboardLayer.NUMPAD
                }
            }
        }
    }

    val handleCandidateSelected: (WordCandidate) -> Unit = { candidate ->
        controller.onCandidateSelected(candidate)
    }

    val onMoveCursor: (Int) -> Unit = remember(controller) {
        { delta: Int -> controller.onMoveCursor(delta); Unit }
    }
    val onDeleteWord: () -> Unit = remember(controller) {
        { controller.onDeletePreviousWord(); Unit }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(palette.backgroundColor))
    ) {
        // Candidate Strip / Top Toolbar (42dp scaled) isolated from Key Grid recomposition (REQ-014, REQ-025)
        if (!editorUiState.isNumericField) {
            LiveCandidateStripHost(
                autoWordingEngine = autoWordingEngine,
                heightDp = metrics.candidateStripHeightDp,
                palette = palette,
                language = effectiveLanguage,
                inputType = inputType,
                oneHandedMode = metrics.effectiveOneHandedMode,
                onCandidateSelected = handleCandidateSelected,
                onLanguageToggled = {
                    controller.onKeyAction(KeyAction.SwitchLanguage)
                },
                onInputTypeToggled = {
                    val nextType = if (inputType == 0) 1 else 0
                    settingsStore.inputType = nextType
                },
                onOneHandedCycle = {
                    val nextMode = when (oneHandedMode) {
                        OneHandedMode.OFF -> OneHandedMode.LEFT
                        OneHandedMode.LEFT -> OneHandedMode.RIGHT
                        OneHandedMode.RIGHT -> OneHandedMode.OFF
                    }
                    settingsStore.oneHandedMode = nextMode
                },
                onOpenSettings = onOpenSettings
            )
        }

        // Main Keyboard Container (with One-Handed docking)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            // Left gutter if One-Handed RIGHT
            if (metrics.effectiveOneHandedMode == OneHandedMode.RIGHT) {
                OneHandedGutter(
                    widthDp = metrics.sideGutterWidthDp,
                    palette = palette,
                    onSwapSide = { settingsStore.oneHandedMode = OneHandedMode.LEFT },
                    onExpandFull = { settingsStore.oneHandedMode = OneHandedMode.OFF }
                )
            }

            // Active Keyboard Grid
            Column(
                modifier = Modifier
                    .width(metrics.activeGridWidthDp.dp)
            ) {
                val rows = layerDef.rows

                if (editorUiState.isNumericField) {
                    val numpadRowHeight = if (isLandscape) 42f else 58f
                    val numpadMetrics = metrics.copy(alphaRowHeightDp = numpadRowHeight)
                    rows.forEach { rowDef ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(numpadRowHeight.dp)
                        ) {
                            rowDef.keys.forEach { keyModel ->
                                KeyButton(
                                    key = keyModel,
                                    metrics = numpadMetrics,
                                    palette = palette,
                                    hapticFeedbackEnabled = hapticFeedbackEnabled,
                                    keyPopupPreviewEnabled = keyPopupPreviewEnabled,
                                    gestureController = gestureController,
                                    onKeyPressed = { act -> handleKeyAction(act, keyModel) },
                                    onMoveCursor = onMoveCursor,
                                    onDeleteWord = onDeleteWord,
                                    modifier = Modifier.weight(keyModel.weight)
                                )
                            }
                        }
                    }
                } else {
                    // Row 1: Dedicated Number Row (if visible)
                    if (metrics.isNumberRowVisible && rows.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(metrics.numberRowHeightDp.dp)
                        ) {
                            rows[0].keys.forEach { keyModel ->
                                KeyButton(
                                    key = keyModel,
                                    metrics = metrics,
                                    palette = palette,
                                    hapticFeedbackEnabled = hapticFeedbackEnabled,
                                    keyPopupPreviewEnabled = keyPopupPreviewEnabled,
                                    gestureController = gestureController,
                                    onKeyPressed = { act -> handleKeyAction(act, keyModel) },
                                    onMoveCursor = onMoveCursor,
                                    onDeleteWord = onDeleteWord,
                                    modifier = Modifier.weight(keyModel.weight)
                                )
                            }
                        }
                    }

                    // Row 2: Top Letter Row (q..p)
                    if (rows.size > 1) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(metrics.alphaRowHeightDp.dp)
                        ) {
                            rows[1].keys.forEach { keyModel ->
                                KeyButton(
                                    key = keyModel,
                                    metrics = metrics,
                                    palette = palette,
                                    hapticFeedbackEnabled = hapticFeedbackEnabled,
                                    keyPopupPreviewEnabled = keyPopupPreviewEnabled,
                                    gestureController = gestureController,
                                    onKeyPressed = { act -> handleKeyAction(act, keyModel) },
                                    onMoveCursor = onMoveCursor,
                                    onDeleteWord = onDeleteWord,
                                    modifier = Modifier.weight(keyModel.weight)
                                )
                            }
                        }
                    }

                    // Row 3: Home Letter Row (a..l) with 5% side insets
                    if (rows.size > 2) {
                        val row3Def = rows[2]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(metrics.alphaRowHeightDp.dp)
                        ) {
                            if (row3Def.sideInsetFraction > 0f) {
                                Spacer(modifier = Modifier.weight(row3Def.sideInsetFraction))
                            }
                            row3Def.keys.forEach { keyModel ->
                                KeyButton(
                                    key = keyModel,
                                    metrics = metrics,
                                    palette = palette,
                                    hapticFeedbackEnabled = hapticFeedbackEnabled,
                                    keyPopupPreviewEnabled = keyPopupPreviewEnabled,
                                    gestureController = gestureController,
                                    onKeyPressed = { act -> handleKeyAction(act, keyModel) },
                                    onMoveCursor = onMoveCursor,
                                    onDeleteWord = onDeleteWord,
                                    modifier = Modifier.weight(keyModel.weight)
                                )
                            }
                            if (row3Def.sideInsetFraction > 0f) {
                                Spacer(modifier = Modifier.weight(row3Def.sideInsetFraction))
                            }
                        }
                    }

                    // Row 4: Bottom Letter Row (⇧ [15%] + 7 keys [10%] + ⌫ [15%])
                    if (rows.size > 3) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(metrics.alphaRowHeightDp.dp)
                        ) {
                            rows[3].keys.forEach { keyModel ->
                                val displayKey = if (keyModel.action is KeyAction.Shift) {
                                    val shiftLabel = if (isCapsLock) "⇪" else "⇧"
                                    keyModel.copy(primaryLabel = shiftLabel)
                                } else {
                                    keyModel
                                }
                                KeyButton(
                                    key = displayKey,
                                    metrics = metrics,
                                    palette = palette,
                                    hapticFeedbackEnabled = hapticFeedbackEnabled,
                                    keyPopupPreviewEnabled = keyPopupPreviewEnabled,
                                    gestureController = gestureController,
                                    onKeyPressed = { act -> handleKeyAction(act, displayKey) },
                                    onMoveCursor = onMoveCursor,
                                    onDeleteWord = onDeleteWord,
                                    modifier = Modifier.weight(displayKey.weight),
                                    isShiftActive = isShifted || isCapsLock,
                                    isCapsLocked = isCapsLock
                                )
                            }
                        }
                    }

                    // Row 5: Bottom Function Row (!#1 [15%] + , [10%] + Spacebar [50%] + . [10%] + Enter [15%])
                    if (rows.size > 4) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(metrics.bottomRowHeightDp.dp)
                        ) {
                            rows[4].keys.forEach { keyModel ->
                                KeyButton(
                                    key = keyModel,
                                    metrics = metrics,
                                    palette = palette,
                                    hapticFeedbackEnabled = hapticFeedbackEnabled,
                                    keyPopupPreviewEnabled = keyPopupPreviewEnabled,
                                    gestureController = gestureController,
                                    onKeyPressed = { act -> handleKeyAction(act, keyModel) },
                                    onMoveCursor = onMoveCursor,
                                    onDeleteWord = onDeleteWord,
                                    modifier = Modifier.weight(keyModel.weight)
                                )
                            }
                        }
                    }
                }
            }

            // Right gutter if One-Handed LEFT
            if (metrics.effectiveOneHandedMode == OneHandedMode.LEFT) {
                OneHandedGutter(
                    widthDp = metrics.sideGutterWidthDp,
                    palette = palette,
                    onSwapSide = { settingsStore.oneHandedMode = OneHandedMode.RIGHT },
                    onExpandFull = { settingsStore.oneHandedMode = OneHandedMode.OFF }
                )
            }
        }

        // Bottom Chin Padding (14dp default in portrait above gesture navigation bar)
        if (metrics.effectiveBottomChinDp > 0f) {
            Spacer(modifier = Modifier.height(metrics.effectiveBottomChinDp.dp))
        }
    }
}

/**
 * Isolated candidate strip host preserving 120Hz frame budget by isolating
 * candidate state observations from key grid recompositions (REQ-014, ADR-014).
 */
@Composable
private fun LiveCandidateStripHost(
    autoWordingEngine: AutoWordingEngine,
    heightDp: Float,
    palette: com.tuyenmai.openkey.core.ThemePalette,
    language: Int,
    inputType: Int,
    oneHandedMode: OneHandedMode,
    onCandidateSelected: (WordCandidate) -> Unit,
    onLanguageToggled: () -> Unit,
    onInputTypeToggled: () -> Unit,
    onOneHandedCycle: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var candidates by remember { mutableStateOf(autoWordingEngine.activeCandidates) }
    DisposableEffect(autoWordingEngine) {
        val listener: (List<WordCandidate>) -> Unit = { updated ->
            if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    candidates = updated
                }
            } else {
                candidates = updated
            }
        }
        autoWordingEngine.onCandidatesChanged = listener
        onDispose {
            autoWordingEngine.onCandidatesChanged = null
        }
    }

    CandidateStripView(
        candidates = candidates,
        heightDp = heightDp,
        palette = palette,
        language = language,
        inputType = inputType,
        oneHandedMode = oneHandedMode,
        onCandidateSelected = onCandidateSelected,
        onLanguageToggled = onLanguageToggled,
        onInputTypeToggled = onInputTypeToggled,
        onOneHandedCycle = onOneHandedCycle,
        onOpenSettings = onOpenSettings,
        modifier = modifier
    )
}

/**
 * 18% One UI Side Dock Gutter for One-Handed Mode (⇄ Swap Side, ⤢ Expand Full Width).
 */
@Composable
private fun OneHandedGutter(
    widthDp: Float,
    palette: com.tuyenmai.openkey.core.ThemePalette,
    onSwapSide: () -> Unit,
    onExpandFull: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(widthDp.dp)
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .clickable { onSwapSide() }
                .background(Color(palette.functionalKeycapColor), RoundedCornerShape(8.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(20.dp)) {
                val w = size.width
                val h = size.height
                val strokeWidth = 1.8.dp.toPx()
                val tint = Color(palette.primaryTextColor)

                // Top arrow pointing right
                drawLine(
                    color = tint,
                    start = Offset(w * 0.15f, h * 0.32f),
                    end = Offset(w * 0.85f, h * 0.32f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                val rightHead = Path().apply {
                    moveTo(w * 0.62f, h * 0.18f)
                    lineTo(w * 0.85f, h * 0.32f)
                    lineTo(w * 0.62f, h * 0.46f)
                }
                drawPath(rightHead, color = tint, style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))

                // Bottom arrow pointing left
                drawLine(
                    color = tint,
                    start = Offset(w * 0.85f, h * 0.68f),
                    end = Offset(w * 0.15f, h * 0.68f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                val leftHead = Path().apply {
                    moveTo(w * 0.38f, h * 0.54f)
                    lineTo(w * 0.15f, h * 0.68f)
                    lineTo(w * 0.38f, h * 0.82f)
                }
                drawPath(leftHead, color = tint, style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .clickable { onExpandFull() }
                .background(Color(palette.functionalKeycapColor), RoundedCornerShape(8.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(20.dp)) {
                val w = size.width
                val h = size.height
                val strokeWidth = 1.8.dp.toPx()
                val tint = Color(palette.primaryTextColor)

                // Top-right arrow
                val trCorner = Path().apply {
                    moveTo(w * 0.45f, h * 0.18f)
                    lineTo(w * 0.82f, h * 0.18f)
                    lineTo(w * 0.82f, h * 0.55f)
                }
                drawPath(trCorner, color = tint, style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawLine(color = tint, start = Offset(w * 0.82f, h * 0.18f), end = Offset(w * 0.55f, h * 0.45f), strokeWidth = strokeWidth, cap = StrokeCap.Round)

                // Bottom-left arrow
                val blCorner = Path().apply {
                    moveTo(w * 0.55f, h * 0.82f)
                    lineTo(w * 0.18f, h * 0.82f)
                    lineTo(w * 0.18f, h * 0.45f)
                }
                drawPath(blCorner, color = tint, style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawLine(color = tint, start = Offset(w * 0.18f, h * 0.82f), end = Offset(w * 0.45f, h * 0.55f), strokeWidth = strokeWidth, cap = StrokeCap.Round)
            }
        }
    }
}
