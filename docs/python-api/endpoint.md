# Python API / 엔드포인트 정리

Python 서버 입장에서 **Java가 무엇을 보내고**, **Python이 Java에 무엇을 돌려주는지** 정리한 문서입니다.  
(Java가 클라이언트, Python이 서버입니다. Python은 Java를 직접 호출하지 않습니다.)

---

## 1. 개요

- **역할**: Python은 **AI 오케스트레이션 서버** (페르소나·OpenAI 채팅·BERT 감정 분석·리포트 생성).
- **실행**: `uvicorn app:app --host 0.0.0.0 --port 5001`
- **Java 연동**: Java가 `app.ai-server.url` (기본 `http://localhost:5001`) 로 **POST** 요청을 보내고, Python은 JSON 응답을 반환합니다.

---

## 2. Java가 호출하는 엔드포인트 (Python이 제공하는 API)

### 2-1. POST /ai/chat (채팅 1턴)

- **호출 시점**: 사용자가 USER 메시지를 보낼 때, Java가 해당 대화 히스토리와 함께 1회 호출.
- **Java → Python (Request)**

| 필드 | 타입 | 설명 |
|------|------|------|
| conversationId | number | 대화방 ID |
| userId | number | 유저 ID |
| partnerType | string? | 여친 유형 (공주, 신, 인형, 동그라미, random 등). 페르소나 프롬프트 선택에 사용 |
| mode | string? | 예: SIMULATION |
| messages | array | `{ role: "USER" \| "ASSISTANT", content: string }[]` (최근 N개 히스토리) |

- **Python → Java (Response)**

| 필드 | 타입 | 설명 |
|------|------|------|
| assistantMessage | string | OpenAI로 생성한 여친 답변 1개 |
| emotionAnalysis | object \| null | 채팅 API에서는 **null** (감정은 리포트에서만) |
| report | object \| null | 채팅 API에서는 **null** |
| tokenUsage | object? | `{ promptTokens, completionTokens, totalTokens }` (OpenAI 사용량) |

- **Python 동작**: `partnerType`으로 시스템 프롬프트 선택 → OpenAI Chat Completions 호출 → `assistantMessage` + `tokenUsage` 반환.

---

### 2-2. POST /ai/report (감정 분석 + 통합 리포트)

- **호출 시점**: 프론트가 “결과 보고서 분석”을 요청했을 때, Java가 해당 대화의 전체 USER/AI 메시지 본문만 보냄.
- **Java → Python (Request)**

| 필드 | 타입 | 설명 |
|------|------|------|
| userTexts | string[] | 사용자(USER) 메시지 본문 배열 (순서 유지) |
| assistantTexts | string[] | 상대방(AI) 메시지 본문 배열 (순서 유지) |

- **Python → Java (Response)**

| 필드 | 타입 | 설명 |
|------|------|------|
| userEmotionAnalyses | array | 메시지별 감정 분석. 각 항목: `{ emotion, confidence, scores }` (5종) |
| assistantEmotionAnalyses | array | 동일 구조 |
| report | object | `{ summary, details }` |

**report.details** 구조:

| 키 | 설명 |
|----|------|
| userReport | `{ summary, counts, messages }` — 사용자 메시지별 감정 요약/횟수/상세 |
| assistantReport | 동일 — 상대방 메시지 |
| userFocusReport | 사용자 중심 코칭 리포트. `emotionBalance`, `volatility`, `habitAnalysis`, **insights**(string[]), **coaching**(string[], 2개 고정) |

- **Python 동작**:  
  - 각 텍스트에 대해 BERT 감정 분석(체크포인트) 수행.  
  - `build_report_from_emotions()` 로 요약·counts·messages·userFocusReport 생성.  
  - 코칭 문장은 OpenAI로 생성(실패 시 폴백).  
  - Java는 `report.details` 를 JSON 문자열로 저장해 프론트에 `detailsJson` 으로 전달.

---

## 3. 그 외 Python 엔드포인트 (Java는 미사용)

- **GET /health**: 헬스체크. `{ "status": "ok" }`
- **POST /analyze**: 단문 감정 분석. Request `{ "text" }` → Response `{ emotion, confidence, scores }`. (Java는 /ai/report 로 일괄 처리)
- **GET /labels**: 감정 5종 라벨 반환. `{ "emotions": ["분노", "두려움", "기쁨", "평온", "슬픔"] }`

---

## 4. 요약: Java ↔ Python 소통

| 방향 | 내용 |
|------|------|
| **Java → Python** | POST /ai/chat: 대화 히스토리 + partnerType 등. POST /ai/report: userTexts, assistantTexts |
| **Python → Java** | POST /ai/chat: assistantMessage, tokenUsage. POST /ai/report: user/assistantEmotionAnalyses, report(summary + details) |

Python은 Java 서버를 **호출하지 않고**, Java가 Python을 호출할 때만 **응답 body(JSON)** 를 돌려줍니다.  
리포트의 `details`(userFocusReport 포함)는 Java에서 그대로 `detailsJson` 으로 저장·프론트에 전달됩니다.
