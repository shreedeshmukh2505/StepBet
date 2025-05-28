package com.example.stepbet.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.example.stepbet.R
import java.io.ByteArrayOutputStream
import java.io.InputStream

object ImageUtils {

    /**
     * Convert a Bitmap to Base64 String
     */
    fun bitmapToBase64(bitmap: Bitmap, quality: Int = 70): String {
        val outputStream = ByteArrayOutputStream()

        // Compress and resize the bitmap to avoid large data storage
        val scaledBitmap = if (bitmap.width > 500 || bitmap.height > 500) {
            val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
            val width = if (ratio > 1) 500 else (500 * ratio).toInt()
            val height = if (ratio > 1) (500 / ratio).toInt() else 500
            Bitmap.createScaledBitmap(bitmap, width, height, true)
        } else {
            bitmap
        }

        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)

        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.DEFAULT)
    }

    /**
     * Convert a Base64 String to Bitmap
     */
    fun base64ToBitmap(base64String: String): Bitmap? {
        return try {
            val imageBytes = Base64.decode(base64String, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Load an image from Uri to a Bitmap
     */
    fun uriToBitmap(context: Context, uri: Uri): Bitmap? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Load image from base64 string into ImageView using Glide
     */
    fun loadBase64Image(context: Context, base64String: String, imageView: ImageView) {
        if (base64String.isBlank()) {
            // Load default image
            Glide.with(context)
                .load(R.drawable.ic_profile)
                .circleCrop()
                .into(imageView)
        } else {
            try {
                val imageBytes = Base64.decode(base64String, Base64.DEFAULT)
                Glide.with(context)
                    .load(imageBytes)
                    .circleCrop()
                    .into(imageView)
            } catch (e: Exception) {
                // Load default image on error
                Glide.with(context)
                    .load(R.drawable.ic_profile)
                    .circleCrop()
                    .into(imageView)
            }
        }
    }
}