package com.nocturne.audiocapture

import android.app.Application
import androidx.work.Configuration
import com.nocturne.audiocapture.data.AppDatabase
import com.nocturne.audiocapture.upload.UploadManager

class App : Application(), Configuration.Provider {
    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val uploadManager: UploadManager by lazy { UploadManager(this) }

    override fun onCreate() {
        super.onCreate()
        uploadManager.schedulePeriodic()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setMinimumLoggingLevel(android.util.Log.INFO).build()
}
