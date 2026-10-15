package com.github.damontecres.wholphin.rawkoon

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RawkoonModule {
    @Provides
    @Singleton
    fun rawkoonSettingsDataStore(
        @ApplicationContext context: Context,
        serializer: RawkoonSettingsSerializer,
    ): DataStore<RawkoonSettings> =
        DataStoreFactory.create(
            serializer = serializer,
            produceFile = { context.dataStoreFile("rawkoon_settings.pb") },
        )
}
