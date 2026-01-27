package com.project.bingo.ui.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View

/**
 * WeeklyBarChartView.kt
 * 사용자의 주간 누적 러닝 거리를 요일별 막대 그래프로 화면에 직접 그리는 UI 컴포넌트
 */
class WeeklyBarChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 막대(바)를 그리기 위한 페인트 객체 설정
    private val barPaint = Paint().apply {
        style = Paint.Style.FILL // 면을 채우는 스타일
        isAntiAlias = true // 외곽선 매끄럽게 처리
    }

    // 하단 요일 글자를 그리기 위한 페인트 객체 설정
    private val textPaint = Paint().apply {
        color = Color.parseColor("#185F70") // 텍스트 색상 설정
        textSize = 40f // 텍스트 크기 설정
        textAlign = Paint.Align.CENTER  // 가운데 정렬
        isAntiAlias = true
    }

    // 요일별 거리 데이터를 보관하는 리스트 (기본값은 모두 0.0)
    private var data: List<Float> = listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f)
    // 그래프 아래에 표시할 요일 문자열 리스트
    private val days = listOf("월", "화", "수", "목", "금", "토", "일")

    // 외부(HomeFragment)로부터 새로운 요일별 데이터를 받아 그래프를 다시 그림
    fun setData(weeklyDistances: List<Float>) {
        this.data = weeklyDistances
        invalidate() // 화면을 무효화하여 onDraw() 함수가 다시 호출되도록 함
    }

    // 실제 화면 영역에 그림을 그리는 핵심 콜백 함수
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (data.isEmpty()) return  // 데이터가 없으면 그리지 않음

        val widthSize = width.toFloat()  // 뷰의 가로 너비
        val heightSize = height.toFloat() - 60f // 하단 텍스트 공간 제외 그래프 실영역 높이
        val barWidth = widthSize / (data.size * 2) // 각 막대의 가로 너비 계산
        val spacing = widthSize / data.size  // 막대 사이의 간격 포함 너비

        // 데이터 중 가장 큰 값을 찾아 비율을 정함 (모두 0이면 기준값 10 사용)
        val maxVal = data.maxOrNull()?.let { if (it == 0f) 10f else it } ?: 10f

        // 7개의 요일 데이터를 순회하며 막대를 하나씩 그림
        data.forEachIndexed { index, value ->
            val barHeight = (value / maxVal) * heightSize // 최대값 대비 막대 높이 비율 계산
            val left = spacing * index + (spacing - barWidth) / 2 // 막대의 왼쪽 시작 좌표
            val top = heightSize - barHeight // 막대의 상단 좌표 (y축은 아래로 갈수록 커짐)
            val right = left + barWidth  // 막대의 오른쪽 끝 좌표
            val bottom = heightSize // 막대의 하단 끝 좌표 (텍스트 영역 바로 위)

            // 막대에 위아래 그라데이션 색상 적용
            val gradient = LinearGradient(
                left, top, left, bottom,
                Color.parseColor("#2596B2"), // 시작 부분 (진한 파랑)
                Color.parseColor("#FCFCFC"), // 끝 부분 (연한 회색)
                Shader.TileMode.CLAMP
            )
            barPaint.shader = gradient

            // 캔버스에 계산된 사각형(막대) 그리기
            canvas.drawRect(left, top, right, bottom, barPaint)

            // 막대 바로 아래 요일 텍스트 그리기
            canvas.drawText(days[index], left + barWidth / 2, height.toFloat() - 10f, textPaint)
        }

    }
}