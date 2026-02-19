# Java API / 엔드포인트 정리

Java 서버 입장에서 **프론트엔드**와 **Python AI 서버**와 어떻게 연결되는지 정리한 문서입니다.

---

## 1. 개요

- **프론트 → Java**: REST API (인증·세션·대화·리포트·여친 유형 등). `Authorization: Bearer {accessToken}` 필요.
- **Java → Python**: Java가 **클라이언트**로 Python 서버를 호출. 설정: `app.ai-server.url` (기본 `http://localhost:5001`).

---

## 2. 프론트 → Java (Java가 제공하는 API)

### 2-1. 인증

| Method | URL | 설명 | 인증 |
|--------|-----|------|------|
| POST | `/api/auth/signup` | 회원가입 | 불필요 |
| POST | `/api/auth/login` | 로그인 → accessToken + 유저/여친유형/최근대화 반환 | 불필요 |

- **login Request**: `{ "userId": string, "password": string }`
- **login Response**: `{ accessToken, user, partnerType: { isSet, value }, lastConversation, lastConversationMessages }`

### 2-2. 앱 초기화 / 세션

| Method | URL | 설명 | 인증 |
|--------|-----|------|------|
| GET | `/api/bootstrap` | 앱 초기화. `partnerType.isSet` 로 온보딩/채팅 라우팅 판단 | (현재는 인증 없이 고정값 반환) |
| GET | `/api/session` | 현재 유저 정보 + 여친 유형 + 최근 대화 1개 + 그 대화 메시지 목록 | Bearer 필수 |

- **session Response**: `{ user: UserInfo, partnerType: { isSet, value }, lastConversation, lastConversationMessages }`

### 2-3. 유저 / 여친 유형

| Method | URL | 설명 | 인증 |
|--------|-----|------|------|
| GET | `/api/me` | 내 정보 (MeController) | Bearer 필수 |
| GET | `/api/users/me` | 내 정보 (UserController) | Bearer 필수 |
| POST | `/api/users/test-results/partner-type` | 여친 유형 저장 또는 **갱신** (다시 테스트하기) | Bearer 필수 |
| POST | `/api/test-results/partner-type` | 여친 유형 저장 또는 **갱신** (동일 로직, 다른 경로) | Bearer 필수 |

- **partner-type Request**: `{ "value": "공주" | "신" | "인형" | "동그라미" | "random" }`
- 성공 시 200. 이후 GET `/api/session` 재호출로 상태 반영.

### 2-4. 대화 (Conversations)

| Method | URL | 설명 | 인증 |
|--------|-----|------|------|
| POST | `/api/conversations` | 대화방 생성 | Bearer 필수 |
| GET | `/api/conversations` | 대화 목록 (ConversationSummary[]) | Bearer 필수 |
| GET | `/api/conversations/{id}` | 해당 대화 + 메시지 전체 (ConversationThread) | Bearer 필수 |
| GET | `/api/conversations/{id}/messages` | 해당 대화의 메시지 목록만 | Bearer 필수 |
| POST | `/api/conversations/{id}/messages` | 메시지 추가 (USER 시 Python 호출 → ASSISTANT + report 등) | Bearer 필수 |
| POST | `/api/conversations/{id}/report` | 결과 보고서 생성 (해당 대화 전체로 Python 호출) | Bearer 필수 |

- **대화방 생성 Request**: `{ "title", "scenarioKey", "personaKey" }`  
  **Response**: `{ "conversationId": number }`
- **메시지 추가 Request**: `{ "role": "USER", "content": string, "clientMessageId": string? }`  
  **Response**: `{ userMessage, assistantMessage, emotionAnalysis, report }`  
  - 채팅 시에는 `emotionAnalysis`, `report` 는 null. (리포트는 POST `.../report` 에서만 생성)
- **리포트 Response**: `{ "summary": string, "detailsJson": string }`  
  - `detailsJson` 은 JSON 문자열. 파싱 시 `userReport`, `assistantReport`, `userFocusReport`(insights, coaching 등) 포함.

---

## 3. Java → Python (Java가 호출하는 API)

Java는 **WebClient** (`aiServerWebClient`) 로 Python 서버에 HTTP 요청을 보냅니다.

- **Base URL**: `app.ai-server.url` (기본 `http://localhost:5001`)
- **호출 시점**:  
  - 사용자가 **USER 메시지**를 보낼 때 → **POST /ai/chat**  
  - 프론트가 **결과 보고서 분석**을 요청할 때 → **POST /ai/report**

### 3-1. POST /ai/chat (채팅 1턴)

- **호출 시점**: `ConversationService.addMessage()` 에서 role=USER 일 때.
- **Request (Java → Python)**  
  - `AiChatRequest`: `conversationId`, `userId`, `partnerType`, `mode`, `messages: [{ role, content }]`
- **Response (Python → Java)**  
  - `assistantMessage`: 여친 답변 텍스트  
  - `emotionAnalysis`: 채팅 API에서는 null (Python이 비움)  
  - `report`: 채팅 API에서는 null  
  - `tokenUsage`: OpenAI 사용량 (promptTokens, completionTokens, totalTokens)
- **실패 시**: Java는 `Optional.empty()` 로 받고, 503 등으로 프론트에 실패 응답.

### 3-2. POST /ai/report (감정 분석 + 통합 리포트)

- **호출 시점**: `ConversationService.runReport()` → 프론트가 **POST /api/conversations/{id}/report** 호출할 때.
- **Request (Java → Python)**  
  - `ReportRequest`: `userTexts: string[]`, `assistantTexts: string[]` (해당 대화의 USER/AI 메시지 본문만)
- **Response (Python → Java)**  
  - `userEmotionAnalyses`: 메시지별 감정 분석 결과 배열  
  - `assistantEmotionAnalyses`: 동일  
  - `report`: `{ summary, details }`  
    - `details`: `userReport`, `assistantReport`, `userFocusReport`(emotionBalance, volatility, habitAnalysis, insights, coaching)
- Java는 `report.details` 를 JSON 문자열로 직렬화해 DB에 저장하고, 프론트에는 `summary` + `detailsJson` 으로 내려줌.

---

## 4. 에러 코드 (프론트 → Java)

| Status | 의미 |
|--------|------|
| 401 | 인증 실패 (토큰 없음/만료/잘못됨) |
| 403 | 권한 없음 |
| 404 | 리소스 없음 (대화 등) |
| 409 | 비즈니스 충돌 (예: signup 시 이미 존재하는 아이디). 여친 유형은 갱신 가능하므로 409 없음. |
| 503 | Python AI 서버 호출 실패 (채팅/리포트) |

---

## 5. 설정 (application.yml)

```yaml
app:
  ai-server:
    url: http://localhost:5001   # Python 서버 주소
```

Python 서버는 기본적으로 `uvicorn app:app --host 0.0.0.0 --port 5001` 로 띄우면 위 URL로 접근 가능합니다.

---

## 6. 설계 v2 대비 구현 상태 및 유의사항

설계 문서(개발 설계 v2)와 현재 Java 구현을 비교한 차이·유의사항입니다.

### 6-1. 로그인 응답

- **설계**: `{ "accessToken": "JWT_TOKEN_STRING" }` 만 반환.
- **구현**: **전체 세션 정보**를 한 번에 반환합니다.  
  `TokenResponse`: `accessToken`, `user`, `partnerType`, `lastConversation`, `lastConversationMessages`
- **FE 권장**: 로그인 직후 별도 GET /api/session 없이도 `TokenResponse` 만으로 프로필·partnerType·이어하기 UI 구성 가능. 필요 시 이후 GET /api/session 로 갱신.

### 6-2. 로그인 vs GET /api/session — lastConversation 구조 차이

- **GET /api/session** 의 `lastConversation`:  
  `{ id, title, mode, lastMessageAt }`  
  `lastConversationMessages[].clientMessageId` 있음.
- **POST /api/auth/login** 의 `lastConversation` (TokenResponse):  
  `{ id, title, scenarioKey, updatedAt }`  
  `lastConversationMessages[]` 에 **clientMessageId 없음** (필드 자체가 없음).
- **유의**: 같은 “최근 대화”라도 **login**과 **session** 응답 필드가 다릅니다.  
  설계서의 “lastConversation: mode, lastMessageAt” 은 **session** 기준입니다.  
  FE에서 login 응답만 쓸 경우 `mode`/`lastMessageAt` 대신 `scenarioKey`/`updatedAt` 이 오고, 메시지에 `clientMessageId` 는 없습니다.

### 6-3. 여친 유형 저장 (POST /api/test-results/partner-type)

- **설계**: “이미 설정된 경우 409 CONFLICT”.
- **구현**: **이미 있어도 409 없음.**  
  있으면 기존 row를 **갱신**, 없으면 새로 저장. 모두 **200** 반환.  
  “다시 테스트하기” 후 같은 API로 재전송하면 값이 갱신됩니다.

### 6-4. GET /api/bootstrap

- **설계**: 필요 시 `partnerType: { isSet, value }` 반환.
- **구현**: **미구현.**  
  인증 없이 항상 `{ "partnerType": { "isSet": false } }` 고정 반환. DB/세션 미조회.  
  실제 partnerType 은 **login** 또는 **GET /api/session** 에서만 사용하세요.

### 6-5. 메시지 전송 응답 (POST /api/conversations/{id}/messages)

- **설계**: “MessageResponse” 단일 메시지 반환, “이후 ASSISTANT를 같은 API or 별도 경로로” 언급.
- **구현**: **한 번에 두 메시지** 반환.  
  `AddMessageResponse`: `{ userMessage, assistantMessage, emotionAnalysis?, report? }`  
  - USER 전송 시: 서버가 Python 호출 후 **userMessage + assistantMessage** 를 같은 응답에 담아 반환.  
  - 채팅 시점에는 `emotionAnalysis`, `report` 는 null.
- **FE 권장**: 한 번의 POST 응답으로 **내 메시지 + 여친 답변** 둘 다 받으므로, 별도 조회/폴링 없이 `userMessage` / `assistantMessage` 만 채팅창에 추가하면 됩니다.

### 6-6. role 값

- **설계**: USER | ASSISTANT | SYSTEM.
- **구현**: **USER | ASSISTANT** 만 사용. SYSTEM 은 없음.

### 6-7. 여친 유형 value (personaKey)

- **설계 예시**: `"따뜻한 츤데레형"`, `TSUNDERE_GF` 등.
- **구현**: **공주, 신, 인형, 동그라미, random** (한글 키).  
  POST /api/conversations 의 `personaKey`, partner-type 의 `value` 모두 위 값 사용.

### 6-8. 요약 표

| 항목 | 설계 v2 | 현재 구현 |
|------|---------|-----------|
| Login 응답 | accessToken 만 | accessToken + user + partnerType + lastConversation + lastConversationMessages |
| lastConversation (login) | mode, lastMessageAt | scenarioKey, updatedAt |
| lastConversationMessages (login) | clientMessageId 포함 | clientMessageId 필드 없음 |
| Partner-type 이미 있음 | 409 | 200 + 갱신 |
| Bootstrap | partnerType 반환 가능 | 고정 isSet: false, DB 미연동 |
| 메시지 전송 응답 | 단일 MessageResponse | AddMessageResponse (userMessage + assistantMessage) |
| role | USER \| ASSISTANT \| SYSTEM | USER \| ASSISTANT |
| personaKey / value | 예: TSUNDERE_GF | 공주, 신, 인형, 동그라미, random |
