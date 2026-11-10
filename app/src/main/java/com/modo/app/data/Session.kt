package com.modo.app.data

/** 로그인한 사용자의 실시간 구독 전체를 켜고 끄는 곳 */
object Session {

    private var running = ""

    /** 같은 사용자로 다시 불러도 안전 (모든 화면 onCreate에서 호출됨) */
    fun start(uid: String, fallbackName: String, email: String) {
        if (running == uid) return
        stop()
        running = uid
        DataHub.begin(uid, fallbackName)
        ProfileRepository.start(uid, fallbackName, email)
        TeamRepository.start(uid)
        InviteRepository.start(uid)
        ChatRepository.start(uid)
    }

    fun stop() {
        ChatRepository.stop()
        InviteRepository.stop()
        TeamRepository.stop()
        ProfileRepository.stop()
        DataHub.reset()
        running = ""
    }
}
