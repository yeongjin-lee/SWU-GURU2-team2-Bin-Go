package com.project.bingo.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding

/**
 * BaseFragment.kt
 * 앱 내 모든 프래그먼트의 기반이 되는 추상 클래스입니다.
 * 중복되는 ViewBinding 연결 및 해제 로직을 한곳에서 관리하여 코드 효율성을 높임
 */

abstract class BaseFragment<VB : ViewBinding> : Fragment() {

    // 뷰 바인딩 객체를 담는 변수 (메모리 누수 방지를 위해 null 허용)
    protected var _binding: VB? = null

    // 외부에서 바인딩 객체에 접근할 때 쓰는 속성
    protected val binding get() = _binding!!


    // 자식 프래그먼트에서 구체적인 바인딩 클래스를 지정하도록 하는 추상 함수
    abstract fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?): VB

    // 프래그먼트 뷰를 생성할 때 호출되는 생명주기 함수
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // 추상 함수를 통해 전달받은 바인딩 객체를 저장
        _binding = getViewBinding(inflater, container)
        // 바인딩된 최상단 루트 뷰를 반환하여 화면에 표시
        return binding.root

    }

    // 뷰가 파괴될 때 호출되는 생명주기 함수
    override fun onDestroyView() {
        super.onDestroyView()
        // 메모리 누수를 방지하기 위해 바인딩 객체 참조를 해제
        _binding = null
    }
}
