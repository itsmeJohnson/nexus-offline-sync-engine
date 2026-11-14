package com.nexus.sync.data.datastore

import androidx.datastore.core.DataStore
import com.nexus.sync.data.proto.ConflictResolutionStrategyProto
import com.nexus.sync.data.proto.SyncConfigProto
import com.nexus.sync.domain.model.ConflictResolutionStrategy
import com.nexus.sync.domain.model.SyncConfig
import com.nexus.sync.domain.repository.SyncConfigRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncConfigRepositoryImpl @Inject constructor(
    private val syncConfigDataStore: DataStore<SyncConfigProto>
) : SyncConfigRepository {

    override fun observeSyncConfig(): Flow<SyncConfig> {
        return syncConfigDataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(SyncConfigProto.getDefaultInstance())
                } else {
                    throw exception
                }
            }
            .map { proto -> proto.toDomain() }
    }

    override suspend fun getSyncConfig(): SyncConfig {
        return observeSyncConfig().first()
    }

    override suspend fun updateSyncInterval(minutes: Int) {
        syncConfigDataStore.updateData { current ->
            current.toBuilder()
                .setSyncIntervalMinutes(minutes)
                .build()
        }
    }

    override suspend fun updateBatchSize(batchSize: Int) {
        syncConfigDataStore.updateData { current ->
            current.toBuilder()
                .setBatchSize(batchSize)
                .build()
        }
    }

    override suspend fun updateConflictStrategy(strategy: ConflictResolutionStrategy) {
        syncConfigDataStore.updateData { current ->
            current.toBuilder()
                .setConflictStrategy(
                    when (strategy) {
                        ConflictResolutionStrategy.LAST_WRITE_WINS -> ConflictResolutionStrategyProto.CONFLICT_STRATEGY_LAST_WRITE_WINS
                        ConflictResolutionStrategy.SERVER_AUTHORITATIVE -> ConflictResolutionStrategyProto.CONFLICT_STRATEGY_SERVER_AUTHORITATIVE
                    }
                )
                .build()
        }
    }

    override suspend fun updateAuthSession(token: String, expiryEpoch: Long) {
        syncConfigDataStore.updateData { current ->
            current.toBuilder()
                .setAuthToken(token)
                .setTokenExpiryEpoch(expiryEpoch)
                .build()
        }
    }

    override suspend fun updateLastSuccessfulSync(epoch: Long) {
        syncConfigDataStore.updateData { current ->
            current.toBuilder()
                .setLastSuccessfulSyncEpoch(epoch)
                .build()
        }
    }

    override suspend fun setAutoSyncEnabled(enabled: Boolean) {
        syncConfigDataStore.updateData { current ->
            current.toBuilder()
                .setAutoSyncEnabled(enabled)
                .build()
        }
    }

    private fun SyncConfigProto.toDomain(): SyncConfig {
        return SyncConfig(
            syncIntervalMinutes = if (syncIntervalMinutes > 0) syncIntervalMinutes else 15,
            batchSize = if (batchSize > 0) batchSize else 50,
            conflictStrategy = when (conflictStrategy) {
                ConflictResolutionStrategyProto.CONFLICT_STRATEGY_SERVER_AUTHORITATIVE -> ConflictResolutionStrategy.SERVER_AUTHORITATIVE
                else -> ConflictResolutionStrategy.LAST_WRITE_WINS
            },
            authToken = authToken,
            tokenExpiryEpoch = tokenExpiryEpoch,
            lastSuccessfulSyncEpoch = lastSuccessfulSyncEpoch,
            autoSyncEnabled = autoSyncEnabled
        )
    }
}
