import java.util.Properties // 자바 설정 파일 도구 임포트(로컬 프로퍼티에서 카카오키 가져오기 위함)


plugins {
    alias(libs.plugins.android.application)  // 안드로이드 앱 플러그인
    alias(libs.plugins.kotlin.android) // 코틀린 플러그인
    id("org.jetbrains.kotlin.kapt") // 어노테이션 프로세서 지원
    id("kotlin-parcelize")  // 객체 직렬화 기능 지원

}

// local.properties에서 키값을 읽어오는 로직
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}
// 카카오 네이티브 키를 변수에 저장
val kakaoKey = localProperties.getProperty("kakao_native_key") ?: ""



android {
    namespace = "com.project.bingo"  // 앱의 고유 패키지 경로
    compileSdk = 36 // 컴파일 시 사용할 SDK 버전


    // 앱의 디지털 서명 설정 (카카오 지도 해시키 통합 관리용)
    signingConfigs {
        create("release") {
            storeFile = file("debug.keystore") // 공용 해시키 파일 이름
            storePassword = "android"          // 기본 비번
            keyAlias = "androiddebugkey"       // 기본 별명
            keyPassword = "android"            // 기본 비번
        }
    }


    // 프로젝트를 빌드할 때 현재 연결된 기기에 맞는 것만 우선적으로 빌드하도록 설정
    bundle {
        abi {
            enableSplit = true
        }
    }


    defaultConfig {
        applicationId = "com.project.bingo" // 앱의 고유 ID
        minSdk = 24 // 최소 지원 Android 버전 (Nougat)
        targetSdk = 36 // 앱이 최적화된 대상 SDK 버전
        versionCode = 1 // 빌드 번호
        versionName = "1.0" // 앱 표시 버전

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" // 테스트 실행 도구


        // 카카오 지도 라이브러리 구동을 위한 CPU 환경 제한
        ndk {
            abiFilters.add("armeabi-v7a")
            abiFilters.add("arm64-v8a")
        }

        // 코드 내부와 매니페스트에서 사용할 카카오 키 변수 생성
        buildConfigField("String", "KAKAO_NATIVE_KEY", "\"$kakaoKey\"")
        manifestPlaceholders["KAKAO_APP_KEY"] = kakaoKey



        }




    buildTypes {
        // 개발용(Debug) 빌드 시에도 공용 서명키를 사용하도록 설정
        getByName("debug") {
            signingConfig = signingConfigs.getByName("release")
        }

        // 배포용(Release) 빌드 설정
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")  // 공용 서명키 적용
        }
    }
    // Java 및 Kotlin 컴파일 버전 설정 (최신 라이브러리 호환을 위해 17 사용)
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true // 레이아웃 위젯 접근 편의 기능
        buildConfig = true // 코드 내에서 환경 변수(BuildConfig) 사용 가능
    }
    kapt {
        correctErrorTypes = true // KAPT 에러 발생 시 상세 추적 허용
    }

}

dependencies {
    // 기본 AndroidX 및 UI 라이브러리
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.ui)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // 네트워크 통신 (Retrofit & OKHttp)
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // 이미지 로딩 (Glide)
    implementation("com.github.bumptech.glide:glide:4.16.0")
    kapt ("com.github.bumptech.glide:compiler:4.16.0")

    // --- 로컬 DB (러닝 경로 및 기록 저장용) ---
    val room_version = "2.6.1"
    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version")
    kapt("androidx.room:room-compiler:$room_version")

    // --- 안드로이드 Jetpack (데이터 관리 및 화면 이동) ---
    val lifecycle_version = "2.7.0"
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:$lifecycle_version")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:$lifecycle_version")

    // 화면 이동 관리 (Navigation Component)
    val nav_version = "2.7.7"
    implementation("androidx.navigation:navigation-fragment-ktx:$nav_version")
    implementation("androidx.navigation:navigation-ui-ktx:$nav_version")

    // 실시간 위치 트래킹
    implementation("com.google.android.gms:play-services-location:21.1.0")

    // --- 캘린더 (히트맵 구현용) ---
    implementation("com.github.prolificinteractive:material-calendarview:2.0.1")

    // AndroidX / Material
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // Kakao Map SDK v2
    implementation("com.kakao.maps.open:android:2.13.0")



}