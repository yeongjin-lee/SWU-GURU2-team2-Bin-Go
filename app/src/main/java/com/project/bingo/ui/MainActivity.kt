package com.project.bingo.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.project.bingo.R
import com.project.bingo.databinding.ActivityMainBinding
import java.security.MessageDigest
import android.util.Base64
import android.util.Log
import android.os.Build

/**
 * MainActivity
 * 앱의 중심 뼈대가 되는 액티비티
 * Navigation Component의 호스트
 * Bottom Navigation 탭 메뉴 관리
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding // 메인 레이아웃 바인딩

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // --- 카카오 지도 인증용 해시키 추출 로직 (로그 확인용) ---
        // 팀원들 해시키 추출 위해 사용했던 코드(심사위원님들께는 해당 X)
        try {
            val packageName = packageName
            val packageManager = packageManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // 앱의 서명 정보를 가져옴
                val info = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                val signatures = info.signingInfo?.getApkContentsSigners()

                signatures?.forEach { signature ->
                    val md = MessageDigest.getInstance("SHA")
                    md.update(signature.toByteArray())
                    val keyHash = Base64.encodeToString(md.digest(), Base64.DEFAULT)
                    Log.d("KeyHash", "내 해시키 (API 28+): $keyHash") // 로그캣에서 확인 가능
                }
            }
        } catch (e: Exception) {
            Log.e("KeyHash", "해시키 추출 실패", e)
        }
        // ------------------------------------------

        setupNavigation() // 네비게이션 초기 설정 실행

        // 앱이 처음 켜질 때 Intent 확인
        checkNavigationIntent(intent)
    }


    // 외부에서 액티비티가 실행 중일 때 새로운 인텐트가 전달되는 경우 호출
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent) // 새로 받은 인텐트로 교체
        checkNavigationIntent(intent) // 네비게이션 분기 처리 실행
    }


    // 인텐트 데이터에 따라 특정 화면(예: 코스 선택)으로 즉시 이동시키는 함수
    private fun checkNavigationIntent(intent: Intent?) {
        // "navigate_to" 키값이 "routeSelect"인 경우 코스 선택 화면으로 전환
        val destination = intent?.getStringExtra("navigate_to")
        if (destination == "routeSelect") {
            // 레이아웃의 네비게이션 호스트 찾기
            val navHostFragment = supportFragmentManager
                .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
            val navController = navHostFragment.navController

            // 코스 선택 프래그먼트로 이동
            navController.navigate(R.id.routeSelectFragment)
        }
    }

    // 하단 탭 메뉴(Bottom Navigation)와 화면 전환 도구를 연결하는 핵심 함수
    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        // 1. 하단 바와 네비게이션 컨트롤러 기본 연결
        binding.bottomNavigation.setupWithNavController(navController)

        // 2. 하단 바 메뉴 선택 시 동작을 커스텀하게 제어
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            val destinationId = item.itemId

            // 현재 화면과 누른 버튼이 같으면 무시 (중복 생성 방지)
            if (navController.currentDestination?.id == destinationId) {
                return@setOnItemSelectedListener true
            }

            val builder = androidx.navigation.NavOptions.Builder()
                .setLaunchSingleTop(true)
                .setRestoreState(true)

            if (destinationId == R.id.homeFragment) {
                // 홈 탭을 누를 경우 백스택을 모두 비우고 홈으로 깨끗하게 이동
                builder.setPopUpTo(R.id.nav_graph, true)
            } else {
                // 다른 탭 선택 시 홈까지만 백스택을 유지하고 이동
                builder.setPopUpTo(navController.graph.startDestinationId, false)
            }

            val options = builder.build()

            try {
                // 목적지로 이동
                navController.navigate(destinationId, null, options)
                true
            } catch (e: Exception) {
                false
            }
        }

        // 3. 화면의 목적지에 따라 BottomNavigation 바를 보여주거나 숨김 처리
        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.homeFragment,
                R.id.runningSettingFragment,
                R.id.calendarFragment,
                R.id.myPageFragment -> {
                    binding.bottomNavigation.visibility = View.VISIBLE
                }
                else -> {
                    // 러닝 중이거나 결과 페이지에서는 숨김
                    binding.bottomNavigation.visibility = View.GONE
                }
            }
        }
    }
}
