package com.example.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.StudentFeePayment

@Entity(
  tableName = "fee_payments",
  foreignKeys = [
    ForeignKey(
      entity = StudentEntity::class,
      parentColumns = ["id"],
      childColumns = ["studentId"],
      onDelete = ForeignKey.RESTRICT,
      onUpdate = ForeignKey.CASCADE,
      deferred = true
    )
  ],
  indices = [
    Index(value = ["studentId"]),
    Index(value = ["invoiceId"]),
    Index(value = ["instituteId"])
  ]
)
data class FeePaymentEntity(
  @PrimaryKey val id: String,
  val studentId: String,
  val studentName: String,
  val amount: Double,
  val paymentDate: String,
  val paymentStatus: String, // "Paid" | "Pending" | "Partial" | "Overdue" | "Refunded"
  val feeType: String,
  val paymentMode: String,
  val receiptNo: String,
  val month: String,
  val remarks: String,
  val recordedBy: String,
  val timestamp: Long = System.currentTimeMillis(),
  val invoiceId: String = "",
  val instituteId: String = "FSI_MAIN",
  val transactionId: String = ""
) {
  fun toDomain(): StudentFeePayment {
    return StudentFeePayment(
      id = id,
      studentId = studentId,
      studentName = studentName,
      amount = amount,
      paymentDate = paymentDate,
      paymentStatus = paymentStatus,
      feeType = feeType,
      paymentMode = paymentMode,
      receiptNo = receiptNo,
      month = month,
      remarks = remarks,
      recordedBy = recordedBy,
      timestamp = timestamp,
      invoiceId = invoiceId,
      instituteId = instituteId,
      transactionId = transactionId.ifBlank { receiptNo }
    )
  }

  companion object {
    fun fromDomain(payment: StudentFeePayment): FeePaymentEntity {
      val finalReceipt = if (payment.receiptNo.isBlank()) {
        "REC-${payment.id.takeLast(6).uppercase()}"
      } else {
        payment.receiptNo
      }
      return FeePaymentEntity(
        id = payment.id,
        studentId = payment.studentId,
        studentName = payment.studentName,
        amount = payment.amount,
        paymentDate = payment.paymentDate,
        paymentStatus = payment.paymentStatus,
        feeType = payment.feeType,
        paymentMode = payment.paymentMode,
        receiptNo = finalReceipt,
        month = payment.month,
        remarks = payment.remarks,
        recordedBy = payment.recordedBy,
        timestamp = payment.timestamp,
        invoiceId = payment.invoiceId,
        instituteId = payment.instituteId,
        transactionId = payment.transactionId.ifBlank { finalReceipt }
      )
    }
  }
}
