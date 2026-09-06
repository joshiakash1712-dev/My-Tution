package com.example.utils

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.StudentFeePayment
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object InvoiceGenerator {

    /**
     * Renders a high-resolution, crisp invoice/receipt bitmap for a specific payment record.
     */
    fun createInvoiceBitmap(payment: StudentFeePayment): Bitmap {
        val width = 1080
        val height = 1520
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background
        val bgPaint = Paint().apply {
            color = android.graphics.Color.parseColor("#F8FAFC")
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Header Background Banner
        val headerPaint = Paint().apply {
            color = android.graphics.Color.parseColor("#0061A4")
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRect(0f, 0f, width.toFloat(), 240f, headerPaint)

        // Institute Brand Details
        val textPaint = Paint().apply {
            isAntiAlias = true
        }

        // Institute Title
        textPaint.color = android.graphics.Color.WHITE
        textPaint.textSize = 46f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("MY TUITION (FSI)", 60f, 90f, textPaint)

        // Tagline / Subtitle
        textPaint.color = android.graphics.Color.parseColor("#E0F2FE")
        textPaint.textSize = 24f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Advanced Coaching & Excellence Platform", 60f, 135f, textPaint)

        // Header Right Badge: INVOICE / RECEIPT
        val badgePaint = Paint().apply {
            color = android.graphics.Color.parseColor("#0284C7")
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val badgeRect = RectF(width - 340f, 50f, width - 60f, 110f)
        canvas.drawRoundRect(badgeRect, 16f, 16f, badgePaint)

        textPaint.color = android.graphics.Color.WHITE
        textPaint.textSize = 24f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("FEE INVOICE", width - 200f, 90f, textPaint)
        textPaint.textAlign = Paint.Align.LEFT

        // Date in Header
        textPaint.color = android.graphics.Color.parseColor("#BAE6FD")
        textPaint.textSize = 20f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Generated: ${SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())}", 60f, 195f, textPaint)

        // Card Container
        val cardPaint = Paint().apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val cardBorderPaint = Paint().apply {
            color = android.graphics.Color.parseColor("#E2E8F0")
            style = Paint.Style.STROKE
            strokeWidth = 3f
            isAntiAlias = true
        }
        val cardRect = RectF(50f, 270f, width - 50f, height - 160f)
        canvas.drawRoundRect(cardRect, 24f, 24f, cardPaint)
        canvas.drawRoundRect(cardRect, 24f, 24f, cardBorderPaint)

        var currentY = 340f

        // Invoice Meta Row
        fun drawMetaField(label: String, value: String, x: Float, valColor: Int = android.graphics.Color.parseColor("#0F172A"), isBold: Boolean = false) {
            textPaint.color = android.graphics.Color.parseColor("#64748B")
            textPaint.textSize = 22f
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText(label, x, currentY, textPaint)

            textPaint.color = valColor
            textPaint.textSize = 26f
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, if (isBold) Typeface.BOLD else Typeface.NORMAL)
            canvas.drawText(value, x, currentY + 36f, textPaint)
        }

        drawMetaField("INVOICE / RECEIPT NO", payment.receiptNo, 90f, android.graphics.Color.parseColor("#0061A4"), true)
        drawMetaField("PAYMENT DATE", payment.paymentDate, width - 420f, android.graphics.Color.parseColor("#0F172A"), true)

        currentY += 100f

        // Divider Line
        val dividerPaint = Paint().apply {
            color = android.graphics.Color.parseColor("#F1F5F9")
            strokeWidth = 3f
        }
        canvas.drawLine(90f, currentY, width - 90f, currentY, dividerPaint)
        currentY += 50f

        // Student Info Section
        drawMetaField("BILLED TO STUDENT", payment.studentName, 90f, android.graphics.Color.parseColor("#0F172A"), true)
        drawMetaField("STUDENT ID", payment.studentId, width - 420f, android.graphics.Color.parseColor("#475569"), false)

        currentY += 95f
        drawMetaField("BILLING MONTH / PERIOD", payment.month.ifBlank { "Academic Year 2026-27" }, 90f)
        drawMetaField("FEE CATEGORY", payment.feeType.ifBlank { "Tuition Fee" }, width - 420f, android.graphics.Color.parseColor("#0284C7"), true)

        currentY += 110f
        canvas.drawLine(90f, currentY, width - 90f, currentY, dividerPaint)
        currentY += 40f

        // Payment Summary Table Header
        val thBgPaint = Paint().apply {
            color = android.graphics.Color.parseColor("#F8FAFC")
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val thRect = RectF(90f, currentY, width - 90f, currentY + 60f)
        canvas.drawRoundRect(thRect, 12f, 12f, thBgPaint)

        textPaint.color = android.graphics.Color.parseColor("#475569")
        textPaint.textSize = 22f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("DESCRIPTION", 120f, currentY + 38f, textPaint)

        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("AMOUNT (INR)", width - 120f, currentY + 38f, textPaint)
        textPaint.textAlign = Paint.Align.LEFT

        currentY += 100f

        // Table Item Row
        textPaint.color = android.graphics.Color.parseColor("#1E293B")
        textPaint.textSize = 26f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(payment.feeType.ifBlank { "Coaching & Tuition Fee" }, 120f, currentY, textPaint)

        textPaint.color = android.graphics.Color.parseColor("#64748B")
        textPaint.textSize = 20f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Recorded via ${payment.paymentMode} • Ref #${payment.id}", 120f, currentY + 32f, textPaint)

        textPaint.color = android.graphics.Color.parseColor("#0F172A")
        textPaint.textSize = 28f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("₹${String.format(Locale.getDefault(), "%,d", payment.amount.toInt())}", width - 120f, currentY + 10f, textPaint)
        textPaint.textAlign = Paint.Align.LEFT

        currentY += 100f
        canvas.drawLine(90f, currentY, width - 90f, currentY, dividerPaint)
        currentY += 50f

        // Payment Mode & Status Highlights
        val statusBgColor = when (payment.paymentStatus.lowercase(Locale.ROOT)) {
            "paid" -> android.graphics.Color.parseColor("#DCFCE7")
            "partial" -> android.graphics.Color.parseColor("#FEF3C7")
            "pending" -> android.graphics.Color.parseColor("#DBEAFE")
            else -> android.graphics.Color.parseColor("#FEE2E2")
        }
        val statusTextColor = when (payment.paymentStatus.lowercase(Locale.ROOT)) {
            "paid" -> android.graphics.Color.parseColor("#15803D")
            "partial" -> android.graphics.Color.parseColor("#B45309")
            "pending" -> android.graphics.Color.parseColor("#1D4ED8")
            else -> android.graphics.Color.parseColor("#B91C1C")
        }

        drawMetaField("PAYMENT MODE", payment.paymentMode, 120f, android.graphics.Color.parseColor("#0284C7"), true)

        // Status Badge
        textPaint.color = android.graphics.Color.parseColor("#64748B")
        textPaint.textSize = 22f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("STATUS", width - 420f, currentY, textPaint)

        val stRect = RectF(width - 420f, currentY + 12f, width - 200f, currentY + 56f)
        val stPaint = Paint().apply {
            color = statusBgColor
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRoundRect(stRect, 10f, 10f, stPaint)

        textPaint.color = statusTextColor
        textPaint.textSize = 22f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(payment.paymentStatus.uppercase(Locale.ROOT), width - 310f, currentY + 42f, textPaint)
        textPaint.textAlign = Paint.Align.LEFT

        currentY += 100f

        // Total Amount Highlight Box
        val totalBgPaint = Paint().apply {
            color = android.graphics.Color.parseColor("#F0FDF4")
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val totalBorderPaint = Paint().apply {
            color = android.graphics.Color.parseColor("#BBF7D0")
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            isAntiAlias = true
        }
        val totalRect = RectF(90f, currentY, width - 90f, currentY + 100f)
        canvas.drawRoundRect(totalRect, 16f, 16f, totalBgPaint)
        canvas.drawRoundRect(totalRect, 16f, 16f, totalBorderPaint)

        textPaint.color = android.graphics.Color.parseColor("#166534")
        textPaint.textSize = 26f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("TOTAL AMOUNT PAID", 130f, currentY + 58f, textPaint)

        textPaint.color = android.graphics.Color.parseColor("#15803D")
        textPaint.textSize = 38f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("₹${String.format(Locale.getDefault(), "%,d", payment.amount.toInt())}", width - 130f, currentY + 62f, textPaint)
        textPaint.textAlign = Paint.Align.LEFT

        currentY += 140f

        // Remarks / Notes
        if (payment.remarks.isNotBlank()) {
            textPaint.color = android.graphics.Color.parseColor("#64748B")
            textPaint.textSize = 20f
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("Remarks: ${payment.remarks}", 90f, currentY, textPaint)
            currentY += 30f
        }

        textPaint.color = android.graphics.Color.parseColor("#94A3B8")
        textPaint.textSize = 18f
        canvas.drawText("Authorized Signature / Recorded By: ${payment.recordedBy.ifBlank { "Admin" }}", 90f, currentY + 20f, textPaint)

        // Footer Outside Card
        textPaint.color = android.graphics.Color.parseColor("#64748B")
        textPaint.textSize = 20f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("This is an electronically generated official receipt issued by My Tuition (FSI).", width / 2f, height - 90f, textPaint)
        textPaint.textSize = 18f
        textPaint.color = android.graphics.Color.parseColor("#94A3B8")
        canvas.drawText("For support & inquiries, contact the Tuition Administrator.", width / 2f, height - 55f, textPaint)
        textPaint.textAlign = Paint.Align.LEFT

        return bitmap
    }

    /**
     * Saves the rendered invoice bitmap into the application cache directory and returns the File.
     */
    fun saveInvoiceToCache(context: Context, payment: StudentFeePayment): File {
        val bitmap = createInvoiceBitmap(payment)
        val fileName = "invoice_${payment.receiptNo.replace("[^a-zA-Z0-9_-]".toRegex(), "_")}.png"
        val file = File(context.cacheDir, fileName)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
        }
        return file
    }

    /**
     * Saves the invoice screenshot/image to the device's Pictures/Gallery MediaStore.
     */
    fun saveInvoiceToGallery(context: Context, payment: StudentFeePayment): Uri? {
        val bitmap = createInvoiceBitmap(payment)
        val fileName = "Invoice_${payment.receiptNo}_${System.currentTimeMillis()}.png"
        val resolver = context.contentResolver

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/MyTuitionInvoices")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (imageUri != null) {
            try {
                resolver.openOutputStream(imageUri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    out.flush()
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(imageUri, contentValues, null, null)
                }
                return imageUri
            } catch (e: Exception) {
                e.printStackTrace()
                return null
            }
        }
        return null
    }

    /**
     * Opens Android System Share Sheet with the Invoice Screenshot attached as PNG.
     */
    fun shareInvoice(context: Context, payment: StudentFeePayment) {
        try {
            val file = saveInvoiceToCache(context, payment)
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, file)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Fee Payment Invoice - ${payment.studentName} (${payment.receiptNo})")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Official Tuition Fee Invoice for ${payment.studentName}\n" +
                            "Receipt No: ${payment.receiptNo}\n" +
                            "Amount: ₹${payment.amount.toInt()}\n" +
                            "Status: ${payment.paymentStatus}\n" +
                            "Date: ${payment.paymentDate}"
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Share Payment Invoice")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Could not share invoice: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}
