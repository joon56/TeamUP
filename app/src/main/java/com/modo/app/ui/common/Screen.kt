package com.modo.app.ui.common

import android.app.Activity
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowInsets
import androidx.annotation.RequiresApi

/** 화면 크기 구하기 (기기마다 화면 크기가 다르므로 비율로 배치할 때 씀) */
object Screen {

    /**
     * 앱이 쓸 수 있는 가로 길이(px).
     * API 30부터는 WindowMetrics로 현재 창 크기를 구하고(분할 화면에서도 정확),
     * 그 전에는 DisplayMetrics를 쓴다 (API 30에서 getMetrics가 지원 중단됨).
     */
    fun widthPx(activity: Activity): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowWidth(activity)
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            activity.windowManager.defaultDisplay.getMetrics(metrics)
            metrics.widthPixels
        }

    /** 창 전체 너비에서 좌우 시스템 바 · 화면 잘림(노치) 영역을 뺀 너비 */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun windowWidth(activity: Activity): Int {
        val metrics = activity.windowManager.currentWindowMetrics
        val insets = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
        return metrics.bounds.width() - insets.left - insets.right
    }
}
