package com.project.bingo.data.local.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.project.bingo.data.local.entity.RunningRecord

/**
 * RunningRecordDao.kt
 * 러닝 기록 데이터(RunningRecord)에 대한 DB 쿼리(삽입, 조회)를 담당하는 인터페이스
 */
@Dao
interface RunningRecordDao {
    // 새로운 러닝 기록을 저장 (중복 발생 시 덮어쓰기 전략 사용)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: RunningRecord): Long

    // 모든 기록을 날짜 내림차순으로 조회하여 실시간 감시(LiveData) 형태로 반환
    @Query("SELECT * FROM running_records ORDER BY date DESC")
    fun getAllRecords(): LiveData<List<RunningRecord>>


    // 모든 기록을 날짜 내림차순으로 조회 (코루틴 환경에서 동기적으로 실행 가능)
    @Query("SELECT * FROM running_records ORDER BY date DESC")
    suspend fun getAllRecordsSync(): List<RunningRecord>

    // 특정 시작 날짜와 종료 날짜 사이의 기록만 필터링하여 조회
    @Query("SELECT * FROM running_records WHERE date >= :startDate AND date <= :endDate")
    suspend fun getRecordsByDateRangeSync(startDate: Long, endDate: Long): List<RunningRecord>
}
