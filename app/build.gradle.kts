plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

/**
 * P212：发布签名。
 *
 * 密钥与密码放在根目录 `keystore.properties`（已 gitignore），代码里不出现密码；
 * 这份文件不存在时（比如别人 clone 下来）自动退回 debug 签名，仍然能 `assembleDebug`。
 */
val keystorePropsFile = rootProject.file("keystore.properties")

/** 读 keystore.properties 里的一行（不依赖 java.util.Properties，Kotlin DSL 里更稳）。 */
fun keystoreProp(key: String): String? {
    if (!keystorePropsFile.exists()) return null
    return keystorePropsFile.readLines()
        .firstOrNull { it.trimStart().startsWith("$key=") }
        ?.substringAfter('=')
        ?.trim()
}

android {
    // ── 锁定项（T01 收尾确认，后续卡片不得修改） ─────────────
    // applicationId / minSdk / targetSdk 一经锁定，改包名等同换应用。
    namespace = "com.lanweixi.client"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.lanweixi.client"
        minSdk = 26
        targetSdk = 35
        // P212：上架定稿版本（versionCode 每次发版 +1，不能回退）
        versionCode = 1
        versionName = "2.0.0"
    }

    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProp("storeFile") ?: "")
                storePassword = keystoreProp("storePassword")
                keyAlias = keystoreProp("keyAlias")
                keyPassword = keystoreProp("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = if (keystorePropsFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            // 暂不开混淆：org.json / lua4j / Room 反射面太大，收益不如风险
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core:design"))
    implementation(project(":core:data"))
    implementation(project(":core:network"))
    // P06c：对话用例与历史端口（生产实现 RoomChatHistoryPort 在本模块）
    implementation(project(":feature:chat"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:notes"))
    implementation(project(":feature:prompts"))
    implementation(project(":feature:tasks"))
    implementation(project(":feature:docs"))
    implementation(project(":feature:review"))
    implementation(project(":feature:plugins"))
    // P199锛氬叏灞€鎼滅储鐣岄潰
    implementation(project(":feature:search"))
    implementation(project(":feature:onboarding"))
    // P206：AI 待审核队列
    implementation(project(":feature:inbox"))
    // P12b：最小路由
    implementation(libs.androidx.navigation.compose)
    implementation(project(":domain:prompt"))
    implementation(project(":domain:plugin"))
    implementation(project(":domain:docs"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)
    // P08a：API Key 存 DataStore Preferences（不写 Room、不改 schema）
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Hilt：应用级单例容器（OkHttpClient / LanWeixiDatabase 都在 SingletonComponent）
    // P17：主动消息调度
    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
