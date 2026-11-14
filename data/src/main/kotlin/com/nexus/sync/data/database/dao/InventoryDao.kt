package com.nexus.sync.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nexus.sync.data.database.entity.InventoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryDao {

    @Query("SELECT * FROM inventory_items WHERE isDeleted = 0 ORDER BY lastModifiedEpoch DESC")
    fun observeAllActiveItems(): Flow<List<InventoryEntity>>

    @Query("SELECT * FROM inventory_items WHERE id = :id LIMIT 1")
    fun observeItemById(id: String): Flow<InventoryEntity?>

    @Query("SELECT * FROM inventory_items WHERE id = :id LIMIT 1")
    suspend fun getItemById(id: String): InventoryEntity?

    @Query("SELECT * FROM inventory_items WHERE sku = :sku LIMIT 1")
    suspend fun getItemBySku(sku: String): InventoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: InventoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<InventoryEntity>)

    @Query("UPDATE inventory_items SET isDeleted = 1, syncStatus = :syncStatus, lastModifiedEpoch = :now WHERE id = :id")
    suspend fun markDeleted(id: String, syncStatus: String, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM inventory_items WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("UPDATE inventory_items SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: String)

    @Query("SELECT COUNT(*) FROM inventory_items WHERE isDeleted = 0")
    suspend fun getActiveCount(): Int
}
