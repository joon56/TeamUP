# TeamUp — 팀 프로젝트 관리 앱 설계 (초안)

작성일: 2026-11-06 · 상태: 초안 승인됨 (개발 진행)

## 1. 목표

팀원 각자가 자기 폰에서 맡은 업무를 체크하면, 모든 팀원의 대시보드에 프로젝트 진행률이 실시간으로 반영되는 팀 프로젝트 관리 앱.
수업(모바일 프로그래밍 2~9주차)에서 배운 위젯·레이아웃·이벤트·리소스를 최대한 활용하고, 데이터 동기화는 Firebase로 한다.

## 2. 결정 사항

| 항목 | 결정 | 이유 |
|---|---|---|
| 데이터 | Firebase Auth(이메일/비밀번호) + Cloud Firestore | 팀원 간 실시간 동기화, 초대 코드 합류가 실제로 동작 |
| 오프라인 | Firestore 기본 오프라인 캐시 | 시연장 네트워크가 불안해도 동작, 재연결 시 자동 동기화 |
| UI | XML + ViewBinding, Activity 기반 (Fragment 미사용) | 수업 내용(5~7주차)과 일치 |
| 목록 | LinearLayout에 항목 inflate (RecyclerView 미사용) | 수업 기술 정리의 "LinearLayout: 팀원·업무 목록 배치" 반영 |
| 최소 버전 | minSdk 26 / target·compile 37 | `java.time` 날짜 API 사용 |
| 빌드 | AGP 9.3.3, Gradle 9.5.0 (수업 템플릿과 동일) | 팀원 PC의 Android Studio와 호환 |
| 디자인 | 다크 + 보라 포인트 (레퍼런스 "Linear Feel"), 라이트 테마 선택 가능 | 레퍼런스 이미지 |
| 글꼴 | Pretendard (SIL OFL) | 한글 가독성, 레퍼런스 분위기 |
| 아이콘 | Material Symbols Rounded (Apache 2.0) → Vector Drawable | 일관된 아이콘 |

## 3. 화면 구성

하단 탭 4개를 가진 `MainActivity` + 상세/편집 화면은 별도 Activity.

| 화면 | 내용 |
|---|---|
| 로그인 / 회원가입 | 이메일, 비밀번호, (가입 시) 이름. 키보드 Enter로 다음 칸 이동·제출 |
| 홈 (탭) | 시간대별 인사, `+ 새 프로젝트` / `코드로 참여` 버튼, 통계 2×2(전체·완료·진행 중·지연), 내 프로젝트 카드(D-day·이번 주 진행률), 다가오는 일정(정기 회의·임박 업무), 최근 변경 업무 |
| 업무 (탭) | 내 모든 프로젝트의 업무. 상태 필터(전체/예정/진행 중/완료/지연), "내 업무만", 마감순 정렬, 체크박스로 완료 |
| 일정 (탭) | 지연 / 오늘 / 이번 주 / 이후로 묶은 타임라인 (업무 마감, 정기 회의, 프로젝트 마감) |
| 설정 (탭) | 내 이름, 테마(다크/라이트/시스템), 언어(한국어/English), 시연용 샘플 프로젝트 만들기, 로그아웃 |
| 프로젝트 만들기/수정 | 이름, 설명, 시작일, 마감일, 색상, 정기 회의 요일·시간 |
| 프로젝트 상세 | 기간·D-day·초대 코드(복사·공유), 세그먼트 [개요 / 업무 / 팀원] |
| 업무 추가/수정 | 제목, 설명, 담당자, 마감일, 우선순위(높음/보통/낮음), 상태(예정/진행 중/완료), 다음 주로 미루기, 삭제 |
| 팀원 상세 | 이름·역할, 완료 현황, 담당 업무 목록 |
| 검색 | 업무·프로젝트 이름 검색 |

상단 벨 아이콘: 지연 + 3일 내 마감 업무 개수 배지, 누르면 일정 탭.
가로 모드: 홈·프로젝트 상세는 2단 레이아웃 (`layout-land`).

## 4. 주간 진행률 규칙

팀은 매주 한 번 회의하므로 전체 업무량을 처음부터 정하기 어렵다. 그래서 진행률의 기본 단위를 "주차"로 둔다.

- 프로젝트마다 정기 회의 요일·시간을 정한다 (예: 수요일 19:00).
- **주차 = 회의일부터 다음 회의 전날까지.** 1주차는 프로젝트 시작일부터 두 번째 회의 전날까지.
- 업무는 **마감일이 속한 주차에 자동 배정**된다. 주차를 따로 입력하지 않는다.
- 이번 주 진행률 = 이번 주차 업무 중 완료 비율. 전체 진행률 = 전체 업무 중 완료 비율.
- 홈 카드에는 이번 주 진행률, 프로젝트 상세에는 이번 주·전체·주차별 막대를 모두 표시.
- 마감일이 지났는데 완료되지 않은 업무 = **지연**. `다음 주로 미루기`는 마감일을 7일 뒤로 옮긴다.
- 업무가 없는 주차는 0%가 아니라 "업무 없음"으로 표시.

계산식 (첫 회의일 `F` = 시작일 이후 첫 회의 요일):

```
weekIndex(d) = max(1, (d - F).days / 7 + 1)   // 정수 나눗셈은 0 방향 버림
```

## 5. 데이터 모델 (Firestore)

```
users/{uid}                       name, email, createdAt
inviteCodes/{code}                projectId, ownerUid
projects/{projectId}              name, description, startDate, endDate (yyyy-MM-dd),
                                  colorIndex, meetingDay(1=월..7=일), meetingTime("19:00"),
                                  inviteCode, ownerUid, memberUids[], createdAt, updatedAt
projects/{projectId}/members/{id} name, role, uid(앱 가입자면 uid, 수동 추가면 null), joinedAt
projects/{projectId}/tasks/{id}   title, description, assigneeId?, dueDate, priority(HIGH/MEDIUM/LOW),
                                  status(TODO/IN_PROGRESS/DONE), createdBy, createdAt, updatedAt, completedAt?
```

- 팀원은 두 종류: 초대 코드로 합류한 앱 사용자(문서 id = uid), 앱을 안 쓰는 팀원을 이름·역할로 수동 추가.
- 프로젝트를 만든 사람은 자동으로 첫 팀원이 된다. "내 업무" = 담당자 id가 내 uid인 업무.
- 날짜는 `yyyy-MM-dd` 문자열, 시각은 epoch millis(Long).

## 6. 보안 규칙 요약

- 로그인한 사용자만 접근.
- 프로젝트·팀원·업무는 `memberUids`에 포함된 사람만 읽기/쓰기.
- 초대 코드는 코드를 정확히 아는 경우에만 단건 조회 가능 (목록 조회 불가).
- 합류: 같은 batch에서 (1) 자기 member 문서를 올바른 코드와 함께 생성, (2) `memberUids`에 자기 uid만 추가 — 규칙이 둘 다 검증.
- 프로젝트 삭제는 만든 사람만.

## 7. 코드 구조

```
com.teamup.app
├─ TeamUpApp                 Application: 테마·언어 적용
├─ data/  Models, TeamRepository(Firestore 실시간 리스너 → LiveData<AppData>), FirestoreMapper, SampleData
├─ logic/ WeekCalculator, ProgressCalculator, TaskRules, InviteCode, Greeting   ← 순수 Kotlin, 단위 테스트
├─ ui/auth     LoginActivity
├─ ui/main     MainActivity + HomeTab, TasksTab, ScheduleTab, SettingsTab
├─ ui/project  ProjectEditActivity, ProjectDetailActivity
├─ ui/task     TaskEditActivity
├─ ui/member   MemberDetailActivity
├─ ui/search   SearchActivity
└─ ui/common   목록 항목 바인더, 다이얼로그, 터치 효과, 날짜 포맷
```

화면은 `TeamRepository.data`(LiveData)를 observe해서 그린다. 쓰기는 Firestore에 바로 하고, 리스너가 변경을 다시 내려주면 모든 화면이 자동 갱신된다 (오프라인에서도 로컬 캐시에 먼저 반영).

## 8. 수업 내용 매핑

| 수업 항목 | 적용 위치 |
|---|---|
| val/var, fun, if/when | 상태·우선순위 → 색/문구 변환(`when`), 시간대 인사 |
| for/while, List/MutableList | 목록 inflate, 초대 코드 중복 검사 `while` |
| indices / withIndex() | 주차별 진행률 막대, 색상 선택 버튼 |
| 널 허용 `?` | 담당자 없음(`assigneeId: String?`), 설명 없음 |
| lateinit / lazy | ViewBinding(`lateinit`), 날짜 포맷터·리포지토리(`lazy`) |
| TextView/EditText/Button/ImageView | 전 화면 |
| CheckBox | 업무 완료 체크 |
| RadioButton/RadioGroup | 우선순위, 상태, 테마, 언어, 프로젝트 색상, 업무 필터 |
| ViewBinding | 모든 Activity·목록 항목 |
| LinearLayout | 프로젝트·업무·팀원 목록 |
| RelativeLayout | 업무 카드 내부 배치 |
| FrameLayout | 원형 진행률 + 가운데 % 겹치기, 탭 컨테이너, 프로필 이니셜 |
| GridLayout | 홈 통계 2×2 |
| ConstraintLayout | 홈, 업무 상세 화면 |
| 클릭 이벤트 | 추가·수정·삭제·화면 이동 |
| 터치 이벤트 | 카드 누를 때 눌림 효과 (`OnTouchListener`) |
| 키 이벤트 | 검색 Enter, 로그인 입력칸 이동 (`OnKeyListener`, IME 액션) |
| 뒤로 가기 | 저장 안 한 변경 확인 팝업, 탭 → 홈, 두 번 눌러 종료 (`OnBackPressedCallback`) |
| drawable / mipmap | 벡터 아이콘·카드 배경 / 앱 아이콘 |
| strings / colors / dimens / styles / fonts | 전체 리소스 분리 |
| 리소스 조건 | `values-en`(영어), `values-night`(다크), `layout-land`(가로) |

## 9. 테스트

- 순수 로직(주차 계산, 진행률, 지연 판정, D-day, 초대 코드)은 JUnit 단위 테스트.
- 에뮬레이터(Pixel 7, API 37)에서 가입 → 프로젝트 생성 → 업무 추가 → 완료 체크 → 진행률 갱신 확인.
- 계정 2개로 초대 코드 합류와 실시간 반영 확인.

## 10. 범위 밖 (이번 초안 제외)

채팅, 푸시 알림, 파일 첨부, 구글 로그인. 필요하면 다음 주차에 추가.
