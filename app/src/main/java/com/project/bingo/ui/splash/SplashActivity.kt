package com.project.bingo.ui.splash

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.project.bingo.databinding.ActivitySplashBinding
import com.project.bingo.ui.auth.AuthActivity

/**
 * Splash Activity
 * - 앱 시작 시 표시되는 스플래시 화면
 * - 2초 후 Auth Activity(로그인 화면)로 이동
 */
@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivitySplashBinding  // 뷰 바인딩 객체
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 2초 후 Auth Activity로 이동
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, AuthActivity::class.java))
            finish() // 전환 후 현재 스플래시 화면은 즉시 종료
        }, 2000)
    }
}
