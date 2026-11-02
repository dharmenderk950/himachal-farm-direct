package com.dk.himachalfarmdirect.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.persistentCacheSettings
import com.google.firebase.functions.FirebaseFunctions
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun auth(): FirebaseAuth = FirebaseAuth.getInstance()

    /** 100 MB offline cache so browsing keeps working on patchy rural connections. */
    @Provides @Singleton
    fun firestore(): FirebaseFirestore = FirebaseFirestore.getInstance().apply {
        firestoreSettings = firestoreSettings {
            setLocalCacheSettings(persistentCacheSettings { setSizeBytes(100L * 1024 * 1024) })
        }
    }

    /** Functions deployed to Mumbai for lowest latency from Himachal. */
    @Provides @Singleton
    fun functions(): FirebaseFunctions = FirebaseFunctions.getInstance("asia-south1")
}
