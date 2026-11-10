package com.modo.app.ui.common

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * 화면이 상태 바·내비게이션 바 아래까지 그려지므로(edge-to-edge), 그만큼 안쪽 여백을 준다.
 * 키보드가 올라오면 아래 여백을 키보드 높이만큼 늘린다.
 */
fun View.applySystemBarsPadding(top: Boolean = true, bottom: Boolean = true) {
    val baseLeft = paddingLeft
    val baseTop = paddingTop
    val baseRight = paddingRight
    val baseBottom = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
        view.setPadding(
            baseLeft + bars.left,
            if (top) baseTop + bars.top else baseTop,
            baseRight + bars.right,
            if (bottom) baseBottom + maxOf(bars.bottom, ime.bottom) else baseBottom,
        )
        insets
    }
}
