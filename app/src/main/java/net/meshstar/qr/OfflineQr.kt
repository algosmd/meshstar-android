package net.meshstar.qr

import android.media.Image
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer

/** Only the Y plane is read locally; no models, telemetry, transport SDK or retained camera frame. */
object OfflineQr {
    fun decode(image: Image): String? {
        val width = image.width; val height = image.height
        if (width <= 0 || height <= 0 || width.toLong() * height > 16_000_000) return null
        val plane = image.planes[0]
        val buffer = plane.buffer.duplicate()
        val luminance = ByteArray(width * height)
        for (y in 0 until height) for (x in 0 until width) {
            val index = y * plane.rowStride + x * plane.pixelStride
            if (index >= buffer.limit()) return null
            luminance[y * width + x] = buffer.get(index)
        }
        var data = luminance; var w = width; var h = height
        val reader = MultiFormatReader()
        val hints = mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE), DecodeHintType.TRY_HARDER to true)
        repeat(4) {
            val source = PlanarYUVLuminanceSource(data,w,h,0,0,w,h,false)
            for (candidate in listOf(source, source.invert())) {
                val result = runCatching { reader.decode(BinaryBitmap(HybridBinarizer(candidate)), hints).text }.getOrNull()
                reader.reset()
                if (!result.isNullOrBlank() && result.length <= 4096) return result
            }
            val rotated = ByteArray(data.size)
            for(y in 0 until h) for(x in 0 until w) rotated[(w-1-x)*h+y]=data[y*w+x]
            data=rotated;val old=w;w=h;h=old
        }
        return null
    }
}
