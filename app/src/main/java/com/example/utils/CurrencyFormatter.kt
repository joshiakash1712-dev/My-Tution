package com.example.utils

import java.util.Locale

object CurrencyFormatter {

    /**
     * Formats an amount with the Indian Rupee symbol and Indian numbering comma grouping.
     * e.g. 700000 -> "₹7,00,000", 96000 -> "₹96,000", 6400 -> "₹6,400"
     */
    fun formatIndianRupees(amount: Number): String {
        val longVal = amount.toLong()
        return "₹${formatIndianNumber(longVal)}"
    }

    /**
     * Formats an amount using Indian Lakhs / Crores shorthand when large.
     * e.g. 700000 -> "₹7.00 L", 548000 -> "₹5.48 L", 12000000 -> "₹1.20 Cr", 96000 -> "₹96,000"
     */
    fun formatLakhs(amount: Number): String {
        val dbl = amount.toDouble()
        val absDbl = Math.abs(dbl)
        return when {
            absDbl >= 10000000.0 -> {
                val cr = dbl / 10000000.0
                String.format(Locale.US, "₹%.2f Cr", cr)
            }
            absDbl >= 100000.0 -> {
                val lakhs = dbl / 100000.0
                String.format(Locale.US, "₹%.2f L", lakhs)
            }
            else -> formatIndianRupees(amount)
        }
    }

    /**
     * Formats compact short values for chart bars and tight spaces.
     * e.g. 96000 -> "₹96k", 548000 -> "₹5.48L", 700000 -> "₹7.0L", 6400 -> "₹6.4k"
     */
    fun formatCompact(amount: Number): String {
        val dbl = amount.toDouble()
        val absDbl = Math.abs(dbl)
        return when {
            absDbl >= 10000000.0 -> String.format(Locale.US, "₹%.1f Cr", dbl / 10000000.0)
            absDbl >= 100000.0 -> {
                val l = dbl / 100000.0
                if (l % 1.0 == 0.0) String.format(Locale.US, "₹%.0f L", l)
                else String.format(Locale.US, "₹%.2f L", l)
            }
            absDbl >= 1000.0 -> {
                val k = dbl / 1000.0
                if (k % 1.0 == 0.0) String.format(Locale.US, "₹%.0fk", k)
                else String.format(Locale.US, "₹%.1fk", k)
            }
            else -> "₹${amount.toLong()}"
        }
    }

    /**
     * Formats pure number using Indian comma grouping without currency symbol.
     * e.g. 700000 -> "7,00,000", 1234567 -> "12,34,567"
     */
    fun formatIndianNumber(num: Long): String {
        val isNeg = num < 0
        val absNum = Math.abs(num).toString()
        if (absNum.length <= 3) {
            return if (isNeg) "-$absNum" else absNum
        }
        val lastThree = absNum.substring(absNum.length - 3)
        val remaining = absNum.substring(0, absNum.length - 3)
        val builder = StringBuilder()
        var count = 0
        for (i in remaining.length - 1 downTo 0) {
            builder.append(remaining[i])
            count++
            if (count == 2 && i != 0) {
                builder.append(',')
                count = 0
            }
        }
        val formatted = builder.reverse().toString() + "," + lastThree
        return if (isNeg) "-$formatted" else formatted
    }
}
