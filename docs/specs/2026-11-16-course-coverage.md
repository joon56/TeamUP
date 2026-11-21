# 수업 개념 반영 현황 (v0.3.1, 2026-11-21)

기준 문서: 팀의 "기술정리 완성본"(3~12주차). 대상 코드: v0.3.1.
경로는 `app/src/main/` 아래 기준. `…Activity.kt` 등은 `java/com/modo/app/` 아래.

- 1부: 교안 개념이 앱의 어디에 들어갔는지
- 2부: 교안에 있지만 일부러 넣지 않은 것과 이유
- 3부: 교안에 없는데 앱에 들어 있는 것 — 이번에 교안 내용으로 바꾼 것 / 남은 것(바꿀 수 있는지 · 없어도 되는지)
- 4부: 교수님이 설명한 항목 5가지가 앱의 어디에 있는지

**2026-11-21 재점검**: 기준 문서의 항목을 하나씩 코드에서 다시 찾아 확인했고, 빠져 있던 세부 항목(`@TargetApi`, `LENGTH_LONG`, `onToastShown`, 플랫폼 문자열, `getDimension`, 전원 종류 상수, 세기를 정한 진동 패턴, 리시버 `android:enabled`)을 채웠다. 팀 피드백(`autoLink` · `maxLines`)도 반영했고, 교수님 항목 "타이머 + 알람"에 맞춰 회의 타이머를 넣었다. 아래 표는 그 뒤의 상태.

---

## 1부. 교안 개념 → 앱 위치

### 1. Kotlin 기본 문법 (3~4주차)

val/var, fun, if/when, for/while, List/MutableList, indices/withIndex, 널 허용 타입, lateinit/lazy — 전 파일에서 사용.
대표: `logic/AlarmRules.kt`(when · for · 널 허용), `ui/task/TaskEditActivity.kt`(lateinit · lazy).

### 2. 화면 구성 · 위젯 (5주차)

| 개념 | 위치 |
|---|---|
| Activity · XML · ViewBinding | 화면 20개 전부 |
| TextView, EditText, ImageView | 전 화면 (`activity_minutes_edit.xml`의 받아쓰기 칸이 EditText) |
| Button | 레이아웃 46곳이 `<Button>` 태그 (기존 41곳을 바꿈 + 새 화면 5곳. 예: `activity_alarm.xml`, `tab_me.xml`) |
| CheckBox | 업무 완료 체크(`item_task.xml`), 알람 반복(`activity_alarm_set.xml`), 완료 효과음 설정(`tab_me.xml`) |
| RadioButton / RadioGroup | 우선순위 · 상태(`activity_task_edit.xml`), 알람 시각(`activity_alarm_set.xml`), 회의 타이머 길이(`activity_minutes_edit.xml`), 테마 · 언어(`tab_me.xml`) |
| TextView `autoLink` (팀 피드백) | 글 속 웹 주소를 누를 수 있는 링크로: 채팅 말풍선(`item_msg_mine.xml` · `item_msg_other.xml`), 공지 본문(`item_notice.xml`). 파일 메시지는 파일 이름이 주소로 잘못 걸리지 않게 끔(`autoLinkMask = 0`) |
| TextView `maxLines` · `ellipsize` (팀 피드백) | 회의록 메모 · 받아쓰기 입력칸의 최대 줄 수(`activity_minutes_edit.xml`, 세로 10 · 8줄 / 가로 5 · 4줄 — `values` · `values-land`의 integer). 넘치면 칸 안에서 넘겨 봄. 회의록 카드 미리보기는 2줄 + 말줄임(`item_minutes.xml`) |

### 3. 레이아웃 (6주차)

| 개념 | 위치 |
|---|---|
| LinearLayout | 전 화면. 진행 막대는 `weightSum` + `layout_weight`로 길이를 정함(`view_bar.xml`) |
| RelativeLayout | 업무 줄(`item_task.xml`), 스플래시(`activity_splash.xml`) |
| FrameLayout | 알람 화면의 원 겹치기(`activity_alarm.xml`), 프로필 사진(`tab_me.xml`), 알람 시트(`activity_alarm_set.xml`) |
| GridLayout | 월 달력 — `MonthCalendarView`가 GridLayout을 상속해 7칸 격자로 날짜를 배치(`ui/common/MonthCalendarView.kt`, `tab_schedule.xml`) |
| ConstraintLayout | 업무 수정(가로 chain 3:2 · Barrier), 알람 화면(세로 packed chain · 비율) |

### 4. 이벤트 (7주차)

| 개념 | 위치 |
|---|---|
| 클릭 | 전 화면 |
| 터치 | 가능 시간 격자 `onTouchEvent`, 누름 효과 `View.addPressEffect()`(`ui/common/Ui.kt` — 카드, 업무 체크 표시), 보드 스냅, 줄 수를 제한한 입력칸 안에서 먼저 넘기기(`TextView.scrollInsideFirst()`), 링크가 든 채팅 글 길게 누르기(`ChatRoomController.keepMenuOnLinkedText`) |
| 체크 상태 변경 | `setOnCheckedChangeListener` — 회의록 할 일 후보, 필터 · 설정 등. 업무 완료 체크는 클릭 리스너(화면 회전 때 상태 복원으로 잘못 불리는 것을 막음) |
| 키 | 업무 제목 Enter(`TaskEditActivity.setupKeyEvents`), 검색 Enter |
| 뒤로 가기 | `OnBackPressedCallback` — 홈에서 한 번 더 누르면 종료(`RootActivity`), 저장 안 한 변경 확인(`TaskEditActivity`) |

### 5. 리소스 (2 · 9주차)

| 개념 | 위치 |
|---|---|
| res 폴더: drawable, layout, values, color, font, menu, anim, xml | 기존 |
| res/**animator** | `animator/splash_dot.xml` · `logo_pop.xml`(스플래시), `alarm_pulse.xml` · `eq_bar.xml`(알람 화면), `bar_slide.xml`(AI 요약 대기 막대) — `AnimatorInflater.loadAnimator` |
| res/**raw** | `raw/alarm_modo.wav`(MODO 알람음), `raw/task_done.wav`(완료 효과음) |
| res/**mipmap** 해상도별 | `mipmap-mdpi ~ xxxhdpi/ic_launcher.png` (API 26 이상은 `mipmap-anydpi-v26`의 적응형 아이콘이 우선) |
| 해상도별 **PNG** | `drawable-mdpi ~ xxxhdpi/logo_tile.png` — 스플래시 로고 |
| **9.png** | `drawable-xxhdpi/btn_raised_base.9.png` · `btn_raised_pressed_base.9.png` — 입체 버튼(길이가 달라도 모서리 · 그림자가 안 늘어남) |
| shape rectangle · oval | `drawable/bg_*.xml` |
| shape **ring** | `ring_pulse.xml`(알람 화면에서 퍼지는 고리), `ring_track.xml` · `ring_progress.xml`(진행률 고리, `useLevel`로 퍼센트만큼만 그림) |
| shape **line** | `divider_dashed.xml`(알람 시트 소리 목록의 점선) |
| corners · gradient(선형 · 방사형) · size · solid · stroke(점선 포함) | `bg_sheet.xml`, `bg_alarm_circle_inner.xml`, `bg_sheet_handle.xml`, `bg_chip_square.xml` |
| selector **state_pressed · state_focused** · state_checked · state_enabled | `btn_raised.xml`, `btn_flat.xml`, `bg_chip_square.xml`, `bg_preview_button.xml`, `color/selector_link_text.xml` 등 |
| 스타일 상속(parent) | `values/styles.xml` (`Widget.Modo.Button.Raised` ← `Primary` 등) |
| 값 리소스: 문자열 · 색 · 크기 · 스타일 · 배열 · 정수 | `values/strings*.xml`, `colors.xml`, `dimens.xml`, `styles.xml`, `arrays.xml`(요일 이름 `string-array` 등) |
| 플랫폼 리소스 | 코드: `android.R.drawable.ic_dialog_alert`(`Dialogs.confirmLeave`), `android.R.string.copy`(채팅 메뉴 "복사") / XML: `@android:string/search_go`(검색 버튼 설명, `tab_tasks.xml`), `@android:interpolator/…`, `@android:color/transparent` |
| `getString`, `getDimension`, `getDimensionPixelSize`, `getInteger` | 전반. `getDimension`: 가능 시간 격자의 글자 크기(`AvailabilityGridView`) / `getInteger`: 보드 칸 너비 비율(`BoardSection`), 회전 뒤 최대 줄 수(`MinutesEditActivity.onConfigurationChanged`) |
| `ResourcesCompat.getColor` | `Context.colorOf()`(`ui/common/Ui.kt`) — 코드에서 색을 읽는 모든 곳 |
| 한정자: 언어 | `values-en/` (문자열 688개, 한국어와 키 일치) |
| 한정자: 야간 | `values-night/colors.xml` |
| 한정자: 방향 | `layout-land/tab_home.xml`, `values-land/dimens.xml`(하단 탭 높이, 회의록 입력칸 최대 줄 수) |
| 한정자: 화면 크기 | `values-sw600dp/dimens.xml`(태블릿 여백 · 크기), `values-w600dp/dimens.xml`(보드 두 칸 보기) |
| 한정자: 밀도 | `drawable-*dpi/`, `mipmap-*dpi/` |
| 한정자: 버전 | `values-v31/themes.xml`(시작 화면 배경 속성), `mipmap-anydpi-v26/` |
| dp · sp | 전 레이아웃 |
| DisplayMetrics vs **WindowMetrics**(API 30) + SDK_INT 분기 | `ui/common/Screen.kt` → 보드 칸 너비 |
| 인트로 화면 | `ui/splash/SplashActivity.kt` (LAUNCHER) |

### 6. 다이얼로그와 알림 (10주차)

| 개념 | 위치 |
|---|---|
| API 호환: `SDK_INT` 분기 · `@RequiresApi` | 진동 API 31(`notify/Feedback.kt`), 벨소리 반복 API 28(`AlarmPlayer`), 잠금 화면 위 표시 API 27(`AlarmActivity`), 토스트 콜백 · WindowMetrics API 30, 알림 권한 · FLAG_MUTABLE API 33 · 31 |
| `@TargetApi` | 벨소리 반복(`AlarmPlayer.setLooping`, API 28). RequiresApi와 달리 부르는 쪽 확인을 대신해 주지 않아, 부르는 곳에서 `SDK_INT`를 확인 (lint는 RequiresApi를 권하지만 교안 개념이라 한 곳에 사용) |
| `<uses-permission>` | `AndroidManifest.xml` — 알림, 진동, 부팅 완료, 정확한 알람, 포그라운드 서비스, 전체 화면 알림, 마이크 |
| **`<permission>` 직접 만들기**(protectionLevel=signature) + `android:permission` | `com.modo.app.permission.INTERNAL_EVENTS` — 알림 버튼 리시버와 앱 내부 방송 보호 |
| checkSelfPermission | `Notifier.canPost`, `MyInfoTab.isGranted`, `MinutesEditActivity` |
| RequestPermission | 알림(`RootActivity`, `AlarmSetActivity`, 회의 타이머를 시작할 때 `MinutesEditActivity`), 마이크(`MinutesEditActivity`) |
| **RequestMultiplePermissions** | 내 정보 > 앱 권한(알림 + 마이크 한 번에, `MyInfoTab`) |
| Toast (`LENGTH_SHORT` · `LENGTH_LONG`) | 전반. 저장 실패 같은 오류 안내는 읽을 시간이 필요해 길게(`Context.failureToast`) |
| **Toast 콜백**(API 30) | "한 번 더 누르면 종료" — 토스트가 뜨면(`onToastShown`) 받기 시작하고 사라지면(`onToastHidden`) 그만 받음(`RootActivity.armWhileShown`) |
| DatePickerDialog | 업무 마감일, 프로젝트 기간, 회의 날짜, 알람 직접 설정(최소 · 최대 날짜 제한) |
| **TimePickerDialog** | 업무 마감 시간, 알람 직접 설정, 마감 알림 시각, 정기 회의 시간(`Dialogs.pickTime`), 회의 타이머 길이를 "시간 : 분"으로 고르기(`MinutesEditActivity.pickTimerLength`) |
| AlertDialog.Builder: 제목 · 내용 · Positive/Negative | `Dialogs.confirm` 등 |
| **setIcon · setNeutralButton · 리스너 하나에서 BUTTON_POSITIVE/NEGATIVE/NEUTRAL 구분 · setCancelable(false) · setCanceledOnTouchOutside(false)** | `Dialogs.confirmLeave` (저장 / 나가기 / 계속 작성) |
| setItems | 첨부 메뉴(`AttachPicker`), 보드 카드 길게 누르기(`BoardSection`), 프로필 사진 메뉴, 새 대화 상대 |
| setSingleChoiceItems | 담당자 · 프로젝트 고르기(`Dialogs.choose`), 기본 알람음(`MyInfoTab`), 화면 모드 |
| setMultiChoiceItems | 받을 알림 종류(`Dialogs.chooseMany`) |
| **RingtoneManager** | 기본 알람음(`AlarmPlayer.playSystem`), 채팅 채널 알림음(`Notifier.createChannels`) |
| **MediaPlayer** + res/raw | MODO 알람음 · 내 노래(`AlarmPlayer`), 완료 효과음(`Feedback.taskDone`) |
| **진동**(Vibrator · VibrationEffect · VibratorManager API 31) | `notify/Feedback.kt` — 알람 패턴 진동(`createWaveform(timings, amplitudes, repeat)`: 구간별 세기까지 지정), 완료 진동(`createOneShot`, `DEFAULT_AMPLITUDE`) |
| NotificationManager · NotificationCompat.Builder · 채널 + 채널 설정(설명, 배지, 소리+AudioAttributes, 불빛, 진동 패턴) | `notify/Notifier.kt` (채널 6개. 회의 타이머 채널은 소리를 앱의 `res/raw` 알람음 + 알람 음량으로 지정) |
| notify · cancel | `Notifier.post` / `cancelChat` 등 |
| 알림 묶음(setGroup · setGroupSummary) | 앱의 알림을 한 묶음으로(`Notifier.ensureSummary`) — 교안 밖 보완. Android 16이 묶이지 않은 알림을 스스로 묶으며 조용히 처리해 알람 배너가 안 뜨는 문제를 막음 |
| setOngoing · setAutoCancel(false) | 울리는 알람 알림, 올리는 중 알림 |
| PendingIntent.getActivity · getBroadcast · getService | 알림 누르면 화면 열기 / 알림 버튼 / [알람 끄기] |
| 액션(최대 3개) | 알람 알림: 완료로 표시 · 10분 뒤 다시 · 알람 끄기 |
| **RemoteInput** | 채팅 배너에서 바로 답장 |
| **setProgress** | 파일 올리기 진행률(`FileActions.upload`) |
| 스타일 4종 | Messaging(채팅 배너), BigPicture(사진 메시지), BigText(업무 배정), Inbox(아침 마감 목록) |

### 7. 액티비티 (11주차)

| 개념 | 위치 |
|---|---|
| Intent · startActivity · 매니페스트 등록 | 화면 20개 |
| putExtra / getStringExtra · getBooleanExtra · **getIntExtra** | `RootActivity.openTabFrom`(알림이 가리키는 탭 번호) 등 |
| 암시적 인텐트 | 기기 설정 열기(`MyInfoTab`), 음악 파일 고르기, 공유 |
| **setResult · finish + StartActivityForResult** | 업무 화면 ⇄ 알람 설정 시트(`AlarmSetActivity.RESULT_ALARM`), 음악 파일 고르기 |
| **PickContact** | 앱 안 쓰는 팀원 추가 때 연락처에서 이름 가져오기(`InviteActivity`) |
| **TakePicture** | 첨부 메뉴 "사진 찍기"(`AttachPicker`, FileProvider) |
| **TakePicturePreview** | 프로필 사진(`MyInfoTab`) |
| 생명주기 onCreate · onStart · onResume · onPause · onStop · onDestroy · **onRestart** | `AlarmActivity`(onStart/onStop에서 리시버 등록 · 해제), `RootActivity.onRestart`(돌아왔을 때 D-day 갱신), `ModoApp`(화면 수 세기) |
| onSaveInstanceState · **onRestoreInstanceState** | `TaskEditActivity`, `AlarmSetActivity`(저장), `RootActivity.onRestoreInstanceState`(탭 복원) |
| 코루틴(ANR 방지) | 스플래시 대기(`delay`), 회의 타이머의 남은 시간을 0.5초마다 다시 그리기(화면이 보일 때만, `MinutesEditActivity.watchTimer`), 파일 올리기 · 받기, AI 요약, 알림용 사진 받기 |

### 8. 브로드캐스트 리시버 (12주차)

| 개념 | 위치 |
|---|---|
| 정적 등록(매니페스트: `android:name` · `enabled` · `exported`) | `notify/AlarmReceiver`, `BootReceiver`, `NotificationActionReceiver` |
| BOOT_COMPLETED | `BootReceiver` — 재부팅 뒤 알람 다시 예약 |
| 동적 등록 · 해제 | `AlarmActivity`(onStart/onStop), `MinutesEditActivity`(onCreate/onDestroy) |
| sendBroadcast | `notify/AppEvents.send`(알람이 멈췄다는 신호 → 알람 화면이 닫힘), 직접 만든 퍼미션으로 보호 |
| SCREEN_OFF / SCREEN_ON | 회의 녹음 중 화면이 꺼지면 멈추고, 켜지면 이어서 녹음 |
| 배터리: BATTERY_LOW · OKAY · POWER_CONNECTED · DISCONNECTED | 녹음 중 배터리 경고 표시 · 숨김 |
| sticky 조회(ACTION_BATTERY_CHANGED: LEVEL · SCALE · STATUS · PLUGGED) | 녹음 시작 때 충전 상태와 남은 양 계산(`MinutesEditActivity.checkBattery`). 전원 종류는 `BATTERY_PLUGGED_AC` · `USB` · `WIRELESS`로 구분 |
| goAsync | `NotificationActionReceiver.keepAlive` |

---

## 2부. 교안에 있지만 넣지 않은 것

| 항목 | 이유 |
|---|---|
| `startActivityForResult`(옛 방식) | 지원 중단. 같은 일을 `ActivityResultLauncher`(StartActivityForResult 계약)로 함 |
| 한정자 mcc/mnc, ldrtl, round, car/tv/watch, 키보드 · 탐색 키 | 휴대폰용 팀플 앱과 무관 |
| pt · px · mm · in 단위 | 화면마다 크기가 달라지는 단위라 dp · sp만 사용 |
| JPG · GIF 그림 파일 | 넣을 사진 · 움직이는 그림이 없음 (PNG · 9.png · XML · WAV는 사용) |
| 문자 · 전화 · 위치 · 블루투스 · 저장소 등 무관한 권한 | 쓰지 않는 권한은 선언하지 않음 |
| 알림 채널의 `SDK_INT >= O` 분기 | minSdk가 26이라 항상 참 → 분기 없이 채널 생성 (다른 API 수준 분기는 6장 표 참고) |
| 카메라 권한 | 카메라 앱을 불러 찍는 방식(TakePicture)이라 권한이 필요 없음 |
| 옛 진동 방식 `vibrate(milliseconds)` · `vibrate(pattern, repeat)` | API 26부터 지원 중단. 앱의 minSdk가 26이라 `VibrationEffect`만 씀 |
| 교안 예시의 minSdk 24 | 앱은 minSdk 26 (날짜 · 시간 API와 알림 채널이 API 26부터). 그래서 API 24~25용 분기는 둘 곳이 없음 |
| XML에서 플랫폼 그림(`@android:drawable/…`) | 플랫폼 그림은 코드(`android.R.drawable.…`)에서 사용. XML에서는 플랫폼 문자열 · 색 · 보간기를 사용 |
| `registerReceiver`의 `RECEIVER_EXPORTED` | 앱 안에서 보내는 방송과 시스템 방송만 받으므로 `RECEIVER_NOT_EXPORTED`로 등록 (다른 앱이 보낸 방송은 받지 않음) |

교안의 "적용 기능" 문장과 쓰인 자리가 다른 것 (개념은 모두 반영): 삭제 확인창의 `setCancelable(false)` → 저장 안 한 변경 확인창 / 여러 팀원 선택의 `setMultiChoiceItems` → 받을 알림 종류 / 원격 입력으로 업무 메모 → 채팅 답장 / 진행률 알림으로 프로젝트 진행률 → 파일 올리기 진행률 / 업무 추가 화면의 `setResult` → 알람 설정 시트 / 업무 상태 변경 방송 → 알람이 멈췄다는 방송 / 화면 켬 감지 → 녹음 이어 하기 / 배터리 부족 안내 → 녹음 중 경고 / GridLayout 통계 → 월 달력.

---

## 3부. 교안에 없는데 앱에 들어 있는 것

Firebase(로그인 · Firestore · App Check)와 Supabase(파일)는 점검 대상에서 제외.

### 3-1. 이번에 교안 내용으로 바꾼 것

| 전(교안 밖) | 후(교안) |
|---|---|
| NumberPicker 시간 휠(직접 만든 다이얼로그) | TimePickerDialog (테마에서 휠 모양 지정) |
| MaterialButton 태그 42곳 | `<Button>` 41곳 (녹음 버튼 1곳은 코드에서 아이콘을 바꿔서 유지) |
| LinearProgressIndicator 10곳 | LinearLayout weight + shape (`view_bar.xml`) |
| CircularProgressIndicator 2곳 | shape ring + level (`ring_progress.xml`) |
| 담당자 · 프로젝트 드롭다운(AutoCompleteTextView) | setSingleChoiceItems 선택창 |
| PopupMenu 2곳(첨부 메뉴 · 보드 카드) | AlertDialog setItems |
| MaterialAlertDialogBuilder 7개 파일 | AlertDialog.Builder |
| ContextCompat.getColor | ResourcesCompat.getColor |

### 3-2. 남아 있는 것 — 바꿀 수 있는지 · 없어도 되는지

"교안으로 대체"가 **가능**인 것은 요청하면 바꿀 수 있음. 판단은 팀이.

**화면 부품**

| 항목 | 쓰는 곳 | 교안으로 대체 | 없어도 되는지 · 의견 |
|---|---|---|---|
| TextInputLayout(입력칸) | 9개 화면 56곳 | 가능: EditText + shape 배경. 떠오르는 라벨 · 오류 문구 · 아이콘을 직접 만들어야 해 작업량이 큼 | 유지 권장 |
| BottomNavigationView(하단 탭) | 홈, 워크스페이스 모드 | 가능: LinearLayout + 버튼. 단 `res/menu` 사용처가 사라짐 | 유지 권장 (res/menu 반영처) |
| TabLayout(위 탭) | 프로젝트 화면 1곳 | 가능: RadioGroup 칩 (소통 중심 모드는 이미 RadioGroup) | 바꿔도 무방 |
| FloatingActionButton | 4곳 | 가능: ImageButton + oval shape | 바꿔도 무방 (쉬움) |
| MaterialSwitch | 3곳 | 가능: CheckBox | 바꿔도 무방 (쉬움) |
| PopupMenu | 3곳(채팅 메시지 길게 누르기, 공지 메뉴, 프로젝트 ⋮) | 가능: setItems | 바꿔도 무방 (쉬움) |
| MaterialButton(녹음 버튼) | 1곳 | 가능: 글자만 바꾸고 아이콘 고정 | 바꿔도 무방 |
| ScrollView 계열 | 긴 화면 전부 | 불가 | 필요 |
| 커스텀 View(Canvas) | 월 달력, 가능 시간 격자 | 격자는 GridLayout + 버튼으로 가능하나 끌어서 고르기가 어려워짐 | 유지 권장 (터치 이벤트 반영처) |
| 화면 가장자리까지 그리기(WindowInsets) | 전 화면 | 불가 (targetSdk 35 이상은 강제) | 필요 |
| Material 3 테마 | 앱 전체 | 가능하나 전 화면 모양이 바뀜 | 유지 권장 |

**안드로이드 기능**

| 항목 | 쓰는 곳 | 교안으로 대체 | 없어도 되는지 · 의견 |
|---|---|---|---|
| AlarmManager | 업무 알람, 아침 마감 알림, 회의 타이머가 끝나는 시각 | 불가 | "정한 시각에 울림"에 필수. 빼면 앱이 켜져 있을 때만 알릴 수 있음 |
| 포그라운드 서비스 | 알람 소리 · 진동 30초 | 불가 | 앱이 꺼져 있어도 소리를 내려면 필수 |
| SharedPreferences | 테마 · 언어 · 알림 설정 · 알람 저장 | 불가(기기에 저장할 다른 교안 방법 없음) | 필요 |
| LiveData | 데이터가 바뀌면 화면 갱신 | 가능: 콜백 인터페이스. 전 화면 수정 | 유지 권장 |
| Handler(`postDelayed`) | 잠깐 뒤 실행: 알람 30초 제한 · 알람 화면의 지난 시간 표시 · 미리 듣기 멈춤 · 받아쓰기 다시 시작 등 10곳 | 가능: 코루틴 `delay` (회의 타이머 · 스플래시는 이미 코루틴). 알람 쪽은 바꾸면 다시 확인해야 함 | 동작은 같음. 바꿔도 무방 |
| 음성 인식(SpeechRecognizer) | 회의록 받아쓰기 | 불가 | 빼면 회의록은 직접 입력만. 화면 꺼짐 · 배터리 리시버 반영처도 같이 사라짐 |
| Gemini AI 요약 | 회의록 요약 · 할 일 뽑기 | 불가 | 없어도 회의록은 동작. 팀 결정 |
| Google 로그인(Credential Manager) | 로그인 화면 | — | 없어도 이메일 로그인 가능. 팀 결정 |
| FileProvider | 사진 찍어 첨부, 받은 파일 열기 | 불가 | TakePicture에 필요 |
| 네트워크(HttpURLConnection) | Supabase 파일 올리기 · 받기 | 불가 | 파일 첨부에 필요 |
| 사진 · 문서 선택기 | 첨부 | ActivityResult 계약의 연장 | 필요 |
| 앱 안에서 언어 · 테마 바꾸기 | 내 정보 | 기기 설정으로도 바뀜 | 유지 권장 (values-en · values-night 시연이 쉬움) |
| 클립보드 | 초대 코드 · 태그 복사 | — | 없어도 됨 (편의) |

**이번에 교안 반영을 위해 넣은 작은 기능** (어색하면 빼도 됨)

| 기능 | 반영한 교안 개념 | 의견 |
|---|---|---|
| 프로필 사진 (이 기기에만 저장, 다른 팀원에게는 안 보임) | TakePicturePreview | 공유가 안 돼 어색할 수 있음 |
| 연락처에서 팀원 이름 가져오기 | PickContact | 무해 |
| 내 정보 > 앱 권한 | RequestMultiplePermissions | 무해 |
| 녹음 중 배터리 경고 · 화면 꺼짐 뒤 이어서 녹음 | 배터리 · 화면 방송, sticky 조회 | 무해 |
| 완료 효과음 · 진동 (끌 수 있음) | MediaPlayer + raw, 진동 | 무해 |
| 종료 안내를 토스트가 떠 있는 동안만 받기 | Toast 콜백 | 무해 |
| 회의 타이머 (회의록 화면) | 교수님 항목 "타이머 + 알람" · 코루틴 · 알림 채널의 소리 · 진동 · TimePickerDialog | 회의 시간을 재는 데 쓸모 있음 |

**기능 자체가 교안과 무관한 것** (기술이 아니라 "기능"): 검색, 멘션, 투표, 회의 시간 찾기, 알림함 화면, 활동 기록, 샘플 프로젝트 만들기(시연용). 전부 v0.2 회의 피드백으로 넣은 것이라 그대로 둠.

**Kotlin 심화 · 개발 방식**: data class, sealed class, object, 확장 함수, 람다, suspend 함수, java.time, 정규식 / 단위 테스트 128개, Gradle 버전 카탈로그 — 코드 구조라 대체 대상 아님.

### 3-3. 채팅 알림의 범위

- **배너**(화면 위에 내려오는 알림)는 교안의 알림(채널 중요도 HIGH + MessagingStyle + RemoteInput) 범위 → 구현.
- **푸시**(서버가 보내는 알림, FCM)는 교안에 없음 → 구현하지 않음.
- 그래서 채팅 배너는 **앱을 보고 있을 때**(다른 화면을 보는 중)만 온다. 홈 화면으로 나가면 시스템이 앱의 통신을 멈춰서 오지 않고, 앱을 다시 열면 최근 2분 안에 온 메시지가 그때 뜬다 (에뮬레이터에서 확인).
- 업무 알람과 아침 마감 알림은 시스템 예약(AlarmManager)이라 앱이 꺼져 있어도, 재부팅한 뒤에도 온다.

---

## 4부. 교수님이 설명한 항목 (2026-11-21 점검)

| 항목 | 앱에서 하는 일 | 위치 |
|---|---|---|
| 상속 | 안드로이드 클래스를 상속해 화면 · 부품을 만들고, 앱 안의 공통 틀도 물려받아 구현 | 화면 20개 `: AppCompatActivity()` / 직접 만든 뷰 `MonthCalendarView : GridLayout`, `WeekStripView : LinearLayout`, `AvailabilityGridView : View` / `AlarmReceiver` · `BootReceiver` · `NotificationActionReceiver : BroadcastReceiver()` / `AlarmRingService : Service()` / `ModoApp : Application()` / `interface Section`을 구현한 프로젝트 탭 9개(`override fun render`) / `sealed class ScheduleItem` ← `Meeting` · `TaskDue` · `ProjectEnd` 등 8곳 / 스타일 `parent` 상속 |
| 레이아웃 배치 | LinearLayout · RelativeLayout · FrameLayout · GridLayout · ConstraintLayout 다섯 가지를 모두 사용 | 1부 3장 표 |
| 체크 표시를 누를 때 터치 이벤트 | 업무 체크박스를 누르는 동안 눌린 것처럼 작아지고(터치 이벤트: `MotionEvent.ACTION_DOWN` · `ACTION_UP`), 손을 떼면 완료 처리(클릭 이벤트) + 완료 효과음 · 진동 | `Items.addTask`, `View.addPressEffect`(`ui/common/Ui.kt`), `Context.changeTaskStatus`, `Feedback.taskDone` |
| 타이머 + 알람 | **회의 타이머**: 회의록 화면에서 길이(10 · 30 · 60분 · 직접)를 고르고 시작 → 남은 시간이 줄어드는 게 보임 → 끝나면 알람음 · 진동 · 알림(화면을 끄거나 앱을 나가 있어도). **업무 알람**: 고른 시각에 울림 → 30초 동안 울리며 지난 시간을 표시("재생 중 · 0:09 / 0:30") → 스스로 멈추고 10분 뒤 다시(최대 3번) | 타이머: `logic/TimerRules`(남은 시간 · 비율 계산, 단위 테스트), `MinutesEditActivity`(카드 · 코루틴으로 0.5초마다 갱신), `AlarmScheduler.startTimer`(끝나는 시각 예약), `AlarmReceiver`(끝남 방송 수신), `Notifier.timerDone`(알림) / 알람: `AlarmScheduler`, `AlarmRingService`, `AlarmActivity`, `AlarmSetActivity` |
| 사진 삽입 | 채팅 · 업무 · 회의록에 사진 첨부(사진 찍기 `TakePicture` / 사진 선택기), 말풍선과 첨부 목록에 미리보기(ImageView), 프로필 사진(`TakePicturePreview`), 사진 메시지 알림(BigPicture) | `ui/files/AttachPicker`, `FileActions.loadThumbnail`, `ui/root/MyInfoTab`, `ui/common/ProfilePhoto` |

참고
- 상속: 직접 만든 부모 클래스(`open` · `abstract class`)는 없다. 앱 안의 물려받기는 인터페이스(`Section`)와 `sealed class`로 한다.
- 타이머: 남은 시간 표시는 교안의 코루틴으로 만들었다(안드로이드의 타이머 전용 도구 CountDownTimer · Chronometer는 교안에 없어 쓰지 않음). 끝날 때 울리는 예약은 업무 알람과 같은 방식(AlarmManager).
