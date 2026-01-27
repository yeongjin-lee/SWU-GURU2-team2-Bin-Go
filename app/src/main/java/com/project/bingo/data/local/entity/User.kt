package com.project.bingo.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
/**
 * User.kt
 * 유저 계정 정보를 DB에 저장하기 위한 데이터 틀
 */
@Entity(tableName = "user_table")
data class User(
    @PrimaryKey val userId: String, // 아이디
    val userPw: String,           // 비밀번호
    val userName: String          // 이름
)
