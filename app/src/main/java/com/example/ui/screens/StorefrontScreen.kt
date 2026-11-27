package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.AppCategory
import com.example.data.InstalledApp
import com.example.data.MarketplaceApp
import com.example.ui.components.AppIconBadge
import com.example.ui.components.RatingCompactBadge
import com.example.viewmodel.UiState

@Composable
fun StorefrontScreen(
    appsState: UiState<List<MarketplaceApp>>,
    installedApps: List<InstalledApp>,
    installingAppIds: Set<String>,
    isAdmin: Boolean,
    onAppClick: (MarketplaceApp) -> Unit,
    onInstallClick: (MarketplaceApp) -> Unit,
    onOpenInstalledApp: (MarketplaceApp) -> Unit,
    onGoToWebsite: () -> Unit,
    onSeedShowcaseApps: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    val installedIds = remember(installedApps) { installedApps.map { it.appId }.toSet() }
    val categories = remember { listOf("All") + AppCategory.entries.map { it.label } }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text(stringResource(R.string.search_apps_hint)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("store_search_input"),
        )

        // Category Filter Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            categories.forEach { category ->
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = { selectedCategory = category },
                    label = { Text(category) },
                    modifier = Modifier.testTag("category_chip_$category"),
                )
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
                    Text(
                        text = appsState.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }

            is UiState.Success -> {
                val filteredApps = remember(appsState.data, searchQuery, selectedCategory) {
                    appsState.data.filter { app ->
                        val matchesCategory = selectedCategory == "All" ||
                            app.category.equals(selectedCategory, ignoreCase = true)
                        val q = searchQuery.trim()
                        val matchesQuery = q.isBlank() ||
                            app.name.contains(q, ignoreCase = true) ||
                            app.tagline.contains(q, ignoreCase = true) ||
                            app.packageName.contains(q, ignoreCase = true)
                        matchesCategory && matchesQuery
                    }
                }

                LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    // Featured Banner
                    if (searchQuery.isBlank() && selectedCategory == "All") {
                        item {
                            FeaturedStoreBanner(
                                appCount = appsState.data.size,
                                onGoToWebsite = onGoToWebsite,
                            )
                        }
                    }

                    if (filteredApps.isEmpty()) {
                        item {
                            EmptyStoreCard(
                                isSearchEmpty = appsState.data.isNotEmpty(),
                                isAdmin = isAdmin,
                                onGoToWebsite = onGoToWebsite,
                                onSeedShowcaseApps = onSeedShowcaseApps,
                            )
                        }
                    } else {
                        if (searchQuery.isBlank() && selectedCategory == "All") {
                            item {
                                Text(
                                    text = "Editor's Picks",
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                )
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    items(filteredApps.take(6), key = { "pick_${it.id}" }) { app ->
                                        FeaturedAppCard(
                                            app = app,
                                            isInstalled = app.id in installedIds,
                                            isInstalling = app.id in installingAppIds,
                                            onClick = { onAppClick(app) },
                                            onAction = {
                                                if (app.id in installedIds) onOpenInstalledApp(app)
                                                else onInstallClick(app)
                                            },
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            Text(
                                text = if (selectedCategory == "All") "Top Charts" else "$selectedCategory Apps",
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
                            )
                        }

                        itemsIndexed(filteredApps, key = { _, app -> app.id }) { index, app ->
                            StoreChartRow(
                                rank = index + 1,
                                app = app,
                                isInstalled = app.id in installedIds,
                                isInstalling = app.id in installingAppIds,
                                onClick = { onAppClick(app) },
                                onActionClick = {
                                    if (app.id in installedIds) onOpenInstalledApp(app)
                                    else onInstallClick(app)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeaturedStoreBanner(
    appCount: Int,
    onGoToWebsite: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .height(166.dp)
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onGoToWebsite),
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_store_hero),
            contentDescription = "Featured Marketplace Banner",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color(0xE608101E), Color(0x5508101E))
                    )
                )
                .padding(18.dp),
        ) {
            Column(
                modifier = Modifier.align(Alignment.CenterStart),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Surface(
                    color = Color(0x3310B981),
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Text(
                        text = "$appCount APPROVED APPS LIVE",
                        color = Color(0xFF6EE7B7),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                Text(
                    text = "AppVault Play Store",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                )
                Text(
                    text = "Upload new apps on the Website & approve them live.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFCBD5E1),
                )
            }
        }
    }
}

@Composable
private fun FeaturedAppCard(
    app: MarketplaceApp,
    isInstalled: Boolean,
    isInstalling: Boolean,
    onClick: () -> Unit,
    onAction: () -> Unit,
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .width(190.dp)
            .testTag("featured_card_${app.id}"),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                AppIconBadge(
                    iconKey = app.iconKey,
                    accentHex = app.accentHex,
                    appName = app.name,
                    size = 56.dp,
                )
                RatingCompactBadge(ratingAvg = app.ratingAvg, ratingCount = app.ratingCount)
            }
            Text(
                text = app.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = app.tagline,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.height(38.dp),
            )
            FilledTonalButton(
                onClick = onAction,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isInstalling) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text(if (isInstalled) "Open" else "Install • ${app.sizeLabel}")
                }
            }
        }
    }
}

@Composable
private fun StoreChartRow(
    rank: Int,
    app: MarketplaceApp,
    isInstalled: Boolean,
    isInstalling: Boolean,
    onClick: () -> Unit,
    onActionClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag("store_app_row_${app.id}"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = rank.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(24.dp),
            )
            AppIconBadge(
                iconKey = app.iconKey,
                accentHex = app.accentHex,
                appName = app.name,
                size = 54.dp,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${app.category} • ${app.developerName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    RatingCompactBadge(ratingAvg = app.ratingAvg, ratingCount = app.ratingCount)
                    Text(
                        text = "${app.downloadsCount} installs",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onActionClick,
                shape = RoundedCornerShape(12.dp),
                colors = if (isInstalled) {
                    ButtonDefaults.filledTonalButtonColors()
                } else {
                    ButtonDefaults.buttonColors()
                },
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier.testTag("install_btn_${app.id}"),
            ) {
                if (isInstalling) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(if (isInstalled) "Open" else "Install")
                }
            }
        }
    }
}

@Composable
private fun EmptyStoreCard(
    isSearchEmpty: Boolean,
    isAdmin: Boolean,
    onGoToWebsite: () -> Unit,
    onSeedShowcaseApps: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp),
            )
            Text(
                text = if (isSearchEmpty) "No matching apps found" else "Play Store is ready for apps",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = if (isSearchEmpty) {
                    "Try a different search term or category filter."
                } else {
                    "Upload an app from the Developer Website and approve it in the Approve tab."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!isSearchEmpty) {
                Button(onClick = onGoToWebsite) {
                    Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open Upload Website")
                }
                if (isAdmin) {
                    FilledTonalButton(onClick = onSeedShowcaseApps) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Load 4 Showcase Apps")
                    }
                }
            }
        }
    }
}
