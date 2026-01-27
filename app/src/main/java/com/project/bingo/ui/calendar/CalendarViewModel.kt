package com.project.bingo.ui.calendar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import com.project.bingo.data.local.BinGoDatabase
import com.project.bingo.data.local.entity.RunningRecord
import com.project.bingo.data.repository.RunningRepository

/**
 * CalendarViewModel.kt
 * 캘린더 프래그먼트와 데이터 저장소(Repository) 사이를 연결하는 ViewModel입니다.
 */

class CalendarViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: RunningRepository // 데이터 접근 저장소
    val runningRecords: LiveData<List<RunningRecord>> // 관찰 가능한 러닝 기록 목록

    init {
        // DB 인스턴스에서 DAO를 가져옴
        val dao = BinGoDatabase.getDatabase(application).runningRecordDao()
        // DAO를 사용하여 저장소 초기화
        repository = RunningRepository(dao)
        // 저장소로부터 전체 러닝 기록 LiveData를 연결
        runningRecords = repository.allRecords
    }
}
