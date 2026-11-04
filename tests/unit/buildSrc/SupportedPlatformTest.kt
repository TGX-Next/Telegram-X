// region @MadeByLLM("gpt-6", id = "android13-arm64")
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class SupportedPlatformTest {
  @Test fun usesOnlyAndroid13AndNewer() {
    assertThat(Config.MIN_SDK_VERSION).isEqualTo(33)
    assertThat(Sdk.VARIANTS.values.map { it.flavor }).containsExactly("latest")
    assertThat(Sdk.VARIANTS.values.single().minSdk).isEqualTo(33)
  }

  @Test fun defaultsToArm64AndAcceptsBothArm64Names() {
    assertThat(Abi.FILTERS).containsExactly("arm64-v8a")
    for (name in listOf("", "arm64", "arm64-v8a")) {
      assertThat(Abi.toFilters(name)).containsExactly("arm64-v8a")
    }
    assertThat(Abi.ARM64_V8A.versionCode).isEqualTo(2)
    assertThat(Sdk.LATEST).isEqualTo(5)
  }

  @Test fun rejectsUnsupportedAndMixedAbis() {
    val unsupported = listOf(
      "arm32", "armeabi-v7a", "x86", "x64", "x86_64", "all",
      "universal", "arm", "intel", "arm64,x64"
    )
    for (name in unsupported) {
      assertThrows(RuntimeException::class.java) { Abi.toFilters(name) }
    }
    assertThrows(IllegalArgumentException::class.java) {
      Abi.fromFilters(listOf("arm64-v8a", "x86_64"))
    }
  }
}
// endregion
