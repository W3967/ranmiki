plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // 需要把插件的 PluginFunction 转成工具声明
    implementation(project(":domain:plugin"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
}