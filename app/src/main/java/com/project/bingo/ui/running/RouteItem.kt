package com.project.bingo.ui.running

import android.graphics.Bitmap

/**
 * RouteItem.kt
 *  RouteSelectFragment RecyclerView에 뿌릴 UI 모델
 * - courseIndex: RunningCourseStore.courses 인덱스와 매칭
 */
data class RouteItem(
    val courseIndex: Int, // 원본 코스 리스트에서의 순서 번호
    val distanceText: String, // 화면에 표시될 거리 문자열 (예: "3.5km")
    val waypointCount: Int, // 한 루트 안의 총 경유지 수
    val toiletCount: Int,       // 루트 내 화장실 개수
    val binCount: Int,          // 루트 내 쓰레기통 개수
    val storeCount: Int,        // 루트 내 편의점 개수
    val thumbnail: Bitmap?      // 지도 경로가 그려진 미리보기 이미지
)
