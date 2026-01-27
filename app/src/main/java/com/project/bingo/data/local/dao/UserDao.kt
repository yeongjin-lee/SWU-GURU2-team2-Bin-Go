package com.project.bingo.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.project.bingo.data.local.entity.User

/**
 * UserDao.kt
 * AuthActivity에서 유저 정보 데이터(User)에 접근하여 로그인 검증 및 사용자 조회를 담당
 */

@Dao
interface UserDao {
    // 새로운 유저 정보를 삽입 (이미 존재하는 경우 무시)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(user: User)

    // 입력된 아이디와 비밀번호가 모두 일치하는 유저가 있는지 확인 (로그인 기능)
    @Query("SELECT * FROM user_table WHERE userId = :id AND userPw = :pw")
    suspend fun login(id: String, pw: String): User?

    // 현재 DB에 저장된 유저 중 가장 첫 번째 한 명의 정보를 가져옴 (로그인 검증용)
    @Query("SELECT * FROM user_table LIMIT 1")
    suspend fun getOneUser(): User?


}