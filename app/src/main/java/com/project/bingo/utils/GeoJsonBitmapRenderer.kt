package com.project.bingo.utils

import android.content.Context
import android.graphics.*
import androidx.core.content.ContextCompat
import com.project.bingo.R
import com.project.bingo.data.model.GeoJson
import com.project.bingo.data.model.Waypoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min

/**
 *  GeoJsonBitmapRenderer.kt
 *  추천된 경로 선과 마커를 비트맵 이미지로 변환하여 렌더링하는 도구
 */
object GeoJsonBitmapRenderer {

    // 전체 경로(GeoJSON)와 시설물 마커들을 하나의 비트맵 이미지로 그려서 반환하는 함수
    suspend fun renderFullRoute(
        context: Context,
        geojson: GeoJson?,
        waypoints: List<Waypoint>,
        width: Int = 520, // 생성될 비트맵 가로 길이
        height: Int = 320 // 생성될 비트맵 세로 길이
    ): Bitmap? = withContext(Dispatchers.Default) { // 연산량이 많으므로 백그라운드 스레드에서 실행
        val feature = geojson?.features?.firstOrNull()
        val coords = feature?.geometry?.coordinates ?: return@withContext null // 좌표 데이터가 없으면 중단
        if (coords.size < 2) return@withContext null // 선을 그리려면 최소 2개 이상의 좌표가 필요

        // 지도 상의 위도/경도 경계(Min, Max)를 찾아 좌표 평면 생성
        val lngs = coords.map { it[0] }
        val lats = coords.map { it[1] }
        val minLng = lngs.minOrNull()!!; val maxLng = lngs.maxOrNull()!!
        val minLat = lats.minOrNull()!!; val maxLat = lats.maxOrNull()!!

        // 빈 도화지(비트맵)와 붓(캔버스) 생성
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. 배경 채우기 및 모눈종이 그리드 효과 추가
        canvas.drawColor(Color.parseColor("#F8F9FA"))
        val gridPaint = Paint().apply {
            color = Color.parseColor("#E9ECEF")
            strokeWidth = 1.5f
        }
        for (i in 0..width step 20) canvas.drawLine(i.toFloat(), 0f, i.toFloat(), height.toFloat(), gridPaint)
        for (i in 0..height step 20) canvas.drawLine(0f, i.toFloat(), width.toFloat(), i.toFloat(), gridPaint)

        // 2. 좌표를 캔버스 픽셀로 변환하기 위한 배율 계산
        val padding = width * 0.15f
        val usableW = width - padding * 2
        val usableH = height - padding * 2

        val lngRange = if (maxLng - minLng == 0.0) 1.0 else maxLng - minLng
        val latRange = if (maxLat - minLat == 0.0) 1.0 else maxLat - minLat

        val scale = min(usableW / lngRange, usableH / latRange) // 화면 크기에 맞춘 최적의 배율
        val offsetX = (width - lngRange * scale) / 2 // 중앙 정렬용 가로 오프셋
        val offsetY = (height - latRange * scale) / 2 // 중앙 정렬용 세로 오프셋

        // GPS 좌표를 픽셀 X, Y 좌표로 바꿔주는 내부 함수들
        fun getX(lng: Double): Float = (offsetX + (lng - minLng) * scale).toFloat()
        fun getY(lat: Double): Float = (height - (offsetY + (lat - minLat) * scale)).toFloat()

        // 3. 실제 경로 선(Path) 그리기
        val path = Path()
        coords.forEachIndexed { idx, p ->
            val x = getX(p[0]); val y = getY(p[1])
            if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y) // 시작점 설정 및 선 잇기
        }

        // 경로 선을 그릴 스타일 붓 설정
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#5296B1") // 메인 선 색상 (하늘색)
            style = Paint.Style.STROKE
            strokeWidth = 8f // 선 두께
            strokeCap = Paint.Cap.ROUND // 선 끝 모양 둥글게
            strokeJoin = Paint.Join.ROUND // 꺾이는 부분 둥글게
            setShadowLayer(5f, 0f, 4f, Color.parseColor("#66000000")) // 입체감을 위한 그림자 효과
        }
        canvas.drawPath(path, linePaint) // 선 그리기 실행

        // --- 텍스트(시설 이름)용 Paint 설정 ---
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#333333") // 진한 회색 텍스트
            textSize = 15f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) // 볼드체
        }
        val textBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CCFFFFFF") // 흰색 반투명 배경
            style = Paint.Style.FILL
        }

        // 4. 시설물 위치 마커 위에 실제 이름 텍스트 표시
        waypoints.forEach { wp ->
            val mX = getX(wp.lng)
            val mY = getY(wp.lat)

            //  실제 시설 이름 표시 및 데이터가 없을 경우 "경유지"로 대체
            val label: String = wp.name ?: "경유지"
            val textY = mY - 35f // 마커 머리 위 적절한 높이 설정

            val rect = Rect()
            // 텍스트가 차지할 사각형 영역 크기 측정
            textPaint.getTextBounds(label, 0, label.length, rect)

            // 텍스트 뒤에 둥근 사각형 배경 상자 그리기 (가독성 향상 목적)
            canvas.drawRoundRect(
                mX - rect.width()/2f - 6f, textY + rect.top - 2f,
                mX + rect.width()/2f + 6f, textY + rect.bottom + 2f,
                6f, 6f, textBgPaint
            )
            // 실제 글자 그리기
            canvas.drawText(label, mX, textY, textPaint)
        }

        // 5. 경로의 시작점에 빨간색 시작 마커 추가
        val startX = getX(coords[0][0]); val startY = getY(coords[0][1])
        ContextCompat.getDrawable(context, R.drawable.ic_marker_red)?.apply {
            val sW = 30; val sH = 41
            setBounds((startX - sW/2).toInt(), (startY - sH/2).toInt(), (startX + sW/2).toInt(), (startY + sH/2).toInt())
            draw(canvas)
        }

        // 6. 경유지 타입별 시설 아이콘 마커(쓰레기통, 화장실, 편의점) 추가
        waypoints.forEach { wp ->
            val mX = getX(wp.lng); val mY = getY(wp.lat)
            val markerRes = when {
                wp.type.contains("bin", true) -> R.drawable.ic_bin_marker
                wp.type.contains("toilet", true) -> R.drawable.ic_toilet_marker
                wp.type.contains("store", true) -> R.drawable.ic_store_marker
                else -> R.drawable.ic_bin_marker
            }

            // 아이콘 이미지 가져와서 캔버스에 그리기
            ContextCompat.getDrawable(context, markerRes)?.apply {
                val mW = 26; val mH = 37
                setBounds((mX - mW/2).toInt(), (mY - mH).toInt(), (mX + mW/2).toInt(), mY.toInt())
                draw(canvas)
            }
        }
        return@withContext bitmap // 완성된 이미지 비트맵 반환
    }
}
