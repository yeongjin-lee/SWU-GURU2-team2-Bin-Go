package com.project.bingo.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.project.bingo.R
import com.project.bingo.databinding.ItemRecommendedRouteBinding
import com.project.bingo.model.RecommendedRoute

/**
 * RecommendedRouteAdapter.kt
 * 홈화면 추천 러닝 코스 어댑터 (가로 스크롤)
 */
class RecommendedRouteAdapter :
    ListAdapter<RecommendedRoute, RecommendedRouteAdapter.RouteViewHolder>(RouteDiffCallback()) {

    // 카드 아이템 뷰를 생성하고 뷰홀더와 연결
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RouteViewHolder {
        val binding = ItemRecommendedRouteBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return RouteViewHolder(binding)
    }

    // 생성된 뷰홀더에 데이터 아이템을 매칭
    override fun onBindViewHolder(holder: RouteViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    // 개별 카드 아이템의 UI 요소들을 관리하는 뷰홀더 클래스
    class RouteViewHolder(private val binding: ItemRecommendedRouteBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(route: RecommendedRoute) {
            binding.apply {
                tvRouteName.text = route.name // 코스 제목 설정
                tvRouteDistance.text = "${route.distance}km" // 코스 거리 설정
                tvRouteDifficulty.text = route.difficulty // 난이도 설정
                tvRouteDescription.text = route.description // 요약 설명 설정

                // 리소스 이름을 통해 Drawable 아이디 가져오기
                val resId = itemView.context.resources.getIdentifier(route.imageUrl, "drawable", itemView.context.packageName)
                if (resId != 0) {
                    ivRouteImage.setImageResource(resId) // 이미지가 존재할 때 설정
                } else {
                    ivRouteImage.setImageResource(R.drawable.ic_launcher_foreground) // 없을 때 기본 이미지로 대체
                }
            }
        }
    }

    // 리스트 데이터 갱신 시 바뀐 부분만 효율적으로 찾기 위한 비교 콜백
    private class RouteDiffCallback : DiffUtil.ItemCallback<RecommendedRoute>() {
        // 아이템의 고유 ID를 비교하여 동일 여부 판별
        override fun areItemsTheSame(oldItem: RecommendedRoute, newItem: RecommendedRoute): Boolean {
            return oldItem.id == newItem.id
        }

        // 아이템의 전체 내용(필드)이 똑같은지 비교
        override fun areContentsTheSame(oldItem: RecommendedRoute, newItem: RecommendedRoute): Boolean {
            return oldItem == newItem
        }
    }
}
