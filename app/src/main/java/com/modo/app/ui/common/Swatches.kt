package com.modo.app.ui.common

import android.view.View
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.core.content.ContextCompat
import com.modo.app.R

/** 색 동그라미 8개를 RadioGroup에 추가 (프로젝트 색·아바타 색 공용). 선택 링은 foreground로 그림 */
object Swatches {

    fun build(group: RadioGroup, selected: Int, onSelected: (Int) -> Unit) {
        val context = group.context
        val size = context.resources.getDimensionPixelSize(R.dimen.color_swatch)
        val gap = context.resources.getDimensionPixelSize(R.dimen.gap_s)
        val ids = mutableListOf<Int>()
        for (i in Labels.PROJECT_COLORS.indices) {
            val swatch = RadioButton(context, null, 0, 0)
            swatch.id = View.generateViewId()
            swatch.buttonDrawable = null
            swatch.setBackgroundResource(R.drawable.bg_swatch_dot)
            swatch.backgroundTintList = context.tintOf(Labels.projectColor(i))
            swatch.foreground = ContextCompat.getDrawable(context, R.drawable.fg_swatch_ring)
            swatch.contentDescription = context.getString(R.string.desc_color, i + 1)
            swatch.isClickable = true
            swatch.isFocusable = true
            swatch.isSaveEnabled = false
            val params = RadioGroup.LayoutParams(size, size)
            if (i < Labels.PROJECT_COLORS.lastIndex) params.marginEnd = gap
            group.addView(swatch, params)
            ids.add(swatch.id)
        }
        if (selected in ids.indices) group.check(ids[selected])
        group.setOnCheckedChangeListener { _, checkedId ->
            val index = ids.indexOf(checkedId)
            if (index >= 0) onSelected(index)
        }
    }
}
