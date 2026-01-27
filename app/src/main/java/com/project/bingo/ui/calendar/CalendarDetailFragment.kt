package com.project.bingo.ui.calendar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import com.project.bingo.base.BaseFragment
import com.project.bingo.databinding.FragmentCalendarDetailBinding
import java.text.SimpleDateFormat
import java.util.*

/**
 * CalendarDetailFragment.kt
 * 캘린더에서 선택한 특정 날짜의 러닝 통계(거리, 시간, 페이스, 칼로리)를 상세히 보여주는 프래그먼트
 */
class CalendarDetailFragment : BaseFragment<FragmentCalendarDetailBinding>() {

    // 바인딩 클래스 지정
    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentCalendarDetailBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 이전 화면(CalendarFragment)에서 넘겨준 데이터(Arguments) 추출
        val distance = arguments?.getDouble("distance") ?: 0.0 // 달린 거리
        val duration = arguments?.getLong("duration") ?: 0L // 달린 시간
        val pace = arguments?.getDouble("pace") ?: 0.0 // 평균 페이스
        val calories = arguments?.getInt("calories") ?: 0 // 소모 칼로리
        val dateMillis = arguments?.getLong("date") ?: System.currentTimeMillis() // 날짜 타임스탬프

        // 날짜를 "2024년 1월 20일" 형식으로 변환하기 위한 설정
        val sdf = SimpleDateFormat("yyyy년 M월 d일", Locale.KOREAN)

        // 뷰 바인딩을 통해 화면 UI 요소 업데이트
        binding.apply {
            // 날짜 표시
            tvDetailDate.text = sdf.format(Date(dateMillis))
            // 거리 표시 (소수점 2자리까지)
            tvDetailDistance.text = String.format("%.2f", distance)
            // 시간 표시 (00:00 형식)
            tvDetailDuration.text = String.format("%02d:%02d", duration / 60, duration % 60)

            // 페이스 계산: 소수점 이하를 초 단위로 변환
            val min = pace.toInt()
            val sec = ((pace - min) * 60).toInt()
            // 페이스 표시 (X'YY 형식)
            tvDetailPace.text = String.format("%d'%02d", min, sec)

            // 소모 칼로리 표시
            tvDetailCalories.text = calories.toString()

            // 뒤로가기 버튼 클릭 시 현재 상세 화면을 닫고 이전 화면으로 복귀
            btnDetailConfirm.setOnClickListener {
                findNavController().popBackStack()
            }
        }
    }
}