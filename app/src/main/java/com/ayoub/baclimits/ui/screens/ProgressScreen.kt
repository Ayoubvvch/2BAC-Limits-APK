package com.ayoub.baclimits.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayoub.baclimits.data.StudentStats
import com.ayoub.baclimits.data.TechniqueMastery
import com.ayoub.baclimits.ui.i18n.AppStrings
import com.ayoub.baclimits.ui.theme.OledColors

@Composable
fun ProgressScreen(
    displayName: String,
    username: String,
    stats: StudentStats?,
    strings: AppStrings,
    onRefresh: () -> Unit,
    onPracticeTechnique: (String) -> Unit
) {
    val totalSolved = stats?.totalSolved ?: 0
    val accuracy = (stats?.accuracy ?: 0).coerceIn(0, 100)
    val streak = stats?.currentStreak ?: 0
    val animatedAcc by animateFloatAsState(
        targetValue = accuracy / 100f,
        animationSpec = tween(durationMillis = 700),
        label = "accuracy_arc"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OledColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Profile Hero Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = OledColors.Surface,
            border = BorderStroke(1.dp, OledColors.BorderStrong)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = OledColors.PrimarySoft,
                        border = BorderStroke(1.5.dp, OledColors.Primary),
                        modifier = Modifier.size(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = displayName.trim().take(1).uppercase().ifEmpty { "?" },
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF93C5FD)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = displayName.ifBlank { username },
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "@$username",
                            style = MaterialTheme.typography.bodySmall,
                            color = OledColors.TextSecondary,
                            maxLines = 1
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = OledColors.AmberSoft,
                        shape = RoundedCornerShape(99.dp),
                        border = BorderStroke(1.dp, OledColors.Amber.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.LocalFireDepartment,
                                contentDescription = null,
                                tint = OledColors.Amber,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = streak.toString(),
                                fontWeight = FontWeight.ExtraBold,
                                color = OledColors.Amber,
                                fontSize = 14.sp
                            )
                        }
                    }
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = strings.refreshStats,
                            tint = OledColors.TextSecondary
                        )
                    }
                }
            }
        }

        // KPI Summary Row + Accuracy Ring
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = OledColors.Surface,
            border = BorderStroke(1.dp, OledColors.Border)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier.size(92.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val stroke = 9.dp.toPx()
                        drawArc(
                            color = OledColors.SurfaceHighlight,
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                        val arcColor = when {
                            accuracy >= 75 -> OledColors.Green
                            accuracy >= 45 -> OledColors.Primary
                            else -> OledColors.Amber
                        }
                        drawArc(
                            color = arcColor,
                            startAngle = -90f,
                            sweepAngle = 360f * animatedAcc,
                            useCenter = false,
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$accuracy%",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = OledColors.TextPrimary
                        )
                        Text(
                            text = strings.lblAccuracy,
                            fontSize = 10.sp,
                            color = OledColors.TextSecondary,
                            maxLines = 1
                        )
                    }
                }

                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    KpiMiniTile(
                        value = totalSolved.toString(),
                        label = strings.lblSolved,
                        accent = OledColors.Teal,
                        modifier = Modifier.weight(1f)
                    )
                    KpiMiniTile(
                        value = streak.toString(),
                        label = strings.lblStreak,
                        accent = OledColors.Amber,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Mastery by Technique
        Text(
            text = strings.masteryHeading,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 4.dp)
        )

        val coreTechs = listOf(
            "poly_factor",
            "sqrt_conjugate",
            "nth_root",
            "add_sub_zero",
            "trig",
            "hybrid"
        )
        val masteryMap = stats?.masteryByTechnique?.associateBy { it.technique }.orEmpty()

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            coreTechs.forEach { tech ->
                val item = masteryMap[tech] ?: TechniqueMastery(tech, 0.0, 0, 0)
                TechniqueMasteryCard(
                    title = strings.techniques[tech] ?: tech,
                    item = item,
                    practiceLabel = strings.practiceThisTech,
                    onPractice = { onPracticeTechnique(tech) }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
    }
}

@Composable
private fun KpiMiniTile(
    value: String,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = OledColors.SurfaceElevated,
        border = BorderStroke(1.dp, OledColors.Border)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = value,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = accent
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = OledColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TechniqueMasteryCard(
    title: String,
    item: TechniqueMastery,
    practiceLabel: String,
    onPractice: () -> Unit
) {
    val pct = item.percent
    val progress by animateFloatAsState(
        targetValue = (pct / 100f).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 600),
        label = "tech_mastery"
    )
    val barColor = when {
        pct >= 75 -> OledColors.Green
        pct >= 45 -> OledColors.Primary
        item.attempts > 0 -> OledColors.Amber
        else -> OledColors.TextMuted
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = OledColors.Surface,
        border = BorderStroke(1.dp, OledColors.Border)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${item.correct}/${item.attempts}  •  $pct%",
                        style = MaterialTheme.typography.bodySmall,
                        color = OledColors.TextSecondary
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                FilledTonalButton(
                    onClick = onPractice,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = OledColors.PrimarySoft,
                        contentColor = Color(0xFF93C5FD)
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = practiceLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF93C5FD)
                    )
                }
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(99.dp)),
                color = barColor,
                trackColor = OledColors.SurfaceHighlight
            )
        }
    }
}
