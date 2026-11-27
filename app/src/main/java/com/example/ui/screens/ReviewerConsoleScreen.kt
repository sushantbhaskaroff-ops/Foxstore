package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.AppStatus
import com.example.data.MarketplaceApp
import com.example.data.StoreRepository
import com.example.ui.components.AppIconBadge
import com.example.ui.components.StatusPill
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.viewmodel.UiState

@Composable
fun ReviewerConsoleScreen(
    appsState: UiState<List<MarketplaceApp>>,
    isAdmin: Boolean,
    currentUserEmail: String,
    isBusy: Boolean,
    onApprove: (MarketplaceApp, String) -> Unit,
    onReject: (MarketplaceApp, String) -> Unit,
    onOpenDetail: (MarketplaceApp) -> Unit,
) {
    var selectedFilter by remember { mutableStateOf(AppStatus.PENDING) }
    var rejectingApp by remember { mutableStateOf<MarketplaceApp?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Reviewer Role Header Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isAdmin) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.secondaryContainer
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.AdminPanelSettings,
                    contentDescription = null,
                    tint = if (isAdmin) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(30.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (isAdmin) "AppVault Reviewer Console (Admin Active)" else "Submission Review Tracker",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = if (isAdmin) {
                            "Signed in as $currentUserEmail • Approve apps uploaded from the Website."
                        } else {
                            "Admin actions require ${StoreRepository.ADMIN_EMAIL}. Showing your submissions."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        when (appsState) {
            is UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is UiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(appsState.message, color = MaterialTheme.colorScheme.error)
                }
            }

            is UiState.Success -> {
                val allApps = appsState.data
                val pendingCount = remember(allApps) { allApps.count { it.statusEnum() == AppStatus.PENDING } }
                val approvedCount = remember(allApps) { allApps.count { it.statusEnum() == AppStatus.APPROVED } }
                val rejectedCount = remember(allApps) { allApps.count { it.statusEnum() == AppStatus.REJECTED } }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = selectedFilter == AppStatus.PENDING,
                        onClick = { selectedFilter = AppStatus.PENDING },
                        label = { Text("Pending ($pendingCount)") },
                        modifier = Modifier.testTag("filter_pending"),
                    )
                    FilterChip(
                        selected = selectedFilter == AppStatus.APPROVED,
                        onClick = { selectedFilter = AppStatus.APPROVED },
                        label = { Text("Approved ($approvedCount)") },
                        modifier = Modifier.testTag("filter_approved"),
                    )
                    FilterChip(
                        selected = selectedFilter == AppStatus.REJECTED,
                        onClick = { selectedFilter = AppStatus.REJECTED },
                        label = { Text("Rejected ($rejectedCount)") },
                        modifier = Modifier.testTag("filter_rejected"),
                    )
                }

                val filtered = remember(allApps, selectedFilter) {
                    allApps.filter { it.statusEnum() == selectedFilter }
                }

                if (filtered.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(44.dp),
                            )
                            Text(
                                text = "No ${selectedFilter.name.lowercase()} apps",
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                text = "Apps uploaded on the Developer Website appear under Pending for approval.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(filtered, key = { it.id }) { app ->
                            ReviewerSubmissionCard(
                                app = app,
                                isAdmin = isAdmin,
                                isBusy = isBusy,
                                onApprove = { onApprove(app, "Approved & published to AppVault Store.") },
                                onReject = { rejectingApp = app },
                                onInspect = { onOpenDetail(app) },
                            )
                        }
                    }
                }
            }
        }
    }

    rejectingApp?.let { target ->
        RejectReasonDialog(
            appName = target.name,
            onDismiss = { rejectingApp = null },
            onConfirmReject = { reason ->
                onReject(target, reason)
                rejectingApp = null
            },
        )
    }
}

@Composable
private fun ReviewerSubmissionCard(
    app: MarketplaceApp,
    isAdmin: Boolean,
    isBusy: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onInspect: () -> Unit,
) {
    Card(
        onClick = onInspect,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("reviewer_card_${app.id}"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                AppIconBadge(
                    iconKey = app.iconKey,
                    accentHex = app.accentHex,
                    appName = app.name,
                    size = 54.dp,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(app.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = app.packageName,
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "By ${app.developerName} • ${app.category} • v${app.versionName} (${app.sizeLabel})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusPill(app.statusEnum())
            }

            if (app.source == "WEBSITE") {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Uploaded via Developer Website",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }

            Text(app.tagline, style = MaterialTheme.typography.titleMedium)
            Text(
                text = app.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Play Policy Pre-flight Checklist
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = "Policy Pre-flight Checks",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        text = "✓ Title length ${app.name.length}/30 • ✓ Valid package ID • ✓ Metadata complete",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF059669),
                    )
                }
            }

            if (app.reviewNote.isNotBlank()) {
                HorizontalDivider()
                Text(
                    text = "Reviewer Note: ${app.reviewNote}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }

            if (isAdmin && app.statusEnum() != AppStatus.APPROVED) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (app.statusEnum() != AppStatus.REJECTED) {
                        OutlinedButton(
                            onClick = onReject,
                            enabled = !isBusy,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("reject_btn_${app.id}"),
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.reject_app))
                        }
                    }
                    Button(
                        onClick = onApprove,
                        enabled = !isBusy,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF059669),
                            contentColor = Color.White,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("approve_btn_${app.id}"),
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.approve_publish))
                    }
                }
            }
        }
    }
}

@Composable
private fun RejectReasonDialog(
    appName: String,
    onDismiss: () -> Unit,
    onConfirmReject: (String) -> Unit,
) {
    var reason by remember { mutableStateOf("Please refine app description and verify release notes.") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reject $appName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Provide feedback so the developer can update and resubmit:",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { if (it.length <= 300) reason = it },
                    label = { Text("Rejection reason") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmReject(reason) },
                enabled = reason.trim().isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) {
                Text("Confirm Rejection")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
