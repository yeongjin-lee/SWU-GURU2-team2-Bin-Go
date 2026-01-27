package com.project.bingo.data.remote

import android.os.Build
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * RetrogitClient.kt
 * 서버와의 네트워크 연결 통로를 생성하고 유지하는 싱글톤 객체
 * 타임아웃, 로그 기록, 환경별 서버 주소 변경 등을 처리
 * 실기기와 에뮬레이터 환경을 자동으로 구분하여 접속 주소를 결정
 */

object RetrofitClient {

    // 실기기의 경우 본인의 PC IPv4 주소를 입력 (cmd -> ipconfig로 확인)
    // 에뮬레이터의 경우 값을 비워둠
    private const val MY_PC_IP = ""

    // 서버 기본 주소를 결정 (에뮬레이터냐 실기기냐에 따라 다름)
    private val BASE_URL: String by lazy {
        // 현재 실행 중인 기기가 에뮬레이터인지 확인하는 로직
        val isEmulator = (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || Build.FINGERPRINT.contains("generic")
                || Build.FINGERPRINT.contains("vbox")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.FINGERPRINT.contains("sdk_gphone")

        if (isEmulator) {
            // 안드로이드 에뮬레이터에서 내 PC로 접속하는 주소
            "http://10.0.2.2:3000/"
        } else {
            // 실기기의 경우 내 PC 서버로 접속하는 주소
            "http://$MY_PC_IP:3000/"
        }
    }

    // 서버와 주고받는 모든 패킷 로그를 가로채서 보여주는 인터셉터
    private val loggingInterceptor by lazy {
        HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY  // 전체 본문 내용을 로그에 표시
        }
    }

    // 실제 네트워크 요청을 처리하는 클라이언트 설정
    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor) // 로그 인터셉터 추가
            .connectTimeout(20, TimeUnit.SECONDS) // 서버 연결 대기 시간 20초
            .readTimeout(30, TimeUnit.SECONDS) // 데이터 읽기 대기 시간 30초
            .writeTimeout(30, TimeUnit.SECONDS) // 데이터 쓰기 대기 시간 30초
            .build()
    }

    // Retrofit 설정 및 생성
    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL) // 서버 주소 설정
            .client(okHttpClient) // 위에서 만든 클라이언트 사용
            .addConverterFactory(GsonConverterFactory.create()) // JSON 변환기 추가
            .build()
    }


    // 외부에서 접근할 실제 서비스 인터페이스
    val courseService: CourseService by lazy {
        retrofit.create(CourseService::class.java)
    }
}
