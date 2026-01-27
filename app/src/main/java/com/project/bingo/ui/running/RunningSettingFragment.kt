package com.project.bingo.ui.running

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.forEach
import com.google.android.gms.location.LocationServices
import com.project.bingo.base.BaseFragment
import com.project.bingo.databinding.FragmentRunningSettingBinding
import com.project.bingo.ui.splash.RunningSplashActivity
import com.project.bingo.utils.PermissionUtils

/**
 * RunningSettingFragment.kt
 * 러닝 설정 화면 프래그먼트
 * 러닝 목표 거리와 경유하고 싶은 시설물 옵션을 선택
 */
class RunningSettingFragment : BaseFragment<FragmentRunningSettingBinding>() {

    // 기기 위치 정보를 가져오기 위한 클라이언트
    private val fusedLocationClient by lazy {
        LocationServices.getFusedLocationProviderClient(requireActivity())
    }

    // 위치 권한이 없을 때 시스템 권한 요청 팝업을 띄우고 결과를 처리하는 런처
    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) { // 허용되었다면 추천 생성 프로세스 시작
                startRecommendFlow()
            } else { // 거절되었을 때 알림
                Toast.makeText(requireContext(), "현재 위치 권한이 필요해요!", Toast.LENGTH_SHORT).show()
            }
        }

    // 사용자가 선택한 목표 수치들을 담을 변수들
    private var selectedTargetKm: Double = 3.0 // 목표 거리 (기본 3km)
    private var includeToilet: Boolean = true   // 화장실 경유 여부
    private var includeBin: Boolean = true      // 쓰레기통 경유 여부
    private var includeStore: Boolean = true    // 편의점 경유 여부

    // 바인딩 클래스 인플레이트
    override fun getViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentRunningSettingBinding {
        return FragmentRunningSettingBinding.inflate(inflater, container, false)
    }

    // 뷰 생성 후 초기화 작업 수행
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupView()  // 각종 버튼 리스너 및 초기 UI 상태 설정
    }

    // 화면 내의 클릭 이벤트들을 설정하는 핵심 함수
    private fun setupView() = with(binding) {
        // 거리 선택 버튼(3km, 5km, 7km, 10km) 설정 로직
        val distanceButtons = listOf(btnDist3, btnDist5, btnDist7, btnDist10)
        btnDist3.isSelected = true // 3km 버튼을 기본 선택 상태로 지정

        distanceButtons.forEach { btn ->
            btn.setOnClickListener { selectedView ->
                // 모든 버튼을 미선택 상태로 비우기
                distanceButtons.forEach {
                    it.isSelected = false
                    (it as ViewGroup).forEach { child -> child.isSelected = false }
                }
                // 방금 누른 버튼만 선택 상태로 변경
                selectedView.isSelected = true
                (selectedView as ViewGroup).forEach { child -> child.isSelected = true }
            }
        }

        // 화장실 버튼은 선택 상태로 유지
        btnToilet.isSelected = true
        (btnToilet as ViewGroup).forEach { it.isSelected = true }


        // 편의점은 토글(OnOff) 버튼 방식으로 작동하도록 설정
        btnConvenience.setOnClickListener {
            val newState = !it.isSelected // 현재 상태 뒤집기
            it.isSelected = newState
            (it as ViewGroup).forEach { child -> child.isSelected = newState }
            includeStore = newState // 저장 변수 업데이트
        }

        // 추천 루트 선택하기 버튼
        btnRecommendRoute.setOnClickListener {
            // 현재 어떤 거리 버튼이 선택되어 있는지 판별하여 목표 거리 확정
            selectedTargetKm = when {
                btnDist3.isSelected -> 3.0
                btnDist5.isSelected -> 5.0
                btnDist7.isSelected -> 7.0
                btnDist10.isSelected -> 10.0
                else -> 3.0
            }

            // 화장실과 쓰레기통은 서비스 핵심 기능이므로 기본 포함으로 고정 전송
            includeToilet = true
            includeBin = true
            includeStore = btnConvenience.isSelected // 편의점은 토글 상태에 따름



            // 실제 위치 권한이 있는지 최종 체크 후 서버 요청 단계로 진입
            if (PermissionUtils.hasLocationPermission(requireContext())) {
                startRecommendFlow()
            } else {
                // 권한 없으면 시스템 권한창 요청
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }


    // 현재 GPS 위치를 확보하고 서버 통신 로딩 화면으로 전환하는 함수.
    @SuppressLint("MissingPermission")
    private fun startRecommendFlow() {
        if (!PermissionUtils.hasLocationPermission(requireContext())) return // 권한 최종 방어

        // 사용자에게 기본 포함 정책 안내
        Toast.makeText(requireContext(), "화장실과 쓰레기통은\n기본으로 포함하여 추천해줄게요!", Toast.LENGTH_SHORT).show()

        // 1. 우선 기기에 마지막으로 남은 캐시 위치를 시도함 (빠른 응답을 위해)
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                moveToSplash(location) // 위치 확보 성공 시 이동
            } else {
                // 2. 캐시가 없으면(null) 실시간으로 위치를 새로 요청 (에뮬레이터 초기 실행 대응용)
                val locationRequest = com.google.android.gms.location.LocationRequest.Builder(
                    com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, 1000
                ).setMaxUpdates(1).build()

                fusedLocationClient.getCurrentLocation(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { curLocation ->
                        if (curLocation != null) {
                            moveToSplash(curLocation)
                        } else {
                            Toast.makeText(requireContext(), "현재 위치를 가져오지 못했어요. GPS 설정을 확인해주세요!", Toast.LENGTH_SHORT).show()
                        }
                    }
            }
        }.addOnFailureListener { e ->
            Log.e("RunningSettingFragment", "위치 가져오기 실패", e)
        }
    }

    // 로딩 화면 액티비티(RunningSplashActivity)로 사용자의 설정을 전달하며 실행
    private fun moveToSplash(location: android.location.Location) {
        val intent = Intent(requireContext(), RunningSplashActivity::class.java).apply {
            putExtra(RunningSplashActivity.EXTRA_START_LAT, location.latitude) // 시작 위도
            putExtra(RunningSplashActivity.EXTRA_START_LNG, location.longitude) // 시작 경도
            putExtra(RunningSplashActivity.EXTRA_TARGET_KM, selectedTargetKm) // 목표 거리
            putExtra(RunningSplashActivity.EXTRA_INCLUDE_TOILET, includeToilet) // 화장실 옵션
            putExtra(RunningSplashActivity.EXTRA_INCLUDE_BIN, includeBin) // 쓰레기통 옵션
            putExtra(RunningSplashActivity.EXTRA_INCLUDE_STORE, includeStore) // 편의점 옵션
        }
        startActivity(intent)
    }
}