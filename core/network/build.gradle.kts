plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.lanweixi.core.network"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    api(libs.okhttp)
    api(libs.okhttp.sse)
    implementation(libs.kotlinx.coroutines.android)

    // Hilt：OkHttpClient 由 SingletonComponent 统一持有，见 NetworkModule
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation("org.json:json:20240303")
}

// T03 端到端验收：把 node mock_server 的地址传进 JVM 测试
// 支持 -Dmock.baseUrl=... 或环境变量 MOCK_BASE_URL（Windows 下后者更稳）
tasks.withType<Test>().configureEach {
    val fromProp = System.getProperty("mock.baseUrl").orEmpty()
    val fromEnv = System.getenv("MOCK_BASE_URL").orEmpty()
    systemProperty("mock.baseUrl", if (fromProp.isNotBlank()) fromProp else fromEnv)
    testLogging {
        showStandardStreams = true
        events("passed", "failed", "skipped")
    }
}
