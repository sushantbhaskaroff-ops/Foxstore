package com.example.web

import android.content.Context
import com.example.R
import com.example.data.StoreRepository
import com.google.firebase.firestore.FirebaseFirestore

object PortalServerManager {
    private var server: LocalWebPortalServer? = null
    private var activeUserId: String? = null
    private var refCount = 0
    private var onUploadListener: ((String) -> Unit)? = null

    @Synchronized
    fun acquire(
        context: Context,
        userId: String,
        developerName: String,
        onAppUploaded: (String) -> Unit,
    ) {
        onUploadListener = onAppUploaded
        if (server != null && activeUserId != userId) {
            server?.stop()
            server = null
            refCount = 0
        }
        if (server == null) {
            val appCtx = context.applicationContext
            val dbId = appCtx.getString(R.string.firestore_database_id)
            val db = FirebaseFirestore.getInstance(dbId)
            server = LocalWebPortalServer(
                context = appCtx,
                repository = StoreRepository(db),
                userId = userId,
                developerName = developerName,
                onAppUploadedFromWeb = { name -> onUploadListener?.invoke(name) },
            ).also { it.start() }
            activeUserId = userId
        }
        refCount++
    }

    @Synchronized
    fun release() {
        refCount = (refCount - 1).coerceAtLeast(0)
        if (refCount == 0) {
            server?.stop()
            server = null
            activeUserId = null
            onUploadListener = null
        }
    }
}
