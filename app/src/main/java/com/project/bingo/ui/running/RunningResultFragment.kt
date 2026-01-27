package com.project.bingo.ui.running

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import com.project.bingo.R
import com.project.bingo.base.BaseFragment
import com.project.bingo.data.repository.RunningCourseStore
import com.project.bingo.databinding.FragmentRunningResultBinding
import java.text.SimpleDateFormat
import java.util.*

/**
 * RunningResultFragment.kt
 * 러닝 결과 화면 프래그먼트
 */
class RunningResultFragment : BaseFragment<FragmentRunningResultBinding>() {

    // 바인딩 객체 연결
    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentRunningResultBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 이전 화면(RunningFragment)에서 전달받은 운동 수치들 추출
        val distance = arguments?.getDouble("distance") ?: 0.0
        val duration = arguments?.getLong("duration") ?: 0L
        val pace = arguments?.getDouble("pace") ?: 0.0
        val location = arguments?.getString("location") ?: "서울시"
        val dateMillis = arguments?.getLong("date") ?: System.currentTimeMillis()

        binding.apply {
            // 1. 실제 날짜 반영하여 "2024년 1월 20일" 형식으로 표시
            val sdf = SimpleDateFormat("yyyy년 M월 d일", Locale.KOREA)
            tvHeaderDate.text = sdf.format(Date(dateMillis)) // fragment_running_result.xml의 ID 확인 필요

            //  2. 지오코딩으로 찾은 실제 구 단위 위치명을 코스 이름 영역에 반영
            tvCourseName.text = "$location"

            // 3. 선택 단계에서 미리 생성하여 보관 중인 루트 미리보기 이미지 반영
            RunningCourseStore.selectedCourseBitmap?.let {
                ivResultMap.setImageBitmap(it)
            }

            // 기존 데이터 세팅 및 표시 형식 변환
            tvDistance.text = String.format("%.2f", distance) // 거리 소수점 2자리
            tvDuration.text = String.format("%02d:%02d", (duration % 3600) / 60, duration % 60)
            val pMin = pace.toInt()
            val pSec = ((pace - pMin) * 60).toInt()
            tvPace.text = String.format("%d'%02d", pMin, pSec) // 페이스 X'YY"
            tvCalories.text = (distance * 65).toInt().toString() // 칼로리 계산
        }

        // 확인 버튼 클릭 시 메모리 정리 후 홈 화면으로 이동
        binding.btnConfirm.setOnClickListener {
            // 4. 확인 버튼 누르고 나갈 때 지도 이미지 메모리에서 삭제 처리
            RunningCourseStore.clear()
            findNavController().navigate(R.id.action_result_to_home)
        }

        // 5. 스마트폰 시스템 뒤로가기 버튼 클릭 시에도 동일하게 메모리 정리 후 홈으로 이동
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                RunningCourseStore.clear()
                val navOptions = androidx.navigation.NavOptions.Builder()
                    .setPopUpTo(R.id.homeFragment, true)
                    .build()
                findNavController().navigate(R.id.homeFragment, null, navOptions)
            }
        })
    }
}