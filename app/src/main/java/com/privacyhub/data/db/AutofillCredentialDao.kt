package com.privacyhub.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.privacyhub.data.db.entity.AutofillCredentialEntity

@Dao
interface AutofillCredentialDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(entity: AutofillCredentialEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertAllIgnore(entities: List<AutofillCredentialEntity>): List<Long>

    @Update
    fun update(entity: AutofillCredentialEntity)

    @Query("SELECT * FROM autofill_credentials ORDER BY updated_at DESC")
    fun all(): List<AutofillCredentialEntity>

    @Query("SELECT * FROM autofill_credentials WHERE id = :id LIMIT 1")
    fun getById(id: Long): AutofillCredentialEntity?

    @Query("DELETE FROM autofill_credentials WHERE id = :id")
    fun deleteById(id: Long)

    @Query("DELETE FROM autofill_credentials")
    fun deleteAll()
}
