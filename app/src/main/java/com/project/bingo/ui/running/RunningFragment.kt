package com.project.bingo.ui.running

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.gms.location.*
import com.kakao.vectormap.*
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.Label
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.LabelStyles
import com.kakao.vectormap.route.RouteLineOptions
import com.kakao.vectormap.route.RouteLineSegment
import com.kakao.vectormap.route.RouteLineStyle
import com.kakao.vectormap.route.RouteLineStyles
import com.project.bingo.R
import com.project.bingo.base.BaseFragment
import com.project.bingo.data.repository.RunningCourseStore
import com.project.bingo.databinding.FragmentRunningBinding

/**
 * RunningFragment.kt
 * 러닝 화면 프래그먼트
 * 사용자 현재 위치와 선택한 러닝 경로를 지도에 띄우고, 타이머/러닝 길이/페이스 실제 수치를 수집 혹은 계산
 */
class RunningFragment : BaseFragment<FragmentRunningBinding>() {

    private val viewModel: RunningViewModel by viewModels() // 러닝 데이터 관리 뷰모델
    private var kakaoMap: KakaoMap? = null // 지도 객체 보관용
    private var userLabel: Label? = null // 내 위치 마커 객체 보관용
    private var isFirstLocation = true // 처음 위치 잡았을 때 카메라 이동을 위한 플래그

    private lateinit var fusedLocationClient: FusedLocationProviderClient // 위치 클라이언트
    private val locationCallback = object : LocationCallback() { // 위치 갱신 콜백 정의
        override fun onLocationResult(locationResult: LocationResult) {
            for (location in locationResult.locations) {
                val latLng = LatLng.from(location.latitude, location.longitude)
                updateUserLocationMarker(latLng) // 내 위치 마커 갱신

                if (isFirstLocation) { // 처음에는 내 위치로 지도 중앙 이동
                    kakaoMap?.moveCamera(CameraUpdateFactory.newCenterPosition(latLng, 16))
                    isFirstLocation = false
                }
                viewModel.updateLocation(location) // 뷰모델에 위치 데이터 전달하여 거리 계산
            }
        }
    }

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentRunningBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity()) // 클라이언트 초기화

        binding.mapView.start(object : MapLifeCycleCallback() {
            override fun onMapDestroy() {}
            override fun onMapError(error: Exception) {}
        }, object : KakaoMapReadyCallback() {
            override fun onMapReady(map: KakaoMap) {  // 지도가 준비되었을 때
                kakaoMap = map
                drawSelectedRoute() // 선택된 코스 경로와 시설물 마커 그리기 로직 호출
                checkLocationPermission() // 위치 권한 확인 후 위치 추적 시작
            }
        })

        setupObservers() // 데이터 변화 감찰 설정
        setupButtons() // 버튼 클릭 이벤트 설정

        // 러닝 중 뒤로가기를 누를 경우 홈으로 이동하며 백스택 정리
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            findNavController().navigate(
                R.id.homeFragment,
                null,
                androidx.navigation.NavOptions.Builder()
                    .setPopUpTo(R.id.homeFragment, true)
                    .build()
            )
        }
    }


    // 선택된 경로와 3개의 경유지(화장실, 쓰레기통, 편의점)를 지도에 표시
    private fun drawSelectedRoute() {
        val course = RunningCourseStore.selectedCourse ?: return // 선택 코스 없으면 종료
        val map = kakaoMap ?: return // 지도 객체 없으면 종료

        val coords = course.geojson?.features?.firstOrNull()?.geometry?.coordinates ?: return  // 좌표 리스트 추출
        val pathPoints = coords.map { LatLng.from(it[1], it[0]) } // 카카오 LatLng 형태로 변환

        // 1. 경로선 테두리 (회색)
        val backgroundStyle = RouteLineStyle.from(15f, Color.parseColor("#CCCCCC"))
        val backgroundSegment = RouteLineSegment.from(pathPoints).setStyles(RouteLineStyles.from(backgroundStyle))
        map.routeLineManager?.layer?.addRouteLine(RouteLineOptions.from(backgroundSegment))

        // 2. 메인 경로선 (하늘색)
        val mainLineStyle = RouteLineStyle.from(10f, Color.parseColor("#2596B2"))
        val mainSegment = RouteLineSegment.from(pathPoints).setStyles(RouteLineStyles.from(mainLineStyle))
        map.routeLineManager?.layer?.addRouteLine(RouteLineOptions.from(mainSegment))


        // 3. 경유지 마커 표시 (최대 3개 대응)
        course.waypoints.forEach { wp ->
            // 타입별 마커 리소스 매핑 (화장실, 쓰레기통, 편의점)
            val markerRes = when {
                wp.type.contains("bin", true) -> R.drawable.ic_bin_marker_map
                wp.type.contains("toilet", true) -> R.drawable.ic_toilet_marker_map
                wp.type.contains("store", true) -> R.drawable.ic_store_marker_map
                else -> R.drawable.ic_bin_marker_map
            }

            val wpStyles = LabelStyles.from(LabelStyle.from(markerRes))  // 라벨 스타일 생성
            val wpPoint = LatLng.from(wp.lat, wp.lng) // 마커 위치 좌표

            // 지도 상의 라벨 레이어에 마커 추가
            map.labelManager?.layer?.addLabel(LabelOptions.from(wpPoint).setStyles(wpStyles))
        }
    }

    // 내 위치를 나타내는 빨간색 마커를 관리하는 함수
    private fun updateUserLocationMarker(latLng: LatLng) {
        val map = kakaoMap ?: return
        val layer = map.labelManager?.layer

        if (userLabel == null) { // 마커가 처음이면 새로 생성
            val styles = LabelStyles.from(LabelStyle.from(R.drawable.ic_marker_red_map))
            userLabel = layer?.addLabel(LabelOptions.from(latLng).setStyles(styles))
        } else { // 이미 있으면 위치만 이동
            userLabel?.moveTo(latLng)
        }
    }

    // 위치 권한 허용 상태를 확인하는 함수
    private fun checkLocationPermission() {
        val permissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (permissions.all { ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED }) {
            startLocationUpdates()  // 권한이 있으면 위치 업데이트 시작
        } else {
            requestPermissionLauncher.launch(permissions)  // 권한 없으면 요청 창 띄우기
        }
    }

    // 권한 요청 결과를 처리하는 런처
    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        if (results.values.all { it }) startLocationUpdates()
    }

    // 실제로 위치 추적 및 운동 타이머를 시작하는 함수
    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000).build() // 2초 간격 갱신
        fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        viewModel.startTimer() // 뷰모델 타이머 시작
    }

    // 뷰모델의 데이터(시간, 거리, 페이스) 변화를 관찰하여 UI 갱신
    private fun setupObservers() {
        viewModel.timeSeconds.observe(viewLifecycleOwner) { s ->
            binding.tvTime.text = String.format("%02d:%02d", (s % 3600) / 60, s % 60)
        }
        viewModel.distanceKm.observe(viewLifecycleOwner) { d ->
            binding.tvDistance.text = String.format("%.2f", d)
        }
        viewModel.pace.observe(viewLifecycleOwner) { p ->
            val min = p.toInt()
            val sec = ((p - min) * 60).toInt()
            binding.tvPace.text = String.format("%d'%02d\"", min, sec)
        }
        viewModel.isPaused.observe(viewLifecycleOwner) { paused ->
            // fragment_running.xml에서 추가한 ID를 통해 뷰를 직접 제어합니다.
            if (paused) {
                // 1. 정지 상태일 때: 문구는 '러닝 재개', 아이콘은 Play 버튼으로 변경
                binding.tvPauseText.text = "러닝 재개"
            } else {
                // 2. 실행 상태일 때: 문구는 '일시 정지', 아이콘은 Pause 버튼으로 변경
                binding.tvPauseText.text = "일시 정지"
            }
        }

    }

    // 일시정지 및 중지 버튼 이벤트 설정
    private fun setupButtons() {
        binding.btnPause.setOnClickListener { viewModel.togglePause() } // 일시정지/재개
        binding.btnStop.setOnClickListener { // 중지 버튼 클릭 시 결과 저장 프로세스 시작
            val geocoder = android.location.Geocoder(requireContext(), java.util.Locale.KOREAN)
            val startLatLng = RunningCourseStore.selectedCourse?.geojson?.features?.firstOrNull()?.geometry?.coordinates?.firstOrNull()

            // 현재 위치의 동네 주소명을 가져오는 로직
            val locationName = try {
                val addresses = if (startLatLng != null) {
                    geocoder.getFromLocation(startLatLng[1], startLatLng[0], 1)
                } else {
                    viewModel.lastLocation?.let { geocoder.getFromLocation(it.latitude, it.longitude, 1) }
                }

                val address = addresses?.firstOrNull()
                if (address != null) {
                    val fullLine = address.getAddressLine(0) ?: ""
                    // "서울시 노원구" 와 같은 형식만 추출하기 위한 정규식
                    val match = Regex("(\\S+[시|도])\\s+(\\S+[구|군|시])").find(fullLine)
                    match?.value ?: address.adminArea
                } else {
                    "알 수 없는 위치"
                }
            } catch (e: Exception) {
                "알 수 없는 위치"
            }

            // DB에 기록 저장 후 결과 화면으로 데이터 전달하며 이동
            viewModel.saveRecord { recordId ->
                val bundle = Bundle().apply {
                    putDouble("distance", viewModel.distanceKm.value ?: 0.0)
                    putLong("duration", viewModel.timeSeconds.value ?: 0L)
                    putDouble("pace", viewModel.pace.value ?: 0.0)
                    putString("location", locationName)
                    putLong("date", System.currentTimeMillis())
                }
                findNavController().navigate(R.id.action_running_to_result, bundle)
            }
        }
    }

    // 화면 종료 시 위치 갱신 중단
    override fun onDestroyView() {
        super.onDestroyView()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}