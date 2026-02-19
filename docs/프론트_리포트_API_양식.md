# 결과 보고서 API – 프론트 요청/응답 양식

## 1. 서버가 기대하는 요청 (프론트 → 자바)

| 항목 | 값 |
|------|-----|
| **URL** | `POST /api/conversations/{conversationId}/report` |
| **Method** | `POST` |
| **Path** | `{conversationId}` = 대화방 ID (숫자) |
| **Request Body** | **없음** (body 비움) |
| **Headers** | **필수** |

```
Content-Type: application/json
Authorization: Bearer {accessToken}
```

- **Authorization**  
  - 로그인 후 받은 `accessToken` 문자열을 그대로 사용.  
  - **반드시 객체로 넣어서 보내야 함.**  
  - ❌ `authHeader = "Bearer " + token` (문자열 하나)  
  - ✅ `headers: { "Authorization": "Bearer " + token }` (객체)

---

## 2. 프론트에서 보내는 예시 (복사용)

**fetch**

```ts
const conversationId = 6; // 실제 대화 ID
const accessToken = "eyJhbGciOiJIUzI1NiJ9..."; // 로그인 후 저장한 토큰

const res = await fetch(`http://localhost:8080/api/conversations/${conversationId}/report`, {
  method: "POST",
  headers: {
    "Content-Type": "application/json",
    "Authorization": `Bearer ${accessToken}`,
  },
  body: JSON.stringify({}),  // body 없어도 빈 객체로 POST
});

if (!res.ok) throw new Error(`API ${res.status}`);
const data = await res.json();
```

**axios**

```ts
await axios.post(
  `http://localhost:8080/api/conversations/${conversationId}/report`,
  {},  // body 없음
  {
    headers: {
      "Content-Type": "application/json",
      "Authorization": `Bearer ${accessToken}`,
    },
  }
);
```

- baseURL 쓰면: `POST ${baseURL}/api/conversations/${conversationId}/report`  
- **401 나오면**: 위처럼 `Authorization: Bearer {accessToken}` 이 **객체**로 들어가는지, 토큰이 비어있지 않은지 확인.

---

## 3. 서버가 내려주는 응답 (자바 → 프론트)

**Status**: `200 OK` (성공 시)

**Response body (JSON)**

```json
{
  "summary": "사용자: 슬픔 2회, 분노 1회 / 상대방: 평온 1회, 기쁨 1회",
  "detailsJson": "{\"userReport\":{\"summary\":\"...\",\"counts\":{\"슬픔\":2,\"분노\":1},\"messages\":[...]},\"assistantReport\":{\"summary\":\"...\",\"counts\":{...},\"messages\":[...]}}"
}
```

| 필드 | 타입 | 설명 |
|------|------|------|
| `summary` | string | 전체 감정 요약 한 문장 |
| `detailsJson` | string | **JSON 문자열**. 한 번 더 `JSON.parse(detailsJson)` 해서 써야 함 |

**`detailsJson` 파싱 후 구조**

```ts
const details = JSON.parse(data.detailsJson);

// 사용자(내) 대화 감정
details.userReport.summary   // "사용자 3개: 슬픔 2회, 분노 1회"
details.userReport.counts    // { "슬픔": 2, "분노": 1 }
details.userReport.messages  // 메시지별 상세 (아래 항목 구조)

// 상대방(AI) 대화 감정
details.assistantReport.summary
details.assistantReport.counts
details.assistantReport.messages

// 사용자 중심 코칭 리포트 (이렇게 말해보면 어때요? 섹션용)
details.userFocusReport.emotionBalance   // { userNegativeRatio, assistantNegativeRatio, difference, sentence }
details.userFocusReport.volatility       // { transitionCount, level, sentence }
details.userFocusReport.habitAnalysis   // { strongExpressions, questionRatio, ... }
details.userFocusReport.insights        // string[] – 관찰 문장들
details.userFocusReport.coaching        // string[] – 추천 말하기 문장들 (박스에 하나씩 표시)
```

**메시지 한 항목 (userReport.messages / assistantReport.messages)**

| 필드 | 타입 | 설명 |
|------|------|------|
| `text` | string | 원문 |
| `emotion` | string | 판정 감정 (분노/두려움/기쁨/평온/슬픔) |
| `confidence` | number | 해당 감정 확신도 **퍼센트** (0~100) |
| `scores` | object | 5종 감정 비율 0~1 (레거시) |
| `scoresPct` | object | **5종 감정 전부 퍼센트** (0~100) – 차트/바 등에 그대로 사용 |

```json
{
  "text": "나 우울해서 오늘 빵 샀어",
  "emotion": "슬픔",
  "confidence": 99.91,
  "scores": { "분노": 0.0002, "두려움": 0, "기쁨": 0.0006, "평온": 0.0001, "슬픔": 0.9991 },
  "scoresPct": { "분노": 0.02, "두려움": 0, "기쁨": 0.06, "평온": 0.01, "슬픔": 99.91 }
}
```

- **요약**: `summary`(전체), `userReport.summary`, `assistantReport.summary`  
- **횟수**: `userReport.counts`, `assistantReport.counts` (예: `{ "슬픔": 2, "분노": 1 }`)  
- **퍼센트**: 각 `messages[].confidence`, `messages[].scoresPct` 사용하면 됨.

---

## 3-1. Colab처럼 보이게 할 때 (메시지별 블록)

아래처럼 **한 메시지마다** 쓰면 Colab 출력과 동일하게 만들 수 있음. **지금 서버가 주는 값이 이거 맞음.**

| 화면에 보일 것 | 쓸 필드 |
|----------------|---------|
| `💬 입력: 나 우울해서 오늘 빵 샀어` | `msg.text` |
| `🎯 결과: 【 슬픔 】 (99.91%)` | `msg.emotion`, `msg.confidence` |
| `분노 : ■■... 0.02%` | `msg.scoresPct["분노"]` |
| `두려움 : □□... 0.00%` | `msg.scoresPct["두려움"]` |
| `기쁨 : □□... 0.06%` | `msg.scoresPct["기쁨"]` |
| `평온 : □□... 0.01%` | `msg.scoresPct["평온"]` |
| `슬픔 : ■■... 99.91%` | `msg.scoresPct["슬픔"]` |

**순서**: `분노` → `두려움` → `기쁨` → `평온` → `슬픔` (고정)

**예시 (한 메시지 렌더)**

```ts
const EMOTIONS_ORDER = ["분노", "두려움", "기쁨", "평온", "슬픔"];

function MessageEmotionBlock({ msg }: { msg: { text: string; emotion: string; confidence: number; scoresPct: Record<string, number> } }) {
  return (
    <div>
      <p>💬 입력: {msg.text}</p>
      <p>🎯 결과: 【 {msg.emotion} 】 ({msg.confidence}%)</p>
      <p>📊 상세 감정 확률:</p>
      {EMOTIONS_ORDER.map((e) => (
        <p key={e}>{e}: {bar(msg.scoresPct[e] ?? 0)} {msg.scoresPct[e] ?? 0}%</p>
      ))}
    </div>
  );
}
```

- `userReport.messages` / `assistantReport.messages` 를 위 컴포넌트로 돌리면 됨.  
- **지금 API가 내려주는 구조가 위 표/예시와 동일**하니까, 이대로 쓰면 Colab처럼 나옴.

---

## 4. 에러 시

| Status | 의미 | 프론트 처리 |
|--------|------|-------------|
| **401** | 인증 안 됨 (토큰 없음/만료/잘못됨) | `Authorization: Bearer {accessToken}` 객체로 넣었는지 확인, 토큰 재발급(재로그인) |
| 403 | 권한 없음 | 해당 대화가 본인 소유인지 확인 |
| 404 | 대화 없음 | conversationId 확인 |
| 503 | 감정 분석 서버(Python) 오류 | "잠시 후 다시 시도해 주세요" 등 안내 |

---

## 5. 리포트를 받는 경로 (자바 → 프론트)

| 경로 | 언제 | 응답 안에 리포트 |
|------|------|------------------|
| **POST** `/api/conversations/{id}/messages` | 사용자가 메시지(USER) 보낼 때 | `report: { summary, detailsJson }` (마지막 AI 답변 기준 리포트) |
| **POST** `/api/conversations/{id}/report` | '결과 보고서 분석' 버튼 등에서 호출 시 | `{ summary, detailsJson }` |

둘 다 `detailsJson`은 **문자열**이므로 `JSON.parse(detailsJson)` 후 `userReport`, `assistantReport`, **`userFocusReport`**(`insights`, `coaching`) 사용.

---

## 6. 한 줄 체크리스트 (프론트)

1. **POST** `/api/conversations/{conversationId}/report` (또는 메시지 전송 응답의 `report`)  
2. **Body** 없음 (빈 객체 `{}` 라도 OK)  
3. **Headers** 에 **`Authorization: Bearer {accessToken}`** 를 **객체**로 넣기 (문자열만 넣지 말 것)  
4. 응답의 **`detailsJson`** 은 **`JSON.parse(detailsJson)`** 한 뒤 `userReport`, `assistantReport`, **`userFocusReport`** 사용  
5. **"이렇게 말해보면 어때요?"** 박스 문장은 **`details.userFocusReport.coaching`** 배열을 그대로 순서대로 렌더하면 됨  

이 양식대로 보내면 서버와 프론트가 동일한 형식으로 맞습니다.
