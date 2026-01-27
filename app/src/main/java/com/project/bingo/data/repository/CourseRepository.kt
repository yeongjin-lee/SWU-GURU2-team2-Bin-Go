package com.project.bingo.data.repository

import com.project.bingo.data.model.CourseGenerateRequest
import com.project.bingo.data.model.CourseGenerateResponse
import com.project.bingo.data.remote.RetrofitClient

/**
 * CourseRepositiory.kt
 * 서버 API 호출을 관리하는 Repository
 * Activity나 Fragment에서 직접 접근하여 서버 데이터를 호출하도록 설계
 */
object CourseRepository {

    // RetrofitClient를 통해 생성된 API 서비스 객체를 지연 초기화로 가져옴
    private val service by lazy { RetrofitClient.courseService }


    /**
     * 서버에 코스 생성 요청을 보내고 응답을 반환하는 함수
     * @param request 시작 좌표, 거리 등 요청 데이터
     * @return 서버로부터 받은 코스 리스트 응답
     */
    suspend fun generateCourse(request: CourseGenerateRequest): CourseGenerateResponse {
        // 네트워크 통신 서비스를 통해 코스 생성 API 실행
        return service.generateCourse(request)
    }
}
