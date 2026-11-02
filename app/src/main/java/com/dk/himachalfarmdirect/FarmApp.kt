package com.dk.himachalfarmdirect

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class FarmApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            listOf(
                NotificationChannel("general", "General", NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel("orders", "Orders", NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel("support", "Support replies", NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel("nearby", "New farmers nearby", NotificationManager.IMPORTANCE_DEFAULT),
            ).forEach(nm::createNotificationChannel)
        }
    }

    /** Large disk cache: product images download once, then load offline (spec B). */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.20).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("img")).maxSizeBytes(150L * 1024 * 1024).build() }
        .respectCacheHeaders(false)
        .crossfade(true)
        .build()
}
