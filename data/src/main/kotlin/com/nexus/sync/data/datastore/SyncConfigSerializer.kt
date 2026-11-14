package com.nexus.sync.data.datastore

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.google.protobuf.InvalidProtocolBufferException
import com.nexus.sync.data.proto.ConflictResolutionStrategyProto
import com.nexus.sync.data.proto.SyncConfigProto
import java.io.InputStream
import java.io.OutputStream

object SyncConfigSerializer : Serializer<SyncConfigProto> {
    override val defaultValue: SyncConfigProto = SyncConfigProto.newBuilder()
        .setSyncIntervalMinutes(15)
        .setBatchSize(50)
        .setConflictStrategy(ConflictResolutionStrategyProto.CONFLICT_STRATEGY_LAST_WRITE_WINS)
        .setAutoSyncEnabled(true)
        .build()

    override suspend fun readFrom(input: InputStream): SyncConfigProto {
        return try {
            SyncConfigProto.parseFrom(input)
        } catch (exception: InvalidProtocolBufferException) {
            throw CorruptionException("Cannot read proto.", exception)
        }
    }

    override suspend fun writeTo(t: SyncConfigProto, output: OutputStream) {
        t.writeTo(output)
    }
}
