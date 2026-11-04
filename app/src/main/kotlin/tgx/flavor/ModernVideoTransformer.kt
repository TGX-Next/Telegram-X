// region @MadeByLLM("gpt-6", id = "android13-arm64")
@file:JvmName("VideoTransformer")

package tgx.flavor

import android.content.Context
import android.media.MediaFormat
import androidx.core.net.toUri

private fun MediaFormat.getInt(key: String, defaultValue: Int = 0): Int {
  if (!containsKey(key)) {
    return defaultValue
  }
  return getNumber(key)?.toInt() ?: defaultValue
}

fun getVideoFrameRate(context: Context, sourcePath: String): Int {
  val extractor = MediaExtractor(context)
  try {
    if (sourcePath.startsWith("content://")) {
      extractor.setDataSource(sourcePath.toUri(), 0)
    } else {
      extractor.setDataSource(sourcePath)
    }
    for (i in 0 until extractor.trackCount) {
      val format = extractor.getTrackFormat(i)
      val frameRate = format.getInt(MediaFormat.KEY_FRAME_RATE)
      if (frameRate > 0) {
        return frameRate
      }
    }
  } finally {
    extractor.release()
  }
  return -1
}
// endregion
