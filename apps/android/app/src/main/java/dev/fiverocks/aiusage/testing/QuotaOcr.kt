package dev.fiverocks.aiusage.testing

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/** Processes a user-selected screenshot entirely in the Android app. */
object QuotaOcr {
    fun read(context: Context, uri: Uri, onResult: (String) -> Unit) {
        val image = try { InputImage.fromFilePath(context, uri) }
        catch (_: Exception) {
            onResult("이미지를 열 수 없습니다")
            return
        }
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(image)
            .addOnSuccessListener { onResult(QuotaTextExtractor.summary(it.text)) }
            .addOnFailureListener { onResult("OCR 실패: 이미지 형식을 확인하세요") }
            .addOnCompleteListener { recognizer.close() }
    }
}
