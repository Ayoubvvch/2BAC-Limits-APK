package com.ayoub.baclimits.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayoub.baclimits.core.BuildConfig
import com.ayoub.baclimits.data.SyncStatusSnapshot
import com.ayoub.baclimits.ui.components.KaTeXFormulaView
import com.ayoub.baclimits.ui.i18n.AppStrings
import com.ayoub.baclimits.ui.theme.OledColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LoginScreen(
    strings: AppStrings,
    syncState: SyncStatusSnapshot,
    recentUsers: List<String>,
    isLoggingIn: Boolean,
    loginError: String?,
    onLogin: (String) -> Unit,
    onToggleLanguage: () -> Unit,
    onOpenSyncSheet: () -> Unit
) {
    var usernameInput by remember { mutableStateOf(recentUsers.firstOrNull().orEmpty()) }
    val syncDotColor = remember(syncState.colorHex) {
        try {
            Color(android.graphics.Color.parseColor(syncState.colorHex))
        } catch (_: Exception) {
            OledColors.Primary
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OledColors.Background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Utility Row: App badge + Sync status + Language toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = RoundedCornerShape(99.dp),
                    color = OledColors.Surface,
                    border = BorderStroke(1.dp, OledColors.Border),
                    modifier = Modifier.clickable { onOpenSyncSheet() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(syncDotColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = syncState.label.replace("●", "").replace("⚠", "").trim(),
                            style = MaterialTheme.typography.labelSmall,
                            color = syncDotColor,
                            maxLines = 1
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(99.dp),
                    color = OledColors.SurfaceElevated,
                    border = BorderStroke(1.dp, OledColors.BorderStrong),
                    modifier = Modifier.clickable { onToggleLanguage() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Translate,
                            contentDescription = null,
                            tint = OledColors.Primary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = strings.switchLangShort,
                            style = MaterialTheme.typography.labelMedium,
                            color = OledColors.TextPrimary
                        )
                    }
                }
            }

            // Hero Header with KaTeX sample emblem
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = OledColors.Surface,
                    border = BorderStroke(1.dp, Color(0xFF1D3566))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color(0xFF0A1429),
                                        OledColors.Surface
                                    )
                                )
                            )
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${strings.appTitle} • v${BuildConfig.VERSION_NAME}",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color(0xFF93C5FD)
                            )
                            Text(
                                text = strings.appSubtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = OledColors.TextSecondary
                            )
                        }
                        Box(modifier = Modifier.width(125.dp)) {
                            KaTeXFormulaView(
                                latex = "\\lim_{x\\to 0}\\frac{\\sin x}{x}=1",
                                displayMode = false,
                                fontPx = 15f,
                                minScale = 0.72f,
                                centerWhenFits = true,
                                minHeightDp = 34
                            )
                        }
                    }
                }

                Text(
                    text = strings.loginHeading,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 32.sp,
                    color = OledColors.TextPrimary
                )
                Text(
                    text = strings.loginDesc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = OledColors.TextSecondary
                )
            }

            // Login Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = OledColors.Surface,
                border = BorderStroke(1.dp, OledColors.BorderStrong)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    OutlinedTextField(
                        value = usernameInput,
                        onValueChange = { usernameInput = it },
                        label = { Text(strings.usernameLabel) },
                        placeholder = { Text(strings.usernameHint) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = null,
                                tint = OledColors.Primary
                            )
                        },
                        singleLine = true,
                        enabled = !isLoggingIn,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (usernameInput.trim().length >= 2) {
                                    onLogin(usernameInput)
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

                    if (!loginError.isNullOrBlank()) {
                        Surface(
                            color = OledColors.RedSoft,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, OledColors.Red.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = loginError,
                                style = MaterialTheme.typography.bodySmall,
                                color = OledColors.Red,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            )
                        }
                    }

                    Button(
                        onClick = { onLogin(usernameInput) },
                        enabled = !isLoggingIn && usernameInput.trim().length >= 2,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OledColors.Primary,
                            contentColor = Color.White,
                            disabledContainerColor = OledColors.SurfaceHighlight,
                            disabledContentColor = OledColors.TextMuted
                        )
                    ) {
                        if (isLoggingIn) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.5.dp,
                                color = Color.White
                            )
                        } else {
                            Text(
                                text = strings.loginSubmit,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    if (recentUsers.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = strings.recentUsers,
                                style = MaterialTheme.typography.labelSmall,
                                color = OledColors.TextSecondary
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                recentUsers.forEach { user ->
                                    Surface(
                                        shape = RoundedCornerShape(99.dp),
                                        color = OledColors.SurfaceElevated,
                                        border = BorderStroke(1.dp, OledColors.BorderStrong),
                                        modifier = Modifier.clickable(enabled = !isLoggingIn) {
                                            usernameInput = user
                                            onLogin(user)
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Person,
                                                contentDescription = null,
                                                tint = OledColors.Teal,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text(
                                                text = user,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = OledColors.TextPrimary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Feature highlights tuned for mobile native UX
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FeatureRow(
                    icon = Icons.Rounded.ZoomOutMap,
                    accent = OledColors.Primary,
                    title = strings.feat1Title,
                    desc = strings.feat1Desc
                )
                FeatureRow(
                    icon = Icons.Rounded.CloudSync,
                    accent = OledColors.Teal,
                    title = strings.feat2Title,
                    desc = strings.feat2Desc
                )
                FeatureRow(
                    icon = Icons.Rounded.AutoAwesome,
                    accent = OledColors.Amber,
                    title = strings.feat3Title,
                    desc = strings.feat3Desc
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun FeatureRow(
    icon: ImageVector,
    accent: Color,
    title: String,
    desc: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = OledColors.Surface,
        border = BorderStroke(1.dp, OledColors.Border)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                shape = RoundedCornerShape(11.dp),
                color = accent.copy(alpha = 0.14f),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = OledColors.TextPrimary
                )
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = OledColors.TextSecondary
                )
            }
        }
    }
}
