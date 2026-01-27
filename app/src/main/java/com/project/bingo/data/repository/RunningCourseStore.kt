package com.project.bingo.data.repository

import android.graphics.Bitmap
import com.project.bingo.data.model.CourseItem

/**
 * RunningCourseStore.kt
 * 추천 코스 생성 결과를 '앱 내부 메모리'에 잠깐 들고 있는 저장소
 * 서버 응답 결과(방대한 GeoJSON 데이터 등)를 저장해두고 선택된 코스만 지정하여 러닝 화면에서 쓰게 함
 * GeoJSON이 크고 화면 간 이동(RouteSelect -> Running) 시 Bundle로 다 들고 다니면 무겁기 때문에 효율적으로 전달하기 위해 사용
 */

object RunningCourseStore {
    // 서버로부터 전달받은 전체 추천 코스 목록
    var courses: List<CourseItem> = emptyList()

    // 사용자가 여러 코스 중 최종적으로 선택한 코스 정보
    var selectedCourse: CourseItem? = null

    // 선택된 코스의 지도 이미지를 임시 보관 (결과 페이지용)
    var selectedCourseBitmap: Bitmap? = null

    // 사용이 끝난 데이터를 초기화하여 메모리를 해제하는 함수
    fun clear() {
        courses = emptyList() // 코스 목록 비우기
        selectedCourse = null  // 선택 코스 초기화
        selectedCourseBitmap = null // 이미지 메모리 해제
    }
}