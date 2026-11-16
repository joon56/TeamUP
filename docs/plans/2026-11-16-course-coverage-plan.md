# 수업 개념 완전 반영 작업 계획 (v0.3.0)

브랜치 `feat/course-coverage`. 커밋·푸시는 사용자 확인 후.
결과 정리: `docs/specs/2026-11-16-course-coverage.md` (개념별 위치 · 넣지 않은 것 · 교안 밖 항목 점검표). 디자인: Figma 사본(`docs/design/figma/`, 로컬에만 보관 · 저장소에는 올리지 않음).

## 사용자 결정 (2026-11-16)

- 화면 모드 4종 + 루트 홈 + 하단 탭은 지금 그대로 유지 (Figma 구조로 통일하지 않음)
- 채팅 알림은 배너까지. 서버 푸시(FCM)는 교안 밖이라 하지 않음
- 구현 어려운 디자인 항목(카카오 로그인, 말한 사람 구분, 온라인 표시, 수업 시간표 불러오기)은 뺌
- 전화번호는 넣지 않음
- 교안 내용은 전부 반영, 교안 밖 내용은 줄임 (Firebase · Supabase 제외). 교안 밖 기능을 뺄지는 목록을 보고 사용자가 결정
- 새 화면(스플래시 · 알람 시트 · 알람 화면)은 Figma 배치를 따르되 앱의 기존 색 · 글꼴(라이트/다크) 사용

## 작업

- [x] 0. 날짜 선택창 다크 모드 버그 (창 배경 흰색 고정) + 다이얼로그를 `AlertDialog.Builder`로
- [x] 1. 리소스: raw 소리, 9.png, 해상도별 PNG, shape ring · line, state_pressed · focused, animator, values-sw600dp · w600dp · v31
- [x] 2. 업무 마감 시간(TimePickerDialog) + 알람 규칙 `logic/AlarmRules`(테스트)
- [x] 3. 알림 기반: 채널 5개, `Notifier`, 권한, 직접 만든 퍼미션
- [x] 4. 업무 알람: 알람 시트(B06), 예약(AlarmManager), 리시버 3개, `AlarmRingService`(소리 · 진동), 알람 화면(B07)
- [x] 5. 채팅 배너(MessagingStyle · RemoteInput · BigPicture), 업무 배정(BigText), 아침 마감 목록(Inbox), 올리기 진행률(Progress)
- [x] 6. 내 정보 알림 설정(setMultiChoiceItems · setSingleChoiceItems · TimePickerDialog), 권한 한 번에 요청
- [x] 7. 스플래시(B01) + animator
- [x] 8. 다이얼로그 세부(setIcon · setNeutralButton · setCancelable · setCanceledOnTouchOutside · setItems), Toast 콜백
- [x] 9. 액티비티: setResult + StartActivityForResult, PickContact, TakePicture, TakePicturePreview, getIntExtra, onStart · onRestart · onRestoreInstanceState
- [x] 10. 리시버: 화면 켬/끔, 배터리, sendBroadcast + 동적 등록
- [x] 11. WindowMetrics 분기, ResourcesCompat.getColor
- [x] 12. 교안 밖 → 교안 대체: MaterialButton 태그 → Button, 진행 막대(LinearLayout weight + shape) · 진행 고리(shape ring), 시간 휠 → TimePickerDialog, 드롭다운 → setSingleChoiceItems, PopupMenu 2곳 → setItems
- [x] 13. 영어 문자열, 단위 테스트 120개, 빌드, 에뮬레이터 확인
- [x] 14. 문서(대조표 · README), 버전 0.3.0, APK

계획에서 바뀐 것
- "회의 녹음 중" 알림은 뺌: 화면을 벗어나면 녹음이 멈추므로 계속 떠 있을 이유가 없음. 지울 수 없는 알림(setOngoing)은 울리는 알람과 올리는 중 알림에 들어감
- Snackbar는 코드에서 쓰는 곳이 없어 바꿀 것이 없었음
- 로고는 Figma의 글자 "M" 대신 앱의 기존 로고(//)를 씀 (앱 아이콘 · 로그인 화면과 맞춤)
- 알람 시각 칩은 Figma의 2줄 배치 대신 한 줄 4칸 + 아래에 "○○에 울려요" 한 줄 (RadioGroup 하나로 묶기 위해)

## 에뮬레이터(API 36)에서 확인한 것

- 날짜 · 시간 선택창: 다크 · 라이트 모두 글자와 색이 보임
- 업무 알람: 앱 종료 + 화면 꺼짐 상태에서 정각에 화면이 켜지며 알람 화면이 뜸, 알람 음량으로 소리 재생, 30초 뒤 멈추고 10분 뒤 재예약, 놓친 알람 알림
- 알림의 [완료로 표시]: 앱이 꺼진 상태에서 눌러도 업무가 완료 처리되고 알람이 지워짐
- 앱을 쓰는 중에 울리면 알람 화면이 바로 뜸. 앱이 뒤에 있고 화면이 켜져 있으면 버튼 3개가 달린 배너가 뜸 (다시 울릴 때도)
- 알람 화면의 [10분 뒤 다시](10분 뒤로 재예약) · [알람 끄기](알람 삭제): 소리가 멈추고 화면이 닫힘
- 재부팅 뒤 앱을 열지 않아도 알람과 마감 알림이 다시 예약됨
- 채팅 배너(다른 계정이 보낸 메시지)와 배너에서 답장 → 채팅방에 반영. 앱이 뒤에 있으면 배너가 오지 않음(다시 열면 뜸)
- 업무 배정 알림, 아침 마감 알림(앱 종료 상태), 파일 올리기 진행률 알림
- 사진 찍어 첨부, 연락처에서 이름 가져오기, 받을 알림 · 기본 알람음 선택창, 저장 안 한 변경 확인(버튼 3개), 보드 카드 길게 누르기
- 회의 녹음 중 배터리 경고, 충전기 연결 시 숨김, 화면이 꺼졌다 켜지면 이어서 녹음
- 진행 막대 · 진행 고리(도형으로 바꾼 것), 시작 화면, 내 정보 알림 설정

확인하지 못한 것
- 알람 화면의 [업무 열기], 배너에 달린 [10분 뒤 다시] · [알람 끄기] 버튼 (알람 화면의 같은 버튼과 알림의 [완료로 표시]는 확인)
- 프로필 사진 찍기, 내 노래(기기 음악 파일) 고르기 — 에뮬레이터에 음악 파일이 없음
- 영어 화면, 가로 화면, 태블릿 크기에서 새 화면의 모양
- 실기기 (제조사별 절전 · 알림 정책)

## 메모

- Android 16은 묶이지 않은 알림이 2개 이상이면 스스로 묶고, 그 뒤 같은 앱의 새 알림을 조용한 알림으로 만든다(배너 · 전체 화면 억제). 그래서 앱의 알림을 한 묶음 + 대표 알림으로 올림 (`Notifier.ensureSummary`)

- 알람 데이터는 기기에 저장(Prefs JSON): 재부팅 뒤 네트워크 없이 다시 예약해야 해서
- AlarmManager · 포그라운드 서비스는 교안 밖이지만 "정해진 시각에 울리는 알람"에 필수
- 에뮬레이터를 `adb reboot` 하면 앱에서 고른 언어(한국어)가 시스템 언어로 돌아감. 부팅 리시버를 뺀 빌드에서도 똑같아 이번 변경과는 무관 (실기기 확인 필요)
