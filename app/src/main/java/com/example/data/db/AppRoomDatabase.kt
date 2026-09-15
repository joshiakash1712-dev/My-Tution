package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
  entities = [
    StudentEntity::class,
    TeacherEntity::class,
    BatchEntity::class,
    BatchEnrollmentEntity::class,
    AttendanceRecordEntity::class,
    FeePaymentEntity::class,
    TimetableEntryEntity::class
  ],
  version = 5,
  exportSchema = false
)
@TypeConverters(RoomConverters::class)
abstract class AppRoomDatabase : RoomDatabase() {

  abstract fun studentDao(): StudentDao
  abstract fun teacherDao(): TeacherDao
  abstract fun batchDao(): BatchDao
  abstract fun batchEnrollmentDao(): BatchEnrollmentDao
  abstract fun attendanceDao(): AttendanceDao
  abstract fun feePaymentDao(): FeePaymentDao
  abstract fun timetableDao(): TimetableEntryDao

  companion object {
    @Volatile
    private var INSTANCE: AppRoomDatabase? = null

    fun getDatabase(context: Context): AppRoomDatabase {
      return INSTANCE ?: synchronized(this) {
        val instance = Room.databaseBuilder(
          context.applicationContext,
          AppRoomDatabase::class.java,
          "my_tuition_fsi_database"
        )
          .fallbackToDestructiveMigration()
          .build()
        INSTANCE = instance
        instance
      }
    }
  }
}
