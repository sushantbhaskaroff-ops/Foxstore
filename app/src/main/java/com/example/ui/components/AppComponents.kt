package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.AppStatus
import com.example.data.MarketplaceApp
import com.example.ui.theme.JetBrainsMonoFontFamily

fun parseHexColor(hex: String, fallback: Color = Color(0xFF10B981)): Color {
    return try {
        val clean = hex.removePrefix("#")
        if (clean.length == 6) {
            Color(android.graphics.Color.parseColor("#$clean"))
        } else {
            fallback
        }
    } catch (_: Exception) {
        fallback
    }
}

@Composable
fun AppIconBadge(
    iconKey: String,
    accentHex: String,
    appName: String,
    size: Dp = 58.dp,
    cornerRadius: Dp = 16.dp,
) {
    val drawableRes = when (iconKey.lowercase()) {
        "pulsefit" -> R.drawable.img_app_pulsefit
        "novanotes" -> R.drawable.img_app_novanotes
        "skylens" -> R.drawable.img_app_skylens
        else -> null
    }

    val shape = RoundedCornerShape(cornerRadius)
    if (drawableRes != null) {
        Image(
            painter = painterResource(id = drawableRes),
            contentDescription = "$appName icon",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(shape),
        )
    } else {
        val baseColor = parseHexColor(accentHex)
        val iconVector = when (iconKey.lowercase()) {
            "rocket" -> Icons.Default.RocketLaunch
            "code" -> Icons.Default.Code
            "music" -> Icons.Default.MusicNote
            "shield" -> Icons.Default.Security
            "wallet", "vaultcalc" -> Icons.Default.AccountBalanceWallet
            "game" -> Icons.Default.SportsEsports
            else -> Icons.Default.Apps
        }
        Box(
            modifier = Modifier
                .size(size)
                .clip(shape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(baseColor, baseColor.copy(alpha = 0.72f))
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = iconVector,
                contentDescription = "$appName icon",
                tint = Color.White,
                modifier = Modifier.size(size * 0.52f),
            )
        }
    }
}

@Composable
fun StatusPill(status: AppStatus) {
    val (bg, fg, label) = when (status) {
        AppStatus.APPROVED -> Triple(Color(0x2610B981), Color(0xFF059669), "LIVE")
        AppStatus.PENDING -> Triple(Color(0x26F59E0B), Color(0xFFD97706), "PENDING")
        AppStatus.REJECTED -> Triple(Color(0x26F43F5E), Color(0xFFE11D48), "REJECTED")
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(999.dp),
    ) {
        Text(
            text = label,
            color = fg,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun RatingCompactBadge(ratingAvg: Double, ratingCount: Long) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = if (ratingCount > 0) String.format("%.1f", ratingAvg) else "New",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(3.dp))
        Icon(
            imageVector = Icons.Default.Star,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
fun InteractiveAppLaunchDialog(
    app: MarketplaceApp,
    onDismiss: () -> Unit,
) {
    val accent = parseHexColor(app.accentHex)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIconBadge(
                    iconKey = app.iconKey,
                    accentHex = app.accentHex,
                    appName = app.name,
                    size = 44.dp,
                    cornerRadius = 12.dp,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(app.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = app.packageName,
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = accent.copy(alpha = 0.12f)
                    ),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Sandbox Runtime Preview • v${app.versionName}",
                                style = MaterialTheme.typography.labelLarge,
                                color = accent,
                            )
                        }
                        Text(
                            text = app.tagline,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = app.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    text = "Developer: ${app.developerName} • Size: ${app.sizeLabel}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close Preview")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Back to Store")
            }
        },
    )
}
