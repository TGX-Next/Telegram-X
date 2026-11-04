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
package tgx.gradle

import Abi
import Sdk
import SdkVariant
import com.android.build.api.dsl.BaseFlavor
import com.android.build.api.dsl.VariantDimension
import com.android.build.api.variant.*
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.support.uppercaseFirstChar
import org.gradle.util.internal.VersionNumber
import tgx.gradle.task.wrapInDoubleQuotes

fun BaseFlavor.buildConfigInt (name: String, value: Int) =
  this.buildConfigField("int", name, value.toString())
fun BaseFlavor.buildConfigLong (name: String, value: Long) =
  this.buildConfigField("long", name, value.toString())
fun BaseFlavor.buildConfigBool (name: String, value: Boolean) =
  this.buildConfigField("boolean", name, value.toString())
fun BaseFlavor.buildConfigString (name: String, value: String?) =
  this.buildConfigField("String", name, value?.wrapInDoubleQuotes() ?: "null")
fun VariantDimension.buildConfigInt (name: String, value: Int) =
  this.buildConfigField("int", name, value.toString())
fun VariantDimension.buildConfigLong (name: String, value: Long) =
  this.buildConfigField("long", name, value.toString())
fun VariantDimension.buildConfigString (name: String, value: String?) =
  this.buildConfigField("String", name, value?.wrapInDoubleQuotes() ?: "null")

fun ApplicationAndroidComponentsExtension.disableRudimentaryVariants(
  filter: (SdkVariant) -> Boolean = { _ -> true }
) =
  beforeVariants { variantBuilder ->
    val sdkFlavor = variantBuilder.productFlavors.first { it.first == "SDK" }.second
    val sdkVariant = Sdk.VARIANTS.values.first { it.flavor == sdkFlavor }
    variantBuilder.enable = filter(sdkVariant)
  }

fun TestAndroidComponentsExtension.disableRudimentaryVariants(
  filter: (SdkVariant) -> Boolean = { _ -> true }
) =
  beforeVariants { variantBuilder ->
    val sdkFlavor = variantBuilder.productFlavors.first { it.first == "SDK" }.second
    val sdkVariant = Sdk.VARIANTS.values.first { it.flavor == sdkFlavor }
    variantBuilder.enable = filter(sdkVariant)
  }

fun findHostAbi(): String =
  if (System.getProperty("os.arch") in listOf("aarch64", "arm64")) {
    "arm64"
  } else {
    "x64"
  }

fun findHostTag(): String {
  val os = System.getProperty("os.name")
  return when {
    os.startsWith("mac", ignoreCase = true) ->
      "darwin-x86_64"
    os.startsWith("Linux", ignoreCase = true) ->
      "linux-x86_64"
    os.startsWith("Windows", ignoreCase = true) ->
      "windows-x86_64"
    else ->
      error("Unknown system: $os")
  }
}

fun VariantOutput.findAbiFilters(ifEmpty: List<String>): List<String> =
  this.filters.filter { filter ->
    filter.filterType == FilterConfiguration.FilterType.ABI
  }.map {
    it.identifier
  }.ifEmpty {
    ifEmpty
  }

fun ApplicationVariant.findAbiFilters(ifEmpty: List<String>): List<String> =
  this.outputs.flatMap { output ->
    output.findAbiFilters(ifEmpty)
  }.distinct()

fun Project.findAbiFilters(): Provider<List<String>> =
  providers.gradleProperty("abi")
    .map(Abi::toFilters)
    .orElse(Abi.FILTERS)
    .zip(providers.gradleProperty("android.injected.build.abi").orElse("")) { filters, injected ->
      require(injected.isEmpty() || "arm64-v8a" in injected.split(',').map { it.trim() }) {
        "Only arm64-v8a devices are supported: $injected"
      }
      filters
    }

fun String.ndkVersionMajor(): Int =
  VersionNumber.parse(this).major

fun String.ndkVersionToMinSdk(): Int =
  this.ndkVersionMajor().let { major ->
    when {
      major >= 27 -> 21
      major >= 24 -> 19
      major >= 23 -> 16
      else -> error("Unsupported NDK version: $this")
    }
  }

fun Project.gradleFlag(name: String, defaultValue: Boolean = false): Provider<Boolean> =
  providers.gradleProperty(name).map {
    it.toBoolean()
  }.orElse(defaultValue)
// endregion
