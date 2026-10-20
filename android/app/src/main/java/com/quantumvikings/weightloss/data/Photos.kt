package com.quantumvikings.weightloss.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.LruCache
import java.io.ByteArrayOutputStream

/** Progress photos: shrunk on the phone before upload, decoded again for display. */
object Photos {
    /** The server (behind nginx) takes at most 1 MB. */
    private const val MAX_BYTES = 900_000
    private const val MAX_EDGE = 1600

    /** Thumbnails and full images already fetched in this process, by "<id>/<size>". */
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /** The picked image as an upright JPEG of at most [MAX_EDGE] pixels and [MAX_BYTES]. */
    fun jpeg(context: Context, uri: Uri): ByteArray {
        val resolver = context.contentResolver
        val bitmap = if (Build.VERSION.SDK_INT >= 28) {
            // ImageDecoder also applies the EXIF rotation of camera photos.
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                val w = info.size.width
                val h = info.size.height
                val scale = minOf(1.0, MAX_EDGE.toDouble() / maxOf(w, h))
                decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            // Android 8: a sampled decode; the EXIF rotation is not applied there.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val options = BitmapFactory.Options().apply { inSampleSize = sampleFor(bounds.outWidth, bounds.outHeight, MAX_EDGE) }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                ?: error("Deze foto kan niet gelezen worden")
        }
        var quality = 85
        while (true) {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            if (out.size() <= MAX_BYTES || quality <= 40) return out.toByteArray()
            quality -= 15
        }
    }

    /** Photo [id] at about [maxEdge] pixels, from the cache or the server. */
    suspend fun load(session: Session, id: Int, maxEdge: Int): Bitmap? {
        val key = "$id/$maxEdge"
        cache.get(key)?.let { return it }
        val bytes = session.api.photo(id).use { it.bytes() }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val options = BitmapFactory.Options().apply { inSampleSize = sampleFor(bounds.outWidth, bounds.outHeight, maxEdge) }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.also { cache.put(key, it) }
    }

    private fun sampleFor(width: Int, height: Int, maxEdge: Int): Int {
        var sample = 1
        while (maxOf(width, height) / (sample * 2) >= maxEdge) sample *= 2
        return sample
    }
}
