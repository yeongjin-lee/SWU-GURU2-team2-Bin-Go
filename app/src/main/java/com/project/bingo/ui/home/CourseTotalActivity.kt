package com.project.bingo.ui.home

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.project.bingo.R
import com.project.bingo.databinding.ActivityCourseTotalBinding

/**
 * CourseTotalActivity.kt
 * 홈화면 추천 코스들의 전체 목록을 수직 리스트로 사용자에게 한눈에 보여주는 화면
 */

// 1. 코스 정보를 담기 위한 전용 데이터 클래스 정의
data class PresetCourse(
    val id: Long,           // 코스 고유 ID
    val name: String,       // 코스 이름
    val description: String, // 코스 상세 설명
    val imageRes: Int       // 이미지 리소스 ID
)

class CourseTotalActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCourseTotalBinding // 뷰 바인딩 변수 선언

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 레이아웃 파일을 바인딩 객체로 변환하여 화면 설정
        binding = ActivityCourseTotalBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 시스템 상단바/하단바 겹침 방지 여백 설정
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // 상단 뒤로가기 버튼 클릭 시 현재 액티비티 종료
        binding.btnBack.setOnClickListener { finish() }

        // 화면에 표시할 고정 코스 데이터 리스트 생성 (ID는 캡처 이미지 번호와 맞춤)
        val courseList = listOf(
            PresetCourse(1, "여의도 한강 코스", "한강을 바라보며 시원하게 달리는 코스", R.drawable.course_img_1),
            PresetCourse(2, "반포 한강 코스", "야경이 아름다운 반포대교 달빛광장 코스", R.drawable.course_img_2),
            PresetCourse(3, "올림픽공원 코스", "나무가 많아 공기가 맑은 루프 코스", R.drawable.course_img_3)
        )

        // 리사이클러뷰를 수직 방향 리스트 형태로 설정
        binding.rvCourses.layoutManager = LinearLayoutManager(this)
        // 데이터 리스트와 어댑터를 연결하여 최종 화면 표시
        binding.rvCourses.adapter = CourseAdapter(courseList)
    }
}