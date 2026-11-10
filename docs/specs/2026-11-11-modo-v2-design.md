# MODO v0.2 설계 — 회의 피드백(2026-11-10) + 플로우 시안 4종

> v0.1(TeamUp) 위에서 이어 만드는 2차 개편. 원본 피드백은 회의록, 시안은 `docs/reference/flows/*.pdf`.

## 1. 목표

1. 화면 체계를 **루트 홈 ↔ 프로젝트 홈** 2단으로 나눈다 (넷플릭스 프로필처럼 프로젝트를 고르면 그 프로젝트의 공간으로 들어감).
2. 홈을 간결하게: 주간 캘린더 한 줄 + 마감 임박 강조 + 진행 중 프로젝트 + 선택한 날 일정. 모든 탭을 줄글 나열 대신 탭·소분류로 정리.
3. when2meet식 회의 시간 조율.
4. 채팅(팀방 + 1:1, 카톡식 목록) + 파일 첨부(노션식).
5. 팀원 초대는 태그(이름#1234) 또는 이메일로 정확한 사람을 찾아서.
6. 앱 이름 TeamUp → **MODO**.
7. 버그: 알림 배지 잘림, 시계형 시간 선택 → 스크롤 휠, "예정" 목록에 "지연" 섞임.
8. 플로우 시안 4종을 **프로젝트별 화면 모드**로 구현하고, 테스트 프로젝트 4개에 하나씩 적용해 비교.

## 2. 확정된 결정

| 주제 | 결정 |
|---|---|
| 시안 적용 범위 | 개별 **프로젝트 홈에만** 적용. 루트 홈은 공통 1개 |
| 파일 저장 | **Supabase Storage**(무료). Firebase 로그인 토큰으로 인증 (Supabase Third-party Auth) |
| 이름만 넣는 팀원 | 유지하되 **보조 메뉴**로. 기본은 태그·이메일 초대 |
| 이름 변경 | 화면·문서·APK + 패키지 `com.teamup.app` → **`com.modo.app`** |
| 시각 스타일 | 지금 앱(보라 `#7C5CFF`, 다크 기본, Pretendard) 유지하며 다듬음 |
| 시간 선택 | NumberPicker 스크롤 휠 시·분 (초 없음) |
| 업무 상태 | 할 일 / 진행 중 / 완료. 마감 지난 미완료는 **지연**으로 따로 분류 (목록 필터는 서로 겹치지 않음) |
| 초대 | 태그·이메일로 찾은 프로필에 초대 → 상대가 알림에서 수락해야 합류. 초대 코드도 유지 |
| 회의 시간 조율 | 요일 × 30분 격자. 가입 직후 "보통 되는 시간" 질문(건너뛰기 가능), 프로젝트별 히트맵·추천 TOP 3 |
| 채팅 알림 | 푸시 없음(서버 필요). 앱 안 배지만 |
| 회의록·STT·AI | 구현. STT = 안드로이드 SpeechRecognizer, AI = Firebase AI Logic(Gemini Developer API, Spark 무료 한도) |
| 소셜 로그인 | Google 로그인 |
| 화면 모드 저장 위치 | 프로젝트 문서(`mode`) — 팀 전체 공통. 아무 팀원이나 프로젝트 설정에서 변경 |
| 배포 | 에뮬레이터에서 단계별 검증. 보안 규칙은 기존 v0.1 앱이 깨지지 않게 추가 위주. GitHub 푸시·APK 공유는 사용자 확인 후 |

## 3. 정보 구조

### 3.1 공통 (모든 화면)

- **앱 시작**: 스플래시(자동 로그인 확인) → 로그인 ↔ 회원가입 → (첫 가입이면) 가능 시간 입력 → **루트 홈**.
  앱을 완전히 종료했다 켜면 항상 루트 홈, 백그라운드였다면 보던 화면 (안드로이드 기본 동작 그대로: 루트가 런처 액티비티, 프로젝트·상세는 그 위에 쌓임).
- **상단 바**: 왼쪽 = 전환 버튼(루트에선 `MODO ▾`, 프로젝트에선 `● 프로젝트명 ▾`), 오른쪽 = 🔔 알림 · 💬 채팅 (둘 다 안 읽은 수 배지).
- **전환 시트** (위에서 내려오는 시트): 내 프로필 한 줄 / 전체 홈 / 진행 중 프로젝트(진행률·모드) / 끝난 프로젝트(접힘) / 새 프로젝트 · 코드로 참여.

### 3.2 루트

| 하단 탭 | 내용 |
|---|---|
| 홈 | 인사 한 줄 → **주간 캘린더 한 줄**(이번 주 7일, 회의·마감 점, ‹ › 주 이동, 날짜 탭하면 아래 목록이 그날로) → **마감 임박 띠**(내 업무 중 지연·D-2 이내, 최대 3개 + 더보기) → **진행 중 프로젝트** 가로 카드(이번 주 진행률·다음 회의·모드) → **선택한 날 일정**(전 프로젝트 회의·마감). 숫자 박스 4개는 삭제 |
| 할 일 | 전 프로젝트 통합. 세그먼트 `할 일 · 진행 중 · 지연 · 완료`(개수 표시) + "내 업무만" 토글 + 날짜 묶음(오늘/이번 주/나중) |
| 일정 | 월간 캘린더(GridLayout) + 선택한 날 목록. 전 프로젝트 회의·마감·종료일 |
| 내 정보 | 프로필(아바타·이름#태그 복사·이메일·학교) / 가능 시간 / 테마·언어 / 로그아웃 / 앱 정보 |

### 3.3 프로젝트 — 화면 모드 4종 (프로젝트 설정 → 화면 모드)

공통 구성 요소(섹션): 개요(오늘형·진행률형), 보드, 일정, 회의록, 채팅·공지, 공지, 팀원, 파일, 더보기.
프로젝트 안에서는 루트 하단 탭을 숨긴다 (모드 3만 프로젝트 전용 하단 탭).

| 모드 | 첫 화면 | 내비게이션 |
|---|---|---|
| 1 오늘 (Today-first) | 개요-오늘형: 이 프로젝트의 **내 오늘 할 일**(체크) · 임박 마감 D-day · 이번 주 진행률 · 고정 공지 | 상단 탭 6: 개요 / 보드 / 일정 / 회의록 / 채팅·공지 / 팀원 |
| 2 진행률 (Progress-first) | 개요-진행률형: 이번 주·전체 진행률 링 · **팀원별 진행률** · 남은 업무 · 다음 회의 · 최근 활동(회의록·공지·완료) | 상단 탭 6 (모드 1과 같음) |
| 3 워크스페이스 (Workspace-first) | **보드** | 하단 탭 5: 보드 / 일정 / 회의록 / 채팅·공지 / 더보기(개요·팀원·파일·설정) |
| 4 소통 (Chat-first) | **팀 채팅** + 위 띠(오늘 내 할 일 n · 가장 가까운 D-day) | 상단 툴바 6: 채팅 / 공지 / 보드 / 일정 / 회의록 / 팀원. 메시지 길게 누르기 → 할 일로 전환 |

테스트 프로젝트 4개: `[시안1] 오늘 중심`, `[시안2] 진행률 중심`, `[시안3] 워크스페이스`, `[시안4] 소통 중심`.

### 3.4 상세 화면

업무 상세·생성(담당자·마감·우선순위·상태 휠·첨부), 회의록 작성(녹음 STT·AI 요약 → 보드 등록), 공지 작성(투표 옵션), 팀원 초대(태그·이메일 검색 / 초대 코드 / 앱 안 쓰는 팀원), 팀원 상세(업무·가능 시간·1:1 채팅·팀장 기능), 가능 시간 편집, 회의 시간 찾기(히트맵·추천), 채팅 목록·채팅방, 알림, 검색, 프로젝트 설정(이름·과목·기간·색·회의·모드·나가기·삭제).

## 4. 데이터 모델 (Firestore)

```
users/{uid}                 name, email, tag("4821"), school, avatarColor, availability[7]*, notifReadAt, createdAt
userHandles/{이름소문자#태그}  uid, name, tag, avatarColor, school     ← 태그 검색 (단건 get만)
userEmails/{이메일소문자}      uid, name, tag, avatarColor, school     ← 이메일 검색 (단건 get만)
inviteCodes/{code}          projectId, ownerUid
projects/{pid}              (기존) + subject, mode, meetingDurationMin, meetingDecided
  members/{mid}             (기존) + tag, avatarColor, availability[7]*, inviteId
  tasks/{tid}               (기존) + attachments[], sourceMessage{roomId,messageId}
  notices/{nid}             title, body, authorUid, authorName, pinned, poll{question, options[{id,text}], multiple}?, createdAt
    votes/{uid}             optionIds[], at
  minutes/{id}              title, meetingDate, body, transcript, summary{decisions[], actions[{title,assignee,due}]}, attendeeIds[], attachments[], createdBy, createdByName, createdAt, updatedAt
  invites/{iid}             projectId, projectName, fromUid, fromName, toUid, toName, status(pending|accepted|declined|canceled), createdAt
  files/{fid}               name, size, mime, path, uploadedBy, uploadedByName, source(task|minutes|chat|direct), sourceId, createdAt
chatRooms/{roomId}          type(project|direct), projectId?, memberUids[], names{uid:name}, lastMessage{text,type,senderUid,senderName,at}, updatedAt, unread{uid:n}
  messages/{mid}            senderUid, senderName, type(text|image|file|notice|poll|task|system), text, file{name,size,mime,path}?, refId?, mentions[], createdAt
```

- `availability`: 7개 Long(월~일). 비트 i = 00:00부터 30분 단위 i번째 칸이 가능.
- 방 id: 프로젝트방 `p_{pid}`, 1:1방 `d_{작은uid}_{큰uid}`.
- 안 읽은 수: 보내는 사람이 같은 batch에서 다른 참여자의 `unread.{uid}`를 +1, 읽는 사람이 자기 값을 0으로.
- 상태 값(`TODO/IN_PROGRESS/DONE`)과 기존 필드는 그대로 → v0.1 앱과 호환.

### 4.1 보안 규칙 요약

- users: 본인만. userHandles·userEmails: 로그인 사용자는 단건 get만, 목록 불가, 쓰기는 본인 것만(이메일 문서 id는 토큰 이메일과 같아야 함).
- projects 하위(members/tasks/notices/minutes/files/invites): 팀원만. 합류는 (a) 초대 코드 일치 또는 (b) 나에게 온 초대를 같은 batch에서 accepted로 바꾼 경우만.
- 팀장 넘기기: 현재 팀장만 `ownerUid`를 다른 팀원으로 변경 가능. 초대 코드 삭제는 현재 팀장.
- invites: 팀원이 생성(fromUid=본인), 받은 사람은 컬렉션 그룹 쿼리(`toUid == 나`)로 조회·상태 변경.
- chatRooms: 참여자만 읽기. 프로젝트방 참여자 목록은 항상 프로젝트 `memberUids`와 같아야 함(합류·나가기 batch에서 함께 갱신). 메시지는 참여자만, 보낸 사람 = 본인.
- 멘션 알림: `messages` 컬렉션 그룹에서 `mentions array-contains 나`만 허용.

## 5. 외부 서비스

| 서비스 | 용도 | 설정 |
|---|---|---|
| Supabase Storage (무료) | 파일·사진 | Management API로 프로젝트·비공개 버킷 `modo-files`·Firebase Third-party Auth·RLS(iss/aud 확인) 구성. 앱은 REST + Firebase ID 토큰. 경로 `projects/{pid}/{uuid}/{파일명}`, `dm/{roomId}/{uuid}/{파일명}`. 파일당 50MB |
| Firebase AI Logic (Gemini Developer API) | 회의록 요약·할 일 추출 (`gemini-3.8-flash`, JSON 스키마 응답) | 콘솔에서 시작하기 1회. API 키 허용 목록에 `firebasevertexai`·`firebaseappcheck` 추가. App Check 필수(2026-11-02부터 자동 적용) → 디버그 빌드는 디버그 공급자, 기기별 디버그 토큰을 콘솔에 등록(또는 적용 시점을 2027-01-21까지 늦춤). 무료 요금제는 보낸 내용이 제품 개선에 쓰일 수 있어 처음 요약 때 안내 |
| Google 로그인 | 소셜 로그인 | 콘솔에서 Google 제공업체 사용 설정 + 서명 SHA-1 등록 |
| SpeechRecognizer | 회의 녹음 받아쓰기 | 기기 음성 인식(무료). 오디오 파일은 저장하지 않음 |

한계: 파일 접근 권한은 "우리 Firebase 프로젝트 로그인 사용자 + 추측 불가능한 경로"까지만 보장(Supabase가 Firestore 팀원 여부를 모름). 문서에 명시.

## 6. 로직

- **업무 분류** `TaskRules.category(task, today)`: 완료 → DONE, 마감 < 오늘 → OVERDUE, 그 외 상태대로 TODO/IN_PROGRESS. 필터·세그먼트는 이 분류를 쓴다. 보드는 상태 열(할 일/진행 중/완료)에 두고 지연은 빨간 배지로 표시.
- **마감 임박**: 내 미완료 업무 중 지연 또는 D-0~D-2.
- **회의 시간 추천** `MeetingFinder`: 회의 길이 L칸(기본 60분 = 2칸)에 대해 요일·시작 칸마다 L칸 모두 가능한 팀원 수를 세고, 많은 순 → 겹치지 않게 TOP 3. 동점이면 평일·저녁(18~22시) 우선, 그다음 이른 요일·시간. 2순위부터는 인원이 같을 때 아직 안 고른 요일을 먼저(같은 날 연달은 시간만 나오지 않게). 가능 시간을 입력한 팀원만 분모에 넣고 "n명 중 m명"과 불가 인원을 표시.
- **회의 시간 찾기 화면**: 입력 현황(팀원 칩, 나·앱 안 쓰는 팀원은 눌러서 입력) → 회의 길이 30/60/90/120 → 추천 카드(정기 회의로 지정 시 프로젝트 회의 요일·시간을 바꾸고 팀 채팅에 시스템 메시지) → 히트맵(칸을 누르면 가능한 사람). 아직 입력 안 한 앱 사용자를 멘션해 "입력 요청" 시스템 메시지(누르면 이 화면).
- **추천 변경 알림**: "팀원 시간 모아서" 프로젝트에서 2명 이상 입력했고, 지금 1순위가 이 기기에서 마지막으로 본 1순위(`Prefs.meetingSeen`)와 다르면 알림 항목. 시각은 가장 최근 가능 시간 입력(`members.availabilityAt`).
- **태그**: 가입 시 1000~9999 무작위 → `userHandles/{이름#태그}` 생성(이미 있으면 규칙상 실패 → 다시 뽑기, 최대 5회). 이름 바꾸면 같은 태그로 다시 등록, 안 되면 새 태그. v0.1 사용자는 앱 시작 시 자동 발급.
- **알림 화면**(파생): 받은 초대 / 마감(지연·D-2) / 최근 공지(14일) / 나를 멘션한 메시지 / 추천 회의 시간 변경. `notifReadAt` 이후 것만 배지에 셈.
- **최근 활동**(모드 2): 공지 작성·회의록 작성·업무 완료·팀원 합류를 시간순으로 합침.
- **회의록 AI**: 요청문(`MinutesPrompt`)에 프로젝트·회의 날짜·참석자·팀원 이름·메모·받아쓰기(길면 끝부분 우선)를 넣고, 응답 JSON을 `SummaryParser`로 읽는다(코드 블록·빠진 필드·null 허용). 할 일 후보의 담당자는 `ActionMatcher`가 팀원 이름에 맞추고(같은 이름 → "님" 뗀 이름 → 유일한 부분 일치), 마감은 ISO·"11/14"·"11월 14일"을 읽는다. 보드에 등록한 후보는 `taskId`를 남겨 중복 등록을 막는다.
- **받아쓰기**: `SpeechRecognizer`(ko-KR)를 끊길 때마다 다시 시작해 이어 듣고(조용함·바쁨·과다 요청은 간격을 두고 재시작), 확정 문장만 받아쓰기에 붙인다. 녹음 중에는 화면을 켜 두고, 화면을 벗어나면 멈춘다.

## 7. 수업 내용 반영 (추가분)

| 수업 내용 | 적용 |
|---|---|
| GridLayout | 월간 캘린더 7열 격자 |
| 터치 이벤트 | 가능 시간 격자 드래그로 칠하기(커스텀 View `onTouchEvent`), 카드 눌림 효과 |
| 키 이벤트 | 채팅 Enter 전송, 검색 Enter |
| 뒤로 가기 | 프로젝트 → 루트, 시트·검색 닫기, 저장 안 한 변경 확인 |
| 위젯 | NumberPicker(시간 휠), CheckBox(할 일·투표), RadioGroup(모드·세그먼트·투표 단일 선택) |
| 리소스 조건 | values-en, values-night, layout-land(루트 홈 2단) |

## 8. 검증

- 단위 테스트: 업무 분류, 회의 시간 추천, 가능 시간 비트 연산, 태그 형식·검색어 해석, AI 응답 파싱, 최근 활동 합치기, 주간 캘린더 날짜 계산 + 기존 39개.
- 에뮬레이터(API 36) E2E: 단계마다 스크린샷 확인. 계정 2개로 초대·채팅·안 읽은 수 실시간 확인.
- 보안 규칙: REST로 허용·거부 시나리오 확인 (v0.1 때와 같은 방식).

## 9. 단계

1. 이름 변경(MODO, `com.modo.app`, Firebase 앱 재등록)
2. 데이터 계층 v2 + 보안 규칙·색인 배포 + 기존 사용자 태그 발급
3. 내비게이션 뼈대: 루트(상단 바·전환 시트·하단 탭), 프로젝트 화면(모드 4종 틀)
4. 루트 화면: 홈·할 일·일정·내 정보 + 버그 3개
5. 프로젝트 섹션: 개요 2종·보드·일정·팀원·설정(모드 변경)
6. 계정·초대: 가입 항목·태그·검색·초대·수락·앱 안 쓰는 팀원·알림 화면
7. 채팅: 목록·방·1:1·안 읽은 수·공지·투표·멘션·할 일 전환
8. 파일: Supabase 구성·업로드/다운로드·업무·회의록·채팅 첨부·파일 목록
9. 회의 시간 조율: 가능 시간 편집·가입 직후 질문·히트맵·추천·자동 갱신
10. 회의록: 목록·검색·작성·STT·AI 요약 → 보드 등록
11. Google 로그인, 영어 문자열·가로 화면 점검
12. 시연 데이터(테스트 프로젝트 4개·시연 계정), 스크린샷, README·GUIDE, APK
