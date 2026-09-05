package com.nocturne.audiocapture.upload

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

class UploadManager(private val context: Context) {
    private val wm = WorkManager.getInstance(context)
    private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun schedulePeriodic() {
        wm.enqueueUniquePeriodicWork(
            "periodic_upload",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<UploadWorker>(15, TimeUnit.MINUTES).setConstraints(constraints).build()
        )
    }

    fun enqueueNow() {
        wm.enqueueUniqueWork(
            "upload_now",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<UploadWorker>().setConstraints(constraints).build()
        )
    }
}
