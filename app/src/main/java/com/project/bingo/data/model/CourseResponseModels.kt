package com.project.bingo.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * CourseResponseModel.kt
 * 서버와 주고받는 JSON 데이터를 안드로이드 객체로 매핑하기 위한 클래스
 */

// 서버로 코스 생성을 요청할 때 보내는 데이터 구조
data class CourseGenerateRequest(
    val start: LatLngPoint, // 시작 좌표
    val targetKm: Double, // 목표 거리
    val count: Int = 3, // 받고 싶은 코스 개수
    val include: IncludeOptions // 포함할 시설물 옵션 객체
)

// 시설물 포함 여부 옵션 (프래그먼트 간 이동을 위해 Parcelize 적용)
@Parcelize
data class IncludeOptions(
    val toilet: Boolean = true,
    val bin: Boolean = true,
    val store: Boolean = true
) : Parcelable

// 단순 위도/경도 매핑 클래스
data class LatLngPoint(
    val lat: Double,
    val lng: Double
)

// 서버의 전체 응답 구조
data class CourseGenerateResponse(
    val ok: Boolean, // 성공 여부
    val mode: String?, // 서버 처리 모드
    val requested: Int?, // 요청된 개수
    val returned: Int?, // 실제 반환된 개수
    val courses: List<CourseItem> = emptyList(), // 코스 리스트
    val error: String? = null // 에러 메시지 (있는 경우)
)

// 개별 코스 아이템 정보
@Parcelize
data class CourseItem(
    val meta: CourseMeta, // 코스 분석 정보
    val waypoints: List<Waypoint> = emptyList(), // 코스 내 경유지들
    val geojson: GeoJson? = null // 지도에 그릴 실제 경로 선 데이터
) : Parcelable

// 코스에 대한 메타데이터 (서버 측 계산 결과)
@Parcelize
data class CourseMeta(
    val index: Int,
    val targetKm: Double,
    val targetDistanceM: Int,
    val totalDistanceM: Double,
    val toleranceRatio: Double,
    val toleranceHit: Boolean,
    val radiusM: Int,
    val candidates: Int,
    val orsCallsUsed: Int
) : Parcelable

// 경유지 지점 정보
@Parcelize
data class Waypoint(
    val id: String,
    val type: String,  // "toilet", "bin", "store"
    val name: String?,
    val lat: Double,
    val lng: Double
) : Parcelable

// GeoJSON 표준 구조 (지도의 선을 그리기 위함)
@Parcelize
data class GeoJson(
    val type: String?,
    val features: List<GeoJsonFeature> = emptyList()
) : Parcelable

// GeoJSON의 특징(선, 도형 등)을 담는 클래스
@Parcelize
data class GeoJsonFeature(
    val type: String?,
    val geometry: GeoJsonGeometry?
) : Parcelable

// 실제 좌표 리스트가 들어있는 기하학적 데이터 구조
@Parcelize
data class GeoJsonGeometry(
    val type: String?,
    val coordinates: List<List<Double>> = emptyList() // [[lng, lat], ...]
) : Parcelable