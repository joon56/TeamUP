# TeamUp Implementation Plan

> 실행: 이 세션에서 직접 (inline). 사용자 지시("일단 전부 진행")에 따라 단계별 코드 전문 대신 파일·인터페이스·검증 기준만 기록.

**Goal:** 설계 문서(`docs/specs/2026-11-06-teamup-design.md`)의 TeamUp 초안 앱을 빌드·설치 가능한 상태로 만든다.

**Architecture:** XML + ViewBinding Activity 앱. `TeamRepository`가 Firestore 실시간 리스너로 `LiveData<AppData>`를 내보내고, 화면은 이를 observe해서 그린다. 계산 로직은 `logic/` 순수 Kotlin으로 분리해 JUnit으로 검증.

**Tech Stack:** Kotlin(AGP 9 built-in), AGP 9.3.3, Gradle 9.5.0, compileSdk/targetSdk 37, minSdk 26, Material 1.14.0, AppCompat 1.8.0, Firebase BoM 35.1.0 (Auth, Firestore), google-services 4.5.0.

## Global Constraints

- 프로젝트 경로: `D:\TeamUp` (ASCII 경로), 패키지 `com.teamup.app`
- JDK: Android Studio JBR (`C:\Program Files\Android\Android Studio\jbr`), SDK `D:\Android\Sdk`, Gradle home `D:\Android\.gradle`
- Fragment, RecyclerView, Compose 미사용. 목록은 LinearLayout에 inflate
- 기본 문자열은 한국어(`values/`), 영어는 `values-en/`
- 기본 테마 다크, 포인트 컬러 보라
- 날짜 `yyyy-MM-dd` 문자열, 시각 epoch millis

---

### Task 1: 프로젝트 골격
**Files:** `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `gradle/wrapper/*`, `gradlew(.bat)`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `.gitignore`
**Verify:** `gradlew :app:assembleDebug` 성공

### Task 2: 계산 로직 (TDD)
**Files:** `logic/WeekCalculator.kt`, `logic/ProgressCalculator.kt`, `logic/TaskRules.kt`, `logic/InviteCode.kt`, `logic/Greeting.kt` + `src/test/.../*Test.kt`
**Produces:**
- `WeekCalculator.firstMeeting(start: LocalDate, meetingDay: Int): LocalDate`
- `WeekCalculator.weekIndex(start, meetingDay, date): Int`, `weekRange(start, meetingDay, index): ClosedRange<LocalDate>`, `totalWeeks(start, end, meetingDay): Int`
- `ProgressCalculator.rate(tasks): Int?` (업무 0개면 null), `weekRate(project, tasks, index): Int?`, `memberStats(members, tasks): List<MemberStat>`, `statusCounts(tasks, today): StatusCounts`
- `TaskRules.isOverdue(task, today)`, `dDay(date, today): Int`, `isDueSoon(task, today, days = 3)`
- `InviteCode.generate(random): String` (6자, 혼동 문자 제외)
- `Greeting.of(hour): GreetingType`
**Verify:** `gradlew :app:testDebugUnitTest` 전부 통과

### Task 3: 디자인 시스템 리소스
**Files:** `res/values(-night)/colors.xml`, `values/dimens.xml`, `values/themes.xml`, `values/styles.xml`, `res/font/*`, `res/drawable/ic_*.xml`(Material Symbols), `bg_*.xml`, `mipmap-anydpi-v26/ic_launcher*.xml`, `values/strings.xml`, `values-en/strings.xml`
**Verify:** 빌드 성공, 런처 아이콘 표시

### Task 4: 데이터 계층
**Files:** `data/Models.kt`, `data/FirestoreMapper.kt`, `data/TeamRepository.kt`, `data/SampleData.kt`
**Produces:** `TeamRepository.data: LiveData<AppData>`, `start(uid)`, `stop()`, `createProject`, `updateProject`, `deleteProject`, `joinByCode`, `addMember`, `updateMember`, `removeMember`, `saveTask`, `setTaskStatus`, `postponeTask`, `deleteTask`, `updateMyName`, `createSampleProject`
**Verify:** 매퍼 왕복 단위 테스트 통과, 빌드 성공

### Task 5: Firebase 프로젝트 연결
**Files:** `app/google-services.json`, `firebase.json`, `.firebaserc`, `firestore.rules`, `firestore.indexes.json`
**Steps:** CLI로 프로젝트·Android 앱 생성 → 설정 파일 받기 → Firestore DB(asia-northeast3) 생성 → 규칙 배포 → 이메일/비밀번호 로그인 활성화
**Verify:** 에뮬레이터에서 회원가입 성공, Firestore에 `users/{uid}` 생성

### Task 6: 로그인 + 메인 탭 + 홈
**Files:** `ui/auth/LoginActivity.kt`, `ui/main/MainActivity.kt`, `ui/main/HomeTab.kt`, `ui/common/*`, 레이아웃
**Verify:** 레퍼런스와 같은 구성의 홈 화면 스크린샷

### Task 7: 프로젝트 만들기·상세·팀원
**Files:** `ui/project/ProjectEditActivity.kt`, `ui/project/ProjectDetailActivity.kt`, `ui/member/MemberDetailActivity.kt`, 다이얼로그, 레이아웃
**Verify:** 프로젝트 생성 → 상세 3개 세그먼트 표시, 초대 코드 공유 시트 열림

### Task 8: 업무 편집·업무 탭·일정 탭
**Files:** `ui/task/TaskEditActivity.kt`, `ui/main/TasksTab.kt`, `ui/main/ScheduleTab.kt`, 레이아웃
**Verify:** 업무 추가 → 체크 → 이번 주·전체 진행률 갱신, 지연 업무 미루기 동작

### Task 9: 설정·검색·가로 모드·영어
**Files:** `ui/main/SettingsTab.kt`, `ui/search/SearchActivity.kt`, `layout-land/*`, `values-en/*`
**Verify:** 테마·언어 전환, 회전 시 2단 레이아웃, 검색 Enter 동작

### Task 10: 통합 검증·배포물
**Steps:** 계정 2개로 초대 코드 합류·실시간 반영 확인 → `assembleDebug` APK → 팀 보고 자료
**Verify:** 두 계정 화면에 같은 진행률 표시, APK 설치 성공
