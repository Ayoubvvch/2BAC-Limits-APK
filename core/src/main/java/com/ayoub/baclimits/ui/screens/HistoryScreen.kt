package com.ayoub.baclimits.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayoub.baclimits.data.HistoryEntry
import com.ayoub.baclimits.math.LimitsCasEngine
import com.ayoub.baclimits.ui.HistoryFilter
import com.ayoub.baclimits.ui.components.KaTeXFormulaView
import com.ayoub.baclimits.ui.i18n.AppStrings
import com.ayoub.baclimits.ui.theme.OledColors
import kotlin.math.roundToInt

@Composable
fun HistoryScreen(
    history: List<HistoryEntry>,
    filter: HistoryFilter,
    strings: AppStrings,
    onSelectFilter: (HistoryFilter) -> Unit,
    onOpenSeed: (String) -> Unit
) {
    val filtered = remember(history, filter) {
        when (filter) {
            HistoryFilter.All -> history
            HistoryFilter.Correct -> history.filter { it.isCorrect == true }
            HistoryFilter.Wrong -> history.filter { it.isCorrect == false }
            HistoryFilter.Pending -> history.filter { it.isCorrect == null }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OledColors.Background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = strings.historyHeading,
                style = MaterialTheme.typography.titleLarge
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val options = listOf(
                    HistoryFilter.All to strings.filterAll,
                    HistoryFilter.Correct to strings.filterCorrect,
                    HistoryFilter.Wrong to strings.filterWrong,
                    HistoryFilter.Pending to strings.filterPending
                )
                options.forEach { (key, label) ->
                    val selected = filter == key
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectFilter(key) },
                        label = { Text(label, maxLines = 1) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = OledColors.PrimarySoft,
                            selectedLabelColor = Color(0xFF93C5FD),
                            containerColor = OledColors.Surface,
                            labelColor = OledColors.TextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            borderColor = OledColors.Border,
                            selectedBorderColor = OledColors.Primary,
                            borderWidth = 1.dp
                        ),
                        shape = RoundedCornerShape(99.dp)
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(99.dp),
                        color = OledColors.SurfaceElevated,
                        border = BorderStroke(1.dp, OledColors.Border),
                        modifier = Modifier.size(54.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.History,
                                contentDescription = null,
                                tint = OledColors.TextMuted,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                    Text(
                        text = strings.noHistory,
                        style = MaterialTheme.typography.bodyMedium,
                        color = OledColors.TextSecondary
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { "${it.seed}_${it.isCorrect}" }) { item ->
                    HistoryItemCard(
                        entry = item,
                        strings = strings,
                        onClick = { onOpenSeed(item.seed) }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(18.dp))
                }
            }
        }
    }
}

@Composable
private fun HistoryItemCard(
    entry: HistoryEntry,
    strings: AppStrings,
    onClick: () -> Unit
) {
    val (statusLabel, accentColor, softBg) = when (entry.isCorrect) {
        true -> Triple(strings.statusOk, OledColors.Green, OledColors.GreenSoft)
        false -> Triple(strings.statusFail, OledColors.Red, OledColors.RedSoft)
        null -> Triple(strings.statusPending, OledColors.Amber, OledColors.AmberSoft)
    }

    // Never reveal or regenerate math formulas for exercises still behind the Start Gate!
    val effectiveLatex = remember(entry.seed, entry.fullLimitLatex, entry.isRevealed) {
        if (!entry.isRevealed) {
            ""
        } else if (entry.fullLimitLatex.isNotBlank()) {
            entry.fullLimitLatex
        } else {
            try {
                LimitsCasEngine.generate(
                    requestedTechnique = entry.technique,
                    requestedDifficulty = entry.difficulty,
                    explicitSeed = entry.seed,
                    chapter = entry.resolvedChapter
                ).fullLimitLatex
            } catch (_: Exception) {
                ""
            }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = OledColors.Surface,
        border = BorderStroke(1.dp, OledColors.Border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(accentColor)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
                            color = softBg,
                            shape = RoundedCornerShape(99.dp)
                        ) {
                            Text(
                                text = statusLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = accentColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        Surface(
                            color = OledColors.SurfaceElevated,
                            shape = RoundedCornerShape(99.dp)
                        ) {
                            Text(
                                text = strings.techniques[entry.technique] ?: entry.technique,
                                style = MaterialTheme.typography.labelSmall,
                                color = OledColors.TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        if (entry.elapsedSec > 0.0) {
                            Surface(
                                color = OledColors.SurfaceElevated,
                                shape = RoundedCornerShape(99.dp)
                            ) {
                                Text(
                                    text = "${entry.elapsedSec.roundToInt()}s",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = OledColors.Teal,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }
                        }
                        val fluCode = entry.fluencyCode
                        if (!fluCode.isNullOrBlank()) {
                            val fluText = strings.fluencyLabels[fluCode] ?: fluCode
                            Surface(
                                color = OledColors.PrimarySoft,
                                shape = RoundedCornerShape(99.dp)
                            ) {
                                Text(
                                    text = fluText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF93C5FD),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = strings.openExercise,
                        tint = OledColors.Primary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Mathematical formula rendered with KaTeX (or protected lock state if not revealed yet)
                Surface(
                    color = OledColors.Background,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, OledColors.Border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (!entry.isRevealed) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 12.dp),
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
                                text = strings.hiddenHistoryLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = OledColors.TextSecondary
                            )
                        }
                    } else if (effectiveLatex.isNotBlank()) {
                        KaTeXFormulaView(
                            latex = effectiveLatex,
                            displayMode = true,
                            fontPx = 18f,
                            minScale = 0.72f,
                            centerWhenFits = false,
                            minHeightDp = 50,
                            onTap = onClick
                        )
                    } else {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(
                                text = entry.seed,
                                color = OledColors.TextPrimary,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                Text(
                    text = entry.seed,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = OledColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
