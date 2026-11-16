package com.modo.app.ui.files

import android.content.ActivityNotFoundException
import android.net.Uri
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import com.modo.app.R
import com.modo.app.ui.common.toast
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 첨부할 사진·파일 고르기. 화면이 만들어진 뒤에 생기는 컨트롤러(채팅 탭 등)에서도 쓸 수 있게
 * 생명주기에 묶지 않고 등록하므로, 다 쓰면 release()로 풀어야 한다.
 * - 사진 찍기: 카메라 앱을 띄워 찍은 사진을 받음 (카메라 권한 없이 카메라 앱이 대신 찍어 줌)
 * - 사진: 시스템 사진 선택기(권한 없음) / 파일: 문서 선택기
 */
class AttachPicker(private val activity: ComponentActivity, key: String, private val onPicked: (Uri) -> Unit) {

    /** 카메라 앱이 찍은 사진을 써 넣을 위치 */
    private var cameraUri: Uri? = null

    /** 카메라 앱을 띄우고, 넘겨 준 위치(Uri)에 큰 사진이 저장되면 true가 돌아옴 */
    private val camera = activity.activityResultRegistry.register("$key.camera", ActivityResultContracts.TakePicture()) { saved ->
        val uri = cameraUri
        if (saved && uri != null) onPicked(uri)
    }
    private val photo = activity.activityResultRegistry.register("$key.photo", ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(onPicked)
    }
    private val file = activity.activityResultRegistry.register("$key.file", ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onPicked)
    }

    fun takePhoto() {
        // 찍은 사진을 받을 빈 파일을 앱 캐시 폴더에 만들고, 카메라 앱이 쓸 수 있는 주소(content://)로 바꿔서 넘김
        val folder = File(activity.cacheDir, "modo-camera").apply { mkdirs() }
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val target = FileProvider.getUriForFile(activity, "${activity.packageName}.files", File(folder, "IMG_$stamp.jpg"))
        cameraUri = target
        try {
            camera.launch(target)
        } catch (e: ActivityNotFoundException) {
            activity.toast(R.string.photo_no_camera)
        }
    }

    fun pickPhoto() = photo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    fun pickFile() = file.launch(arrayOf("*/*"))

    /** 첨부 버튼: 사진 찍기 / 사진 / 파일 중 하나를 고르는 선택창 */
    fun showMenu(anchor: View) {
        val context = anchor.context
        val items = arrayOf(context.getString(R.string.attach_camera), context.getString(R.string.attach_photo), context.getString(R.string.attach_file))
        AlertDialog.Builder(context)
            .setTitle(R.string.attach_title)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> takePhoto()
                    1 -> pickPhoto()
                    else -> pickFile()
                }
            }
            .show()
    }

    fun release() {
        camera.unregister()
        photo.unregister()
        file.unregister()
    }
}
