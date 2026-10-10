package com.example.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.Teacher

@Entity(
  tableName = "teachers",
  indices = [
    Index(value = ["instituteId"])
  ]
)
data class TeacherEntity(
  @PrimaryKey val id: String,
  val name: String,
  val photo: String = "",
  val subject: String,
  val contact: String,
  val qualification: String,
  val experience: String,
  val assignedBatchesSerialized: String,
  val classesTaken: Int,
  val attendancePercent: Int,
  val feedbackRating: Float,
  val salary: Int,
  val incentives: Int,
  val deductions: Int,
  val status: String,
  val email: String = "",
  val gender: String = "Male",
  val dob: String = "12/04/1988",
  val joiningDate: String = "01/06/2024",
  val activationCode: String = "",
  val isActivated: Boolean = false,
  val assignedBatchIdsSerialized: String = "",
  val instituteId: String = "FSI_MAIN",
  val lastUpdated: Long = System.currentTimeMillis()
) {
  fun toDomain(): Teacher {
    val batches = if (assignedBatchesSerialized.isBlank()) {
      emptyList()
    } else {
      assignedBatchesSerialized.split("|||")
    }
    val batchIds = if (assignedBatchIdsSerialized.isBlank()) {
      emptyList()
    } else {
      assignedBatchIdsSerialized.split("|||")
    }

    return Teacher(
      id = id,
      name = name,
      photo = photo,
      subject = subject,
      contact = contact,
      qualification = qualification,
      experience = experience,
      assignedBatches = batches,
      classesTaken = classesTaken,
      attendancePercent = attendancePercent,
      feedbackRating = feedbackRating,
      salary = salary,
      incentives = incentives,
      deductions = deductions,
      status = status,
      email = email,
      gender = gender,
      dob = dob,
      joiningDate = joiningDate,
      activationCode = activationCode,
      isActivated = isActivated,
      assignedBatchIds = batchIds,
      instituteId = instituteId
    )
  }

  companion object {
    fun fromDomain(teacher: Teacher): TeacherEntity {
      val batchesSerialized = teacher.assignedBatches.joinToString("|||")
      val batchIdsSerialized = teacher.assignedBatchIds.joinToString("|||")
      return TeacherEntity(
        id = teacher.id,
        name = teacher.name,
        photo = teacher.photo,
        subject = teacher.subject,
        contact = teacher.contact,
        qualification = teacher.qualification,
        experience = teacher.experience,
        assignedBatchesSerialized = batchesSerialized,
        classesTaken = teacher.classesTaken,
        attendancePercent = teacher.attendancePercent,
        feedbackRating = teacher.feedbackRating,
        salary = teacher.salary,
        incentives = teacher.incentives,
        deductions = teacher.deductions,
        status = teacher.status,
        email = teacher.email,
        gender = teacher.gender,
        dob = teacher.dob,
        joiningDate = teacher.joiningDate,
        activationCode = teacher.activationCode,
        isActivated = teacher.isActivated,
        assignedBatchIdsSerialized = batchIdsSerialized,
        instituteId = teacher.instituteId
      )
    }
  }
}
