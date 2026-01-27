package com.project.bingo.data.remote

import com.project.bingo.data.model.CourseGenerateRequest
import com.project.bingo.data.model.CourseGenerateResponse
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * CourseService.kt
 * 서버 API의 경로(Endpoint)와 HTTP 통신 방식(POST)을 정의함
 */
interface CourseService {
    // 서버의 'api/course/recommend' 경로로 POST 요청을 보냄
    @POST("api/course/recommend")
    suspend fun generateCourse(
        // 본문에 CourseGenerateRequest 객체를 실어서 전송
        @Body request: CourseGenerateRequest
    ): CourseGenerateResponse
}
