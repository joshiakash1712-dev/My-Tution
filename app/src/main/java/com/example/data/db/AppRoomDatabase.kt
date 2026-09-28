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
    TimetableEntryEntity::class,
    UserAccountEntity::class,
    FeeInvoiceEntity::class,
    HomeworkEntity::class,
    DoubtEntity::class,
    TestRecordEntity::class,
    SubjectEntity::class,
    LeadEntity::class,
    OnlineFormEntity::class,
    NotificationEntity::class,
    QuestionEntity::class,
    StudentAttemptEntity::class,
    StudyLogEntity::class,
    StudentGoalEntity::class,
    ParentMeetingEntity::class,
    WeeklyTimetableEntity::class
  ],
  version = 8,
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
  abstract fun userAccountDao(): UserAccountDao
  abstract fun feeInvoiceDao(): FeeInvoiceDao
  abstract fun homeworkDao(): HomeworkDao
  abstract fun doubtDao(): DoubtDao
  abstract fun testRecordDao(): TestRecordDao
  abstract fun subjectDao(): SubjectDao
  abstract fun leadDao(): LeadDao
  abstract fun onlineFormDao(): OnlineFormDao
  abstract fun notificationDao(): NotificationDao
  abstract fun questionDao(): QuestionDao
  abstract fun studentAttemptDao(): StudentAttemptDao
  abstract fun studyLogDao(): StudyLogDao
  abstract fun studentGoalDao(): StudentGoalDao
  abstract fun parentMeetingDao(): ParentMeetingDao
  abstract fun weeklyTimetableDao(): WeeklyTimetableDao

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
