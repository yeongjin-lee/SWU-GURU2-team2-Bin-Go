package com.project.bingo.ui.splash

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.project.bingo.R
import com.project.bingo.ui.MainActivity
import com.project.bingo.data.model.CourseGenerateRequest
import com.project.bingo.data.model.IncludeOptions
import com.project.bingo.data.model.LatLngPoint
import com.project.bingo.data.repository.CourseRepository
import com.project.bingo.data.repository.RunningCourseStore
import kotlinx.coroutines.launch

/**
 * RunningSplashActivity.kt
 * 사용자가 러닝 옵션을 정한 직후, 서버로부터 실제 경로 정보를 받아오는 동안 표시되는 '네트워크 로딩' 전용 스플래시 화면
 */

class RunningSplashActivity : AppCompatActivity() {

    // 인텐트 데이터 전달을 위한 키값 상수 정의
    companion object {
        const val EXTRA_START_LAT = "extra_start_lat"
        const val EXTRA_START_LNG = "extra_start_lng"
        const val EXTRA_TARGET_KM = "extra_target_km"
        const val EXTRA_INCLUDE_TOILET = "extra_include_toilet" // 화장실 포함 여부
        const val EXTRA_INCLUDE_BIN = "extra_include_bin" // 쓰레기통 포함 여부

        const val EXTRA_INCLUDE_STORE = "extra_include_store" // 편의점 포함 여부
    }

    // 서버 통신 레포지토리
    private val repo = CourseRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 로딩 화면 레이아웃 XML 설정
        setContentView(R.layout.activity_running_splash)

        // 이전 러닝 옵션 설정 화면에서 전달받은 파라미터들 추출
        val startLat = intent.getDoubleExtra(EXTRA_START_LAT, 0.0)
        val startLng = intent.getDoubleExtra(EXTRA_START_LNG, 0.0)
        val targetKm = intent.getDoubleExtra(EXTRA_TARGET_KM, 5.0)
        val includeToilet = intent.getBooleanExtra(EXTRA_INCLUDE_TOILET, true)
        val includeBin = intent.getBooleanExtra(EXTRA_INCLUDE_BIN, true)
        val includeStore = intent.getBooleanExtra(EXTRA_INCLUDE_STORE, true)

        // 시작 좌표가 비정상일 경우 즉시 중단
        if (startLat == 0.0 && startLng == 0.0) {
            Toast.makeText(this, "시작 위치가 없어서 추천을 생성할 수 없어요!", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 비동기 코루틴으로 서버 API 호출
        lifecycleScope.launch {
            try {
                // 서버 전송을 위한 데이터 구조체 생성
                val req = CourseGenerateRequest(
                    start = LatLngPoint(startLat, startLng),
                    targetKm = targetKm,
                    count = 3, // 3가지 추천 코스 요청
                    include = IncludeOptions(
                        toilet = includeToilet,
                        bin = includeBin,
                        store = includeStore
                    )
                )

                // 실제 서버로부터 코스 정보 수신
                val res = repo.generateCourse(req)

                // 서버 응답이 실패하거나 코스 목록이 비어있는 경우 예외 처리
                if (!res.ok || res.courses.isEmpty()) {
                    Toast.makeText(this@RunningSplashActivity, "추천 코스를 생성하지 못했어요!", Toast.LENGTH_SHORT).show()
                    finish()
                    return@launch
                }

                // 성공적으로 받은 데이터를 전역 공유 메모리인 Store에 임시 저장
                RunningCourseStore.courses = res.courses
                RunningCourseStore.selectedCourse = null

                // 결과를 가지고 MainActivity로 돌아가며,
                // '코스 선택' 화면으로 바로 가도록 플래그 전달
                val goMain = Intent(this@RunningSplashActivity, MainActivity::class.java).apply {
                    putExtra("navigate_to", "routeSelect")
                    // 액티비티 스택을 깔끔하게 정리하여 홈 화면을 최상단으로 올림
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(goMain)
                finish() // 로딩 액티비티 종료

            } catch (e: Exception) {
                // 네트워크 에러 로그 기록 및 알림
                Log.e("RunningSplashActivity", "코스 생성 실패", e)
                Toast.makeText(this@RunningSplashActivity, "서버 호출 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}

