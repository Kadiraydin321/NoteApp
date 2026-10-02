package com.example.noteapp.media

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

object OcrHelper {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun extractTextFromImage(context: Context, imagePath: String): String? {
        return try {
            val file = File(imagePath)
            if (!file.exists()) return null
            
            val inputImage = InputImage.fromFilePath(context, Uri.fromFile(file))
            suspendCancellableCoroutine { continuation ->
                recognizer.process(inputImage)
                    .addOnSuccessListener { result ->
                        val text = result.text.takeIf { it.isNotBlank() }
                        continuation.resume(text)
                    }
                    .addOnFailureListener {
                        continuation.resume(null)
                    }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
