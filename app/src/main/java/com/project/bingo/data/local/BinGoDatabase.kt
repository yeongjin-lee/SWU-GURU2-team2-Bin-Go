package com.project.bingo.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.project.bingo.data.local.dao.RunningRecordDao
import com.project.bingo.data.local.dao.UserDao
import com.project.bingo.data.local.entity.Converters
import com.project.bingo.data.local.entity.RunningRecord
import com.project.bingo.data.local.entity.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * BinGoDatabase.kt
 * 앱의 전체 데이터베이스를 생성하고 관리하며, 초기 실행 시 더미 데이터를 생성합니다.
 */

@Database(entities = [RunningRecord::class, User::class], version = 2, exportSchema = false)
@TypeConverters(Converters::class)
abstract class BinGoDatabase : RoomDatabase() {

    // 각 테이블에 접근할 수 있는 DAO 함수들
    abstract fun runningRecordDao(): RunningRecordDao
    abstract fun userDao(): UserDao

    companion object {

        // 인스턴스를 하나만 유지하기 위한 변수
        @Volatile
        private var INSTANCE: BinGoDatabase? = null

        // DB 객체를 가져오는 함수 (싱글톤 패턴)
        fun getDatabase(context: Context): BinGoDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BinGoDatabase::class.java,
                    "bingo_database"
                )
                    .addCallback(DatabaseCallback()) // DB 생성 시 호출될 콜백 설정
                    .fallbackToDestructiveMigration() // 버전업 시 데이터 초기화 허용
                    .build()
                INSTANCE = instance
                instance
            }
        }

        // 데이터베이스 생성 및 초기화 시점에 수행될 작업 정의
        private class DatabaseCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                // DB가 처음 만들어질 때 비동기로 초기 데이터 생성
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {

                        // 고정 사용자 정보(심사위원용) 생성 로직 추가
                        database.userDao().insert(User("swu1", "1234", "김슈니"))

                        // 러닝 데이터 생성 로직 실행
                        populateDatabase(database.runningRecordDao())

                    }
                }
            }
        }

        // 사용자 정보
        private suspend fun insertAdminUser(userDao: UserDao) {
            val admin = User("admin", "1234", "심사위원")
            userDao.insert(admin) // 이 코드가 실행되면서 DB에 고정 정보가 들어감
        }



        // 2025년 12월 ~ 2026년 1월 26일까지 더미 데이터 생성(캘린더, 그래프 기능 제대로 활용 위함)
        private suspend fun populateDatabase(dao: RunningRecordDao) {
            // 시작 날짜 설정: 2025년 12월 1일
            val startCalendar = Calendar.getInstance().apply {
                set(2025, Calendar.DECEMBER, 1, 9, 0, 0)
            }

            // 종료 날짜 설정: 2026년 1월 26일(제출 날짜 전날까지는 더미DB로 )
            val endCalendar = Calendar.getInstance().apply {
                set(2026, Calendar.JANUARY, 26, 23, 59, 59)
            }

            // 반복문: 시작일이 종료일보다 이후가 될 때까지 하루씩 더하며 무조건 생성
            var i = 0
            // 시작일이 종료일보다 뒤로 갈 때까지 반복
            while (!startCalendar.after(endCalendar)) {
                val recordDate = startCalendar.timeInMillis

                // 랜덤한 수치를 적용한 러닝 기록 객체 생성
                val record = RunningRecord(
                    userId = "swu1",
                    date = recordDate,
                    distance = 2.0 + (Math.random() * 5.0), // 2~7km 랜덤
                    duration = 900L + (Math.random() * 1800).toLong(),  // 15분~45분 랜덤
                    calories = 150 + (Math.random() * 300).toInt(),
                    pace = 5.0 + Math.random() * 2.0,
                    hasTrashCan = true,
                    hasToilet = (i % 2 == 0),
                    hasConvenience = (i % 3 == 0),
                    routePath = "[]",
                    waypoints = "[]"
                )

                // DB에 삽입
                dao.insert(record)

                // 하루를 더해서 다음 날짜로 이동
                startCalendar.add(Calendar.DAY_OF_YEAR, 1)
                i++
            }
        }
    }
}