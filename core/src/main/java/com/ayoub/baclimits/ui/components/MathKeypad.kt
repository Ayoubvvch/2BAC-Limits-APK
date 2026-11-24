package com.ayoub.baclimits.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayoub.baclimits.ui.theme.OledColors

private enum class KeyStyle {
    Digit,
    Function,
    Operator,
    Clear,
    Backspace,
    Submit
}

@Composable
fun MathKeypad(
    onInsertToken: (token: String, cursorBackOffset: Int) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
    canSubmit: Boolean,
    answerKind: String = "constant",
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val isExprN = answerKind == "expression_n"

    fun triggerInsert(token: String, cursorBack: Int = 0) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onInsertToken(token, cursorBack)
    }

    // Mathematical keypad order is always left-to-right even in Arabic UI
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            color = OledColors.Surface,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, OledColors.Border)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (isExprN) {
                    // Sequences expression_n primary strip: n, ^n, ln, e, e^(), π
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        PadKey("n", KeyStyle.Function, 35) { triggerInsert("n", 0) }
                        PadKey("^n", KeyStyle.Function, 35) { triggerInsert("^n", 0) }
                        PadKey("ln()", KeyStyle.Function, 35) { triggerInsert("ln()", 1) }
                        PadKey("e", KeyStyle.Function, 35) { triggerInsert("e", 0) }
                        PadKey("e^()", KeyStyle.Function, 35) { triggerInsert("e^()", 1) }
                        PadKey("π", KeyStyle.Function, 35) { triggerInsert("π", 0) }
                    }
                    // Row 2: roots, infinities, parens
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        PadKey("√()", KeyStyle.Function, 35) { triggerInsert("√()", 1) }
                        PadKey("∛()", KeyStyle.Function, 35) { triggerInsert("∛()", 1) }
                        PadKey("+∞", KeyStyle.Function, 35) { triggerInsert("+inf", 0) }
                        PadKey("-∞", KeyStyle.Function, 35) { triggerInsert("-inf", 0) }
                        PadKey("(", KeyStyle.Operator, 35) { triggerInsert("(", 0) }
                        PadKey(")", KeyStyle.Operator, 35) { triggerInsert(")", 0) }
                    }
                    // Row 3: trig, abs, powers
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        PadKey("sin", KeyStyle.Function, 35) { triggerInsert("sin()", 1) }
                        PadKey("cos", KeyStyle.Function, 35) { triggerInsert("cos()", 1) }
                        PadKey("tan", KeyStyle.Function, 35) { triggerInsert("tan()", 1) }
                        PadKey("| |", KeyStyle.Function, 35) { triggerInsert("abs()", 1) }
                        PadKey("^2", KeyStyle.Operator, 35) { triggerInsert("^2", 0) }
                        PadKey("^", KeyStyle.Operator, 35) { triggerInsert("^", 0) }
                    }
                } else {
                    // Constant limits Row 1: Roots, Infinities, Pi
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        PadKey("√()", KeyStyle.Function, 35) { triggerInsert("√()", 1) }
                        PadKey("∛()", KeyStyle.Function, 35) { triggerInsert("∛()", 1) }
                        PadKey("⁴√()", KeyStyle.Function, 35) { triggerInsert("⁴√()", 1) }
                        PadKey("+∞", KeyStyle.Function, 35) { triggerInsert("+inf", 0) }
                        PadKey("-∞", KeyStyle.Function, 35) { triggerInsert("-inf", 0) }
                        PadKey("π", KeyStyle.Function, 35) { triggerInsert("π", 0) }
                    }
                    // Row 2: ln, e, e^(), parens, ^
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        PadKey("ln()", KeyStyle.Function, 35) { triggerInsert("ln()", 1) }
                        PadKey("e", KeyStyle.Function, 35) { triggerInsert("e", 0) }
                        PadKey("e^()", KeyStyle.Function, 35) { triggerInsert("e^()", 1) }
                        PadKey("(", KeyStyle.Operator, 35) { triggerInsert("(", 0) }
                        PadKey(")", KeyStyle.Operator, 35) { triggerInsert(")", 0) }
                        PadKey("^", KeyStyle.Operator, 35) { triggerInsert("^", 0) }
                    }
                    // Row 3: Trig, abs, quick powers
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        PadKey("sin", KeyStyle.Function, 35) { triggerInsert("sin()", 1) }
                        PadKey("cos", KeyStyle.Function, 35) { triggerInsert("cos()", 1) }
                        PadKey("tan", KeyStyle.Function, 35) { triggerInsert("tan()", 1) }
                        PadKey("| |", KeyStyle.Function, 35) { triggerInsert("abs()", 1) }
                        PadKey("^2", KeyStyle.Operator, 35) { triggerInsert("^2", 0) }
                        PadKey("^3", KeyStyle.Operator, 35) { triggerInsert("^3", 0) }
                    }
                }

                // Row 4: 7 8 9 ÷ × Backspace
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    PadKey("7", KeyStyle.Digit) { triggerInsert("7") }
                    PadKey("8", KeyStyle.Digit) { triggerInsert("8") }
                    PadKey("9", KeyStyle.Digit) { triggerInsert("9") }
                    PadKey("/", KeyStyle.Operator) { triggerInsert("/") }
                    PadKey("×", KeyStyle.Operator) { triggerInsert("*") }
                    IconPadKey(style = KeyStyle.Backspace) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onBackspace()
                    }
                }

                // Row 5: 4 5 6 + - Clear
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    PadKey("4", KeyStyle.Digit) { triggerInsert("4") }
                    PadKey("5", KeyStyle.Digit) { triggerInsert("5") }
                    PadKey("6", KeyStyle.Digit) { triggerInsert("6") }
                    PadKey("+", KeyStyle.Operator) { triggerInsert("+") }
                    PadKey("-", KeyStyle.Operator) { triggerInsert("-") }
                    PadKey("C", KeyStyle.Clear) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClear()
                    }
                }

                // Row 6: 1 2 3 0 . Submit
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    PadKey("1", KeyStyle.Digit) { triggerInsert("1") }
                    PadKey("2", KeyStyle.Digit) { triggerInsert("2") }
                    PadKey("3", KeyStyle.Digit) { triggerInsert("3") }
                    PadKey("0", KeyStyle.Digit) { triggerInsert("0") }
                    PadKey(".", KeyStyle.Digit) { triggerInsert(".") }
                    SubmitPadKey(enabled = canSubmit) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSubmit()
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.PadKey(
    label: String,
    style: KeyStyle,
    heightDp: Int = 42,
    onClick: () -> Unit
) {
    val (bg, fg, border) = when (style) {
        KeyStyle.Digit -> Triple(OledColors.SurfaceHighlight, OledColors.TextPrimary, OledColors.Border)
        KeyStyle.Function -> Triple(OledColors.PrimarySoft, Color(0xFF93C5FD), Color(0xFF1E3A8A))
        KeyStyle.Operator -> Triple(OledColors.SurfaceElevated, OledColors.Teal, OledColors.BorderStrong)
        KeyStyle.Clear -> Triple(OledColors.RedSoft, OledColors.Red, Color(0xFF5B1D24))
        KeyStyle.Backspace -> Triple(OledColors.AmberSoft, OledColors.Amber, Color(0xFF5E4312))
        KeyStyle.Submit -> Triple(OledColors.Primary, Color.White, OledColors.Primary)
    }

    Button(
        onClick = onClick,
        modifier = Modifier
            .weight(1f)
            .height(heightDp.dp),
        shape = RoundedCornerShape(11.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = bg,
            contentColor = fg
        ),
        border = BorderStroke(1.dp, border),
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp)
    ) {
        Text(
            text = label,
            fontSize = if (label.length >= 3) 12.sp else 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun RowScope.IconPadKey(
    style: KeyStyle,
    onClick: () -> Unit
) {
    val bg = if (style == KeyStyle.Backspace) OledColors.AmberSoft else OledColors.SurfaceElevated
    val fg = if (style == KeyStyle.Backspace) OledColors.Amber else OledColors.TextPrimary
    val border = if (style == KeyStyle.Backspace) Color(0xFF5E4312) else OledColors.Border

    Button(
        onClick = onClick,
        modifier = Modifier
            .weight(1f)
            .height(42.dp),
        shape = RoundedCornerShape(11.dp),
        colors = ButtonDefaults.buttonColors(containerColor = bg, contentColor = fg),
        border = BorderStroke(1.dp, border),
        contentPadding = PaddingValues(0.dp)
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.Backspace,
            contentDescription = "Backspace",
            modifier = Modifier.height(18.dp)
        )
    }
}

@Composable
private fun RowScope.SubmitPadKey(
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .weight(1f)
            .height(42.dp),
        shape = RoundedCornerShape(11.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = OledColors.Green,
            contentColor = Color.Black,
            disabledContainerColor = OledColors.SurfaceElevated,
            disabledContentColor = OledColors.TextMuted
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.Check,
            contentDescription = "Submit",
            modifier = Modifier.height(20.dp)
        )
    }
}
