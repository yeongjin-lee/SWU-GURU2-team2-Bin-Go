package com.project.bingo.ui.mypage

import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationServices
import com.project.bingo.base.BaseFragment
import com.project.bingo.data.local.BinGoDatabase
import com.project.bingo.databinding.FragmentMypageBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import android.content.Intent
import com.project.bingo.ui.auth.TermsActivity

/**
 * MyPageFragment.kt
 * 사용자의 운동 분석 결과(페이스 변화 등)와 현재 위치 기반 주소 표시
 * 프로필 관리/이용약관 등의 설정 진입점 역할 담당
 */
class MyPageFragment : BaseFragment<FragmentMypageBinding>() {

    // 현재 기기의 GPS 위치 정보를 가져오기 위한 클라이언트
    private val fusedLocationClient by lazy {
        LocationServices.getFusedLocationProviderClient(requireActivity())
    }

    // 위치 권한 요청 후 결과를 처리하는 리스너 설정
    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // 권한이 승인되면 실시간 동네 이름을 가져옴
        if (isGranted) getCurrentLocationName()
    }

    // 바인딩 객체 연결
    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentMypageBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadUserRunningStats()   // 주간 및 월간 성과 지표 로드
        checkLocationPermission() // 위치 권한 상태 체크
        loadUserInfo()           // 유저 이름 불러오기


        // '이용약관' 클릭 시 약관 액티비티로 이동
        binding.btnGoTerms.setOnClickListener {
            val intent = Intent(requireContext(), TermsActivity::class.java)
            startActivity(intent)
        }

        // '내 프로필 관리' 클릭 시 ProfileActivity로 이동
        binding.btnEditProfile.setOnClickListener {
            val intent = Intent(requireContext(), ProfileActivity::class.java)
            startActivity(intent)
        }
    }

    // DB에서 현재 로그인한 유저의 이름을 가져와 상단에 표시
    private fun loadUserInfo() {
        viewLifecycleOwner.lifecycleScope.launch {
            val user = withContext(Dispatchers.IO) {
                BinGoDatabase.getDatabase(requireContext()).userDao().getOneUser()
            }

            user?.let {
                // 가져온 유저명을 UI 텍스트뷰에 반영
                binding.tvUserName.text = it.userName
            }
        }
    }

    // 주간 누적 거리, 평균 페이스 및 페이스 향상 지표 등을 계산
    private fun loadUserRunningStats() {
        val ctx = context ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            val db = BinGoDatabase.getDatabase(ctx)

            // 이번 주 월요일 오전 00시 정각 시점 계산
            val calendar = Calendar.getInstance(Locale.KOREA).apply {
                set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                // 오늘이 월요일 이전이라 다음주가 잡히는 캘린더 예외 처리
                if (get(Calendar.DAY_OF_YEAR) > Calendar.getInstance().get(Calendar.DAY_OF_YEAR)) {
                    add(Calendar.DAY_OF_YEAR, -7) // 만약 일요일이라 다음주 월요일로 잡히는 버그 방지
                }
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startOfWeek = calendar.timeInMillis


            // 이번 주 일요일 23:59:59까지
            calendar.add(Calendar.DAY_OF_YEAR, 6)
            calendar.set(Calendar.HOUR_OF_DAY, 23)
            calendar.set(Calendar.MINUTE, 59)
            calendar.set(Calendar.SECOND, 59)
            val endOfWeek = calendar.timeInMillis

            val records = withContext(Dispatchers.IO) {
                // 특정 주간 날짜 범위 내의 기록만 필터링 조회
                db.runningRecordDao().getRecordsByDateRangeSync(startOfWeek, endOfWeek)
            }

            if (_binding != null && isAdded) {
                // 주간 기록 합산 및 평균 페이스 계산
                val totalDist = records.sumOf { it.distance }
                val avgPace = if (records.isNotEmpty()) records.map { it.pace }.average() else 0.0

                binding.apply {
                    tvWeeklyDistValue.text = String.format("%.1f", totalDist) // 주간 총 거리
                    tvAvgPaceValue.text = String.format("%d'%02d\"", avgPace.toInt(), ((avgPace - avgPace.toInt()) * 60).toInt()) // 주간 평균 페이스
                    tvRunCountValue.text = records.size.toString() // 주간 달리기 횟수

                    // 지난달 vs 이번달 페이스 변화량 분석 및 표시
                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                        val allRecords = db.runningRecordDao().getAllRecordsSync()
                        if (allRecords.isNotEmpty()) {
                            val now = Calendar.getInstance()

                            // 이번 달 평균 페이스
                            val thisMonth = allRecords.filter {
                                val c = Calendar.getInstance().apply { timeInMillis = it.date }
                                c.get(Calendar.MONTH) == now.get(Calendar.MONTH) && c.get(Calendar.YEAR) == now.get(Calendar.YEAR)
                            }.map { it.pace }.average()

                            // 지난 달 평균 페이스
                            val lastMonthCal = (now.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
                            val lastMonth = allRecords.filter {
                                val c = Calendar.getInstance().apply { timeInMillis = it.date }
                                c.get(Calendar.MONTH) == lastMonthCal.get(Calendar.MONTH) && c.get(Calendar.YEAR) == lastMonthCal.get(Calendar.YEAR)
                            }.map { it.pace }.average()

                            withContext(Dispatchers.Main) {
                                if (!lastMonth.isNaN() && !thisMonth.isNaN()) {
                                    val diff = lastMonth - thisMonth
                                    val status = if (diff >= 0) "-" else "+"
                                    val absDiff = Math.abs(diff)
                                    val m = absDiff.toInt()
                                    val s = ((absDiff - m) * 60).toInt()

                                    // 예시 (지난달 - 0'12")
                                    tvPaceChange.text = String.format("지난달 %s %d'%02d\"", status, m, s)
                                } else {
                                    tvPaceChange.text = "페이스 데이터 분석 중"
                                }
                            }

                            // 주간 평균 횟수 계산 (전체 기간 기준)
                            val firstDate = allRecords.minByOrNull { it.date }?.date ?: System.currentTimeMillis()
                            val diffWeeks = ((System.currentTimeMillis() - firstDate) / (1000 * 60 * 60 * 24 * 7.0)).let {
                                if (it < 1.0) 1.0 else it
                            }
                            val avgCountPerWeek = allRecords.size / diffWeeks

                            withContext(Dispatchers.Main) {
                                tvAvgCount.text = String.format("주 평균 %.1f회", avgCountPerWeek)
                            }
                        }
                    }
                }
            }
        }
    }



    // 위치 권한 승인 여부를 확인하고 필요 시 요청
    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            getCurrentLocationName() // 이미 허용되었으면 주소 획득 시작
        } else {
            locationPermissionRequest.launch(Manifest.permission.ACCESS_FINE_LOCATION) // 권한 요청 창 띄우기
        }
    }

    // GPS 좌표를 받아와 Geocoder를 통해 한국어 주소(동네명)로 변환
    private fun getCurrentLocationName() {
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (isAdded && view != null && location != null) {
                    val geocoder = Geocoder(requireContext(), Locale.KOREA)
                    val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                    if (addresses?.isNotEmpty() == true) {
                        val city = addresses[0].locality ?: addresses[0].adminArea ?: ""  // 도시 또는 동 이름
                        val country = addresses[0].countryName ?: "" // 국가 이름
                        binding.tvUserLocation.text = "$city, $country" // UI에 주소 표시
                    }
                }
            }
        } catch (e: SecurityException) {
            // 위치 정보 접근 불가 시 기본값 표시
            if (isAdded && view != null) binding.tvUserLocation.text = "서울, 대한민국"
        }
    }
}
