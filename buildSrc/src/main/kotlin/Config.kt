/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
// region @MadeByLLM("gpt-6", id = "android13-arm64")

// File with static configuration, that is meant to be adjusted only once

import tgx.gradle.*
import java.io.File
import java.util.*

object Config {
  const val MIN_SDK_VERSION = 33
  val JAVA_VERSION = org.gradle.api.JavaVersion.VERSION_25
  val ANDROIDX_MEDIA_EXTENSIONS = arrayOf(
    "decoder_ffmpeg",
    "decoder_flac",
    "decoder_opus",
    "decoder_vp9"
  )

  val FFMPEG_LIBS = arrayOf(
    "swresample",
    "avformat",
    "swscale",
    "avcodec",
    "avfilter",
    "avutil"
  )
}

data class PullRequest (
  val id: Long,
  val commitShort: String,
  val commitLong: String,
  val commitDate: Long,
  val author: String
) {
  constructor(id: Long, properties: Properties) : this(
    id,
    properties.getOrThrow("pr.$id.commit_short"),
    properties.getOrThrow("pr.$id.commit_long"),
    properties.getLongOrThrow("pr.$id.date"),
    properties.getOrThrow("pr.$id.author")
  )
}

data class BuildVersions(
  val compileSdkVersion: Int,
  val targetSdkVersion: Int,
  val buildToolsVersion: String,
  val primaryNdkVersion: String,
  val rustVersion: String
) {
  constructor(version: Properties) : this(
    compileSdkVersion =
      version.getIntOrThrow("version.sdk_compile"),
    targetSdkVersion =
      version.getIntOrThrow("version.sdk_target"),
    buildToolsVersion =
      version.getOrThrow("version.build_tools"),
    primaryNdkVersion =
      version.getOrThrow("version.ndk_primary"),
    rustVersion =
      version.getOrThrow("version.rust")
  )
}

data class ApplicationConfig(
  val sdkDir: String,
  val rustDir: String,
  val msys2Dir: String,

  val applicationName: String,
  val applicationId: String,
  val extension: String,
  val sourceCodeUrl: String,

  val applicationVersion: Int,
  val majorVersion: Int,

  val isExperimentalBuild: Boolean,
  val forceOptimize: Boolean,
  val doNotObfuscate: Boolean,
  val build: BuildVersions,

  val nativeLibraryVersion: String,
  val leveldbVersion: String,
  val emojiVersion: Int,

  val telegramApiId: Int,
  val telegramApiHash: String,
  val appDownloadUrl: String?,
  val googlePlayUrl: String?,
  val galaxyStoreUrl: String?,
  val huaweiAppGalleryUrl: String?,
  val amazonAppStoreUrl: String?,

  val pullRequests: List<PullRequest>,

  val outputFileNamePrefix: String,
  val creationDateMillis: Long,

  val keystorePropertiesPath: String?
)

data class Keystore(
  val file: File,
  val password: String,
  val keyAlias: String,
  val keyPassword: String
) {
  constructor(configPath: String) : this(loadProperties(configPath))
  constructor(file: File) : this(loadProperties(file))
  constructor(config: org.jetbrains.kotlin.konan.properties.Properties) : this(
    file = File(config.getOrThrow("keystore.file")),
    password = config.getOrThrow("keystore.password"),
    keyAlias = config.getOrThrow("key.alias"),
    keyPassword = config.getOrThrow("key.password")
  )
}

enum class Abi(val versionCode: Int) {
  ARM64_V8A(2);

  fun toDisplayName(): String = "arm64-v8a"

  companion object {
    val FILTERS = listOf("arm64-v8a")

    fun toFilters(abi: String): List<String> {
      if (abi.isNotEmpty()) {
        abi.split(',').forEach { fromFilter(it.trim()) }
      }
      return FILTERS
    }

    fun fromFilters(abiFilters: List<String>): Abi {
      require(abiFilters.isEmpty() || abiFilters == FILTERS) {
        "Only arm64-v8a is supported: $abiFilters"
      }
      return ARM64_V8A
    }

    fun fromFilter(abiFilter: String): Abi = when (abiFilter) {
      "arm64", "arm64-v8a" -> ARM64_V8A
      else -> fatal("Only arm64-v8a is supported: $abiFilter")
    }
  }
}

data class SdkVariant(
  val minSdk: Int = Config.MIN_SDK_VERSION,
  val flavor: String,
  val displayName: String? = flavor
) {
  val jetpackMediaFlavor: String = "latest"

}

object Sdk {
  // Preserve the published version-code offset of the Android 13+ flavor.
  const val LATEST = 5

  val VARIANTS = mapOf(
    LATEST to SdkVariant(flavor = "latest", displayName = null)
  )
}

object App {
  // File extension to be detected as project's theme. Do not change unless you make changes to themes engine / add exclusive colors
  const val THEME_EXTENSION = "tgx-theme"
}

object Emoji {
  // Identifier of the built-in emoji set. Change it if built-in emoji pack is changed
  const val BUILTIN_ID = "apple"
}

object Telegram {
  // Cloud storage for emoji, icons, fonts, etc
  const val RESOURCES_CHANNEL = "Y2xvdWRfdGd4X2FuZHJvaWRfcmVzb3Vy"

  // Channel where to look up for new apks
  const val UPDATES_CHANNEL = "tgxnext"

  // Language pack on server. Do not change
  const val LANGUAGE_PACK = "android_x"
}
// endregion
