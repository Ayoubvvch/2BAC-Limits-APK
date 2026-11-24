package com.ayoub.baclimits.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.KeyboardHide
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalTextInputService
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayoub.baclimits.ui.BacLimitsUiState
import com.ayoub.baclimits.ui.components.KaTeXFormulaView
import com.ayoub.baclimits.ui.components.KaTeXProseView
import com.ayoub.baclimits.ui.components.KaTeXStepsView
import com.ayoub.baclimits.ui.components.MathKeypad
import com.ayoub.baclimits.ui.i18n.AppStrings
import com.ayoub.baclimits.ui.theme.OledColors
import java.util.Locale

private fun formatChronoClock(seconds: Double): String {
    val totalSec = seconds.toInt().coerceAtLeast(0)
    val mins = totalSec / 60
    val secs = totalSec % 60
    return String.format(Locale.US, "%02d:%02d", mins, secs)
}

@Composable
fun PracticeScreen(
    ui: BacLimitsUiState,
    strings: AppStrings,
    onSelectChapter: (String) -> Unit,
    onOpenTechniqueSheet: () -> Unit,
    onOpenDifficultySheet: () -> Unit,
    onNewExercise: () -> Unit,
    onOpenSeedDialog: () -> Unit,
    onStartExercise: () -> Unit,
    onPauseOrResumeChrono: () -> Unit,
    onOpenFullscreenFormula: (String) -> Unit,
    onRequestHint: () -> Unit,
    onOpenSolution: () -> Unit,
    onAnswerChange: (TextFieldValue) -> Unit,
    onInsertToken: (String, Int) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onSubmitAnswer: () -> Unit,
    onToggleSystemKeyboard: () -> Unit,
    onSeedCopied: (String) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    var formulaOverflows by remember(ui.exercise?.seed) { mutableStateOf(false) }

    val ex = ui.exercise

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OledColors.Background)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Chapter Switcher Segmented Bar (Limites / Suites)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = OledColors.Surface,
            border = BorderStroke(1.dp, OledColors.Border)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ChapterTabPill(
                    label = strings.chapterLimits,
                    selected = ui.selectedChapter != "sequences",
                    locked = ui.isFocusLocked && ui.selectedChapter == "sequences",
                    modifier = Modifier.weight(1f),
                    onClick = { onSelectChapter("limits") }
                )
                ChapterTabPill(
                    label = strings.chapterSequences,
                    selected = ui.selectedChapter == "sequences",
                    locked = ui.isFocusLocked && ui.selectedChapter != "sequences",
                    modifier = Modifier.weight(1f),
                    onClick = { onSelectChapter("sequences") }
                )
            }
        }

        // Control Strip Row 1: Technique + Difficulty selectors
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SelectorChip(
                text = strings.techniques[ui.selectedTechnique] ?: ui.selectedTechnique,
                icon = Icons.Rounded.Tune,
                modifier = Modifier.weight(1.35f),
                onClick = onOpenTechniqueSheet
            )
            SelectorChip(
                text = strings.difficulties[ui.selectedDifficulty] ?: ui.selectedDifficulty.toString(),
                icon = null,
                modifier = Modifier.weight(1f),
                onClick = onOpenDifficultySheet
            )
        }

        // Control Strip Row 2: New Exercise + Seed Code
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onNewExercise,
                enabled = !ui.isLoadingExercise,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                shape = RoundedCornerShape(13.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (ui.isFocusLocked) OledColors.SurfaceElevated else OledColors.Primary,
                    contentColor = if (ui.isFocusLocked) OledColors.Amber else Color.White
                ),
                border = if (ui.isFocusLocked) BorderStroke(1.dp, OledColors.Amber.copy(alpha = 0.45f)) else null
            ) {
                if (ui.isLoadingExercise) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                } else {
                    Icon(
                        imageVector = if (ui.isFocusLocked) Icons.Rounded.Lock else Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = if (ui.isLoadingExercise) strings.loadingExercise else if (ui.isFocusLocked) "${strings.newExercise} (${strings.focusLockShort})" else strings.newExercise,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            OutlinedButton(
                onClick = onOpenSeedDialog,
                modifier = Modifier.height(44.dp),
                shape = RoundedCornerShape(13.dp),
                border = BorderStroke(1.dp, OledColors.BorderStrong),
                contentPadding = PaddingValues(horizontal = 14.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Tag,
                    contentDescription = null,
                    tint = OledColors.Teal,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = strings.seedCodeBtn,
                    color = OledColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }

        // Focus Lock Warning Strip
        if (ui.isFocusLocked) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = OledColors.AmberSoft,
                border = BorderStroke(1.dp, OledColors.Amber.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = OledColors.Amber,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = strings.focusLockBanner,
                        style = MaterialTheme.typography.bodySmall,
                        color = OledColors.Amber,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Adaptive Coach Insight Banner
        val coach = ex?.coach
        val coachReasonText = coach?.reason?.get(ui.lang).orEmpty()
        if (coach != null && coachReasonText.isNotBlank()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = OledColors.TealSoft,
                border = BorderStroke(1.dp, OledColors.Teal.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = OledColors.Background.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = strings.coachModes[coach.mode] ?: coach.mode,
                            style = MaterialTheme.typography.labelSmall,
                            color = OledColors.Teal,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        KaTeXProseView(
                            text = coachReasonText,
                            lang = ui.lang,
                            fontPx = 13f,
                            colorHex = "#e2e8f0",
                            fallbackColor = OledColors.TextPrimary,
                            minHeightDp = 20
                        )
                    }
                }
            }
        }

        // Focal Exercise Card
        if (ex != null) {
            val diffColor = when (ex.difficulty) {
                3 -> OledColors.Red
                2 -> OledColors.Amber
                else -> OledColors.Green
            }
            val diffBg = when (ex.difficulty) {
                3 -> OledColors.RedSoft
                2 -> OledColors.AmberSoft
                else -> OledColors.GreenSoft
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = OledColors.Surface,
                border = BorderStroke(1.dp, OledColors.BorderStrong)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Metadata + Seed + Expand Icon (Zoom visible only after Start reveals formula)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = OledColors.PrimarySoft,
                                shape = RoundedCornerShape(99.dp),
                                border = BorderStroke(1.dp, OledColors.Primary.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = strings.techniques[ex.technique] ?: ex.technique,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF93C5FD),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                )
                            }
                            Surface(
                                color = diffBg,
                                shape = RoundedCornerShape(99.dp)
                            ) {
                                Text(
                                    text = strings.diffBadges[ex.difficulty] ?: "L${ex.difficulty}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = diffColor,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = OledColors.SurfaceElevated,
                                border = BorderStroke(1.dp, OledColors.Border),
                                modifier = Modifier.clickable {
                                    clipboard.setText(AnnotatedString(ex.seed))
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onSeedCopied("${strings.seedCopied}: ${ex.seed}")
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ContentCopy,
                                        contentDescription = null,
                                        tint = OledColors.TextSecondary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = ex.seed.takeLast(8),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = OledColors.TextSecondary,
                                        maxLines = 1
                                    )
                                }
                            }

                            if (ex.isRevealed && ex.fullLimitLatex.isNotBlank()) {
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(
                                    onClick = { onOpenFullscreenFormula(ex.fullLimitLatex) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ZoomOutMap,
                                        contentDescription = strings.expandFormula,
                                        tint = OledColors.Primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Chrono Stopwatch Header Strip
                    val tState = ex.timer.state
                    val chronoAccent = when (tState) {
                        "running" -> OledColors.Primary
                        "paused" -> OledColors.Amber
                        "completed" -> OledColors.Green
                        else -> OledColors.TextSecondary
                    }
                    val chronoLabel = when (tState) {
                        "running" -> strings.chronoRunningStatus
                        "paused" -> strings.chronoPausedStatus
                        "completed" -> strings.chronoCompletedStatus
                        else -> strings.chronoReadyStatus
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = OledColors.SurfaceElevated,
                        border = BorderStroke(1.dp, chronoAccent.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Timer,
                                    contentDescription = null,
                                    tint = chronoAccent,
                                    modifier = Modifier.size(17.dp)
                                )
                                Text(
                                    text = formatChronoClock(ui.chronoDisplaySec),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = chronoAccent
                                )
                                Column {
                                    Text(
                                        text = chronoLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = OledColors.TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (!ex.isCompleted) {
                                        Text(
                                            text = "${strings.targetTimePrefix}: ${ex.timer.targetFastSec}s",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = OledColors.TextMuted,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }

                            if (ex.isRevealed && !ex.isCompleted) {
                                val isPaused = tState == "paused"
                                FilledTonalButton(
                                    onClick = onPauseOrResumeChrono,
                                    enabled = !ui.isPausingExercise,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = if (isPaused) OledColors.PrimarySoft else OledColors.AmberSoft,
                                        contentColor = if (isPaused) Color(0xFF93C5FD) else OledColors.Amber
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isPaused) strings.resumeChronoBtn else strings.pauseChronoBtn,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else if (ex.isCompleted && ui.currentFluency != null) {
                                val flu = ui.currentFluency
                                val (fluBg, fluColor) = when (flu.code) {
                                    "fast" -> OledColors.GreenSoft to OledColors.Green
                                    "good" -> OledColors.TealSoft to OledColors.Teal
                                    "slow", "assisted" -> OledColors.AmberSoft to OledColors.Amber
                                    else -> OledColors.RedSoft to OledColors.Red
                                }
                                Surface(
                                    color = fluBg,
                                    shape = RoundedCornerShape(99.dp),
                                    border = BorderStroke(1.dp, fluColor.copy(alpha = 0.45f))
                                ) {
                                    Text(
                                        text = flu.label.get(ui.lang).ifBlank { strings.fluencyLabels[flu.code] ?: flu.code },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = fluColor,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (!ex.isRevealed) {
                        // Hidden Start Gate Card
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(15.dp),
                            color = OledColors.Background,
                            border = BorderStroke(1.dp, OledColors.Primary.copy(alpha = 0.45f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Lock,
                                    contentDescription = null,
                                    tint = OledColors.Primary,
                                    modifier = Modifier.size(28.dp)
                                )
                                Text(
                                    text = strings.startGateTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = OledColors.TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = strings.startGateDesc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OledColors.TextSecondary
                                )
                                Button(
                                    onClick = onStartExercise,
                                    enabled = !ui.isStartingExercise,
                                    shape = RoundedCornerShape(13.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = OledColors.Primary,
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(46.dp)
                                ) {
                                    if (ui.isStartingExercise) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = Color.White
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Rounded.PlayArrow,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = strings.startExerciseBtn,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 15.sp
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        val promptText = ex.prompt?.get(ui.lang)?.takeIf { it.isNotBlank() }
                            ?: if (ex.chapter == "sequences") strings.promptCalculateSequence else strings.promptCalculateLimit
                        KaTeXProseView(
                            text = promptText,
                            lang = ui.lang,
                            fontPx = 13.5f,
                            colorHex = "#9ca3af",
                            fallbackColor = OledColors.TextSecondary,
                            minHeightDp = 20
                        )

                        // Adaptive Math Formula Box (Never clips, fits or left-anchors cleanly + tap to zoom)
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(15.dp),
                            color = OledColors.Background,
                            border = BorderStroke(
                                1.dp,
                                if (formulaOverflows) OledColors.Primary.copy(alpha = 0.45f) else OledColors.Border
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                KaTeXFormulaView(
                                    latex = ex.fullLimitLatex,
                                    displayMode = true,
                                    fontPx = 21f,
                                    minScale = 0.72f,
                                    centerWhenFits = true,
                                    minHeightDp = 72,
                                    onOverflowChanged = { formulaOverflows = it },
                                    onTap = { onOpenFullscreenFormula(ex.fullLimitLatex) }
                                )

                                if (formulaOverflows) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onOpenFullscreenFormula(ex.fullLimitLatex) }
                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.SwapHoriz,
                                            contentDescription = null,
                                            tint = OledColors.Teal,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = strings.scrollHint,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = OledColors.Teal
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (ex.isRevealed) {
                // Assist Actions Row: Hint & Solution
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val hintsRemaining = (ex.totalHints - ui.hintsShown.size).coerceAtLeast(0)
                    OutlinedButton(
                        onClick = onRequestHint,
                        enabled = !ui.isLoadingHint && hintsRemaining > 0 && !ex.isCompleted,
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            1.dp,
                            if (hintsRemaining > 0 && !ex.isCompleted) OledColors.Amber.copy(alpha = 0.55f) else OledColors.Border
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp)
                    ) {
                        if (ui.isLoadingHint) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = OledColors.Amber
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Lightbulb,
                                contentDescription = null,
                                tint = if (hintsRemaining > 0 && !ex.isCompleted) OledColors.Amber else OledColors.TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${strings.hintBtn} (${ui.hintsShown.size}/${ex.totalHints})",
                            color = if (hintsRemaining > 0 && !ex.isCompleted) OledColors.Amber else OledColors.TextMuted,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    OutlinedButton(
                        onClick = onOpenSolution,
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, OledColors.BorderStrong),
                        contentPadding = PaddingValues(horizontal = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                            contentDescription = null,
                            tint = OledColors.Primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = strings.solutionBtn,
                            color = OledColors.TextPrimary,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Inline Unlocked Hints (with KaTeX math support)
                if (ui.hintsShown.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = OledColors.Surface,
                        border = BorderStroke(1.dp, OledColors.Amber.copy(alpha = 0.35f))
                    ) {
                        KaTeXStepsView(
                            lang = ui.lang,
                            hints = ui.hintsShown,
                            steps = emptyList(),
                            minHeightDp = 58,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                // Grade Feedback Banner
                AnimatedVisibility(
                    visible = ui.lastGrade != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    val grade = ui.lastGrade
                    if (grade != null) {
                        val isOk = grade.isCorrect
                        val bg = if (isOk) OledColors.GreenSoft else OledColors.RedSoft
                        val border = if (isOk) OledColors.Green else OledColors.Red
                        val icon = if (isOk) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = bg,
                            border = BorderStroke(1.dp, border.copy(alpha = 0.6f))
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(imageVector = icon, contentDescription = null, tint = border)
                                    Box(modifier = Modifier.weight(1f)) {
                                        KaTeXProseView(
                                            text = grade.message.get(ui.lang),
                                            lang = ui.lang,
                                            fontPx = 14f,
                                            colorHex = "#f8fafc",
                                            fallbackColor = OledColors.TextPrimary,
                                            minHeightDp = 22
                                        )
                                    }
                                }
                                if (isOk && ui.solution != null) {
                                    OutlinedButton(
                                        onClick = onOpenSolution,
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, OledColors.Green.copy(alpha = 0.5f)),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Text(
                                            text = strings.viewHintsSolution,
                                            color = OledColors.Green,
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (ex.isCompleted) {
                    Button(
                        onClick = onNewExercise,
                        enabled = !ui.isLoadingExercise,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OledColors.Primary,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = strings.nextAfterComplete,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                } else if (ex.timer.state == "running") {
                    // Pause-First Notice while thinking timer runs
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = OledColors.Surface,
                        border = BorderStroke(1.dp, OledColors.Amber.copy(alpha = 0.45f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = strings.pauseFirstText,
                                style = MaterialTheme.typography.bodySmall,
                                color = OledColors.TextSecondary
                            )
                            Button(
                                onClick = onPauseOrResumeChrono,
                                enabled = !ui.isPausingExercise,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = OledColors.AmberSoft,
                                    contentColor = OledColors.Amber
                                ),
                                border = BorderStroke(1.dp, OledColors.Amber.copy(alpha = 0.55f))
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Pause,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = strings.pauseNowQuickBtn,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                } else {
                    // Answer Section + Keyboard Switcher + Live Preview + Keypad (unlocked on Pause)
                    val isExprN = ex.answerKind == "expression_n"
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = OledColors.Surface,
                        border = BorderStroke(1.dp, OledColors.Border)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (isExprN) strings.answerLabelExprN else strings.answerLabel,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = OledColors.TextPrimary
                                )

                                Surface(
                                    shape = RoundedCornerShape(99.dp),
                                    color = if (ui.showSystemKeyboard) OledColors.PrimarySoft else OledColors.SurfaceElevated,
                                    border = BorderStroke(
                                        1.dp,
                                        if (ui.showSystemKeyboard) OledColors.Primary else OledColors.Border
                                    ),
                                    modifier = Modifier.clickable { onToggleSystemKeyboard() }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (ui.showSystemKeyboard) Icons.Rounded.KeyboardHide else Icons.Rounded.Keyboard,
                                            contentDescription = null,
                                            tint = if (ui.showSystemKeyboard) OledColors.Primary else OledColors.TextSecondary,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = strings.toggleKeyboardHint,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (ui.showSystemKeyboard) Color(0xFF93C5FD) else OledColors.TextSecondary
                                        )
                                    }
                                }
                            }

                            // Mathematical input is always LTR
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                val fieldContent: @Composable () -> Unit = {
                                    OutlinedTextField(
                                        value = ui.answerField,
                                        onValueChange = onAnswerChange,
                                        placeholder = {
                                            Text(
                                                text = if (isExprN) strings.answerPlaceholderExprN else strings.answerPlaceholder,
                                                color = OledColors.TextMuted,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                        keyboardActions = KeyboardActions(
                                            onDone = {
                                                if (ui.answerField.text.trim().isNotEmpty() && !ui.isSubmitting) {
                                                    onSubmitAnswer()
                                                }
                                            }
                                        ),
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = OledColors.Primary,
                                            unfocusedBorderColor = OledColors.BorderStrong,
                                            focusedTextColor = OledColors.TextPrimary,
                                            unfocusedTextColor = OledColors.TextPrimary,
                                            focusedContainerColor = OledColors.Background,
                                            unfocusedContainerColor = OledColors.Background
                                        )
                                    )
                                }

                                if (!ui.showSystemKeyboard) {
                                    @Suppress("DEPRECATION")
                                    CompositionLocalProvider(LocalTextInputService provides null) {
                                        fieldContent()
                                    }
                                } else {
                                    fieldContent()
                                }
                            }

                            // Live KaTeX Preview Strip
                            if (ui.livePreviewLatex.isNotBlank()) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    color = OledColors.Background,
                                    border = BorderStroke(1.dp, OledColors.Border)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = strings.livePreview,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = OledColors.Teal
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Box(modifier = Modifier.weight(1f)) {
                                            KaTeXFormulaView(
                                                latex = ui.livePreviewLatex,
                                                displayMode = false,
                                                fontPx = 17f,
                                                minScale = 0.72f,
                                                centerWhenFits = false,
                                                minHeightDp = 34
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (!ui.showSystemKeyboard) {
                        MathKeypad(
                            onInsertToken = onInsertToken,
                            onBackspace = onBackspace,
                            onClear = onClear,
                            onSubmit = onSubmitAnswer,
                            canSubmit = ui.answerField.text.trim().isNotEmpty() && !ui.isSubmitting,
                            answerKind = ex.answerKind
                        )
                    }

                    Button(
                        onClick = onSubmitAnswer,
                        enabled = ui.answerField.text.trim().isNotEmpty() && !ui.isSubmitting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OledColors.Green,
                            contentColor = Color.Black,
                            disabledContainerColor = OledColors.SurfaceElevated,
                            disabledContentColor = OledColors.TextMuted
                        )
                    ) {
                        if (ui.isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.2.dp,
                                color = Color.Black
                            )
                        } else {
                            Text(
                                text = strings.submitAnswer,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }
        } else {
            // Empty / Loading exercise state
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                shape = RoundedCornerShape(20.dp),
                color = OledColors.Surface,
                border = BorderStroke(1.dp, OledColors.Border)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (ui.isLoadingExercise) {
                        CircularProgressIndicator(color = OledColors.Primary)
                        Text(strings.loadingExercise, style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Button(
                            onClick = onNewExercise,
                            colors = ButtonDefaults.buttonColors(containerColor = OledColors.Primary)
                        ) {
                            Text(strings.newExercise, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
    }
}

@Composable
private fun ChapterTabPill(
    label: String,
    selected: Boolean,
    locked: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(38.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(10.dp),
        color = if (selected) OledColors.PrimarySoft else OledColors.SurfaceElevated,
        border = BorderStroke(1.dp, if (selected) OledColors.Primary else OledColors.Border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (locked) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = OledColors.Amber,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) Color(0xFF93C5FD) else OledColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SelectorChip(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(42.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(13.dp),
        color = OledColors.Surface,
        border = BorderStroke(1.dp, OledColors.BorderStrong)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = OledColors.Primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelMedium,
                    color = OledColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Rounded.ArrowDropDown,
                contentDescription = null,
                tint = OledColors.TextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
