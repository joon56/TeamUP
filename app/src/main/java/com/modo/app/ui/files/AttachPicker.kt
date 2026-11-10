package com.modo.app.ui.files

import android.net.Uri
import android.view.View
import android.widget.PopupMenu
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.modo.app.R

/**
 * 첨부할 사진·파일 고르기. 화면이 만들어진 뒤에 생기는 컨트롤러(채팅 탭 등)에서도 쓸 수 있게
 * 생명주기에 묶지 않고 등록하므로, 다 쓰면 release()로 풀어야 한다.
 * 사진은 시스템 사진 선택기(권한 없음), 파일은 문서 선택기.
 */
class AttachPicker(activity: ComponentActivity, key: String, private val onPicked: (Uri) -> Unit) {

    private val photo = activity.activityResultRegistry.register("$key.photo", ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(onPicked)
    }
    private val file = activity.activityResultRegistry.register("$key.file", ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onPicked)
    }

    fun pickPhoto() = photo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    fun pickFile() = file.launch(arrayOf("*/*"))

    /** 첨부 버튼: 사진 / 파일 */
    fun showMenu(anchor: View) {
        val popup = PopupMenu(anchor.context, anchor)
        popup.menu.add(0, 1, 0, R.string.attach_photo)
        popup.menu.add(0, 2, 1, R.string.attach_file)
        popup.setOnMenuItemClickListener { item ->
            if (item.itemId == 1) pickPhoto() else pickFile()
            true
        }
        popup.show()
    }

    fun release() {
        photo.unregister()
        file.unregister()
    }
}
