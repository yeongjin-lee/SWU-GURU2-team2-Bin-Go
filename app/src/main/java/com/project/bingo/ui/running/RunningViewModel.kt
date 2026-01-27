package com.project.bingo.ui.running

import android.app.Application
import android.location.Location
import androidx.lifecycle.*
import com.project.bingo.data.local.BinGoDatabase
import com.project.bingo.data.local.entity.RunningRecord
import com.project.bingo.data.repository.RunningRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 러닝 중 발생하는 실시간 데이터(거리, 시간, 페이스)를 계산하고 보관하는 뷰모델
 */
class RunningViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: RunningRepository // 로컬 DB 접근 저장소

    init {
        // DB 객체를 통해 데이터 접근용 저장소 초기화
        val dao = BinGoDatabase.getDatabase(application).runningRecordDao()
        repository = RunningRepository(dao)
    }

    private var timerJob: Job? = null // 초시계 코루틴 작업을 보관할 변수
    val timeSeconds = MutableLiveData(0L) // 흐른 시간을 초 단위로 실시간 보관
    val distanceKm = MutableLiveData(0.0) // 누적 거리를 km 단위로 실시간 보관
    val isPaused = MutableLiveData(false) // 현재 일시정지 상태인지 여부
    var lastLocation: Location? = null // 직전 좌표 정보를 보관 (거리 계산용)

    // 실시간 페이스 계산 (시간 변화가 생길 때마다 자동으로 계산됨)
    val pace: LiveData<Double> = timeSeconds.map { seconds ->
        val dist = distanceKm.value ?: 0.0
        // 거리가 0이 아닐 때만 '분/km' 형식으로 계산 수행
        if (dist > 0.001) (seconds / 60.0) / dist else 0.0
    }

    // 새로운 위치 좌표가 수신될 때마다 누적 거리를 갱신하는 함수
    fun updateLocation(newLocation: Location) {
        if (isPaused.value == true) return // 일시정지 중이면 거리 계산 안 함
        lastLocation?.let { last ->
            val diff = last.distanceTo(newLocation) // 이전 좌표와의 직선 거리(m) 구하기
            // 1m~1km 사이의 정상적인 이동만 기록 (실내에서 실행 시 GPS 튐 현상이 있기 때문)
            if (diff > 1.0 && diff < 1000.0) {
                distanceKm.value = (distanceKm.value ?: 0.0) + (diff / 1000.0)
            }
        }
        lastLocation = newLocation // 현재 위치를 다음 계산을 위한 기준점으로 업데이트
    }

    // 1초마다 숫자를 하나씩 올려주는 타이머 시작 함수
    fun startTimer() {
        if (timerJob != null) return // 이미 타이머가 돌고 있으면 중복 실행 방지
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                if (isPaused.value == false) { // 일시정지 상태가 아닐 때만 시간 증가
                    timeSeconds.value = (timeSeconds.value ?: 0L) + 1
                }
            }
        }
    }

    // 일시정지 상태를 토글(반전)하고, 좌표 기준점을 초기화하여 오류 방지
    fun togglePause() {
        isPaused.value = isPaused.value != true
        if (isPaused.value == true) lastLocation = null
    }

    // 현재까지 측정된 최종 운동 수치를 로컬 DB에 영구 저장하는 함수
    fun saveRecord(onComplete: (Long) -> Unit) {
        val d = distanceKm.value ?: 0.0
        val p = pace.value ?: 0.0

        // 최종 기록 객체 생성
        val record = RunningRecord(
            userId = "swu1", // 고정 유저 아이디 사용
            date = System.currentTimeMillis(), // 현재 날짜 기록
            distance = Math.round(d * 100) / 100.0, // 거리 소수점 2자리 반올림
            duration = timeSeconds.value ?: 0L, // 총 시간
            calories = (d * 65).roundToInt(), // 칼로리 환산 계산
            pace = Math.round(p * 10) / 10.0, // 페이스 소수점 1자리 반올림
            hasTrashCan = false, hasToilet = false, hasConvenience = false, // 옵션값 기본 세팅
            routePath = "[]", waypoints = "[]" // 경로 문자열 데이터 초기화
        )
        // 코루틴 내에서 저장소의 삽입 함수 실행 후 콜백으로 결과 알림
        viewModelScope.launch { onComplete(repository.insert(record)) }
    }
}