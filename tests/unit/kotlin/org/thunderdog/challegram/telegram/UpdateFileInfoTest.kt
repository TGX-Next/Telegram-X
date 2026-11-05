package org.thunderdog.challegram.telegram

import com.google.common.truth.Truth.assertThat
import org.drinkless.tdlib.TdApi
import org.junit.Test
import org.thunderdog.challegram.BuildConfig

// @MadeByLLM("gpt-6")
class UpdateFileInfoTest {
  @Test
  fun missingChannelMessageReturnsNoUpdate() {
    assertThat(Tdlib.parseUpdateFile(null)).isNull()
  }

  @Test
  fun nonDocumentMessageReturnsNoUpdate() {
    val message = TdApi.Message().apply {
      content = TdApi.MessageText()
    }
    assertThat(Tdlib.parseUpdateFile(message)).isNull()
  }

  @Test
  fun newerApkReturnsVersionAndCommit() {
    val build = BuildConfig.ORIGINAL_VERSION_CODE + 1
    val message = apk("${BuildConfig.APK_FILE_PREFIX}1.2.3.$build-arm64.apk", "Commit: abc123")
    val update = requireNotNull(Tdlib.parseUpdateFile(message))
    assertThat(update.document)
      .isSameInstanceAs((message.content as TdApi.MessageDocument).document)
    assertThat(update.buildNo).isEqualTo(build)
    assertThat(update.version).isEqualTo("1.2.3.$build")
    assertThat(update.commit).isEqualTo("abc123")
  }

  @Test
  fun currentAndOlderBuildsReturnNoUpdate() {
    val currentBuild = BuildConfig.ORIGINAL_VERSION_CODE
    for (build in listOf(currentBuild, currentBuild - 1)) {
      val message = apk("${BuildConfig.APK_FILE_PREFIX}1.2.3.$build-arm64.apk")
      assertThat(Tdlib.parseUpdateFile(message)).isNull()
    }
  }

  @Test
  fun wrongPrefixAndMalformedVersionReturnNoUpdate() {
    val names = listOf(
      "other-1.2.3.999999-arm64.apk",
      "${BuildConfig.APK_FILE_PREFIX}bad-arm64.apk"
    )
    for (name in names) {
      assertThat(Tdlib.parseUpdateFile(apk(name))).isNull()
    }
  }

  @Test
  fun missingCommitStillAllowsNewerApk() {
    val build = BuildConfig.ORIGINAL_VERSION_CODE + 1
    val message = apk("${BuildConfig.APK_FILE_PREFIX}1.2.3.$build-arm64.apk")
    assertThat(requireNotNull(Tdlib.parseUpdateFile(message)).commit).isNull()
  }

  private fun apk(name: String, caption: String = ""): TdApi.Message {
    val document = TdApi.Document().apply {
      fileName = name
    }
    return TdApi.Message().apply {
      content = TdApi.MessageDocument(document, TdApi.FormattedText(caption, arrayOf()))
    }
  }
}
