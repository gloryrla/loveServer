# BERT 감정 분류 서비스

Spring 백엔드(`/api/emotion-analysis`)에서 호출하는 BERT 기반 한국어 감정(감성) 분류 API입니다.

## 규약

- **POST /analyze**
  - Request: `{ "text": "사용자가 입력한 텍스트" }`
  - Response: `{ "emotion": "긍정", "confidence": 0.92, "scores": { "부정": 0.08, "긍정": 0.92 } }`

## 실행 방법

```bash
# 가상환경 권장
python -m venv venv
source venv/bin/activate   # Windows: venv\Scripts\activate

pip install -r requirements.txt
uvicorn app:app --host 0.0.0.0 --port 5001
```

- 기본 주소: `http://localhost:5001` (Mac은 5000이 AirPlay라 5001 사용)
- Spring `application.yml`의 `app.bert.service-url`을 이 주소로 두면 됩니다.

## 감정 라벨 (6종)

- **기쁨, 당황, 분노, 불안, 슬픔, 상처**
- `GET /labels` 로 목록 조회 가능
- 현재 5클래스 모델(ko-sent5)로 4종(기쁨, 분노, 불안, 슬픔) 매핑. 6클래스 모델로 교체 시 당황·상처까지 반영 가능.
