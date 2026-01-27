package com.project.bingo.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.project.bingo.data.local.BinGoDatabase
import com.project.bingo.databinding.ActivityAuthBinding
import com.project.bingo.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * AuthActivity.kt
 * 로그인 화면을 관리하며, 입력된 정보를 로컬 DB와 비교하여 로그인 성공 여부를 판단
 */
class AuthActivity : AppCompatActivity() {

    // 뷰 바인딩 객체
    private lateinit var binding: ActivityAuthBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // XML 레이아웃 연결
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 버튼 클릭 등의 리스너 설정 함수 호출
        setupListeners()
    }

    // 각종 클릭 이벤트 리스너 설정
    private fun setupListeners() {
        // 이용약관 [자세히 보기] 텍스트 클릭 시 TermsActivity로 이동
        binding.tvViewTerms.setOnClickListener {
            val intent = Intent(this, TermsActivity::class.java)
            startActivity(intent)
        }

        // [로그인] 버튼 클릭 시 로직 처리
        binding.btnLogin.setOnClickListener {
            // 클릭 리스너 내부에서 최신 id, pw 값을 다시 읽어옴 (공백 제거)
            val id = binding.editId.text.toString().trim()
            val pw = binding.editPw.text.toString().trim()

            // 빈칸 검사
            if (id.isEmpty() || pw.isEmpty()) {
                Toast.makeText(this, "아이디와 비밀번호를 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 약관 동의 검사
            if (!binding.checkboxTerms.isChecked) {
                Toast.makeText(this, "이용약관에 동의해야 로그인 가능합니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 실제 DB 조회 및 로그인 수행
            performLogin(id, pw)
        }




    }

    // 로컬 DB를 조회하여 로그인을 수행하는 비동기 함수
    private fun performLogin(id: String, pw: String) {
        // Coroutine을 사용하여 백그라운드에서 DB 조회
        lifecycleScope.launch {
            // DB 인스턴스 가져오기
            val database = BinGoDatabase.getDatabase(this@AuthActivity)

            // IO 스레드에서 유저 정보 조회
            val user = withContext(Dispatchers.IO) {
                database.userDao().login(id, pw)
            }

            if (user != null) {
                // DB에 일치하는 유저가 있는 경우
                Toast.makeText(this@AuthActivity, "${user.userName}님, 환영합니다!", Toast.LENGTH_SHORT).show()
                navigateToMain() // 메인 화면으로 이동
            } else {
                // 일치하는 유저가 없는 경우
                Toast.makeText(this@AuthActivity, "아이디 또는 비밀번호가 틀렸습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 메인 화면으로 이동하고 현재 로그인 화면을 종료
    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java)) // MainActivity 실행
        finish()  // 현재 액티비티 파괴
    }
}