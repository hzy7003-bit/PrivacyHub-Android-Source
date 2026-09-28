package com.privacyhub.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.privacyhub.data.db.entity.VaultItemEntity

@Dao
interface VaultItemDao {
    @Insert
    fun insert(entity: VaultItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertAllIgnore(entities: List<VaultItemEntity>): List<Long>

    @Update
    fun update(entity: VaultItemEntity)

    @Query("SELECT * FROM vault_items ORDER BY created_at DESC LIMIT :limit")
    fun recent(limit: Int): List<VaultItemEntity>

    @Query("SELECT * FROM vault_items ORDER BY created_at ASC, id ASC")
    fun all(): List<VaultItemEntity>

    @Query("SELECT * FROM vault_items WHERE favorite = 1 ORDER BY updated_at DESC LIMIT :limit")
    fun favorites(limit: Int): List<VaultItemEntity>

    @Query(
        """
        SELECT * FROM vault_items
        WHERE category = :category
        ORDER BY updated_at DESC
        LIMIT :limit
        """
    )
    fun byCategory(category: String, limit: Int): List<VaultItemEntity>

    @Query(
        """
        SELECT * FROM vault_items
        WHERE category IN (:categories)
        ORDER BY updated_at DESC
        LIMIT :limit
        """
    )
    fun byCategories(categories: List<String>, limit: Int): List<VaultItemEntity>

    @Query(
        """
        SELECT DISTINCT category FROM vault_items
        WHERE category IS NOT NULL AND category != ''
        ORDER BY category COLLATE NOCASE ASC
        """
    )
    fun categories(): List<String>

    @Query("UPDATE vault_items SET category = :newCategory, updated_at = :updatedAt WHERE category = :oldCategory")
    fun renameCategory(oldCategory: String, newCategory: String, updatedAt: Long): Int

    @Query("UPDATE vault_items SET category = :newCategory, updated_at = :updatedAt WHERE category IN (:oldCategories)")
    fun moveCategories(oldCategories: List<String>, newCategory: String, updatedAt: Long): Int

    @Query("UPDATE vault_items SET category = NULL, updated_at = :updatedAt WHERE category = :category")
    fun clearCategory(category: String, updatedAt: Long): Int

    @Query("SELECT COUNT(*) FROM vault_items WHERE category IN (:categories)")
    fun countByCategories(categories: List<String>): Int

    @Query("DELETE FROM vault_items WHERE category IN (:categories)")
    fun deleteByCategories(categories: List<String>): Int

    @Query("SELECT * FROM vault_items WHERE id = :id LIMIT 1")
    fun getById(id: Long): VaultItemEntity?

    @Query(
        """
        SELECT * FROM vault_items
        WHERE content LIKE '%' || :query || '%'
           OR title LIKE '%' || :query || '%'
           OR IFNULL(category, '') LIKE '%' || :query || '%'
        ORDER BY updated_at DESC
        LIMIT :limit
        """
    )
    fun search(query: String, limit: Int): List<VaultItemEntity>

    @Query("UPDATE vault_items SET favorite = :favorite, updated_at = :updatedAt WHERE id = :id")
    fun setFavorite(id: Long, favorite: Boolean, updatedAt: Long)

    @Query(
        """
        UPDATE vault_items
        SET title = :title,
            content = :content,
            normalized_link = :normalizedLink,
            original_link = :originalLink,
            access_code = :accessCode,
            category = :category,
            note = :note,
            target = :target,
            updated_at = :updatedAt
        WHERE id = :id
        """
    )
    fun updateDetails(
        id: Long,
        title: String,
        content: String,
        normalizedLink: String?,
        originalLink: String?,
        accessCode: String?,
        category: String?,
        note: String?,
        target: String,
        updatedAt: Long
    )

    @Query("DELETE FROM vault_items WHERE id = :id")
    fun deleteById(id: Long)

    @Query("DELETE FROM vault_items")
    fun deleteAll()
}
