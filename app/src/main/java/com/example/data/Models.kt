package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class AppStatus {
    PENDING,
    APPROVED,
    REJECTED;

    companion object {
        fun fromRaw(raw: String?): AppStatus =
            entries.firstOrNull { it.name == raw } ?: PENDING
    }
}

enum class AppCategory(val label: String) {
    Productivity("Productivity"),
    Fitness("Fitness"),
    Utilities("Utilities"),
    Developer("Developer"),
    Entertainment("Entertainment"),
    Finance("Finance");

    companion object {
        fun fromRaw(raw: String?): AppCategory =
            entries.firstOrNull { it.label.equals(raw, ignoreCase = true) } ?: Productivity
    }
}

@Keep
@IgnoreExtraProperties
data class MarketplaceApp(
    @DocumentId val id: String = "",
    val developerId: String = "",
    val developerName: String = "",
    val name: String = "",
    val packageName: String = "",
    val tagline: String = "",
    val description: String = "",
    val releaseNotes: String = "",
    val category: String = AppCategory.Productivity.label,
    val versionName: String = "1.0.0",
    val sizeLabel: String = "12.0 MB",
    val iconKey: String = "rocket",
    val accentHex: String = "#10B981",
    val status: String = AppStatus.PENDING.name,
    val reviewNote: String = "",
    val reviewedBy: String = "",
    val downloadsCount: Long = 0L,
    val ratingAvg: Double = 0.0,
    val ratingCount: Long = 0L,
    val source: String = "WEBSITE",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
    val reviewedAt: Timestamp? = null,
) {
    fun statusEnum(): AppStatus = AppStatus.fromRaw(status)
}

@Keep
@IgnoreExtraProperties
data class AppReview(
    @DocumentId val id: String = "",
    val userId: String = "",
    val authorName: String = "",
    val rating: Long = 5L,
    val comment: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class InstalledApp(
    @DocumentId val id: String = "",
    val appId: String = "",
    val appName: String = "",
    val packageName: String = "",
    val versionName: String = "1.0.0",
    val category: String = AppCategory.Productivity.label,
    val iconKey: String = "rocket",
    val accentHex: String = "#10B981",
    @ServerTimestamp val installedAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

data class AppSubmissionInput(
    val name: String,
    val packageName: String,
    val developerName: String,
    val tagline: String,
    val description: String,
    val releaseNotes: String,
    val category: String,
    val versionName: String,
    val sizeLabel: String,
    val iconKey: String,
    val accentHex: String,
    val source: String = "WEBSITE",
)
