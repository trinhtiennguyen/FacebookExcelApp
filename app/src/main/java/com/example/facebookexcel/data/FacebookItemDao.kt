package com.example.facebookexcel.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FacebookItemDao {
    @Query("SELECT * FROM facebook_items ORDER BY id ASC")
    fun observeAll(): Flow<List<FacebookItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: FacebookItem): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<FacebookItem>)

    @Update
    suspend fun update(item: FacebookItem)

    @Delete
    suspend fun delete(item: FacebookItem)

    @Query("DELETE FROM facebook_items")
    suspend fun deleteAll()

    @Query("SELECT * FROM facebook_items ORDER BY id ASC")
    suspend fun getAll(): List<FacebookItem>

    @Query("""
        SELECT COUNT(*) FROM facebook_items
        WHERE TRIM(link) = TRIM(:link)
        AND id != :excludeId
    """)
    suspend fun countExistingLink(link: String, excludeId: Long = -1): Int
}
