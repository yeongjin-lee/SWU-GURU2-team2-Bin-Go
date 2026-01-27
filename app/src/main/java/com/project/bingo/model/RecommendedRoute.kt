package com.project.bingo.model

/**
 * RecommendedRoute.kt
 * 홈 화면에서 보이는 추천 경로의 이름, 거리, 난이도, 설명, 경로 좌표 등의 정보를 담는 데이터 모델
 */
data class RecommendedRoute(
    val id: Int,             // 경로 고유 ID
    val name: String,        // 경로 이름 (예: 한강 코스)
    val distance: Double,    // 경로의 총 거리
    val difficulty: String,  // 경로의 난이도
    val imageUrl: String,    // 경로 대표 이미지 주소
    val description: String, // 경로에 대한 상세 설명
    val path: List<LatLngModel> // 경로를 구성하는 좌표 리스트
)

// 추천 경로용 위도/경도 모델
data class LatLngModel(
    val latitude: Double,    // 위도
    val longitude: Double    // 경도
)
