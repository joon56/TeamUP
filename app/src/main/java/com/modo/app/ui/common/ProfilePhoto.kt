package com.modo.app.ui.common

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream

/** 프로필 사진: 카메라에서 받은 작은 사진을 정사각형으로 잘라 글자(Base64)로 바꿔 기기 설정에 저장 */
object ProfilePhoto {

    private const val SIZE_PX = 160

    /** 가운데를 정사각형으로 자르고 줄여서 JPEG → Base64 문자열 */
    fun encode(source: Bitmap): String {
        val side = minOf(source.width, source.height)
        val square = Bitmap.createBitmap(source, (source.width - side) / 2, (source.height - side) / 2, side, side)
        val small = Bitmap.createScaledBitmap(square, SIZE_PX, SIZE_PX, true)
        val bytes = ByteArrayOutputStream()
        small.compress(Bitmap.CompressFormat.JPEG, 85, bytes)
        return Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
    }

    fun decode(text: String): Bitmap? {
        if (text.isEmpty()) return null
        return try {
            val bytes = Base64.decode(text, Base64.NO_WRAP)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
