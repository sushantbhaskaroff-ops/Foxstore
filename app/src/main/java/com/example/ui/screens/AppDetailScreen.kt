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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.AppReview
import com.example.data.MarketplaceApp
import com.example.ui.components.AppIconBadge
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.viewmodel.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailScreen(
    app: MarketplaceApp?,
    isInstalled: Boolean,
    isInstalling: Boolean,
    reviewsState: UiState<List<AppReview>>,
    currentUserId: String,
    onBack: () -> Unit,
    onInstall: (MarketplaceApp) -> Unit,
    onUninstall: (MarketplaceApp) -> Unit,
    onOpenApp: (MarketplaceApp) -> Unit,
    onSubmitReview: (MarketplaceApp, Int, String, List<AppReview>, () -> Unit) -> Unit,
) {
    var showReviewDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(app?.name ?: "App Details") },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("detail_back_button"),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        if (app == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text("App listing is no longer available.")
            }
            return@Scaffold
        }

        val currentReviews = (reviewsState as? UiState.Success)?.data ?: emptyList()

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // Header
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    AppIconBadge(
                        iconKey = app.iconKey,
                        accentHex = app.accentHex,
                        appName = app.name,
                        size = 76.dp,
                        cornerRadius = 20.dp,
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(app.name, style = MaterialTheme.typography.headlineSmall)
                        Text(
                            text = app.developerName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = app.packageName,
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Metrics Strip
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp, horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        MetricColumn(
                            value = if (app.ratingCount > 0) String.format("%.1f ★", app.ratingAvg) else "New",
                            label = "${app.ratingCount} reviews",
                        )
                        MetricColumn(value = app.sizeLabel, label = "v${app.versionName}")
                        MetricColumn(value = "${app.downloadsCount}+", label = "Downloads")
                        MetricColumn(value = app.category, label = "Category")
                    }
                }
            }

            // Action Buttons
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (isInstalled) {
                        OutlinedButton(
                            onClick = { onUninstall(app) },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("detail_uninstall_button"),
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Uninstall")
                        }
                        Button(
                            onClick = { onOpenApp(app) },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("detail_open_button"),
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open")
                        }
                    } else {
                        Button(
                            onClick = { onInstall(app) },
                            enabled = !isInstalling,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("detail_install_button"),
                        ) {
                            if (isInstalling) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Installing…")
                            } else {
                                Text("Install (${app.sizeLabel})")
                            }
                        }
                    }
                }
            }

            // What's New
            if (app.releaseNotes.isNotBlank()) {
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.NewReleases,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("What's new • v${app.versionName}", style = MaterialTheme.typography.titleMedium)
                            }
                            Text(
                                text = app.releaseNotes,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // About this app
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("About this app", style = MaterialTheme.typography.titleLarge)
                        Text(app.tagline, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = app.description,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (app.source == "WEBSITE") {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Text(
                                    text = "Submitted via Developer Upload Website & Admin Verified",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                }
            }

            // Reviews Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Ratings & Reviews", style = MaterialTheme.typography.titleLarge)
                    FilledTonalButton(
                        onClick = { showReviewDialog = true },
                        modifier = Modifier.testTag("write_review_button"),
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Write Review")
                    }
                }
            }

            when (reviewsState) {
                is UiState.Loading -> {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                }

                is UiState.Error -> {
                    item {
                        Text(
                            text = reviewsState.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                is UiState.Success -> {
                    if (reviewsState.data.isEmpty()) {
                        item {
                            Text(
                                text = "No user reviews yet. Be the first to rate ${app.name}!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        items(reviewsState.data, key = { it.id }) { review ->
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = if (review.userId == currentUserId) "${review.authorName} (You)" else review.authorName,
                                            style = MaterialTheme.typography.titleMedium,
                                        )
                                        Row {
                                            repeat(5) { idx ->
                                                Icon(
                                                    imageVector = if (idx < review.rating) Icons.Default.Star else Icons.Outlined.StarOutline,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.tertiary,
                                                    modifier = Modifier.size(16.dp),
                                                )
                                            }
                                        }
                                    }
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                    Text(review.comment, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showReviewDialog && app != null) {
        val currentReviews = (reviewsState as? UiState.Success)?.data ?: emptyList()
        val existingMine = currentReviews.firstOrNull { it.userId == currentUserId }
        ReviewComposerDialog(
            appName = app.name,
            initialRating = existingMine?.rating?.toInt() ?: 5,
            initialComment = existingMine?.comment ?: "",
            onDismiss = { showReviewDialog = false },
            onSubmit = { rating, comment ->
                onSubmitReview(app, rating, comment, currentReviews) {
                    showReviewDialog = false
                }
            },
        )
    }
}

@Composable
private fun MetricColumn(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReviewComposerDialog(
    appName: String,
    initialRating: Int,
    initialComment: String,
    onDismiss: () -> Unit,
    onSubmit: (Int, String) -> Unit,
) {
    var rating by remember { mutableIntStateOf(initialRating) }
    var comment by remember { mutableStateOf(initialComment) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rate $appName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    (1..5).forEach { star ->
                        IconButton(onClick = { rating = star }) {
                            Icon(
                                imageVector = if (star <= rating) Icons.Default.Star else Icons.Outlined.StarOutline,
                                contentDescription = "$star stars",
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(32.dp),
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = comment,
                    onValueChange = { if (it.length <= 500) comment = it },
                    label = { Text("Your review") },
                    placeholder = { Text("Share what you liked or what could improve…") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(rating, comment) },
                enabled = comment.trim().isNotEmpty(),
            ) {
                Text("Post Review")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
