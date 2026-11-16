package com.modo.app.ui.common

import android.animation.Animator
import android.animation.AnimatorInflater
import android.content.res.ColorStateList
import android.graphics.drawable.LayerDrawable
import android.view.View
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import androidx.core.view.isVisible
import com.modo.app.R
import com.modo.app.databinding.ViewBarBinding

/**
 * 진행 막대(view_bar.xml)에 값 넣기.
 * 바탕(가로 LinearLayout) 안에 채움 View가 하나 있고, 그 View의 layout_weight를 바꿔 길이를 정한다.
 * (바탕의 weightSum이 100이라 weight 37 = 37%)
 */
fun ViewBarBinding.set(value: Int, @ColorInt color: Int? = null, max: Int = 100) {
    val percent = if (max <= 0) 0f else (value * 100f / max).coerceIn(0f, 100f)
    val params = fill.layoutParams as LinearLayout.LayoutParams
    params.weight = percent
    fill.layoutParams = params
    if (color != null) fill.backgroundTintList = ColorStateList.valueOf(color)
}

/**
 * 기다리는 동안 보여 주는 움직이는 막대 (얼마나 걸릴지 모를 때).
 * 채움을 꽉 채운 뒤 res/animator/bar_slide 로 왼쪽에서 오른쪽으로 늘어나기를 반복한다.
 * @return 멈출 때 cancel() 할 애니메이터. running == false 면 null
 */
fun ViewBarBinding.setBusy(running: Boolean, previous: Animator?): Animator? {
    previous?.cancel()
    root.isVisible = running
    if (!running) return null
    set(100)
    fill.pivotX = 0f // 왼쪽 끝을 기준으로 늘어남
    val animator = AnimatorInflater.loadAnimator(root.context, R.animator.bar_slide)
    animator.setTarget(fill)
    animator.start()
    return animator
}

/**
 * 진행률 고리(배경이 ring_progress 인 View)에 값 넣기.
 * 고리 도형(shape ring, useLevel=true)은 level 0~10000 만큼만 그려지므로 퍼센트 × 100 을 넣는다.
 */
fun View.setRing(percent: Int, @ColorInt color: Int) {
    val layers = background.mutate() as LayerDrawable
    layers.findDrawableByLayerId(R.id.ring_fill).setTint(color)
    layers.level = percent.coerceIn(0, 100) * 100
}
