package com.project.bingo.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.project.bingo.R
import com.project.bingo.base.BaseFragment
import com.project.bingo.data.local.BinGoDatabase
import com.project.bingo.data.local.entity.RunningRecord
import com.project.bingo.databinding.FragmentHomeBinding
import com.project.bingo.model.RecommendedRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

/**
 * HomeFragment.kt
 * 앱의 첫 화면(홈)으로 유저의 주간 통계 그래프 시각화
 * 러닝 시작/코스 전체보기 등 주요 기능으로의 진입점을 제공
 */
class HomeFragment : BaseFragment<FragmentHomeBinding>() {

    // 추천 코스 리사이클러뷰를 위한 어댑터 변수
    private lateinit var recommendedRouteAdapter: RecommendedRouteAdapter

    // XML 레이아웃 바인딩 객체 연결
    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentHomeBinding {
        return FragmentHomeBinding.inflate(inflater, container, false)
    }

    // DB에서 유저 정보를 읽어와 타이틀 메시지를 설정하는 함수
    private fun loadUserData() {
        val context = context ?: return // 컨텍스트 유효성 검사
        viewLifecycleOwner.lifecycleScope.launch {
            val db = BinGoDatabase.getDatabase(context) // DB 인스턴스 획득
            val user = withContext(Dispatchers.IO) {
                db.userDao().getOneUser() // 백그라운드에서 유저 정보 1건 조회
            }

            // 데이터 로드 후 바인딩과 프래그먼트 활성 상태 확인
            if (_binding != null && isAdded) {
                user?.let {
                    // 유저 이름을 포함한 환영 메시지 텍스트 설정
                    binding.homeTitle.text = "${it.userName}님, 오늘도 달려볼까요?"
                }
            }
        }
    }



    // 뷰가 생성된 후 실행되는 초기화 함수
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecommendedRoutes() // 추천 코스 목록 설정
        loadWeeklyData()  // 주간 운동량 그래프 데이터 로드
        loadUserData()  // 사용자 이름 표시

        // '러닝 시작' 버튼 클릭 시 네비게이션 액션을 통해 화면 이동
        binding.btnStartRunning.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_runningSetting)
        }


        // '전체보기' 버튼 클릭 시 인텐트를 통해 CourseTotalActivity로 이동
        binding.btnSeeAll.setOnClickListener {
            val intent = android.content.Intent(requireContext(), CourseTotalActivity::class.java)
            startActivity(intent)
        }
    }

    // 추천 코스 카드뷰
    private fun setupRecommendedRoutes() {
        recommendedRouteAdapter = RecommendedRouteAdapter()
        binding.rvRecommendedRoutes.apply {
            adapter = recommendedRouteAdapter
            // 가로(Horizontal) 스크롤 리스트 매니저 적용
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        }

        // 화면에 보여줄 임시 데이터 구성
        val dummyRoutes = listOf(
            RecommendedRoute(1, "한강 공원 순환코스", 5.2, "쉬움", "bc_hankang", "한강을 따라 달리는 쾌적한 코스", emptyList()),
            RecommendedRoute(2, "반포대교 달빛광장 코스", 7.8, "보통", "bc_banpo", "야경이 아름다운 반포대교 코스", emptyList())
        )
        recommendedRouteAdapter.submitList(dummyRoutes) // 어댑터에 데이터 전달
    }

    // 이번 주(월~일) 러닝 기록을 가져와 요약하는 함수
    private fun loadWeeklyData() {
        val ctx = context ?: return
        val db = BinGoDatabase.getDatabase(ctx)

        viewLifecycleOwner.lifecycleScope.launch {
            // 현재 시점 기준으로 이번 주 월요일 오전 00시 00분을 계산하는 로직
            val calendar = Calendar.getInstance(Locale.KOREA).apply {
                firstDayOfWeek = Calendar.MONDAY
                set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)

                // 만약 오늘이 월요일 이전(일요일 등)으로 계산되어 미래 날짜가 잡히면 일주일 뒤로 뺌
                if (timeInMillis > System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_YEAR, -7)
                }

                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            // 한 주의 시작 타임스탬프
            val startOfWeek = calendar.timeInMillis

            // 이번 주 일요일 23:59:59까지 (월요일부터 7일치)
            val endOfWeek = startOfWeek + (7 * 24 * 60 * 60 * 1000L) - 1

            val weeklyRecords = withContext(Dispatchers.IO) {
                // 특정 날짜 범위의 기록만 DB에서 동기적으로 조회
                val data = db.runningRecordDao().getRecordsByDateRangeSync(startOfWeek, endOfWeek)
                android.util.Log.d("DB_CHECK", "범위: $startOfWeek ~ $endOfWeek | 가져온 개수: ${data.size}")
                data
            }

            // 프래그먼트가 살아있을 때만 UI 업데이트 실행
            if (isAdded && _binding != null) {
                updateUI(weeklyRecords)
            }
        }
    }

    // 가져온 일주일치 기록으로 평균 페이스/칼로리 및 그래프를 갱신하는 함수
    private fun updateUI(records: List<RunningRecord>) {
        val currentBinding = _binding ?: return
        if (!isAdded) return

        // 데이터가 없으면 0으로 계산, 있으면 평균값 계산
        val avgPace = if (records.isNotEmpty()) records.map { it.pace }.average() else 0.0
        val avgCalories = if (records.isNotEmpty()) records.map { it.calories }.average() else 0.0

        // 평균 수치들을 화면에 텍스트로 반영
        currentBinding.tvAvgPace.text = String.format("%d'%02d\"", avgPace.toInt(), ((avgPace - avgPace.toInt()) * 60).toInt())
        currentBinding.tvAvgCalories.text = "${avgCalories.toInt()} kcal"

        // 주간 데이터 0으로 초기화 후 합산
        val weeklyDistances = FloatArray(7) { 0f }
        val calendar = Calendar.getInstance()

        // 일주일 기록을 순회하며 요일별 인덱스에 거리 데이터 누적
        records.forEach { record ->
            calendar.timeInMillis = record.date
            // 일요일(1) 기준을 월요일(0) 기준으로 변환하는 인덱싱
            val dayIndex = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
            if (dayIndex in 0..6) {
                weeklyDistances[dayIndex] += record.distance.toFloat()
            }
        }

        // 요일별 거리 리스트를 커스텀 그래프 뷰에 전달하여 시각화
        currentBinding.weeklyChartView.setData(weeklyDistances.toList())
    }

}