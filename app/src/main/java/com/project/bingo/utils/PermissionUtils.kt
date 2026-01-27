package com.project.bingo.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * PermissionUtils.kt
 * 앱 구동에 필요한 각종 권한(GPS 등)의 허용 여부를 체크하는 도구 파일
 */
object PermissionUtils {

    // 이 앱에서 필수로 요구하는 위치 권한 리스트 정의
    val LOCATION_PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION, // 정밀 위치 정보
        Manifest.permission.ACCESS_COARSE_LOCATION // 대략적 위치 정보
    )


    // 현재 기기에서 위에서 정의한 모든 위치 권한이 허용되어 있는지 확인하는 함수
    fun hasLocationPermission(context: Context): Boolean {
        return LOCATION_PERMISSIONS.all {
            // 모든 항목의 체크 결과가 '허용됨(GRANTED)'일 때만 최종적으로 참(True) 반환
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }
}

