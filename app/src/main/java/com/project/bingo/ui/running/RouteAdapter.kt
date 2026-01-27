package com.project.bingo.ui.running

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.project.bingo.R
import com.project.bingo.databinding.ItemRouteBinding

/**
 * RouteAdapter.kt
 * 추천 코스 선택 화면 RecyclerView Adapter
 */

class RouteAdapter(
    private val routeList: List<RouteItem>, // 화면에 표시할 루트 데이터 리스트
    private val onItemClick: (RouteItem) -> Unit // 아이템 클릭 시 실행할 콜백 함수
) : RecyclerView.Adapter<RouteAdapter.RouteViewHolder>() {

    // 현재 사용자가 선택한 아이템의 위치 번호 저장 (초기값 -1은 미선택 상태)
    private var selectedPosition = -1

    // 각 아이템의 UI 요소를 관리하는 뷰홀더 클래스
    inner class RouteViewHolder(private val binding: ItemRouteBinding) :
        RecyclerView.ViewHolder(binding.root) {

        // 데이터를 뷰에 결합하는 함수
        fun bind(item: RouteItem, position: Int) = with(binding) {
            // --- 데이터 바인딩 ---
            tvRouteTitle.text = "추천 코스 ${item.courseIndex + 1}" // 코스 제목 설정
            tvDistance.text = item.distanceText // 거리 텍스트 설정
            tvBinCount.text = "쓰레기통 ${item.binCount}개" // 쓰레기통 개수 표시
            tvToiletCount.text = "화장실 ${item.toiletCount}개" // 화장실 개수 표시
            tvStoreCount.text = "편의점 ${item.storeCount}개" // 편의점 개수 표시
            if (item.thumbnail != null) {
                ivMap.setImageBitmap(item.thumbnail) // 썸네일 이미지가 있으면 지도 이미지뷰에 설정
            }

            // 카드뷰 레이아웃 변경 로직
            val isSelected = selectedPosition == position

            // 1. 선택 여부에 따른 카드 배경색 변경 (선택됨: 파란 테두리 / 미선택: 기본)
            layoutCard.setBackgroundResource(
                if (isSelected) R.drawable.bg_route_card_selected
                else R.drawable.bg_route_card_unselected
            )

            // 2. 선택된 카드에만 '시작하기' 버튼 노출
            btnStart.visibility = if (isSelected) View.VISIBLE else View.GONE

            // 3. 카드 전체 영역 클릭 시: 즉시 화면 이동을 하지 않고 선택 상태(레이아웃)만 먼저 변경
            root.setOnClickListener {
                if (selectedPosition != adapterPosition) {
                    val oldPosition = selectedPosition
                    selectedPosition = adapterPosition
                    notifyItemChanged(oldPosition)
                    notifyItemChanged(selectedPosition)
                }
            }

            // 4. 활성화된 '시작하기' 버튼을 눌렀을 때만 실제 러닝 화면 이동 함수 호출
            btnStart.setOnClickListener {
                onItemClick(item)
            }
        }
    }

    // 뷰홀더를 새로 생성하여 레이아웃을 인플레이트함
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RouteViewHolder {
        val binding = ItemRouteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RouteViewHolder(binding)
    }

    // 데이터와 뷰홀더를 연결함
    override fun onBindViewHolder(holder: RouteViewHolder, position: Int) {
        // position을 넘겨서 현재 어떤 카드가 그려지는지 식별하게 함
        holder.bind(routeList[position], position)
    }

    // 리스트의 전체 아이템 개수 반환
    override fun getItemCount(): Int = routeList.size
}