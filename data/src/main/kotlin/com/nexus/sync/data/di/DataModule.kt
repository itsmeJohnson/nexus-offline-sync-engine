package com.nexus.sync.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import androidx.room.Room
import com.nexus.sync.data.database.InventoryDatabase
import com.nexus.sync.data.database.dao.InventoryDao
import com.nexus.sync.data.database.dao.PendingSyncDao
import com.nexus.sync.data.datastore.SyncConfigRepositoryImpl
import com.nexus.sync.data.datastore.SyncConfigSerializer
import com.nexus.sync.data.network.RemoteInventoryDataSourceImpl
import com.nexus.sync.data.proto.SyncConfigProto
import com.nexus.sync.data.repository.InventoryRepositoryImpl
import com.nexus.sync.data.repository.SyncOutboxRepositoryImpl
import com.nexus.sync.domain.repository.InventoryRepository
import com.nexus.sync.domain.repository.RemoteInventoryDataSource
import com.nexus.sync.domain.repository.SyncConfigRepository
import com.nexus.sync.domain.repository.SyncOutboxRepository
import com.nexus.sync.domain.usecase.DeleteInventoryItemUseCase
import com.nexus.sync.domain.usecase.GetInventoryItemsUseCase
import com.nexus.sync.domain.usecase.ObservePendingSyncCountUseCase
import com.nexus.sync.domain.usecase.ObserveSyncConfigUseCase
import com.nexus.sync.domain.usecase.PerformTwoWaySyncUseCase
import com.nexus.sync.domain.usecase.ResolveConflictUseCase
import com.nexus.sync.domain.usecase.SaveInventoryItemUseCase
import com.nexus.sync.domain.usecase.UpdateSyncConfigUseCase
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataBindingsModule {

    @Binds
    @Singleton
    abstract fun bindInventoryRepository(impl: InventoryRepositoryImpl): InventoryRepository

    @Binds
    @Singleton
    abstract fun bindSyncOutboxRepository(impl: SyncOutboxRepositoryImpl): SyncOutboxRepository

    @Binds
    @Singleton
    abstract fun bindSyncConfigRepository(impl: SyncConfigRepositoryImpl): SyncConfigRepository

    @Binds
    @Singleton
    abstract fun bindRemoteInventoryDataSource(impl: RemoteInventoryDataSourceImpl): RemoteInventoryDataSource
}

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    private const val DATABASE_NAME = "nexus_inventory.db"
    private const val SYNC_CONFIG_FILE = "sync_config.pb"

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    fun provideInventoryDatabase(
        @ApplicationContext context: Context
    ): InventoryDatabase {
        return Room.databaseBuilder(
            context,
            InventoryDatabase::class.java,
            DATABASE_NAME
        ).fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideInventoryDao(db: InventoryDatabase): InventoryDao = db.inventoryDao()

    @Provides
    fun providePendingSyncDao(db: InventoryDatabase): PendingSyncDao = db.pendingSyncDao()

    @Provides
    @Singleton
    fun provideSyncConfigDataStore(
        @ApplicationContext context: Context
    ): DataStore<SyncConfigProto> {
        return DataStoreFactory.create(
            serializer = SyncConfigSerializer,
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            produceFile = { context.dataStoreFile(SYNC_CONFIG_FILE) }
        )
    }

    // -------------------------------------------------------------
    // Domain Use Cases Provider
    // -------------------------------------------------------------
    @Provides
    @Singleton
    fun provideResolveConflictUseCase(): ResolveConflictUseCase = ResolveConflictUseCase()

    @Provides
    @Singleton
    fun provideGetInventoryItemsUseCase(
        repository: InventoryRepository
    ): GetInventoryItemsUseCase = GetInventoryItemsUseCase(repository)

    @Provides
    @Singleton
    fun provideSaveInventoryItemUseCase(
        inventoryRepository: InventoryRepository,
        outboxRepository: SyncOutboxRepository,
        json: Json
    ): SaveInventoryItemUseCase = SaveInventoryItemUseCase(inventoryRepository, outboxRepository, json)

    @Provides
    @Singleton
    fun provideDeleteInventoryItemUseCase(
        inventoryRepository: InventoryRepository,
        outboxRepository: SyncOutboxRepository
    ): DeleteInventoryItemUseCase = DeleteInventoryItemUseCase(inventoryRepository, outboxRepository)

    @Provides
    @Singleton
    fun providePerformTwoWaySyncUseCase(
        inventoryRepository: InventoryRepository,
        outboxRepository: SyncOutboxRepository,
        configRepository: SyncConfigRepository,
        remoteDataSource: RemoteInventoryDataSource,
        resolveConflictUseCase: ResolveConflictUseCase
    ): PerformTwoWaySyncUseCase = PerformTwoWaySyncUseCase(
        inventoryRepository = inventoryRepository,
        outboxRepository = outboxRepository,
        configRepository = configRepository,
        remoteDataSource = remoteDataSource,
        resolveConflictUseCase = resolveConflictUseCase
    )

    @Provides
    @Singleton
    fun provideObserveSyncConfigUseCase(
        configRepository: SyncConfigRepository
    ): ObserveSyncConfigUseCase = ObserveSyncConfigUseCase(configRepository)

    @Provides
    @Singleton
    fun provideUpdateSyncConfigUseCase(
        configRepository: SyncConfigRepository
    ): UpdateSyncConfigUseCase = UpdateSyncConfigUseCase(configRepository)

    @Provides
    @Singleton
    fun provideObservePendingSyncCountUseCase(
        outboxRepository: SyncOutboxRepository
    ): ObservePendingSyncCountUseCase = ObservePendingSyncCountUseCase(outboxRepository)
}
