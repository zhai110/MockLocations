// 👉 把 import 放最顶部！！！
import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

// 从 local.properties 读取高德地图 API Key
val localProps = Properties()
val localPropsFile = rootProject.file("local.properties")
if (localPropsFile.exists()) {
    FileInputStream(localPropsFile).use {
        localProps.load(it)
    }
}

android {
    namespace = "com.mockloc"
    compileSdk = 36

    // 签名配置：从 local.properties 读取，避免密钥信息提交到版本控制
    signingConfigs {
        create("release") {
            val storeFilePath = localProps.getProperty("RELEASE_STORE_FILE", "")
            val storePwd = localProps.getProperty("RELEASE_STORE_PASSWORD", "")
            val keyAliasName = localProps.getProperty("RELEASE_KEY_ALIAS", "")
            val keyPwd = localProps.getProperty("RELEASE_KEY_PASSWORD", "")

            if (storeFilePath.isNotEmpty()) {
                try {
                    storeFile = file(storeFilePath)
                    storePassword = storePwd
                    keyAlias = keyAliasName
                    keyPassword = keyPwd
                } catch (e: Exception) {
                    // 如果密钥文件不存在，记录警告但不中断构建
                    println("Warning: Release signing config failed - ${e.message}")
                }
            }
        }
    }

    defaultConfig {
        applicationId = "com.mockloc"
        minSdk = 29
        targetSdk = 36
        versionCode = 8
        versionName = "1.5.1"  // 修复悬浮窗位置模拟坐标问题

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 高德地图 Key
        manifestPlaceholders["AMAP_KEY"] = localProps.getProperty("AMAP_API_KEY", "")
        
        // ✅ Room schema 导出目录配置
        javaCompileOptions {
            annotationProcessorOptions {
                arguments += mapOf(
                    "room.schemaLocation" to "$projectDir/schemas"
                )
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false  // 暂时禁用混淆，避免高德地图死锁
            isShrinkResources = false
            
            // proguardFiles(
            //     getDefaultProguardFile("proguard-android-optimize.txt"),
            //     "proguard-rules.pro"
            // )
            // ✅ 使用 Release 签名配置
            signingConfig = signingConfigs.getByName("release")
            
            // ⚠️ 如果遇到文件锁定问题，可以临时禁用 Lint 检查
            // lintOptions.isCheckReleaseBuilds = false
        }
        debug {
            isMinifyEnabled = false
            // Debug 版本始终使用 debug 签名
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
        viewBinding = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            pickFirsts += setOf("lib/*/libAMapSDKMAP.so", "lib/*/libAMapSDKLOC.so")
        }
        resources {
            excludes += setOf("META-INF/DEPENDENCIES")
        }
    }
}

dependencies {
    // AndroidX Core
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.activity:activity-ktx:1.11.0")
    implementation("androidx.fragment:fragment-ktx:1.8.6")

    // Material Design 3
    implementation("com.google.android.material:material:1.12.0")

    // Dynamic Animation (Spring Animation)
    implementation("androidx.dynamicanimation:dynamicanimation:1.0.0")

    // ConstraintLayout
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    // ✅ 用于检测应用前后台状态
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")

    // Room Database
    val roomVersion = "2.7.0"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // 高德地图 SDK（锁定版本以确保构建稳定性）
    implementation("com.amap.api:3dmap-location-search:11.1.001_loc11.1.001_sea9.7.4")

    // Timber
    implementation("com.jakewharton.timber:timber:5.0.1")

    // OkHttp - 网络请求
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // Gson - JSON解析
    implementation("com.google.code.gson:gson:2.11.0")

    // LeakCanary - 内存泄漏检测 (仅debug版本)
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}