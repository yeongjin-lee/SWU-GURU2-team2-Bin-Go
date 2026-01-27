package com.project.bingo.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.project.bingo.databinding.ItemPresetCourseBinding

/**
 * CourseAdapter.kt
 * 홈화면의 추천코스 리스트(CourseTotalActivity)에서 각 아이템 데이터를
 * 실제 화면의 리스트 UI와 연결해주는 중개자 역할을 수행합니다.
 */

class CourseAdapter(private val itemList: List<PresetCourse>) :
    RecyclerView.Adapter<CourseAdapter.CourseViewHolder>() {

    // 각 아이템의 뷰(바인딩)를 보관하는 내부 뷰홀더 클래스
    inner class CourseViewHolder(val binding: ItemPresetCourseBinding) :
        RecyclerView.ViewHolder(binding.root)

    // 새로운 아이템 레이아웃이 필요할 때 호출되어 뷰홀더를 생성
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CourseViewHolder {
        // 아이템 레이아웃 XML을 바인딩 객체로 인플레이트(메모리 로드)
        val binding = ItemPresetCourseBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return CourseViewHolder(binding) // 생성된 바인딩을 담은 뷰홀더 반환
    }

    // 각 위치(position)에 있는 데이터를 실제 UI 요소에 매핑(결합)
    override fun onBindViewHolder(holder: CourseViewHolder, position: Int) {
        val item = itemList[position]  // 리스트에서 현재 순서의 데이터 획득
        holder.binding.apply {
            // 이미지 리소스 ID를 사용하여 코스 썸네일 이미지 설정
            ivCourseSnapshot.setImageResource(item.imageRes)
            tvCourseName.text = item.name
            tvCourseDesc.text = item.description

        }
    }

    // 어댑터가 관리하는 전체 데이터 아이템의 개수 반환
    override fun getItemCount(): Int = itemList.size
}