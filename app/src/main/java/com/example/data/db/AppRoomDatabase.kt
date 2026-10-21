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
    WeeklyTimetableEntity::class,
    SyncQueueEntity::class,
    CourseEntity::class,
    CourseEnrollmentEntity::class,
    FeeStructureEntity::class
  ],
  version = 10,
  exportSchema = true
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
  abstract fun syncQueueDao(): SyncQueueDao
  abstract fun courseDao(): CourseDao
  abstract fun courseEnrollmentDao(): CourseEnrollmentDao
  abstract fun feeStructureDao(): FeeStructureDao

  companion object {
    val MIGRATION_1_2 = RoomMigrations.MIGRATION_1_2
    val MIGRATION_2_3 = RoomMigrations.MIGRATION_2_3
    val MIGRATION_3_4 = RoomMigrations.MIGRATION_3_4
    val MIGRATION_4_5 = RoomMigrations.MIGRATION_4_5
    val MIGRATION_5_6 = RoomMigrations.MIGRATION_5_6
    val MIGRATION_6_7 = RoomMigrations.MIGRATION_6_7
    val MIGRATION_7_8 = RoomMigrations.MIGRATION_7_8
    val MIGRATION_8_9 = RoomMigrations.MIGRATION_8_9
    val MIGRATION_9_10 = RoomMigrations.MIGRATION_9_10
    val ALL_MIGRATIONS = RoomMigrations.ALL_MIGRATIONS

    @Volatile
    private var INSTANCE: AppRoomDatabase? = null

    fun getInstanceOrNull(): AppRoomDatabase? = INSTANCE

    fun getDatabase(context: Context): AppRoomDatabase {
      return INSTANCE ?: synchronized(this) {
        val existing = INSTANCE
        if (existing != null) {
          existing
        } else {
          val instance = Room.databaseBuilder(
            context.applicationContext,
            AppRoomDatabase::class.java,
            "my_tuition_fsi_database"
          )
            .addMigrations(*ALL_MIGRATIONS)
            .build()
          INSTANCE = instance
          instance
        }
      }
    }

    @Synchronized
    fun closeAndResetInstance() {
      try {
        INSTANCE?.close()
      } catch (_: Exception) {
      } finally {
        INSTANCE = null
      }
    }
  }
}
