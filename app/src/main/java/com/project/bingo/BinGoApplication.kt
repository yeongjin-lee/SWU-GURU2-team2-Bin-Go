package com.project.bingo

import android.app.Application
import com.kakao.vectormap.KakaoMapSdk

/**
 * BinGoApplication.kt
 * - 앱 전체 생명주기 관리
 * - Kakao Map SDK 초기화
 * - Room Database 초기화
 */
class BinGoApplication : Application() {

    // 앱이 처음 메모리에 로드되어 시작될 때 가장 먼저 호출됨
    override fun onCreate() {
        super.onCreate()

        // Kakao Map SDK 초기화 작업 수행
        // local.properties -> build.gradle을 거쳐 BuildConfig에 생성된 안전한 키를 사용하여 초기화
        KakaoMapSdk.init(this, BuildConfig.KAKAO_NATIVE_KEY)
    }
}
