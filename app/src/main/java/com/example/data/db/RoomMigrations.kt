package com.example.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object RoomMigrations {

  private fun hasColumn(db: SupportSQLiteDatabase, tableName: String, columnName: String): Boolean {
    db.query("PRAGMA table_info(`$tableName`)").use { cursor ->
      val nameIndex = cursor.getColumnIndex("name")
      if (nameIndex == -1) return false
      while (cursor.moveToNext()) {
        if (cursor.getString(nameIndex).equals(columnName, ignoreCase = true)) {
          return true
        }
      }
    }
    return false
  }

  private fun addColumnIfMissing(
    db: SupportSQLiteDatabase,
    tableName: String,
    columnName: String,
    columnDef: String
  ) {
    if (!hasColumn(db, tableName, columnName)) {
      db.execSQL("ALTER TABLE `$tableName` ADD COLUMN `$columnName` $columnDef")
    }
  }

  private fun ensureCoreV1Tables(db: SupportSQLiteDatabase) {
    db.execSQL(
      """
      CREATE TABLE IF NOT EXISTS `students` (
        `id` TEXT NOT NULL,
        `name` TEXT NOT NULL,
        `photo` TEXT NOT NULL,
        `mobile` TEXT NOT NULL,
        `parentName` TEXT NOT NULL,
        `parentContact` TEXT NOT NULL,
        `email` TEXT NOT NULL,
        `dob` TEXT NOT NULL,
        `gender` TEXT NOT NULL,
        `address` TEXT NOT NULL,
        `school` TEXT NOT NULL,
        `className` TEXT NOT NULL,
        `batch` TEXT NOT NULL,
        `stream` TEXT NOT NULL,
        `admissionDate` TEXT NOT NULL,
        `status` TEXT NOT NULL,
        `attendancePercent` INTEGER NOT NULL,
        `overallAvg` INTEGER NOT NULL,
        `rank` INTEGER NOT NULL,
        `strongestSubject` TEXT NOT NULL,
        `weakestSubject` TEXT NOT NULL,
        `recentScoresSerialized` TEXT NOT NULL,
        `syncedToFirestore` INTEGER NOT NULL DEFAULT 0,
        `lastUpdated` INTEGER NOT NULL DEFAULT 0,
        PRIMARY KEY(`id`)
      )
      """.trimIndent()
    )

    db.execSQL(
      """
      CREATE TABLE IF NOT EXISTS `teachers` (
        `id` TEXT NOT NULL,
        `name` TEXT NOT NULL,
        `photo` TEXT NOT NULL DEFAULT '',
        `subject` TEXT NOT NULL,
        `contact` TEXT NOT NULL,
        `qualification` TEXT NOT NULL,
        `experience` TEXT NOT NULL,
        `assignedBatchesSerialized` TEXT NOT NULL,
        `classesTaken` INTEGER NOT NULL,
        `attendancePercent` INTEGER NOT NULL,
        `feedbackRating` REAL NOT NULL,
        `salary` INTEGER NOT NULL,
        `incentives` INTEGER NOT NULL,
        `deductions` INTEGER NOT NULL,
        `status` TEXT NOT NULL,
        `lastUpdated` INTEGER NOT NULL DEFAULT 0,
        PRIMARY KEY(`id`)
      )
      """.trimIndent()
    )

    db.execSQL(
      """
      CREATE TABLE IF NOT EXISTS `batches` (
        `id` TEXT NOT NULL,
        `name` TEXT NOT NULL,
        `schedule` TEXT NOT NULL,
        `studentCount` INTEGER NOT NULL,
        `teacherName` TEXT NOT NULL,
        `syncedToFirestore` INTEGER NOT NULL DEFAULT 0,
        `lastUpdated` INTEGER NOT NULL DEFAULT 0,
        PRIMARY KEY(`id`)
      )
      """.trimIndent()
    )

    db.execSQL(
      """
      CREATE TABLE IF NOT EXISTS `batch_enrollments` (
        `id` TEXT NOT NULL,
        `studentId` TEXT NOT NULL,
        `studentName` TEXT NOT NULL,
        `batchId` TEXT NOT NULL,
        `batchName` TEXT NOT NULL,
        `className` TEXT NOT NULL,
        `stream` TEXT NOT NULL,
        `enrollmentDate` TEXT NOT NULL,
        `feeStatus` TEXT NOT NULL DEFAULT 'Up-to-Date',
        `status` TEXT NOT NULL DEFAULT 'Active',
        `syncedToFirestore` INTEGER NOT NULL DEFAULT 0,
        `lastUpdated` INTEGER NOT NULL DEFAULT 0,
        PRIMARY KEY(`id`)
      )
      """.trimIndent()
    )
  }

  /**
   * Migration 1 -> 2:
   * - Adds extended student demographic & academic target columns to `students`
   * - Adds teacher profile columns (`email`, `gender`, `dob`, `joiningDate`) to `teachers`
   * - Creates `attendance_records` table
   */
  val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
      ensureCoreV1Tables(db)

      addColumnIfMissing(db, "students", "caste", "TEXT NOT NULL DEFAULT 'General'")
      addColumnIfMissing(db, "students", "motherTongue", "TEXT NOT NULL DEFAULT 'Marathi'")
      addColumnIfMissing(db, "students", "parentOccupation", "TEXT NOT NULL DEFAULT 'Business / Service'")
      addColumnIfMissing(db, "students", "yearlyIncome", "TEXT NOT NULL DEFAULT '₹4,50,000'")
      addColumnIfMissing(db, "students", "aadharPhoto", "TEXT NOT NULL DEFAULT ''")
      addColumnIfMissing(db, "students", "markMemoPhoto10th", "TEXT NOT NULL DEFAULT ''")
      addColumnIfMissing(db, "students", "markMemoPhoto11th", "TEXT NOT NULL DEFAULT ''")
      addColumnIfMissing(db, "students", "targetExam", "TEXT NOT NULL DEFAULT 'JEE / NEET / CET'")
      addColumnIfMissing(db, "students", "syncedToFirestore", "INTEGER NOT NULL DEFAULT 0")
      addColumnIfMissing(db, "students", "lastUpdated", "INTEGER NOT NULL DEFAULT 0")

      addColumnIfMissing(db, "teachers", "photo", "TEXT NOT NULL DEFAULT ''")
      addColumnIfMissing(db, "teachers", "email", "TEXT NOT NULL DEFAULT ''")
      addColumnIfMissing(db, "teachers", "gender", "TEXT NOT NULL DEFAULT 'Male'")
      addColumnIfMissing(db, "teachers", "dob", "TEXT NOT NULL DEFAULT '12/04/1988'")
      addColumnIfMissing(db, "teachers", "joiningDate", "TEXT NOT NULL DEFAULT '01/06/2024'")
      addColumnIfMissing(db, "teachers", "lastUpdated", "INTEGER NOT NULL DEFAULT 0")

      db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `attendance_records` (
          `id` TEXT NOT NULL,
          `date` TEXT NOT NULL,
          `day` INTEGER NOT NULL DEFAULT 1,
          `month` INTEGER NOT NULL DEFAULT 1,
          `monthName` TEXT NOT NULL DEFAULT 'January',
          `year` INTEGER NOT NULL DEFAULT 2026,
          `time` TEXT NOT NULL DEFAULT '09:00 AM',
          `studentId` TEXT NOT NULL,
          `studentName` TEXT NOT NULL,
          `batchName` TEXT NOT NULL,
          `status` TEXT NOT NULL,
          `timestamp` INTEGER NOT NULL DEFAULT 0,
          `lastModifiedTimestamp` INTEGER NOT NULL DEFAULT 0,
          `reason` TEXT,
          `recordedBy` TEXT,
          PRIMARY KEY(`id`)
        )
        """.trimIndent()
      )
    }
  }

  /**
   * Migration 2 -> 3:
   * - Creates `fee_payments` and `timetable_entries` tables
   */
  val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
      db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `fee_payments` (
          `id` TEXT NOT NULL,
          `studentId` TEXT NOT NULL,
          `studentName` TEXT NOT NULL,
          `amount` REAL NOT NULL,
          `paymentDate` TEXT NOT NULL,
          `paymentStatus` TEXT NOT NULL,
          `feeType` TEXT NOT NULL,
          `paymentMode` TEXT NOT NULL,
          `receiptNo` TEXT NOT NULL,
          `month` TEXT NOT NULL,
          `remarks` TEXT NOT NULL,
          `recordedBy` TEXT NOT NULL,
          `timestamp` INTEGER NOT NULL DEFAULT 0,
          PRIMARY KEY(`id`)
        )
        """.trimIndent()
      )

      db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `timetable_entries` (
          `id` TEXT NOT NULL,
          `subject` TEXT NOT NULL,
          `batch` TEXT NOT NULL,
          `startTime` TEXT NOT NULL,
          `endTime` TEXT NOT NULL,
          `createdAt` INTEGER NOT NULL DEFAULT 0,
          `teacherName` TEXT NOT NULL DEFAULT 'Subject Faculty',
          PRIMARY KEY(`id`)
        )
        """.trimIndent()
      )
    }
  }

  /**
   * Migration 3 -> 4:
   * - Adds `activationCode` and `isActivated` to `students` and `teachers`
   * - Creates `user_accounts` table and indices
   */
  val MIGRATION_3_4: Migration = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
      addColumnIfMissing(db, "students", "activationCode", "TEXT NOT NULL DEFAULT ''")
      addColumnIfMissing(db, "students", "isActivated", "INTEGER NOT NULL DEFAULT 0")

      addColumnIfMissing(db, "teachers", "activationCode", "TEXT NOT NULL DEFAULT ''")
      addColumnIfMissing(db, "teachers", "isActivated", "INTEGER NOT NULL DEFAULT 0")

      db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `user_accounts` (
          `userId` TEXT NOT NULL,
          `email` TEXT NOT NULL,
          `mobile` TEXT NOT NULL,
          `name` TEXT NOT NULL,
          `passwordHash` TEXT NOT NULL,
          `passwordSalt` TEXT NOT NULL,
          `role` TEXT NOT NULL,
          `linkedEntityId` TEXT NOT NULL DEFAULT '',
          `linkedStudentIds` TEXT NOT NULL DEFAULT '[]',
          `status` TEXT NOT NULL DEFAULT 'Active',
          `instituteId` TEXT NOT NULL DEFAULT 'FSI_MAIN',
          `createdAt` INTEGER NOT NULL DEFAULT 0,
          `lastLoginAt` INTEGER NOT NULL DEFAULT 0,
          PRIMARY KEY(`userId`)
        )
        """.trimIndent()
      )
      db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_user_accounts_email` ON `user_accounts` (`email`)")
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_accounts_mobile` ON `user_accounts` (`mobile`)")
    }
  }

  /**
   * Migration 4 -> 5:
   * - Creates operational tables: `fee_invoices`, `homeworks`, `doubts`, `test_records`,
   *   `subjects`, `leads`, `online_forms`, `notifications`
   */
  val MIGRATION_4_5: Migration = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
      db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `fee_invoices` (
          `id` TEXT NOT NULL,
          `studentId` TEXT NOT NULL,
          `studentName` TEXT NOT NULL,
          `batchName` TEXT NOT NULL DEFAULT '',
          `batchId` TEXT NOT NULL DEFAULT '',
          `instituteId` TEXT NOT NULL DEFAULT 'FSI_MAIN',
          `feeAmount` INTEGER NOT NULL,
          `dueDate` TEXT NOT NULL,
          `paidAmount` INTEGER NOT NULL,
          `pendingAmount` INTEGER NOT NULL,
          `paymentStatus` TEXT NOT NULL,
          `month` TEXT NOT NULL,
          `remindedCount` INTEGER NOT NULL DEFAULT 0,
          `lastReminded` TEXT NOT NULL DEFAULT '',
          `lastUpdated` INTEGER NOT NULL DEFAULT 0,
          PRIMARY KEY(`id`)
        )
        """.trimIndent()
      )
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_fee_invoices_studentId` ON `fee_invoices` (`studentId`)")

      db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `homeworks` (
          `id` TEXT NOT NULL,
          `title` TEXT NOT NULL,
          `subject` TEXT NOT NULL,
          `dueDate` TEXT NOT NULL,
          `description` TEXT NOT NULL,
          `status` TEXT NOT NULL,
          `submissionDate` TEXT,
          `teacherFeedback` TEXT,
          `batchName` TEXT NOT NULL DEFAULT '',
          `assignedBy` TEXT NOT NULL DEFAULT '',
          `completedStudentIds` TEXT NOT NULL DEFAULT '[]',
          `batchId` TEXT NOT NULL DEFAULT '',
          `subjectId` TEXT NOT NULL DEFAULT '',
          `teacherId` TEXT NOT NULL DEFAULT '',
          `instituteId` TEXT NOT NULL DEFAULT 'FSI_MAIN',
          `createdAt` INTEGER NOT NULL DEFAULT 0,
          PRIMARY KEY(`id`)
        )
        """.trimIndent()
      )

      db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `doubts` (
          `id` TEXT NOT NULL,
          `studentId` TEXT NOT NULL,
          `studentName` TEXT NOT NULL,
          `batchName` TEXT NOT NULL DEFAULT '',
          `batchId` TEXT NOT NULL DEFAULT '',
          `subject` TEXT NOT NULL,
          `subjectId` TEXT NOT NULL DEFAULT '',
          `chapter` TEXT NOT NULL,
          `questionText` TEXT NOT NULL,
          `date` TEXT NOT NULL,
          `reply` TEXT,
          `repliedBy` TEXT NOT NULL DEFAULT '',
          `status` TEXT NOT NULL,
          `resolutionTime` TEXT,
          `photoUrl` TEXT,
          `instituteId` TEXT NOT NULL DEFAULT 'FSI_MAIN',
          `createdAt` INTEGER NOT NULL DEFAULT 0,
          `updatedAt` INTEGER NOT NULL DEFAULT 0,
          PRIMARY KEY(`id`)
        )
        """.trimIndent()
      )

      db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `test_records` (
          `id` TEXT NOT NULL,
          `testName` TEXT NOT NULL,
          `subject` TEXT NOT NULL,
          `date` TEXT NOT NULL,
          `batch` TEXT NOT NULL,
          `totalMarks` INTEGER NOT NULL,
          `studentMarksJson` TEXT NOT NULL,
          `remarks` TEXT NOT NULL,
          `aiAnalysisStrong` TEXT NOT NULL,
          `aiAnalysisWeak` TEXT NOT NULL,
          `aiSuggestion` TEXT NOT NULL,
          `batchId` TEXT NOT NULL DEFAULT '',
          `subjectId` TEXT NOT NULL DEFAULT '',
          `questionIds` TEXT NOT NULL DEFAULT '[]',
          `durationMinutes` INTEGER NOT NULL DEFAULT 15,
          `instituteId` TEXT NOT NULL DEFAULT 'FSI_MAIN',
          `createdAt` INTEGER NOT NULL DEFAULT 0,
          PRIMARY KEY(`id`)
        )
        """.trimIndent()
      )

      db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `subjects` (
          `id` TEXT NOT NULL,
          `name` TEXT NOT NULL,
          `completionPercent` INTEGER NOT NULL,
          `completedChapters` TEXT NOT NULL,
          `pendingChapters` TEXT NOT NULL,
          `batchId` TEXT NOT NULL DEFAULT '',
          `batchName` TEXT NOT NULL DEFAULT '',
          `teacherId` TEXT NOT NULL DEFAULT '',
          `instituteId` TEXT NOT NULL DEFAULT 'FSI_MAIN',
          PRIMARY KEY(`id`)
        )
        """.trimIndent()
      )

      db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `leads` (
          `id` TEXT NOT NULL,
          `name` TEXT NOT NULL,
          `mobile` TEXT NOT NULL,
          `className` TEXT NOT NULL,
          `stream` TEXT NOT NULL,
          `source` TEXT NOT NULL,
          `inquiryDate` TEXT NOT NULL,
          `assignedCounselor` TEXT NOT NULL,
          `status` TEXT NOT NULL,
          `instituteId` TEXT NOT NULL DEFAULT 'FSI_MAIN',
          `createdAt` INTEGER NOT NULL DEFAULT 0,
          PRIMARY KEY(`id`)
        )
        """.trimIndent()
      )

      db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `online_forms` (
          `id` TEXT NOT NULL,
          `name` TEXT NOT NULL,
          `mobile` TEXT NOT NULL,
          `email` TEXT NOT NULL,
          `school` TEXT NOT NULL,
          `className` TEXT NOT NULL,
          `stream` TEXT NOT NULL,
          `preferredBatch` TEXT NOT NULL,
          `submissionDate` TEXT NOT NULL,
          `status` TEXT NOT NULL,
          `instituteId` TEXT NOT NULL DEFAULT 'FSI_MAIN',
          `createdAt` INTEGER NOT NULL DEFAULT 0,
          PRIMARY KEY(`id`)
        )
        """.trimIndent()
      )

      db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `notifications` (
          `id` TEXT NOT NULL,
          `title` TEXT NOT NULL,
          `message` TEXT NOT NULL,
          `time` TEXT NOT NULL,
          `type` TEXT NOT NULL,
          `recipientRole` TEXT NOT NULL,
          `isRead` INTEGER NOT NULL DEFAULT 0,
          `targetStudentId` TEXT NOT NULL DEFAULT '',
          `targetBatchName` TEXT NOT NULL DEFAULT '',
          `targetBatchId` TEXT NOT NULL DEFAULT '',
          `instituteId` TEXT NOT NULL DEFAULT 'FSI_MAIN',
          `createdAt` INTEGER NOT NULL DEFAULT 0,
          PRIMARY KEY(`id`)
        )
        """.trimIndent()
      )
    }
  }

  /**
   * Migration 5 -> 6:
   * - Creates CBT & Student/Parent companion tables: `question_bank`, `student_test_attempts`,
   *   `study_logs`, `student_goals`, `parent_meetings`, `weekly_timetable`
   */
  val MIGRATION_5_6: Migration = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
      db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `question_bank` (
          `id` TEXT NOT NULL,
          `subject` TEXT NOT NULL,
          `chapter` TEXT NOT NULL,
          `topic` TEXT NOT NULL,
          `difficulty` TEXT NOT NULL,
          `type` TEXT NOT NULL,
          `questionText` TEXT NOT NULL,
          `options` TEXT NOT NULL,
          `correctOption` INTEGER NOT NULL,
          `solution` TEXT NOT NULL,
          `tags` TEXT NOT NULL,
          `subjectId` TEXT NOT NULL DEFAULT '',
          `testId` TEXT NOT NULL DEFAULT '',
          `instituteId` TEXT NOT NULL DEFAULT 'FSI_MAIN',
          `createdAt` INTEGER NOT NULL DEFAULT 0,
          PRIMARY KEY(`id`)
        )
        """.trimIndent()
      )

      db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `student_test_attempts` (
          `attemptId` TEXT NOT NULL,
          `testId` TEXT NOT NULL,
          `testName` TEXT NOT NULL,
          `studentId` TEXT NOT NULL,
          `studentName` TEXT NOT NULL,
          `instituteId` TEXT NOT NULL DEFAULT 'FSI_MAIN',
          `answersJson` TEXT NOT NULL,
          `score` INTEGER NOT NULL,
          `totalMarks` INTEGER NOT NULL,
          `correctCount` INTEGER NOT NULL,
          `wrongCount` INTEGER NOT NULL,
          `unattemptedCount` INTEGER NOT NULL,
          `timeSpentSeconds` INTEGER NOT NULL,
          `status` TEXT NOT NULL DEFAULT 'Submitted',
          `startedAt` INTEGER NOT NULL DEFAULT 0,
          `submittedAt` INTEGER NOT NULL DEFAULT 0,
          PRIMARY KEY(`attemptId`)
        )
        """.trimIndent()
      )
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_student_test_attempts_studentId` ON `student_test_attempts` (`studentId`)")
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_student_test_attempts_testId` ON `student_test_attempts` (`testId`)")

      db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `study_logs` (
          `id` TEXT NOT NULL,
          `studentId` TEXT NOT NULL,
          `date` TEXT NOT NULL,
          `hours` REAL NOT NULL,
          `subjects` TEXT NOT NULL,
          `chapters` TEXT NOT NULL,
          `instituteId` TEXT NOT NULL DEFAULT 'FSI_MAIN',
          `timestamp` INTEGER NOT NULL DEFAULT 0,
          PRIMARY KEY(`id`)
        )
        """.trimIndent()
      )

      db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `student_goals` (
          `studentId` TEXT NOT NULL,
          `targetMarks` INTEGER NOT NULL,
          `targetPercent` INTEGER NOT NULL,
          `targetRank` INTEGER NOT NULL,
          `targetAttendance` INTEGER NOT NULL,
          `weeklyStudyHours` INTEGER NOT NULL,
          `instituteId` TEXT NOT NULL DEFAULT 'FSI_MAIN',
          `updatedAt` INTEGER NOT NULL DEFAULT 0,
          PRIMARY KEY(`studentId`)
        )
        """.trimIndent()
      )

      db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `parent_meetings` (
          `id` TEXT NOT NULL,
          `studentId` TEXT NOT NULL,
          `studentName` TEXT NOT NULL,
          `parentId` TEXT NOT NULL,
          `parentName` TEXT NOT NULL,
          `teacherId` TEXT NOT NULL,
          `teacherName` TEXT NOT NULL,
          `date` TEXT NOT NULL,
          `time` TEXT NOT NULL,
          `notes` TEXT NOT NULL,
          `actionItems` TEXT NOT NULL,
          `status` TEXT NOT NULL,
          `instituteId` TEXT NOT NULL DEFAULT 'FSI_MAIN',
          `createdAt` INTEGER NOT NULL DEFAULT 0,
          PRIMARY KEY(`id`)
        )
        """.trimIndent()
      )

      db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `weekly_timetable` (
          `id` TEXT NOT NULL,
          `day` TEXT NOT NULL,
          `startTime` TEXT NOT NULL,
          `endTime` TEXT NOT NULL,
          `subject` TEXT NOT NULL,
          `batch` TEXT NOT NULL,
          `room` TEXT NOT NULL,
          `teacherName` TEXT NOT NULL DEFAULT '',
          `batchId` TEXT NOT NULL DEFAULT '',
          `teacherId` TEXT NOT NULL DEFAULT '',
          `subjectId` TEXT NOT NULL DEFAULT '',
          `instituteId` TEXT NOT NULL DEFAULT 'FSI_MAIN',
          PRIMARY KEY(`id`)
        )
        """.trimIndent()
      )
    }
  }

  /**
   * Migration 6 -> 7:
   * - Adds normalized relational & multi-institute tenant columns (`instituteId`, `batchId`,
   *   `parentId`, `parentEmail`, `feeStatus`, `assignedBatchIdsSerialized`, `teacherId`,
   *   `invoiceId`, `transactionId`, `subjectId`, `testId`) across existing entities while preserving data
   */
  val MIGRATION_6_7: Migration = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
      addColumnIfMissing(db, "students", "batchId", "TEXT NOT NULL DEFAULT ''")
      addColumnIfMissing(db, "students", "parentId", "TEXT NOT NULL DEFAULT ''")
      addColumnIfMissing(db, "students", "parentEmail", "TEXT NOT NULL DEFAULT ''")
      addColumnIfMissing(db, "students", "feeStatus", "TEXT NOT NULL DEFAULT 'Pending'")
      addColumnIfMissing(db, "students", "instituteId", "TEXT NOT NULL DEFAULT 'FSI_MAIN'")

      addColumnIfMissing(db, "teachers", "assignedBatchIdsSerialized", "TEXT NOT NULL DEFAULT ''")
      addColumnIfMissing(db, "teachers", "instituteId", "TEXT NOT NULL DEFAULT 'FSI_MAIN'")

      addColumnIfMissing(db, "batches", "teacherId", "TEXT NOT NULL DEFAULT ''")
      addColumnIfMissing(db, "batches", "instituteId", "TEXT NOT NULL DEFAULT 'FSI_MAIN'")

      addColumnIfMissing(db, "attendance_records", "batchId", "TEXT NOT NULL DEFAULT ''")
      addColumnIfMissing(db, "attendance_records", "instituteId", "TEXT NOT NULL DEFAULT 'FSI_MAIN'")

      addColumnIfMissing(db, "fee_payments", "invoiceId", "TEXT NOT NULL DEFAULT ''")
      addColumnIfMissing(db, "fee_payments", "instituteId", "TEXT NOT NULL DEFAULT 'FSI_MAIN'")
      addColumnIfMissing(db, "fee_payments", "transactionId", "TEXT NOT NULL DEFAULT ''")

      addColumnIfMissing(db, "timetable_entries", "teacherName", "TEXT NOT NULL DEFAULT 'Subject Faculty'")
      addColumnIfMissing(db, "timetable_entries", "batchId", "TEXT NOT NULL DEFAULT ''")
      addColumnIfMissing(db, "timetable_entries", "teacherId", "TEXT NOT NULL DEFAULT ''")
      addColumnIfMissing(db, "timetable_entries", "subjectId", "TEXT NOT NULL DEFAULT ''")
      addColumnIfMissing(db, "timetable_entries", "instituteId", "TEXT NOT NULL DEFAULT 'FSI_MAIN'")

      addColumnIfMissing(db, "user_accounts", "linkedStudentIds", "TEXT NOT NULL DEFAULT '[]'")
      addColumnIfMissing(db, "user_accounts", "instituteId", "TEXT NOT NULL DEFAULT 'FSI_MAIN'")

      addColumnIfMissing(db, "fee_invoices", "batchName", "TEXT NOT NULL DEFAULT ''")
      addColumnIfMissing(db, "fee_invoices", "batchId", "TEXT NOT NULL DEFAULT ''")
      addColumnIfMissing(db, "fee_invoices", "instituteId", "TEXT NOT NULL DEFAULT 'FSI_MAIN'")

      addColumnIfMissing(db, "homeworks", "batchName", "TEXT NOT NULL DEFAULT ''")
      addColumnIfMissing(db, "homeworks", "assignedBy", "TEXT NOT NULL DEFAULT ''")
      addColumnIfMissing(db, "homeworks", "completedStudentIds", "TEXT NOT NULL DEFAULT '[]'")
      addColumnIfMissing(db, "homeworks", "batchId", "TEXT NOT NULL DEFAULT ''")
      addColumnIfMissing(db, "homeworks", "subjectId", "TEXT NOT NULL DEFAULT ''")
      addColumnIfMissing(db, "homeworks", "teacherId", "TEXT NOT NULL DEFAULT ''")
      addColumnIfMissing(db, "homeworks", "instituteId", "TEXT NOT NULL DEFAULT 'FSI_MAIN'")

      addColumnIfMissing(db, "doubts", "batchName", "TEXT NOT NULL DEFAULT ''")
      addColumnIfMissing(db, "doubts", "batchId", "TEXT NOT NULL DEFAULT ''")
      addColumnIfMissing(db, "doubts", "subjectId", "TEXT NOT NULL DEFAULT ''")
      addColumnIfMissing(db, "doubts", "repliedBy", "TEXT NOT NULL DEFAULT ''")
      addColumnIfMissing(db, "doubts", "instituteId", "TEXT NOT NULL DEFAULT 'FSI_MAIN'")
      addColumnIfMissing(db, "doubts", "updatedAt", "INTEGER NOT NULL DEFAULT 0")

      addColumnIfMissing(db, "test_records", "batchId", "TEXT NOT NULL DEFAULT ''")
      addColumnIfMissing(db, "test_records", "subjectId", "TEXT NOT NULL DEFAULT ''")
      addColumnIfMissing(db, "test_records", "questionIds", "TEXT NOT NULL DEFAULT '[]'")
      addColumnIfMissing(db, "test_records", "durationMinutes", "INTEGER NOT NULL DEFAULT 15")
      addColumnIfMissing(db, "test_records", "instituteId", "TEXT NOT NULL DEFAULT 'FSI_MAIN'")

      addColumnIfMissing(db, "subjects", "batchId", "TEXT NOT NULL DEFAULT ''")
      addColumnIfMissing(db, "subjects", "batchName", "TEXT NOT NULL DEFAULT ''")
      addColumnIfMissing(db, "subjects", "teacherId", "TEXT NOT NULL DEFAULT ''")
      addColumnIfMissing(db, "subjects", "instituteId", "TEXT NOT NULL DEFAULT 'FSI_MAIN'")

      addColumnIfMissing(db, "leads", "instituteId", "TEXT NOT NULL DEFAULT 'FSI_MAIN'")
      addColumnIfMissing(db, "online_forms", "instituteId", "TEXT NOT NULL DEFAULT 'FSI_MAIN'")

      addColumnIfMissing(db, "notifications", "targetStudentId", "TEXT NOT NULL DEFAULT ''")
      addColumnIfMissing(db, "notifications", "targetBatchName", "TEXT NOT NULL DEFAULT ''")
      addColumnIfMissing(db, "notifications", "targetBatchId", "TEXT NOT NULL DEFAULT ''")
      addColumnIfMissing(db, "notifications", "instituteId", "TEXT NOT NULL DEFAULT 'FSI_MAIN'")

      addColumnIfMissing(db, "question_bank", "subjectId", "TEXT NOT NULL DEFAULT ''")
      addColumnIfMissing(db, "question_bank", "testId", "TEXT NOT NULL DEFAULT ''")
      addColumnIfMissing(db, "question_bank", "instituteId", "TEXT NOT NULL DEFAULT 'FSI_MAIN'")

      addColumnIfMissing(db, "student_test_attempts", "instituteId", "TEXT NOT NULL DEFAULT 'FSI_MAIN'")
      addColumnIfMissing(db, "study_logs", "instituteId", "TEXT NOT NULL DEFAULT 'FSI_MAIN'")
      addColumnIfMissing(db, "student_goals", "instituteId", "TEXT NOT NULL DEFAULT 'FSI_MAIN'")
      addColumnIfMissing(db, "parent_meetings", "instituteId", "TEXT NOT NULL DEFAULT 'FSI_MAIN'")

      addColumnIfMissing(db, "weekly_timetable", "teacherName", "TEXT NOT NULL DEFAULT ''")
      addColumnIfMissing(db, "weekly_timetable", "batchId", "TEXT NOT NULL DEFAULT ''")
      addColumnIfMissing(db, "weekly_timetable", "teacherId", "TEXT NOT NULL DEFAULT ''")
      addColumnIfMissing(db, "weekly_timetable", "subjectId", "TEXT NOT NULL DEFAULT ''")
      addColumnIfMissing(db, "weekly_timetable", "instituteId", "TEXT NOT NULL DEFAULT 'FSI_MAIN'")
    }
  }

  /**
   * Migration 7 -> 8:
   * - Adds dynamic timetable scheduling & archival columns (`dateStr`, `scheduledStartMillis`,
   *   `scheduledEndMillis`, `isArchived`) to `timetable_entries`
   * - Enforces foreign-key constraints (`ON UPDATE CASCADE ON DELETE RESTRICT DEFERRABLE INITIALLY DEFERRED`)
   *   and relational indices while preserving 100% of existing historical records
   */
  val MIGRATION_7_8: Migration = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
      // Ensure any prior columns are present if upgrading from an intermediate v7 build
      MIGRATION_6_7.migrate(db)

      addColumnIfMissing(db, "timetable_entries", "dateStr", "TEXT NOT NULL DEFAULT ''")
      addColumnIfMissing(db, "timetable_entries", "scheduledStartMillis", "INTEGER NOT NULL DEFAULT 0")
      addColumnIfMissing(db, "timetable_entries", "scheduledEndMillis", "INTEGER NOT NULL DEFAULT 0")
      addColumnIfMissing(db, "timetable_entries", "isArchived", "INTEGER NOT NULL DEFAULT 0")

      // Parent indices on students, teachers, and batches
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_students_batchId` ON `students` (`batchId`)")
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_students_instituteId` ON `students` (`instituteId`)")
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_students_status` ON `students` (`status`)")
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_teachers_instituteId` ON `teachers` (`instituteId`)")
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_batches_teacherId` ON `batches` (`teacherId`)")
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_batches_instituteId` ON `batches` (`instituteId`)")

      // Preserve any historical studentId or batchId referenced by child tables so zero historical rows are orphaned or lost
      val childTablesWithStudentName = listOf(
        "batch_enrollments" to "studentName",
        "attendance_records" to "studentName",
        "fee_payments" to "studentName",
        "fee_invoices" to "studentName",
        "doubts" to "studentName",
        "student_test_attempts" to "studentName",
        "parent_meetings" to "studentName"
      )
      for ((tableName, nameCol) in childTablesWithStudentName) {
        db.execSQL(
          """
          INSERT OR IGNORE INTO `students` (
            `id`, `name`, `photo`, `mobile`, `parentName`, `parentContact`, `email`, `dob`, `gender`,
            `address`, `school`, `className`, `batch`, `stream`, `admissionDate`, `status`,
            `attendancePercent`, `overallAvg`, `rank`, `strongestSubject`, `weakestSubject`,
            `recentScoresSerialized`, `caste`, `motherTongue`, `parentOccupation`, `yearlyIncome`,
            `aadharPhoto`, `markMemoPhoto10th`, `markMemoPhoto11th`, `targetExam`, `activationCode`,
            `isActivated`, `batchId`, `parentId`, `parentEmail`, `feeStatus`, `instituteId`,
            `syncedToFirestore`, `lastUpdated`
          )
          SELECT DISTINCT
            `studentId`, `$nameCol`, '', '', '', '', '', '', '',
            '', '', '', '', '', '', 'Inactive',
            0, 0, 0, '', '',
            '', 'General', 'Marathi', 'Business / Service', '₹4,50,000',
            '', '', '', 'JEE / NEET / CET', '',
            0, '', '', '', 'Pending', 'FSI_MAIN',
            0, 0
          FROM `$tableName`
          WHERE `studentId` NOT IN (SELECT `id` FROM `students`)
          """.trimIndent()
        )
      }

      val childTablesWithoutStudentName = listOf("study_logs", "student_goals")
      for (tableName in childTablesWithoutStudentName) {
        db.execSQL(
          """
          INSERT OR IGNORE INTO `students` (
            `id`, `name`, `photo`, `mobile`, `parentName`, `parentContact`, `email`, `dob`, `gender`,
            `address`, `school`, `className`, `batch`, `stream`, `admissionDate`, `status`,
            `attendancePercent`, `overallAvg`, `rank`, `strongestSubject`, `weakestSubject`,
            `recentScoresSerialized`, `caste`, `motherTongue`, `parentOccupation`, `yearlyIncome`,
            `aadharPhoto`, `markMemoPhoto10th`, `markMemoPhoto11th`, `targetExam`, `activationCode`,
            `isActivated`, `batchId`, `parentId`, `parentEmail`, `feeStatus`, `instituteId`,
            `syncedToFirestore`, `lastUpdated`
          )
          SELECT DISTINCT
            `studentId`, 'Preserved Student ' || `studentId`, '', '', '', '', '', '', '',
            '', '', '', '', '', '', 'Inactive',
            0, 0, 0, '', '',
            '', 'General', 'Marathi', 'Business / Service', '₹4,50,000',
            '', '', '', 'JEE / NEET / CET', '',
            0, '', '', '', 'Pending', 'FSI_MAIN',
            0, 0
          FROM `$tableName`
          WHERE `studentId` NOT IN (SELECT `id` FROM `students`)
          """.trimIndent()
        )
      }

      // Preserve any batchId referenced in batch_enrollments
      db.execSQL(
        """
        INSERT OR IGNORE INTO `batches` (
          `id`, `name`, `schedule`, `studentCount`, `teacherName`, `teacherId`, `instituteId`, `syncedToFirestore`, `lastUpdated`
        )
        SELECT DISTINCT
          `batchId`, `batchName`, '', 0, '', '', 'FSI_MAIN', 0, 0
        FROM `batch_enrollments`
        WHERE `batchId` NOT IN (SELECT `id` FROM `batches`)
        """.trimIndent()
      )

      // 1. Rebuild batch_enrollments with ForeignKey constraints & indices
      db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `batch_enrollments_new` (
          `id` TEXT NOT NULL,
          `studentId` TEXT NOT NULL,
          `studentName` TEXT NOT NULL,
          `batchId` TEXT NOT NULL,
          `batchName` TEXT NOT NULL,
          `className` TEXT NOT NULL,
          `stream` TEXT NOT NULL,
          `enrollmentDate` TEXT NOT NULL,
          `feeStatus` TEXT NOT NULL,
          `status` TEXT NOT NULL,
          `syncedToFirestore` INTEGER NOT NULL,
          `lastUpdated` INTEGER NOT NULL,
          PRIMARY KEY(`id`),
          FOREIGN KEY(`studentId`) REFERENCES `students`(`id`) ON UPDATE CASCADE ON DELETE RESTRICT DEFERRABLE INITIALLY DEFERRED,
          FOREIGN KEY(`batchId`) REFERENCES `batches`(`id`) ON UPDATE CASCADE ON DELETE RESTRICT DEFERRABLE INITIALLY DEFERRED
        )
        """.trimIndent()
      )
      db.execSQL(
        """
        INSERT INTO `batch_enrollments_new` (
          `id`, `studentId`, `studentName`, `batchId`, `batchName`, `className`, `stream`,
          `enrollmentDate`, `feeStatus`, `status`, `syncedToFirestore`, `lastUpdated`
        )
        SELECT
          `id`, `studentId`, `studentName`, `batchId`, `batchName`, `className`, `stream`,
          `enrollmentDate`, `feeStatus`, `status`, `syncedToFirestore`, `lastUpdated`
        FROM `batch_enrollments`
        """.trimIndent()
      )
      db.execSQL("DROP TABLE `batch_enrollments`")
      db.execSQL("ALTER TABLE `batch_enrollments_new` RENAME TO `batch_enrollments`")
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_batch_enrollments_studentId` ON `batch_enrollments` (`studentId`)")
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_batch_enrollments_batchId` ON `batch_enrollments` (`batchId`)")

      // 2. Rebuild attendance_records with ForeignKey constraint & indices
      db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `attendance_records_new` (
          `id` TEXT NOT NULL,
          `date` TEXT NOT NULL,
          `day` INTEGER NOT NULL,
          `month` INTEGER NOT NULL,
          `monthName` TEXT NOT NULL,
          `year` INTEGER NOT NULL,
          `time` TEXT NOT NULL,
          `studentId` TEXT NOT NULL,
          `studentName` TEXT NOT NULL,
          `batchName` TEXT NOT NULL,
          `status` TEXT NOT NULL,
          `timestamp` INTEGER NOT NULL,
          `lastModifiedTimestamp` INTEGER NOT NULL,
          `reason` TEXT,
          `recordedBy` TEXT,
          `batchId` TEXT NOT NULL,
          `instituteId` TEXT NOT NULL,
          PRIMARY KEY(`id`),
          FOREIGN KEY(`studentId`) REFERENCES `students`(`id`) ON UPDATE CASCADE ON DELETE RESTRICT DEFERRABLE INITIALLY DEFERRED
        )
        """.trimIndent()
      )
      db.execSQL(
        """
        INSERT INTO `attendance_records_new` (
          `id`, `date`, `day`, `month`, `monthName`, `year`, `time`, `studentId`, `studentName`,
          `batchName`, `status`, `timestamp`, `lastModifiedTimestamp`, `reason`, `recordedBy`, `batchId`, `instituteId`
        )
        SELECT
          `id`, `date`, `day`, `month`, `monthName`, `year`, `time`, `studentId`, `studentName`,
          `batchName`, `status`, `timestamp`, `lastModifiedTimestamp`, `reason`, `recordedBy`, `batchId`, `instituteId`
        FROM `attendance_records`
        """.trimIndent()
      )
      db.execSQL("DROP TABLE `attendance_records`")
      db.execSQL("ALTER TABLE `attendance_records_new` RENAME TO `attendance_records`")
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_attendance_records_studentId` ON `attendance_records` (`studentId`)")
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_attendance_records_batchId` ON `attendance_records` (`batchId`)")
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_attendance_records_date` ON `attendance_records` (`date`)")
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_attendance_records_instituteId` ON `attendance_records` (`instituteId`)")

      // 3. Rebuild fee_payments with ForeignKey constraint & indices
      db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `fee_payments_new` (
          `id` TEXT NOT NULL,
          `studentId` TEXT NOT NULL,
          `studentName` TEXT NOT NULL,
          `amount` REAL NOT NULL,
          `paymentDate` TEXT NOT NULL,
          `paymentStatus` TEXT NOT NULL,
          `feeType` TEXT NOT NULL,
          `paymentMode` TEXT NOT NULL,
          `receiptNo` TEXT NOT NULL,
          `month` TEXT NOT NULL,
          `remarks` TEXT NOT NULL,
          `recordedBy` TEXT NOT NULL,
          `timestamp` INTEGER NOT NULL,
          `invoiceId` TEXT NOT NULL,
          `instituteId` TEXT NOT NULL,
          `transactionId` TEXT NOT NULL,
          PRIMARY KEY(`id`),
          FOREIGN KEY(`studentId`) REFERENCES `students`(`id`) ON UPDATE CASCADE ON DELETE RESTRICT DEFERRABLE INITIALLY DEFERRED
        )
        """.trimIndent()
      )
      db.execSQL(
        """
        INSERT INTO `fee_payments_new` (
          `id`, `studentId`, `studentName`, `amount`, `paymentDate`, `paymentStatus`, `feeType`,
          `paymentMode`, `receiptNo`, `month`, `remarks`, `recordedBy`, `timestamp`, `invoiceId`, `instituteId`, `transactionId`
        )
        SELECT
          `id`, `studentId`, `studentName`, `amount`, `paymentDate`, `paymentStatus`, `feeType`,
          `paymentMode`, `receiptNo`, `month`, `remarks`, `recordedBy`, `timestamp`, `invoiceId`, `instituteId`, `transactionId`
        FROM `fee_payments`
        """.trimIndent()
      )
      db.execSQL("DROP TABLE `fee_payments`")
      db.execSQL("ALTER TABLE `fee_payments_new` RENAME TO `fee_payments`")
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_fee_payments_studentId` ON `fee_payments` (`studentId`)")
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_fee_payments_invoiceId` ON `fee_payments` (`invoiceId`)")
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_fee_payments_instituteId` ON `fee_payments` (`instituteId`)")

      // 4. Rebuild fee_invoices with ForeignKey constraint & indices
      db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `fee_invoices_new` (
          `id` TEXT NOT NULL,
          `studentId` TEXT NOT NULL,
          `studentName` TEXT NOT NULL,
          `batchName` TEXT NOT NULL,
          `batchId` TEXT NOT NULL,
          `instituteId` TEXT NOT NULL,
          `feeAmount` INTEGER NOT NULL,
          `dueDate` TEXT NOT NULL,
          `paidAmount` INTEGER NOT NULL,
          `pendingAmount` INTEGER NOT NULL,
          `paymentStatus` TEXT NOT NULL,
          `month` TEXT NOT NULL,
          `remindedCount` INTEGER NOT NULL,
          `lastReminded` TEXT NOT NULL,
          `lastUpdated` INTEGER NOT NULL,
          PRIMARY KEY(`id`),
          FOREIGN KEY(`studentId`) REFERENCES `students`(`id`) ON UPDATE CASCADE ON DELETE RESTRICT DEFERRABLE INITIALLY DEFERRED
        )
        """.trimIndent()
      )
      db.execSQL(
        """
        INSERT INTO `fee_invoices_new` (
          `id`, `studentId`, `studentName`, `batchName`, `batchId`, `instituteId`, `feeAmount`,
          `dueDate`, `paidAmount`, `pendingAmount`, `paymentStatus`, `month`, `remindedCount`, `lastReminded`, `lastUpdated`
        )
        SELECT
          `id`, `studentId`, `studentName`, `batchName`, `batchId`, `instituteId`, `feeAmount`,
          `dueDate`, `paidAmount`, `pendingAmount`, `paymentStatus`, `month`, `remindedCount`, `lastReminded`, `lastUpdated`
        FROM `fee_invoices`
        """.trimIndent()
      )
      db.execSQL("DROP TABLE `fee_invoices`")
      db.execSQL("ALTER TABLE `fee_invoices_new` RENAME TO `fee_invoices`")
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_fee_invoices_studentId` ON `fee_invoices` (`studentId`)")
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_fee_invoices_instituteId` ON `fee_invoices` (`instituteId`)")

      // 5. Rebuild doubts with ForeignKey constraint & indices
      db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `doubts_new` (
          `id` TEXT NOT NULL,
          `studentId` TEXT NOT NULL,
          `studentName` TEXT NOT NULL,
          `batchName` TEXT NOT NULL,
          `batchId` TEXT NOT NULL,
          `subject` TEXT NOT NULL,
          `subjectId` TEXT NOT NULL,
          `chapter` TEXT NOT NULL,
          `questionText` TEXT NOT NULL,
          `date` TEXT NOT NULL,
          `reply` TEXT,
          `repliedBy` TEXT NOT NULL,
          `status` TEXT NOT NULL,
          `resolutionTime` TEXT,
          `photoUrl` TEXT,
          `instituteId` TEXT NOT NULL,
          `createdAt` INTEGER NOT NULL,
          `updatedAt` INTEGER NOT NULL,
          PRIMARY KEY(`id`),
          FOREIGN KEY(`studentId`) REFERENCES `students`(`id`) ON UPDATE CASCADE ON DELETE RESTRICT DEFERRABLE INITIALLY DEFERRED
        )
        """.trimIndent()
      )
      db.execSQL(
        """
        INSERT INTO `doubts_new` (
          `id`, `studentId`, `studentName`, `batchName`, `batchId`, `subject`, `subjectId`,
          `chapter`, `questionText`, `date`, `reply`, `repliedBy`, `status`, `resolutionTime`,
          `photoUrl`, `instituteId`, `createdAt`, `updatedAt`
        )
        SELECT
          `id`, `studentId`, `studentName`, `batchName`, `batchId`, `subject`, `subjectId`,
          `chapter`, `questionText`, `date`, `reply`, `repliedBy`, `status`, `resolutionTime`,
          `photoUrl`, `instituteId`, `createdAt`, `updatedAt`
        FROM `doubts`
        """.trimIndent()
      )
      db.execSQL("DROP TABLE `doubts`")
      db.execSQL("ALTER TABLE `doubts_new` RENAME TO `doubts`")
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_doubts_studentId` ON `doubts` (`studentId`)")
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_doubts_instituteId` ON `doubts` (`instituteId`)")

      // 6. Rebuild student_test_attempts with ForeignKey constraint & indices
      db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `student_test_attempts_new` (
          `attemptId` TEXT NOT NULL,
          `testId` TEXT NOT NULL,
          `testName` TEXT NOT NULL,
          `studentId` TEXT NOT NULL,
          `studentName` TEXT NOT NULL,
          `instituteId` TEXT NOT NULL,
          `answersJson` TEXT NOT NULL,
          `score` INTEGER NOT NULL,
          `totalMarks` INTEGER NOT NULL,
          `correctCount` INTEGER NOT NULL,
          `wrongCount` INTEGER NOT NULL,
          `unattemptedCount` INTEGER NOT NULL,
          `timeSpentSeconds` INTEGER NOT NULL,
          `status` TEXT NOT NULL,
          `startedAt` INTEGER NOT NULL,
          `submittedAt` INTEGER NOT NULL,
          PRIMARY KEY(`attemptId`),
          FOREIGN KEY(`studentId`) REFERENCES `students`(`id`) ON UPDATE CASCADE ON DELETE RESTRICT DEFERRABLE INITIALLY DEFERRED
        )
        """.trimIndent()
      )
      db.execSQL(
        """
        INSERT INTO `student_test_attempts_new` (
          `attemptId`, `testId`, `testName`, `studentId`, `studentName`, `instituteId`,
          `answersJson`, `score`, `totalMarks`, `correctCount`, `wrongCount`, `unattemptedCount`,
          `timeSpentSeconds`, `status`, `startedAt`, `submittedAt`
        )
        SELECT
          `attemptId`, `testId`, `testName`, `studentId`, `studentName`, `instituteId`,
          `answersJson`, `score`, `totalMarks`, `correctCount`, `wrongCount`, `unattemptedCount`,
          `timeSpentSeconds`, `status`, `startedAt`, `submittedAt`
        FROM `student_test_attempts`
        """.trimIndent()
      )
      db.execSQL("DROP TABLE `student_test_attempts`")
      db.execSQL("ALTER TABLE `student_test_attempts_new` RENAME TO `student_test_attempts`")
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_student_test_attempts_studentId` ON `student_test_attempts` (`studentId`)")
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_student_test_attempts_testId` ON `student_test_attempts` (`testId`)")

      // 7. Rebuild study_logs with ForeignKey constraint & indices
      db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `study_logs_new` (
          `id` TEXT NOT NULL,
          `studentId` TEXT NOT NULL,
          `date` TEXT NOT NULL,
          `hours` REAL NOT NULL,
          `subjects` TEXT NOT NULL,
          `chapters` TEXT NOT NULL,
          `instituteId` TEXT NOT NULL,
          `timestamp` INTEGER NOT NULL,
          PRIMARY KEY(`id`),
          FOREIGN KEY(`studentId`) REFERENCES `students`(`id`) ON UPDATE CASCADE ON DELETE RESTRICT DEFERRABLE INITIALLY DEFERRED
        )
        """.trimIndent()
      )
      db.execSQL(
        """
        INSERT INTO `study_logs_new` (
          `id`, `studentId`, `date`, `hours`, `subjects`, `chapters`, `instituteId`, `timestamp`
        )
        SELECT
          `id`, `studentId`, `date`, `hours`, `subjects`, `chapters`, `instituteId`, `timestamp`
        FROM `study_logs`
        """.trimIndent()
      )
      db.execSQL("DROP TABLE `study_logs`")
      db.execSQL("ALTER TABLE `study_logs_new` RENAME TO `study_logs`")
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_study_logs_studentId` ON `study_logs` (`studentId`)")
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_study_logs_instituteId` ON `study_logs` (`instituteId`)")

      // 8. Rebuild student_goals with ForeignKey constraint & index
      db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `student_goals_new` (
          `studentId` TEXT NOT NULL,
          `targetMarks` INTEGER NOT NULL,
          `targetPercent` INTEGER NOT NULL,
          `targetRank` INTEGER NOT NULL,
          `targetAttendance` INTEGER NOT NULL,
          `weeklyStudyHours` INTEGER NOT NULL,
          `instituteId` TEXT NOT NULL,
          `updatedAt` INTEGER NOT NULL,
          PRIMARY KEY(`studentId`),
          FOREIGN KEY(`studentId`) REFERENCES `students`(`id`) ON UPDATE CASCADE ON DELETE RESTRICT DEFERRABLE INITIALLY DEFERRED
        )
        """.trimIndent()
      )
      db.execSQL(
        """
        INSERT INTO `student_goals_new` (
          `studentId`, `targetMarks`, `targetPercent`, `targetRank`, `targetAttendance`,
          `weeklyStudyHours`, `instituteId`, `updatedAt`
        )
        SELECT
          `studentId`, `targetMarks`, `targetPercent`, `targetRank`, `targetAttendance`,
          `weeklyStudyHours`, `instituteId`, `updatedAt`
        FROM `student_goals`
        """.trimIndent()
      )
      db.execSQL("DROP TABLE `student_goals`")
      db.execSQL("ALTER TABLE `student_goals_new` RENAME TO `student_goals`")
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_student_goals_studentId` ON `student_goals` (`studentId`)")

      // 9. Rebuild parent_meetings with ForeignKey constraint & indices
      db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `parent_meetings_new` (
          `id` TEXT NOT NULL,
          `studentId` TEXT NOT NULL,
          `studentName` TEXT NOT NULL,
          `parentId` TEXT NOT NULL,
          `parentName` TEXT NOT NULL,
          `teacherId` TEXT NOT NULL,
          `teacherName` TEXT NOT NULL,
          `date` TEXT NOT NULL,
          `time` TEXT NOT NULL,
          `notes` TEXT NOT NULL,
          `actionItems` TEXT NOT NULL,
          `status` TEXT NOT NULL,
          `instituteId` TEXT NOT NULL,
          `createdAt` INTEGER NOT NULL,
          PRIMARY KEY(`id`),
          FOREIGN KEY(`studentId`) REFERENCES `students`(`id`) ON UPDATE CASCADE ON DELETE RESTRICT DEFERRABLE INITIALLY DEFERRED
        )
        """.trimIndent()
      )
      db.execSQL(
        """
        INSERT INTO `parent_meetings_new` (
          `id`, `studentId`, `studentName`, `parentId`, `parentName`, `teacherId`, `teacherName`,
          `date`, `time`, `notes`, `actionItems`, `status`, `instituteId`, `createdAt`
        )
        SELECT
          `id`, `studentId`, `studentName`, `parentId`, `parentName`, `teacherId`, `teacherName`,
          `date`, `time`, `notes`, `actionItems`, `status`, `instituteId`, `createdAt`
        FROM `parent_meetings`
        """.trimIndent()
      )
      db.execSQL("DROP TABLE `parent_meetings`")
      db.execSQL("ALTER TABLE `parent_meetings_new` RENAME TO `parent_meetings`")
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_parent_meetings_studentId` ON `parent_meetings` (`studentId`)")
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_parent_meetings_teacherId` ON `parent_meetings` (`teacherId`)")
    }
  }

  /**
   * Migration 8 -> 9:
   * - Creates the persistent `sync_queue` table and its deterministic lookup indices
   *   without modifying or dropping any existing user or academic tables.
   */
  val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
      db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `sync_queue` (
          `queueId` TEXT NOT NULL,
          `instituteId` TEXT NOT NULL,
          `entityType` TEXT NOT NULL,
          `entityId` TEXT NOT NULL,
          `operationType` TEXT NOT NULL,
          `serializedPayload` TEXT NOT NULL,
          `createdAt` INTEGER NOT NULL,
          `updatedAt` INTEGER NOT NULL,
          `retryCount` INTEGER NOT NULL,
          `lastAttemptAt` INTEGER,
          `status` TEXT NOT NULL,
          `errorMessage` TEXT,
          PRIMARY KEY(`queueId`)
        )
        """.trimIndent()
      )
      db.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_sync_queue_status_createdAt` ON `sync_queue` (`status`, `createdAt`)"
      )
      db.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_sync_queue_instituteId` ON `sync_queue` (`instituteId`)"
      )
      db.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_sync_queue_entityType_entityId` ON `sync_queue` (`entityType`, `entityId`)"
      )
    }
  }

  /**
   * Migration 9 -> 10:
   * - Adds dedicated tables and indices for courses, course_enrollments, and fee_structures.
   */
  val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
      db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `courses` (
          `id` TEXT NOT NULL,
          `name` TEXT NOT NULL,
          `code` TEXT NOT NULL,
          `description` TEXT NOT NULL,
          `grade` TEXT NOT NULL,
          `stream` TEXT NOT NULL,
          `subjectsSerialized` TEXT NOT NULL,
          `totalFee` REAL NOT NULL,
          `durationMonths` INTEGER NOT NULL,
          `hoursPerWeek` INTEGER NOT NULL,
          `leadInstructorId` TEXT NOT NULL,
          `leadInstructorName` TEXT NOT NULL,
          `status` TEXT NOT NULL,
          `maxCapacity` INTEGER NOT NULL,
          `enrolledStudentCount` INTEGER NOT NULL,
          `syllabusChaptersSerialized` TEXT NOT NULL,
          `instituteId` TEXT NOT NULL,
          `syncedToFirestore` INTEGER NOT NULL DEFAULT 0,
          `createdAt` INTEGER NOT NULL,
          `updatedAt` INTEGER NOT NULL,
          PRIMARY KEY(`id`)
        )
        """.trimIndent()
      )
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_courses_grade` ON `courses` (`grade`)")
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_courses_stream` ON `courses` (`stream`)")
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_courses_instituteId` ON `courses` (`instituteId`)")
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_courses_leadInstructorId` ON `courses` (`leadInstructorId`)")

      db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `course_enrollments` (
          `enrollmentId` TEXT NOT NULL,
          `studentId` TEXT NOT NULL,
          `courseId` TEXT NOT NULL,
          `batchId` TEXT NOT NULL,
          `enrolledDate` TEXT NOT NULL,
          `status` TEXT NOT NULL,
          `discountPercent` REAL NOT NULL,
          `netFee` REAL NOT NULL,
          `instituteId` TEXT NOT NULL,
          `createdAt` INTEGER NOT NULL,
          PRIMARY KEY(`enrollmentId`),
          FOREIGN KEY(`studentId`) REFERENCES `students`(`id`) ON UPDATE CASCADE ON DELETE RESTRICT DEFERRABLE INITIALLY DEFERRED
        )
        """.trimIndent()
      )
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_course_enrollments_studentId` ON `course_enrollments` (`studentId`)")
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_course_enrollments_courseId` ON `course_enrollments` (`courseId`)")
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_course_enrollments_batchId` ON `course_enrollments` (`batchId`)")
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_course_enrollments_instituteId` ON `course_enrollments` (`instituteId`)")

      db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `fee_structures` (
          `id` TEXT NOT NULL,
          `courseId` TEXT NOT NULL,
          `courseName` TEXT NOT NULL,
          `grade` TEXT NOT NULL,
          `stream` TEXT NOT NULL,
          `baseTuitionFee` REAL NOT NULL,
          `examFee` REAL NOT NULL,
          `materialFee` REAL NOT NULL,
          `totalFee` REAL NOT NULL,
          `installmentCount` INTEGER NOT NULL,
          `installmentIntervalMonths` INTEGER NOT NULL,
          `allowDiscount` INTEGER NOT NULL,
          `academicYear` TEXT NOT NULL,
          `instituteId` TEXT NOT NULL,
          `createdAt` INTEGER NOT NULL,
          PRIMARY KEY(`id`)
        )
        """.trimIndent()
      )
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_fee_structures_courseId` ON `fee_structures` (`courseId`)")
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_fee_structures_grade` ON `fee_structures` (`grade`)")
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_fee_structures_stream` ON `fee_structures` (`stream`)")
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_fee_structures_instituteId` ON `fee_structures` (`instituteId`)")
    }
  }

  val ALL_MIGRATIONS: Array<Migration> = arrayOf(
    MIGRATION_1_2,
    MIGRATION_2_3,
    MIGRATION_3_4,
    MIGRATION_4_5,
    MIGRATION_5_6,
    MIGRATION_6_7,
    MIGRATION_7_8,
    MIGRATION_8_9,
    MIGRATION_9_10
  )
}
