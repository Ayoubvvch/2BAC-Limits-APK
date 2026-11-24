package com.ayoub.baclimits.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.ayoub.baclimits.math.LimitsCasEngine
import com.ayoub.baclimits.ui.i18n.AppStrings
import com.ayoub.baclimits.ui.theme.OledColors
import kotlin.math.roundToInt

@Composable
fun ProgressScreen(
    displayName: String,
    username: String,
    stats: StudentStats?,
    strings: AppStrings,
    isFocusLocked: Boolean = false,
    onRefresh: () -> Unit,
    onPracticeTechnique: (String) -> Unit
) {
    val totalSolved = stats?.totalSolved ?: 0
    val accuracy = (stats?.accuracy ?: 0).coerceIn(0, 100)
    val streak = stats?.currentStreak ?: 0
    val bestStreak = stats?.bestStreak ?: streak
    val avgTimeText = stats?.avgSolveTimeSec?.let { "${it.roundToInt()}s" } ?: "—"
    val dueCount = stats?.dueReviewsCount ?: 0
    var roadmapChapter by remember { mutableStateOf("limits") }

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

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        KpiMiniTile(
                            value = bestStreak.toString(),
                            label = strings.bestStreakLabel,
                            accent = OledColors.Primary,
                            modifier = Modifier.weight(1f)
                        )
                        KpiMiniTile(
                            value = avgTimeText,
                            label = strings.lblAvgTime,
                            accent = OledColors.Green,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        if (dueCount > 0) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = OledColors.AmberSoft,
                border = BorderStroke(1.dp, OledColors.Amber.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Schedule,
                        contentDescription = null,
                        tint = OledColors.Amber,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "${strings.dueReviewsLabel}: $dueCount",
                        style = MaterialTheme.typography.labelLarge,
                        color = OledColors.Amber,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Mastery Roadmap Header + Chapter Tabs
        Text(
            text = strings.masteryHeading,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 4.dp)
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = OledColors.Surface,
            border = BorderStroke(1.dp, OledColors.Border)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "limits" to strings.chapterLimits,
                    "sequences" to strings.chapterSequences
                ).forEach { (chapKey, chapLabel) ->
                    val selected = roadmapChapter == chapKey
                    val solvedCount = stats?.solvedByChapter?.get(chapKey) ?: 0
                    val fullLabel = if (solvedCount > 0) "$chapLabel ($solvedCount)" else chapLabel
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clickable { roadmapChapter = chapKey },
                        shape = RoundedCornerShape(9.dp),
                        color = if (selected) OledColors.PrimarySoft else OledColors.SurfaceElevated,
                        border = BorderStroke(1.dp, if (selected) OledColors.Primary else OledColors.Border)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = fullLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) Color(0xFF93C5FD) else OledColors.TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        val chapterTechs = LimitsCasEngine.techniquesForChapter(roadmapChapter)
        val masteryMap = stats?.masteryByTechnique?.associateBy { it.technique }.orEmpty()

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            chapterTechs.forEachIndexed { idx, tech ->
                val item = masteryMap[tech] ?: TechniqueMastery(
                    technique = tech,
                    mastery = 0.0,
                    correct = 0,
                    attempts = 0,
                    chapter = roadmapChapter,
                    effectiveMastery = 0.0,
                    isUnlocked = idx == 0,
                    stage = idx + 1
                )
                TechniqueMasteryCard(
                    title = strings.techniques[tech] ?: tech,
                    item = item,
                    strings = strings,
                    isFocusLocked = isFocusLocked,
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
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = accent,
                maxLines = 1
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
    strings: AppStrings,
    isFocusLocked: Boolean,
    onPractice: () -> Unit
) {
    val pct = item.percent
    val progress by animateFloatAsState(
        targetValue = (pct / 100f).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 600),
        label = "tech_mastery"
    )
    val isLockedStage = !item.isUnlocked
    val barColor = when {
        isLockedStage -> OledColors.TextMuted
        pct >= 75 -> OledColors.Green
        pct >= 45 -> OledColors.Primary
        item.attempts > 0 -> OledColors.Amber
        else -> OledColors.TextMuted
    }
    val unlockHintText = item.unlockHint?.get(strings.langCode).orEmpty()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = OledColors.Surface,
        border = BorderStroke(1.dp, if (isLockedStage) OledColors.Border else OledColors.BorderStrong)
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
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = OledColors.SurfaceElevated,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "${strings.stageLabelPrefix} ${item.stage}",
                                style = MaterialTheme.typography.labelSmall,
                                color = OledColors.TextMuted,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        if (isLockedStage) {
                            Surface(
                                color = OledColors.AmberSoft,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Lock,
                                        contentDescription = null,
                                        tint = OledColors.Amber,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Text(
                                        text = strings.lockedSkillTag,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = OledColors.Amber,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isLockedStage) OledColors.TextSecondary else OledColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    val avgSuffix = item.avgTimeSec?.takeIf { it > 0 }?.let { "  •  ${it.roundToInt()}s" } ?: ""
                    Text(
                        text = "${item.correct}/${item.attempts}  •  $pct%$avgSuffix",
                        style = MaterialTheme.typography.bodySmall,
                        color = OledColors.TextSecondary
                    )
                    if (isLockedStage && unlockHintText.isNotBlank()) {
                        Text(
                            text = unlockHintText,
                            style = MaterialTheme.typography.bodySmall,
                            color = OledColors.Amber,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                FilledTonalButton(
                    onClick = onPractice,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (isFocusLocked) OledColors.SurfaceElevated else OledColors.PrimarySoft,
                        contentColor = if (isFocusLocked) OledColors.Amber else Color(0xFF93C5FD)
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(
                        imageVector = if (isFocusLocked) Icons.Rounded.Lock else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = strings.practiceThisTech,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isFocusLocked) OledColors.Amber else Color(0xFF93C5FD)
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
