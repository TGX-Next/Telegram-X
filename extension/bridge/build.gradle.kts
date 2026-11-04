// region @MadeByLLM("gpt-6", id = "android13-arm64")
plugins {
  id("java-toolchain-convention")
  id(libs.plugins.android.library.get().pluginId)
  id("tgx-module")
}

dependencies {
  implementation(libs.androidx.core.ktx.latest)
  api(libs.kotlinx.coroutines.core)
  implementation(project(":tdlib"))
}

android {
  namespace = "tgx.bridge"
}
// endregion
