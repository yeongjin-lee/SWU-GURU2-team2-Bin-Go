package com.project.bingo.data.repository

import androidx.lifecycle.LiveData
import com.project.bingo.data.local.dao.RunningRecordDao
import com.project.bingo.data.local.entity.RunningRecord


/**
 * RunningRepository.kt
 * 로컬 데이터베이스(Room)의 러닝 기록 데이터에 접근하는 중간 매개체(Repository)
 */

class RunningRepository(private val runningRecordDao: RunningRecordDao) {

    // DB에서 모든 러닝 기록을 가져와 실시간 관찰 가능한 LiveData 형태로 보관
    val allRecords: LiveData<List<RunningRecord>> = runningRecordDao.getAllRecords()

    //새로운 러닝 기록을 DB에 삽입하는 함수
    suspend fun insert(record: RunningRecord): Long {
        // DAO를 통해 실제 DB에 데이터 저장
        return runningRecordDao.insert(record)
    }

    //특정 날짜 범위 내의 러닝 기록을 동기적으로 가져오는 함수
    suspend fun getRecordsByDateRange(startDate: Long, endDate: Long): List<RunningRecord> {
        // DAO를 통해 범위 쿼리 실행
        return runningRecordDao.getRecordsByDateRangeSync(startDate, endDate)
    }
}
