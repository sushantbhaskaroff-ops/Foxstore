package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.AppStatus
import com.example.data.InstalledApp
import com.example.data.MarketplaceApp
import com.example.ui.components.AppIconBadge
import com.example.ui.components.StatusPill
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.viewmodel.UiState

@Composable
fun LibraryScreen(
    userName: String,
    userEmail: String,
    isAdmin: Boolean,
    installedAppsState: UiState<List<InstalledApp>>,
    mySubmissionsState: UiState<List<MarketplaceApp>>,
    approvedApps: List<MarketplaceApp>,
    onOpenInstalled: (MarketplaceApp) -> Unit,
    onUninstall: (String, String) -> Unit,
    onDeleteSubmission: (MarketplaceApp) -> Unit,
    onGoToWebsite: () -> Unit,
    onSignOut: () -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        // Account Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(46.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(userName, style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = userEmail,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (isAdmin) {
                            Text(
                                text = "Store Admin & Reviewer",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = onSignOut,
                        modifier = Modifier.testTag("sign_out_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Sign out",
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sign Out")
                    }
                }
            }
        }

        // Installed Apps Section
        item {
            Text("Installed Apps", style = MaterialTheme.typography.titleLarge)
        }

        when (installedAppsState) {
            is UiState.Loading -> {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            }

            is UiState.Error -> {
                item {
                    Text(installedAppsState.message, color = MaterialTheme.colorScheme.error)
                }
            }

            is UiState.Success -> {
                if (installedAppsState.data.isEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        ) {
                            Text(
                                text = "No apps installed yet. Install apps from the Store tab to launch them here.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                } else {
                    items(installedAppsState.data, key = { "inst_${it.id}" }) { installed ->
                        val liveMatch = approvedApps.firstOrNull { it.id == installed.appId }
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AppIconBadge(
                                    iconKey = installed.iconKey,
                                    accentHex = installed.accentHex,
                                    appName = installed.appName,
                                    size = 48.dp,
                                    cornerRadius = 12.dp,
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(installed.appName, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = "${installed.category} • v${installed.versionName}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(
                                    onClick = { onUninstall(installed.appId, installed.appName) },
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Uninstall")
                                }
                                if (liveMatch != null) {
                                    FilledTonalButton(onClick = { onOpenInstalled(liveMatch) }) {
                                        Text("Open")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // My Website Submissions Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("My Website Submissions", style = MaterialTheme.typography.titleLarge)
                Button(onClick = onGoToWebsite) {
                    Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Upload New")
                }
            }
        }

        when (mySubmissionsState) {
            is UiState.Loading -> {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            }

            is UiState.Error -> {
                item {
                    Text(mySubmissionsState.message, color = MaterialTheme.colorScheme.error)
                }
            }

            is UiState.Success -> {
                if (mySubmissionsState.data.isEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        ) {
                            Text(
                                text = "You haven't uploaded any apps via the Developer Website yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                } else {
                    items(mySubmissionsState.data, key = { "sub_${it.id}" }) { app ->
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AppIconBadge(
                                    iconKey = app.iconKey,
                                    accentHex = app.accentHex,
                                    appName = app.name,
                                    size = 48.dp,
                                    cornerRadius = 12.dp,
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(app.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = app.packageName,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    if (app.reviewNote.isNotBlank()) {
                                        Text(
                                            text = app.reviewNote,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.secondary,
                                        )
                                    }
                                }
                                StatusPill(app.statusEnum())
                                if (app.statusEnum() != AppStatus.APPROVED) {
                                    IconButton(onClick = { onDeleteSubmission(app) }) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete submission")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
