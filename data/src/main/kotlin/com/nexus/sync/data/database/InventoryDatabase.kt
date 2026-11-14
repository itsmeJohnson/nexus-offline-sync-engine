package com.nexus.sync.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.nexus.sync.data.database.dao.InventoryDao
import com.nexus.sync.data.database.dao.PendingSyncDao
import com.nexus.sync.data.database.entity.InventoryEntity
import com.nexus.sync.data.database.entity.PendingSyncEntity

@Database(
    entities = [
        InventoryEntity::class,
        PendingSyncEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class InventoryDatabase : RoomDatabase() {
    abstract fun inventoryDao(): InventoryDao
    abstract fun pendingSyncDao(): PendingSyncDao
}
