package com.privacyhub.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.privacyhub.data.db.entity.TestReportEntity

@Dao
interface TestReportDao {
    @Insert
    fun insert(entity: TestReportEntity): Long

    @Query("SELECT * FROM test_reports ORDER BY created_at DESC LIMIT :limit")
    fun recent(limit: Int): List<TestReportEntity>
}
