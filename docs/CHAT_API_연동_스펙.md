# 채팅 메시지 API 연동 스펙 (FE ↔ 자바 ↔ Python)

자바 서버 기준으로, **프론트는 어떻게 요청하고**, **자바는 Python에 어떻게 요청하는지** 정리한 문서입니다.  
**폴링 없이 POST 응답만으로 채팅창에 반영**하면 됩니다.

---

## 1. 프론트 → 자바 (채팅 시)

### 1-1. 메시지 전송 (사용자가 입력 후 전송)

- **URL**: `POST /api/conversations/{conversationId}/messages`
- **Headers**  
  - `Content-Type: application/json`  
  - `Authorization: Bearer {accessToken}` (로그인 후 받은 토큰)

- **Request body (CreateMessageRequest)**  
  - **role**: 반드시 `"USER"` (문자열). `"ASSISTANT"` 는 보내지 말 것.  
  - **content**: 사용자가 입력한 메시지 문자열  
  - **clientMessageId**: FE에서 생성한 UUID (선택, 중복 전송 방지용)

```json
{
  "role": "USER",
  "content": "어제 왜 그랬어?",
  "clientMessageId": "uuid-1"
}
```

- **Response (AddMessageResponse)**  
  - **userMessage**: 방금 저장된 내 메시지 1개 (MessageDto)  
  - **assistantMessage**: Python이 생성한 여친 답변 1개 (MessageDto)  
  - **emotionAnalysis**: 채팅 시에는 `null`  
  - **report**: 채팅 시에는 `null`

**MessageDto 한 개 구조** (userMessage, assistantMessage 둘 다 동일):

```json
{
  "id": 201,
  "conversationId": 10,
  "role": "USER",
  "content": "어제 왜 그랬어?",
  "clientMessageId": "uuid-1",
  "createdAt": "2026-02-10T11:30:00Z"
}
```

(assistantMessage는 role이 `"ASSISTANT"`, clientMessageId는 `null`)

---

### 1-2. 프론트 권장 흐름 (폴링 없이)

1. 사용자 입력 후 **clientMessageId** 로 UUID 생성 (선택).
2. **POST** `/api/conversations/{conversationId}/messages`  
   body: `{ "role": "USER", "content": "입력 내용", "clientMessageId": "uuid" }`  
   headers: `Authorization: Bearer {accessToken}`, `Content-Type: application/json`
3. **응답을 기다린 뒤**:
   - `res.userMessage` → 채팅 목록에 **내 메시지**로 바로 추가.
   - `res.assistantMessage` → 채팅 목록에 **여친 답변**으로 바로 추가.
4. **GET 폴링은 하지 않음.**  
   같은 API 한 번의 응답에 userMessage + assistantMessage 가 모두 오므로, 이 둘만 화면에 넣으면 됨.
5. **에러 시**: 503 이면 "여친 답변 생성에 실패했어요. 잠시 후 다시 시도해 주세요." 등으로 표시.

---

### 1-3. 특정 대화의 전체 메시지 조회 (대화방 입장 시)

- **URL**: `GET /api/conversations/{conversationId}/messages`
- **Headers**: `Authorization: Bearer {accessToken}`

- **Response**: 메시지 배열. 각 요소는 위 MessageDto와 동일.  
  - **role**: `"USER"` | `"ASSISTANT"` (자바가 AI → ASSISTANT 로 내려줌)

대화방 입장 시 이 API 한 번 호출해서 전체 스레드 렌더링하고,  
이후에는 **새 메시지 전송 시 POST 응답의 userMessage / assistantMessage 만 추가**하면 됨.

---

## 2. 자바 → Python (참고: 채팅 시 자바가 하는 일)

프론트는 Python을 직접 호출하지 않습니다. 자바가 아래처럼 호출합니다.

- **URL**: `POST {app.ai-server.url}/ai/chat` (예: http://localhost:5001/ai/chat)
- **Request body (AiChatRequest)**  
  - conversationId, userId, partnerType, mode  
  - **messages**: `[ { "role": "USER", "content": "..." }, { "role": "ASSISTANT", "content": "..." }, ... ]`  
    (자바가 DB에서 최근 N개 메시지를 넣어서 보냄)

- **Response (AiChatResponse)**  
  - **assistantMessage**: OpenAI로 생성한 여친 답변 문자열  
  - emotionAnalysis, report: 채팅 시에는 null

자바는 이 응답의 assistantMessage 를 DB에 저장한 뒤,  
프론트에 AddMessageResponse(userMessage, assistantMessage, null, null) 로 넘깁니다.

---

## 3. 요약 체크리스트

| 구분 | 누가 | 요청 | 비고 |
|------|------|------|------|
| 메시지 전송 | **프론트** → 자바 | POST `/api/conversations/{id}/messages`  
  body: `{ "role": "USER", "content", "clientMessageId" }` | 응답의 **userMessage, assistantMessage** 를 바로 채팅창에 추가. **폴링 금지** |
| 메시지 목록 | **프론트** → 자바 | GET `/api/conversations/{id}/messages` | 대화방 입장 시 1회. role 은 USER / ASSISTANT |
| 채팅 생성 | **자바** → Python | POST `/ai/chat`  
  body: conversationId, userId, partnerType, mode, messages | 프론트는 호출 안 함 |

---

## 4. “폴링 25번 하다가 미안해, 다른 생각 했어 하고 끝” 현상

- **원인 후보**  
  - POST 응답을 쓰지 않고 GET으로만 폴링해서, **마지막 메시지가 ASSISTANT인지** 확인하는 조건이 잘못되었거나,  
  - Python/OpenAI가 같은 문장만 반복해 오는 경우.
- **해결**  
  1. **폴링 제거**: 메시지 전송 후 **POST 응답의 userMessage, assistantMessage 만** 채팅 목록에 넣기.  
  2. 위 스펙대로 **role: "USER"**, **Authorization** 넣어서 POST 한 번만 호출.  
  3. 503 이면 “답변 생성 실패” 메시지 표시.

이렇게 하면 자바 ↔ 프론트 ↔ Python 이 한 방향으로만 맞춰지고, 폴링 없이 바로 반영됩니다.
