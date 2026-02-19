# AI Orchestration Service (bert-emotion-service)

페르소나 프롬프팅, OpenAI 대화 생성, BERT 감정 분석, 리포트 생성을 담당하는 Python 서버.  
Java는 DB·세션만 담당하고, LLM/감정/리포트는 이 서비스에서만 수행합니다.

## 디렉터리 구조

```
bert-emotion-service/
├── app.py              # FastAPI 앱 진입점, 라우터 등록
├── config.py           # 설정 (경로, 모델명, OPENAI_API_KEY 읽기)
├── persona.py          # 페르소나별 시스템 프롬프트
├── emotion.py          # BERT 감정 분석 (체크포인트 로드)
├── chat.py             # OpenAI API로 대화 생성
├── report.py           # 감정 결과 → 리포트 생성
├── schemas.py          # API 요청/응답 Pydantic 모델
├── routers/
│   ├── analyze.py      # POST /analyze, GET /labels
│   └── ai_chat.py      # POST /ai/chat (오케스트레이션)
├── checkpoints/        # BERT 체크포인트 (checkpoint_epoch_7.pt)
├── requirements.txt
└── README.md
```

## OPENAI_API_KEY 환경 변수 설정

대화 생성(LLM)을 쓰려면 **OpenAI API 키**를 환경 변수로 넣어야 합니다.

### 1) 터미널에서 한 번만 (현재 세션)

```bash
# macOS / Linux
export OPENAI_API_KEY="sk-proj-xxxxxxxxxxxx"

# Windows (CMD)
set OPENAI_API_KEY=sk-proj-xxxxxxxxxxxx

# Windows (PowerShell)
$env:OPENAI_API_KEY="sk-proj-xxxxxxxxxxxx"
```

이후 같은 터미널에서:

```bash
uvicorn app:app --host 0.0.0.0 --port 5001
```

### 2) 실행 시 한 줄로

```bash
OPENAI_API_KEY="sk-proj-xxxxxxxxxxxx" uvicorn app:app --host 0.0.0.0 --port 5001
```

(macOS/Linux)

### 3) .env 파일로 (프로젝트 루트에)

`.env` 파일 생성:

```
OPENAI_API_KEY=sk-proj-xxxxxxxxxxxx
```

그 다음 `python-dotenv` 설치 후 앱에서 로드하거나, 실행 전에 로드:

```bash
pip install python-dotenv
```

그리고 `config.py`에서:

```python
from dotenv import load_dotenv
load_dotenv()
```

또는 실행 시:

```bash
env $(cat .env | xargs) uvicorn app:app --host 0.0.0.0 --port 5001
```

### 4) IDE (VS Code / PyCharm)에서

- **VS Code**: `.vscode/launch.json`에 `"env": { "OPENAI_API_KEY": "sk-..." }` 추가  
- **PyCharm**: Run Configuration → Environment variables에 `OPENAI_API_KEY=sk-...` 추가  

---

키를 설정하지 않으면 `POST /ai/chat` 호출 시 placeholder 메시지와 "API 키 미설정" 안내가 반환됩니다.

## 실행

```bash
cd bert-emotion-service
pip install -r requirements.txt
# checkpoints/checkpoint_epoch_7.pt 가 있는지 확인
uvicorn app:app --host 0.0.0.0 --port 5001
```

- `GET /health` → 서버 상태  
- `POST /analyze` → 감정 분석 (body: `{"text": "..."}`)  
- `GET /labels` → 감정 라벨 목록  
- `POST /ai/chat` → Java에서 호출 (OpenAI로 여친 답변만 생성)  
- `POST /ai/report` → **대화 종료 후 결과 보고서** (checkpoint_epoch_7로 사용자·상대방 감정 분석)

## 대화 종료 후 결과 보고서 (POST /ai/report)

- **역할**: 대화가 끝났을 때, 사용자(USER) 메시지와 상대방(ASSISTANT) 메시지를 **checkpoint_epoch_7.pt** 로 감정 분석해 결과 보고서를 만듦.  
- **호출**: 자바가 `userTexts`, `assistantTexts`만 넘기고, 분석·보고서 생성은 전부 이 서비스에서 수행.  
- **체크포인트**: `checkpoints/checkpoint_epoch_7.pt` 에 Colab에서 학습한 것과 동일한 체크포인트를 두면 됨. (학습 정확도 98.95% 기준)

**Request** (자바 → Python):

```json
{
  "userTexts": ["나 우울해서 오늘 빵 샀어", "오빠는 항상 그런 식이더라"],
  "assistantTexts": ["빵 맛있었어?", "그랬구나..."]
}
```

**Response** 구조 (요약):

- `userEmotionAnalyses`, `assistantEmotionAnalyses`: 메시지별 `{ emotion, confidence, scores }`
- `report.summary`: 전체 감정 요약 문장  
- `report.details.userReport`: 사용자 메시지별 `{ text, emotion, confidence, scores }` + 요약  
- `report.details.assistantReport`: 상대방 메시지별 동일 구조 + 요약  

보고서 양식은 이후에 바꿀 수 있도록 `report.py`에서만 수정하면 됨.
