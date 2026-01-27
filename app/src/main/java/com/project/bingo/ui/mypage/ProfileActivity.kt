package com.project.bingo.ui.mypage

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.project.bingo.data.local.BinGoDatabase
import com.project.bingo.data.local.entity.User
import com.project.bingo.databinding.ActivityProfileBinding
import com.project.bingo.ui.auth.AuthActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ProfileActivity.kt
 * 사용자의 상세 프로필(아이디, 이름)과 전체 앱 이용 기간 동안의 누적 거리 및 시간을 확인
 * 비밀번호 확인, 로그아웃 기능 추가
 */
class ProfileActivity : AppCompatActivity() {
    private lateinit var binding: ActivityProfileBinding // 뷰 바인딩 객체
    private var currentUser: User? = null // 현재 유저 정보를 보관할 변수

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadData() // 데이터 로드 실행

        // 상단 뒤로가기 버튼 클릭 시 화면 닫기
        binding.btnBack.setOnClickListener { finish() }

        // 비밀번호 확인 기능 (팝업창으로 비밀번호 노출)
        binding.btnCheckPw.setOnClickListener {
            currentUser?.let { user ->
                val builder = AlertDialog.Builder(
                    this,
                    android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
                )
                builder.setTitle("비밀번호 확인")
                builder.setMessage("회원님의 비밀번호는 [ ${user.userPw} ] 입니다.")
                builder.setPositiveButton("확인") { dialog, _ -> dialog.dismiss() }

                val dialog = builder.create()
                dialog.window?.setGravity(android.view.Gravity.CENTER) // 화면 중앙 정렬
                dialog.show()
                // 확인 버튼 크기, 색상 설정
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
                    textSize = 17f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    setTextColor(android.graphics.Color.parseColor("#FF000000"))
                }
            }
        }

        // 로그아웃 기능 (세션 비우고 로그인 화면으로 퇴출)
        binding.btnLogout.setOnClickListener {
            val builder =
                AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
            builder.setTitle("로그아웃")
            builder.setMessage("로그아웃 하시겠습니까?")

            // '예' 선택 시 로그인 화면(AuthActivity)으로 이동하고 스택 초기화
            builder.setPositiveButton("예") { dialog, _ ->
                dialog.dismiss()
                val intent = Intent(this, AuthActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }

            // '아니오' 선택 시 팝업만 닫기
            builder.setNegativeButton("아니오") { dialog, _ ->
                dialog.dismiss()
            }

            val dialog = builder.create()

            // 다이얼로그를 화면 정중앙에 배치
            dialog.window?.setGravity(android.view.Gravity.CENTER)

            dialog.show()

            val btnYes = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val btnNo = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)

            // 예/아니오 버튼 크기, 색상 설정
            btnYes?.apply {
                textSize = 17f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(android.graphics.Color.parseColor("#FF000000"))
                setPadding(32, 0, 32, 0) // 터치 영역 확보를 위한 패딩
            }
            btnNo?.apply {
                textSize = 17f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(android.graphics.Color.parseColor("#FF000000"))
                setPadding(32, 0, 32, 0) // 터치 영역 확보를 위한 패딩
            }
        }
    }

    // 로컬 DB에서 유저 정보와 전체 누적 기록 데이터를 비동기로 읽어옴
    private fun loadData() {
        lifecycleScope.launch {
            val db = BinGoDatabase.getDatabase(this@ProfileActivity)
            val user = withContext(Dispatchers.IO) { db.userDao().getOneUser() } // 유저 정보
            val allRecords = withContext(Dispatchers.IO) { db.runningRecordDao().getAllRecordsSync() }  // 전체 기록

            currentUser = user
            user?.let {
                // UI 텍스트뷰에 유저 정보 반영
                binding.tvDisplayName.text = it.userName
                binding.tvUserId.text = it.userId
            }

            // 모든 기록을 합산하여 누적 거리 및 시간 계산
            val totalDist = allRecords.sumOf { it.distance }
            val totalSec = allRecords.sumOf { it.duration }

            // 계산된 누적 수치들을 화면에 표시
            binding.tvTotalDist.text = String.format("%.1f km", totalDist)
            binding.tvTotalTime.text = formatTotalTime(totalSec)
        }
    }

    // 초(seconds) 단위를 HH:MM:SS 시:분:초 형태의 문자열로 변환
    private fun formatTotalTime(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return String.format("%02d:%02d:%02d", h, m, s)
    }
}