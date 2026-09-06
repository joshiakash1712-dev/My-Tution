package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TimetableEntryDao {
  @Query("SELECT * FROM timetable_entries ORDER BY createdAt DESC")
  fun getAllTimetableEntries(): Flow<List<TimetableEntryEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertTimetableEntry(entry: TimetableEntryEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertTimetableEntries(entries: List<TimetableEntryEntity>)

  @Query("DELETE FROM timetable_entries WHERE id = :id")
  suspend fun deleteTimetableEntry(id: String)

  @Query("DELETE FROM timetable_entries")
  suspend fun deleteAllTimetableEntries()
}
