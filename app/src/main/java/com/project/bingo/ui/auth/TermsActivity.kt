package com.project.bingo.ui.auth

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.project.bingo.R
import android.widget.Button

/**
 * TermsActivity.kt
 * 앱 서비스 이용약관 페이지
 */

class TermsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 레이아웃 파일과 연결
        setContentView(R.layout.activity_terms)

        // 레이아웃의 닫기 버튼을 찾아서 클릭 리스너 연결
        findViewById<Button>(R.id.btn_close).setOnClickListener {
            finish() // 현재 화면을 닫고 이전 화면(AuthActivity)으로 복귀
        }
    }
}