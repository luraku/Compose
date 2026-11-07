package com.luraku.compose

import android.app.Application
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

class ComposerViewModel(app: Application) : AndroidViewModel(app) {

    /** Du fond (index 0) vers le dessus. */
    val layers = mutableStateListOf<Layer>()
    var selectedId by mutableStateOf<Long?>(null)
    var aspect by mutableStateOf(Aspect.SQUARE)
    var background by mutableStateOf(Background.WHITE)
    var busy by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)

    private var nextId = 1L

    val pageW: Float get() = if (aspect.ratio >= 1f) PAGE_LONG_SIDE else PAGE_LONG_SIDE * aspect.ratio
    val pageH: Float get() = if (aspect.ratio >= 1f) PAGE_LONG_SIDE / aspect.ratio else PAGE_LONG_SIDE

    val selected: Layer? get() = layers.firstOrNull { it.id == selectedId }

    fun addImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            busy = true
            var failed = 0
            for (uri in uris) {
                val bmp = withContext(Dispatchers.IO) { runCatching { loadBitmap(uri) }.getOrNull() }
                if (bmp == null) {
                    failed++
                    continue
                }
                val fit = min(pageW * 0.6f / bmp.width, pageH * 0.6f / bmp.height)
                val shift = (layers.size % 5) * 24f
                val layer = Layer(nextId++, bmp, pageW / 2f + shift, pageH / 2f + shift, scale = fit)
                layers.add(layer)
                selectedId = layer.id
            }
            busy = false
            if (failed > 0) message = "$failed image(s) n'ont pas pu être chargées"
        }
    }

    fun update(id: Long, change: (Layer) -> Layer) {
        val i = layers.indexOfFirst { it.id == id }
        if (i >= 0) layers[i] = change(layers[i])
    }

    fun hitTest(x: Float, y: Float): Long? =
        layers.lastOrNull { Renderer.contains(it, x, y) }?.id

    fun moveUp(id: Long) {
        val i = layers.indexOfFirst { it.id == id }
        if (i in 0 until layers.lastIndex) layers.add(i + 1, layers.removeAt(i))
    }

    fun moveDown(id: Long) {
        val i = layers.indexOfFirst { it.id == id }
        if (i > 0) layers.add(i - 1, layers.removeAt(i))
    }

    fun duplicate(id: Long) {
        val i = layers.indexOfFirst { it.id == id }
        if (i < 0) return
        val copy = layers[i].copy(id = nextId++, cx = layers[i].cx + 30f, cy = layers[i].cy + 30f)
        layers.add(i + 1, copy)
        selectedId = copy.id
    }

    fun remove(id: Long) {
        layers.removeAll { it.id == id }
        if (selectedId == id) selectedId = layers.lastOrNull()?.id
    }

    fun save() {
        if (layers.isEmpty() || busy) return
        val snapshot = layers.toList()
        val w = pageW
        val h = pageH
        val bg = background
        viewModelScope.launch {
            busy = true
            val ok = withContext(Dispatchers.Default) {
                runCatching {
                    val bmp = Renderer.render(snapshot, w, h, bg)
                    try {
                        writePng(bmp)
                    } finally {
                        bmp.recycle()
                    }
                }.isSuccess
            }
            busy = false
            message = if (ok) "Image enregistrée dans Images/Compose" else "Échec de l'enregistrement"
        }
    }

    private fun writePng(bmp: Bitmap) {
        val cr = getApplication<Application>().contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "compose_${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Compose")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("insert failed")
        try {
            cr.openOutputStream(uri)!!.use { check(bmp.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            cr.update(uri, values, null, null)
        } catch (e: Throwable) {
            cr.delete(uri, null, null)
            throw e
        }
    }

    private fun loadBitmap(uri: Uri): Bitmap? {
        val cr = getApplication<Application>().contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_IMAGE_SIDE) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val raw = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null

        val orientation = runCatching {
            cr.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(-90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(-90f)
            else -> return raw
        }
        val fixed = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
        if (fixed !== raw) raw.recycle()
        return fixed
    }

    companion object {
        const val PAGE_LONG_SIDE = 1000f
        const val MAX_IMAGE_SIDE = 2048
    }
}
