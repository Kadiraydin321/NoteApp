package com.example.noteapp.media

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object FileStorageHelper {

    fun saveImageFromUri(context: Context, uri: Uri): String? {
        return try {
            val fileName = "IMG_${UUID.randomUUID()}.jpg"
            val file = File(context.filesDir, fileName)
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(file).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun createAudioRecordFile(context: Context): File {
        val fileName = "AUDIO_${UUID.randomUUID()}.mp4"
        return File(context.filesDir, fileName)
    }

    fun saveDrawingBitmap(context: Context, bitmap: android.graphics.Bitmap): String? {
        return try {
            val fileName = "DRAW_${UUID.randomUUID()}.png"
            val file = File(context.filesDir, fileName)
            FileOutputStream(file).use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
            }
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun saveEditedImageBitmap(context: Context, bitmap: android.graphics.Bitmap, originalPath: String? = null): String? {
        return try {
            val extension = if (originalPath?.endsWith(".png", ignoreCase = true) == true) "png" else "jpg"
            val fileName = "IMG_EDIT_${System.currentTimeMillis()}.$extension"
            val file = File(context.filesDir, fileName)
            FileOutputStream(file).use { out ->
                if (extension == "png") {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                } else {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, out)
                }
            }
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun deleteFile(path: String): Boolean {
        return try {
            val file = File(path)
            if (file.exists()) file.delete() else false
        } catch (_: Exception) {
            false
        }
    }
}
