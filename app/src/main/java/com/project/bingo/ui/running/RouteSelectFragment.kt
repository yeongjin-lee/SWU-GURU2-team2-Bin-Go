package com.project.bingo.ui.running

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.project.bingo.R
import com.project.bingo.base.BaseFragment
import com.project.bingo.data.repository.RunningCourseStore
import com.project.bingo.databinding.FragmentRouteSelectBinding
import com.project.bingo.utils.GeoJsonBitmapRenderer
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * RouteSelectFragment.kt
 * 사용자가 선택한 옵션에 따른 러닝 추천 코스 선택 화면
 * **/
class RouteSelectFragment : BaseFragment<FragmentRouteSelectBinding>() {

    // 바인딩 객체 생성
    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentRouteSelectBinding.inflate(inflater, container, false)

    // 뷰 생성 후 로직 실행
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 전역 저장소에서 서버가 보내준 코스 목록 가져오기
        val courses = RunningCourseStore.courses
        if (courses.isEmpty()) { // 코스가 비어있을 경우 예외 처리
            Toast.makeText(requireContext(), "추천 코스가 없어요!", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
            return
        }

        // 비동기 코루틴으로 안전하게 썸네일 비트맵 이미지 생성
        viewLifecycleOwner.lifecycleScope.launch {
            val uiItems = courses.mapIndexed { idx, course ->
                val distanceKm = (course.meta.totalDistanceM / 1000.0 * 10).roundToInt() / 10.0
                //  한 루트 내의 경유지 리스트(waypoints) 개수를 확인
                val waypoints = course.waypoints
                val totalWaypoints = waypoints.size // 서버에서 3개를 보내주면 3이 됩니다.

                val toilets = course.waypoints.count { it.type.trim().contains("toilet", true) }
                val bins = course.waypoints.count { it.type.trim().contains("bin", true) }
                val stores = course.waypoints.count { it.type.contains("store", true) }

                // 실제 GeoJSON 데이터를 바탕으로 미니 지도 이미지 렌더링
                val thumbnail = GeoJsonBitmapRenderer.renderFullRoute(
                    requireContext(),
                    course.geojson,
                    course.waypoints
                )

                // 어댑터에 넘겨줄 UI용 데이터 객체 생성
                RouteItem(idx, "${distanceKm}km", totalWaypoints,toilets, bins, stores, thumbnail)
            }

            // 리사이클러뷰 설정
            binding.rvRoutes.apply {
                layoutManager = LinearLayoutManager(requireContext()) // 세로 리스트 매니저
                adapter = RouteAdapter(uiItems) { selected -> // 어댑터 연결 및 클릭 이벤트 정의
                    val selectedCourse = courses[selected.courseIndex]
                    RunningCourseStore.selectedCourse = selectedCourse // 선택된 코스 정보 저장

                    // 선택된 카드의 비트맵 이미지를 결과 화면에서 쓰기 위해 Store에 임시 보존
                    RunningCourseStore.selectedCourseBitmap = selected.thumbnail

                    // 실제 러닝 트래킹 화면으로 이동
                    findNavController().navigate(R.id.action_routeSelect_to_running)
                }
            }
        }

        // 1. 화면 상단 뒤로가기 화살표 버튼 클릭 시 홈 화면으로 이동
        binding.btnBack.setOnClickListener {
            val navOptions = androidx.navigation.NavOptions.Builder()
                .setPopUpTo(R.id.homeFragment, true) // 이전 기록 싹 지우기
                .build()
            findNavController().navigate(R.id.homeFragment, null, navOptions)
        }

        // 2. 스마트폰 자체 시스템 뒤로가기 버튼 클릭 시에도 홈으로 가도록 설정
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val navOptions = androidx.navigation.NavOptions.Builder()
                    .setPopUpTo(R.id.homeFragment, true)
                    .build()
                findNavController().navigate(R.id.homeFragment, null, navOptions)
            }
        })
    }
}