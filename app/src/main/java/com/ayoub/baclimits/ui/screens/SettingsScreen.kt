package com.ayoub.baclimits.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ayoub.baclimits.BuildConfig
import com.ayoub.baclimits.data.SyncStatusSnapshot
import com.ayoub.baclimits.data.UpdateCheckState
import com.ayoub.baclimits.ui.components.SyncDiagnosticsCard
import com.ayoub.baclimits.ui.i18n.AppStrings
import com.ayoub.baclimits.ui.theme.OledColors

@Composable
fun SettingsScreen(
    displayName: String,
    username: String,
    lang: String,
    syncState: SyncStatusSnapshot,
    currentServerToken: String,
    isSyncingAccount: Boolean,
    updateState: UpdateCheckState,
    strings: AppStrings,
    onSwitchLanguage: (String) -> Unit,
    onLogout: () -> Unit,
    onSaveServerConfig: (url: String, token: String) -> Unit,
    onResetDefaultServer: () -> Unit,
    onSyncAndReload: () -> Unit,
    onOpenUpdaterSheet: () -> Unit
) {
    var serverUrlInput by remember(syncState.serverUrl) { mutableStateOf(syncState.serverUrl) }
    var serverTokenInput by remember(currentServerToken) { mutableStateOf(currentServerToken) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OledColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Account Section
        SettingsSectionCard(
            title = strings.accountSection,
            icon = Icons.Rounded.Person,
            accent = OledColors.Primary
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = displayName.ifBlank { username },
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "@$username",
                        style = MaterialTheme.typography.bodySmall,
                        color = OledColors.TextSecondary
                    )
                }
            }
            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, OledColors.Red.copy(alpha = 0.5f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = OledColors.Red)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Logout,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(strings.logoutSwitchAccount, fontWeight = FontWeight.SemiBold)
            }
        }

        // Language Switcher Section
        SettingsSectionCard(
            title = strings.languageSection,
            icon = Icons.Rounded.Translate,
            accent = OledColors.Teal
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LanguageChoicePill(
                    label = "العربية",
                    selected = lang == "ar",
                    modifier = Modifier.weight(1f),
                    onClick = { if (lang != "ar") onSwitchLanguage("ar") }
                )
                LanguageChoicePill(
                    label = "Français (BIOF)",
                    selected = lang == "fr",
                    modifier = Modifier.weight(1f),
                    onClick = { if (lang != "fr") onSwitchLanguage("fr") }
                )
            }
        }

        // Server & Sync Section
        SettingsSectionCard(
            title = strings.serverSyncSection,
            icon = Icons.Rounded.CloudSync,
            accent = OledColors.Green
        ) {
            SyncDiagnosticsCard(syncState = syncState, strings = strings)

            OutlinedTextField(
                value = serverUrlInput,
                onValueChange = { serverUrlInput = it },
                label = { Text(strings.serverUrlLabel) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = OledColors.Primary,
                    unfocusedBorderColor = OledColors.BorderStrong,
                    focusedTextColor = OledColors.TextPrimary,
                    unfocusedTextColor = OledColors.TextPrimary
                )
            )

            OutlinedTextField(
                value = serverTokenInput,
                onValueChange = { serverTokenInput = it },
                label = { Text(strings.bearerTokenLabel) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = OledColors.Primary,
                    unfocusedBorderColor = OledColors.BorderStrong,
                    focusedTextColor = OledColors.TextPrimary,
                    unfocusedTextColor = OledColors.TextPrimary
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onResetDefaultServer,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, OledColors.BorderStrong)
                ) {
                    Text(strings.resetDefaultServer, maxLines = 1)
                }
                Button(
                    onClick = { onSaveServerConfig(serverUrlInput, serverTokenInput) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = OledColors.Primary)
                ) {
                    Text(strings.saveAndTestServer, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }

            Button(
                onClick = onSyncAndReload,
                enabled = !isSyncingAccount,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = OledColors.TealSoft,
                    contentColor = OledColors.Teal
                )
            ) {
                if (isSyncingAccount) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = OledColors.Teal
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(strings.syncAndReload, fontWeight = FontWeight.Bold)
            }
        }

        // OTA Updater Section
        SettingsSectionCard(
            title = strings.updaterSection,
            icon = Icons.Rounded.SystemUpdate,
            accent = OledColors.Amber
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(strings.currentVersionLabel, style = MaterialTheme.typography.bodySmall)
                Text(
                    text = "v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.titleMedium,
                    color = OledColors.TextPrimary
                )
            }

            if (updateState.hasUpdate) {
                Text(
                    text = "${strings.updateAvailablePrefix} v${updateState.remoteVersion}",
                    style = MaterialTheme.typography.labelMedium,
                    color = OledColors.Primary
                )
            }

            Button(
                onClick = onOpenUpdaterSheet,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = OledColors.SurfaceElevated,
                    contentColor = OledColors.TextPrimary
                ),
                border = BorderStroke(1.dp, OledColors.BorderStrong)
            ) {
                Text(
                    text = if (updateState.hasUpdate) strings.downloadInstall else strings.checkUpdates,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    icon: ImageVector,
    accent: Color,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = OledColors.Surface,
        border = BorderStroke(1.dp, OledColors.Border)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            content()
        }
    }
}

@Composable
private fun LanguageChoicePill(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(44.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = if (selected) OledColors.PrimarySoft else OledColors.SurfaceElevated,
        border = BorderStroke(1.dp, if (selected) OledColors.Primary else OledColors.Border)
    ) {
        androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) Color(0xFF93C5FD) else OledColors.TextSecondary
            )
        }
    }
}
