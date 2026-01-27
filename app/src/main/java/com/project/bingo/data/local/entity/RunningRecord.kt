package com.project.bingo.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * RunningRecord.kt
 * 러닝 기록 Entity
 * - 사용자 정보, 러닝 완료 기록, 경로(LatLng 리스트) 저장
 */


// 'running_records'라는 이름의 테이블을 생성하며, Converters 클래스를 사용하여 특수 타입을 처리
@Entity(tableName = "running_records")
@TypeConverters(Converters::class)
data class RunningRecord(


    // 자동으로 1씩 증가하는 고유 ID
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val userId: String, // 이 기록을 남긴 사용자의 ID (예: "swu1")
    val date: Long, // 측정된 날짜 타임스탬프 (milliseconds)
    val distance: Double, // 달린 거리 km
    val duration: Long, // 달린 시간(초)
    val calories: Int, // 소모된 칼로리(kcal)
    val pace: Double, // 페이스(분/km)
    val routePoints: List<LatLng>? = null, // 추천 경로 좌표 리스트 저장용

    // 러닝 조건
    val hasTrashCan: Boolean,
    val hasToilet: Boolean,
    val hasConvenience: Boolean,

    // 경로 정보 (JSON으로 저장)
    val routePath: String, // List<LatLng>를 JSON으로 변환

    // 경유지 정보 (JSON으로 저장)
    val waypoints: String // List<Waypoint>를 JSON으로 변환
)


/**
 * 화장실이나 편의점 같은 경유지 세부 정보를 담는 클래스
 */
data class Waypoint(
    val latitude: Double, // 위도
    val longitude: Double, // 경도
    val type: String, // 시설물 종류
    val name: String, // 시설 이름
    val address: String // 시설 주소
)

/**
 * 위도 경도 정보 데이터 클래스
 */
data class LatLng(
    val latitude: Double,
    val longitude: Double
)

/**
 * Room Type Converter
 * - List, JSON 등을 Room에 저장하기 위한 변환기
 */
class Converters {
    private val gson = Gson()

    // LatLng 리스트를 JSON 문자열로 변환
    @TypeConverter
    fun fromLatLngList(value: List<LatLng>?): String {
        return gson.toJson(value)
    }

    // JSON 문자열을 다시 LatLng 리스트로 복구
    @TypeConverter
    fun toLatLngList(value: String): List<LatLng>? {
        val listType = object : TypeToken<List<LatLng>>() {}.type
        return gson.fromJson(value, listType)
    }

    // Waypoint 리스트를 JSON 문자열로 변환
    @TypeConverter
    fun fromWaypointList(value: List<Waypoint>?): String {
        return gson.toJson(value)
    }


    // JSON 문자열을 다시 Waypoint 리스트로 복구
    @TypeConverter
    fun toWaypointList(value: String): List<Waypoint>? {
        val listType = object : TypeToken<List<Waypoint>>() {}.type
        return gson.fromJson(value, listType)
    }
}
