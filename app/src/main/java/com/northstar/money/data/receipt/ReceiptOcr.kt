package com.northstar.money.data.receipt

import android.graphics.BitmapFactory
import androidx.core.graphics.scale
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.northstar.money.domain.model.Money
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlin.math.roundToInt
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

data class ParsedReceipt(
    val rawText: String,
    val amount: Money?,
    val localDate: String?,
    val merchant: String?,
)

object ReceiptImageNormalizer {
    const val MAX_STORED_BYTES = 1_000_000
    private const val MAX_DIMENSION = 2_048

    fun normalize(content: ByteArray): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(content, 0, content.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "The selected receipt is not a supported image" }
        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > MAX_DIMENSION) sampleSize *= 2
        var bitmap = requireNotNull(
            BitmapFactory.decodeByteArray(
                content,
                0,
                content.size,
                BitmapFactory.Options().apply { inSampleSize = sampleSize },
            ),
        ) { "The selected receipt is not a supported image" }
        try {
            val largestSide = maxOf(bitmap.width, bitmap.height)
            if (largestSide > MAX_DIMENSION) {
                val scale = MAX_DIMENSION.toFloat() / largestSide
                val scaled = bitmap.scale(
                    (bitmap.width * scale).roundToInt().coerceAtLeast(1),
                    (bitmap.height * scale).roundToInt().coerceAtLeast(1),
                )
                bitmap.recycle()
                bitmap = scaled
            }
            while (true) {
                for (quality in 90 downTo 50 step 10) {
                    val encoded = ByteArrayOutputStream().use { output ->
                        check(bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, output))
                        output.toByteArray()
                    }
                    if (encoded.size <= MAX_STORED_BYTES) return encoded
                }
                val scaled = bitmap.scale(
                    (bitmap.width * 0.75f).roundToInt().coerceAtLeast(1),
                    (bitmap.height * 0.75f).roundToInt().coerceAtLeast(1),
                )
                bitmap.recycle()
                bitmap = scaled
            }
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }
}

class ReceiptOcrEngine {
    suspend fun recognize(content: ByteArray, currencyCode: String): ParsedReceipt {
        val bitmap = requireNotNull(BitmapFactory.decodeByteArray(content, 0, content.size)) {
            "The selected receipt is not a supported image"
        }
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            val text = suspendCancellableCoroutine { continuation ->
                recognizer.process(InputImage.fromBitmap(bitmap, 0))
                    .addOnSuccessListener { result -> continuation.resume(result.text) }
                    .addOnFailureListener { error -> continuation.resumeWithException(error) }
            }
            ReceiptOcrParser.parse(text, currencyCode)
        } finally {
            recognizer.close()
            bitmap.recycle()
        }
    }
}

object ReceiptOcrParser {
    private val amountRegex = Regex("(?<!\\d)(?:[€$£]\\s*)?(\\d{1,3}(?:[ .]\\d{3})*[,.]\\d{2}|\\d+[,.]\\d{2})(?!\\d)")
    private val isoDateRegex = Regex("\\b(20\\d{2})[-/.](\\d{1,2})[-/.](\\d{1,2})\\b")
    private val localDateRegex = Regex("\\b(\\d{1,2})[-/.](\\d{1,2})[-/.](20\\d{2})\\b")
    private val totalLabel = Regex("(?i)\\b(total|amount due|valor|a pagar|montante)\\b")

    fun parse(rawText: String, currencyCode: String): ParsedReceipt {
        val lines = rawText.lines().map(String::trim).filter(String::isNotBlank)
        val prioritized = lines.sortedByDescending { line ->
            if (totalLabel.containsMatchIn(line)) 1 else 0
        }
        val amount = prioritized.asSequence()
            .mapNotNull { line -> amountRegex.findAll(line).lastOrNull()?.groupValues?.get(1) }
            .mapNotNull { value ->
                val normalized = value.replace(" ", "").let { compact ->
                    when {
                        compact.contains(',') -> compact.replace(".", "").replace(',', '.')
                        else -> compact
                    }
                }
                runCatching { Money.parseMajor(normalized, currencyCode) }.getOrNull()
            }
            .firstOrNull()
        val date = lines.asSequence().mapNotNull(::parseDate).firstOrNull()
        val merchant = lines.firstOrNull { line ->
            line.length in 2..80 && line.any(Char::isLetter) &&
                !totalLabel.containsMatchIn(line) &&
                !line.contains("receipt", ignoreCase = true) && !line.contains("fatura", ignoreCase = true)
        }
        return ParsedReceipt(rawText, amount, date, merchant)
    }

    private fun parseDate(line: String): String? {
        isoDateRegex.find(line)?.let { match ->
            return validDate(match.groupValues[1], match.groupValues[2], match.groupValues[3])
        }
        localDateRegex.find(line)?.let { match ->
            return validDate(match.groupValues[3], match.groupValues[2], match.groupValues[1])
        }
        return null
    }

    private fun validDate(year: String, month: String, day: String): String? = try {
        LocalDate.of(year.toInt(), month.toInt(), day.toInt()).format(DateTimeFormatter.ISO_LOCAL_DATE)
    } catch (_: DateTimeParseException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}
