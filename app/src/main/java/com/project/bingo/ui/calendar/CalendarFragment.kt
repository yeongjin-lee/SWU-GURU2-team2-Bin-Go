package com.project.bingo.ui.calendar

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.project.bingo.R
import com.project.bingo.base.BaseFragment
import com.project.bingo.data.local.entity.RunningRecord
import com.project.bingo.databinding.FragmentCalendarBinding
import com.prolificinteractive.materialcalendarview.CalendarDay
import com.prolificinteractive.materialcalendarview.DayViewDecorator
import com.prolificinteractive.materialcalendarview.DayViewFacade
import java.text.SimpleDateFormat
import java.util.*

/**
 * CalendarFragment.kt
 * 캘린더를 통해 월별 러닝 통계를 요약하고, 일자별 러닝 강도를 색상(히트맵)으로 표현하는 화면입니다.
 */
class CalendarFragment : BaseFragment<FragmentCalendarBinding>() {

    private lateinit var viewModel: CalendarViewModel  // 데이터 요청을 위한 뷰모델
    private var allRecords: List<RunningRecord> = emptyList() // DB에서 불러온 전체 기록 리스트

    // 상세 페이지로 넘길 선택된 날짜의 데이터를 임시 보관
    private var selectedDayRecord: RunningRecord? = null

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentCalendarBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // 뷰모델 연결
        viewModel = ViewModelProvider(this)[CalendarViewModel::class.java]

        // 캘린더 리스너 및 데이터 관찰 설정
        setupCalendarEvents()
        observeRunningData()

        // 상세보기 파란 버튼 클릭 시 상세 페이지로 이동하며 데이터 전달
        binding.btnGoDetail.setOnClickListener {
            selectedDayRecord?.let { record ->
                val bundle = Bundle().apply {
                    putDouble("distance", record.distance)
                    putLong("duration", record.duration)
                    putDouble("pace", record.pace)
                    putInt("calories", record.calories)
                    putLong("date", record.date)
                }
                // nav_graph에 정의된 action ID를 사용하여 이동
                findNavController().navigate(R.id.action_calendar_to_detail, bundle)
            }
        }
    }

    //캘린더에서 날짜가 바뀌거나 달이 바뀔 때의 이벤트 설정
    private fun setupCalendarEvents() {
        binding.calendarView.apply {
            // 특정 날짜를 터치했을 때 그 날의 기록 요약 카드를 갱신
            setOnDateChangedListener { _, date, _ -> updateSelectedDateCard(date) }
            // 월 변경 시 월 전체 통계 데이터 갱신
            setOnMonthChangedListener { _, date -> updateMonthlySummary(date.month, date.year) }
        }
    }


    //  DB의 러닝 기록 데이터 변화를 관찰하여 UI 갱신
    private fun observeRunningData() {
        viewModel.runningRecords.observe(viewLifecycleOwner) { records ->
            allRecords = records
            // 1. 러닝 거리에 따른 캘린더 히트맵 색상 적용
            applyHeatmap(records)

            // 2. 진입 시 오늘 날짜를 기본값으로 통계 표시
            val today = CalendarDay.today()
            updateSelectedDateCard(today)
            updateMonthlySummary(today.month, today.year)
        }
    }

    // 선택된 특정 날짜의 데이터를 계산하여 하단 UI 카드를 갱신
    private fun updateSelectedDateCard(day: CalendarDay) {
        // 선택한 날짜의 시작 시간(0시 0분) 설정
        val startCal = Calendar.getInstance().apply {
            set(day.year, day.month - 1, day.day, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // 다음 날 0시 전까지를 범위로 설정
        val endCal = (startCal.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) }

        // 전체 기록 중 해당 범위(선택일)에 속하는 기록만 필터링
        val dayRecords = allRecords.filter { it.date in startCal.timeInMillis until endCal.timeInMillis }

        binding.tvSelectedDate.text = "${day.month}월 ${day.day}일" // 선택 날짜 텍스트 업데이트

        if (dayRecords.isNotEmpty()) {
            // 해당 날짜에 기록이 있는 경우 데이터 합산 계산
            val totalDist = dayRecords.sumOf { it.distance }
            val totalDuration = dayRecords.sumOf { it.duration }
            val totalCalories = dayRecords.sumOf { it.calories }
            val avgPace = if (totalDist > 0.001) (totalDuration / 60.0) / totalDist else 0.0

            // 합산 데이터 UI 표시
            binding.tvSelectedDistance.text = String.format("%.2f km", totalDist)
            binding.tvSelectedDuration.text = formatSecondsToTime(totalDuration)
            binding.tvSelectedPace.text = formatPace(avgPace)

            // 상세보기 전달용 데이터 세팅
            // 새로운 객체를 생성하지 않고, 해당 날짜의 첫 번째 객체를 복사해서 합산 값을 덮어씌움
            // DB 필드 누락으로 인한 에러를 방지하기 위함
            selectedDayRecord = dayRecords[0].copy(
                distance = totalDist,
                duration = totalDuration,
                pace = avgPace,
                calories = totalCalories,
                date = startCal.timeInMillis // 선택된 날짜 기준
            )
        } else {
            // 기록이 없는 경우 0으로 표시
            binding.tvSelectedDistance.text = "0.00 km"
            binding.tvSelectedDuration.text = "00:00"
            binding.tvSelectedPace.text = "0'00\""
            selectedDayRecord = null
        }
    }

    // 해당 월의 전체 누적 거리, 평균 페이스, 최장 러닝 기록을 계산하여 표시
    private fun updateMonthlySummary(month: Int, year: Int) {
        val monthRecords = allRecords.filter {
            // 현재 캘린더에 표시된 월에 해당하는 기록들만 필터링
            val cal = Calendar.getInstance().apply { timeInMillis = it.date }
            cal.get(Calendar.MONTH) + 1 == month && cal.get(Calendar.YEAR) == year
        }

        if (monthRecords.isNotEmpty()) {
            val totalDist = monthRecords.sumOf { it.distance } // 월 누적 거리
            val avgPace = monthRecords.map { it.pace }.average() // 월 평균 페이스
            val longestRun = monthRecords.maxByOrNull { it.distance } // 가장 길게 달린 기록

            binding.tvMonthlyTotalDistance.text = String.format("%.1f km", totalDist)
            binding.tvMonthlyAvgPace.text = formatPace(avgPace)

            longestRun?.let {
                binding.tvLongestDistance.text = String.format("%.1f km", it.distance)
                val sdf = SimpleDateFormat("yyyy년 M월 d일", Locale.KOREA)
                binding.tvLongestDate.text = sdf.format(Date(it.date))
            }
        } else {
            // 월간 기록 부재 시 초기화
            binding.tvMonthlyTotalDistance.text = "0.0 km"
            binding.tvMonthlyAvgPace.text = "0'00\""
            binding.tvLongestDistance.text = "0.0 km"
            binding.tvLongestDate.text = "-"
        }
    }

    // 일별 누적 거리에 따라 캘린더 날짜의 배경 색상을 다르게 칠함 (히트맵 효과)
    private fun applyHeatmap(records: List<RunningRecord>) {
        // 날짜별로 거리를 합산한 Map 생성
        val dateDistMap = records.groupBy {
            val cal = Calendar.getInstance().apply { timeInMillis = it.date }
            CalendarDay.from(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
        }.mapValues { it.value.sumOf { r -> r.distance } }

        // 이전 장식 제거 후 새로운 거리 데이터에 맞춰 장식 추가
        binding.calendarView.removeDecorators()
        dateDistMap.forEach { (date, dist) ->
            binding.calendarView.addDecorator(HeatmapDecorator(date, getLevelColor(dist)))
        }
    }

    // 거리에 따른 색상 단계를 반환 (많이 달릴수록 진한 파란색)
    private fun getLevelColor(distance: Double): Int {
        return when {
            distance >= 10.0 -> Color.parseColor("#2596B2") // 매우 높음
            distance >= 7.0 -> Color.parseColor("#59AEC4") // 높음
            distance >= 5.0 -> Color.parseColor("#95CAD9")  // 중간
            distance >= 3.0 -> Color.parseColor("#BDDDE7")  // 낮음
            else -> Color.parseColor("#EBEDF0") // 거의 없음
        }
    }

    // 초 단위를 MM:SS 형식 문자열로 변환
    private fun formatSecondsToTime(sec: Long): String = String.format("%02d:%02d", sec / 60, sec % 60)

    // 더블 페이스 값을 M'SS" 형식 문자열로 변환
    private fun formatPace(pace: Double): String {
        val min = pace.toInt()
        val sec = ((pace - min) * 60).toInt()
        return "$min'$sec\""
    }

    // 캘린더뷰의 특정 날짜 배경색을 변경하기 위한 내부 클래스
    private class HeatmapDecorator(private val date: CalendarDay, private val color: Int) : DayViewDecorator {
        // 데코레이션할 날짜 판정
        override fun shouldDecorate(day: CalendarDay) = day == date
        // 배경색 적용
        override fun decorate(view: DayViewFacade) = view.setBackgroundDrawable(ColorDrawable(color))
    }
}