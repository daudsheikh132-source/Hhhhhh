package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object ProfileAvatarStore {
    private const val AVATAR_DIR = "profile_avatar"
    private const val AVATAR_FILE = "custom_avatar.png"
    private const val AVATAR_SIZE_PX = 512

    private val _avatarBitmap = MutableStateFlow<Bitmap?>(null)
    val avatarBitmap: StateFlow<Bitmap?> = _avatarBitmap.asStateFlow()

    @Volatile
    private var loadedFromDisk = false

    fun loadSyncIfNeeded(context: Context): Bitmap? {
        if (!loadedFromDisk) {
            val file = avatarFile(context.applicationContext)
            if (file.exists()) {
                _avatarBitmap.value = runCatching {
                    BitmapFactory.decodeFile(file.absolutePath)
                }.getOrNull()
            }
            loadedFromDisk = true
        }
        return _avatarBitmap.value
    }

    suspend fun ensureLoaded(context: Context) {
        if (loadedFromDisk) return
        withContext(Dispatchers.IO) {
            loadSyncIfNeeded(context)
        }
    }

    suspend fun saveAvatar(context: Context, sourceBitmap: Bitmap): Result<Bitmap> =
        withContext(Dispatchers.IO) {
            try {
                val square = centerCropAndScale(sourceBitmap, AVATAR_SIZE_PX)
                val file = avatarFile(context.applicationContext)
                file.parentFile?.mkdirs()
                val tempFile = File(file.parentFile, "$AVATAR_FILE.tmp")
                FileOutputStream(tempFile).use { out ->
                    square.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                if (file.exists()) file.delete()
                if (!tempFile.renameTo(file)) {
                    tempFile.copyTo(file, overwrite = true)
                    tempFile.delete()
                }
                loadedFromDisk = true
                _avatarBitmap.value = square
                Result.success(square)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun clearAvatar(context: Context): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val file = avatarFile(context.applicationContext)
                if (file.exists()) {
                    file.delete()
                }
                loadedFromDisk = true
                _avatarBitmap.value = null
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    fun buildAvatarPrompt(description: String, style: String): String {
        val cleanDesc = description.trim().ifBlank {
            "friendly modern AI companion user portrait with subtle purple and cyan rim lighting"
        }
        val cleanStyle = style.trim().ifBlank { "3D Pixar Portrait" }
        return "Centered square profile avatar portrait of $cleanDesc, $cleanStyle style, " +
            "clean dark background, crisp facial framing suitable for a circular profile picture, high detail"
    }

    private fun centerCropAndScale(bitmap: Bitmap, targetSize: Int): Bitmap {
        val size = minOf(bitmap.width, bitmap.height)
        val x = ((bitmap.width - size) / 2).coerceAtLeast(0)
        val y = ((bitmap.height - size) / 2).coerceAtLeast(0)
        val cropped = if (bitmap.width == bitmap.height) {
            bitmap
        } else {
            Bitmap.createBitmap(bitmap, x, y, size, size)
        }
        val scaled = if (cropped.width == targetSize) {
            cropped
        } else {
            Bitmap.createScaledBitmap(cropped, targetSize, targetSize, true)
        }
        if (cropped !== bitmap && cropped !== scaled) {
            cropped.recycle()
        }
        return scaled
    }

    private fun avatarFile(appContext: Context): File =
        File(File(appContext.filesDir, AVATAR_DIR), AVATAR_FILE)
}
