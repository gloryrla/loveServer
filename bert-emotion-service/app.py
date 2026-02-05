"""
BERT 기반 한국어 감정 분류 API (Spring에서 HTTP로 호출)
감정 라벨: 분노, 두려움, 기쁨, 평온, 슬픔 (5종) — AIHub 감성대화 말뭉치 기반, 연애·대화에 적합
실행: uvicorn app:app --host 0.0.0.0 --port 5001
"""
import logging
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
import torch
from transformers import AutoTokenizer, AutoModelForSequenceClassification

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(title="BERT Emotion API")

# 5종 감정 (AIHub 감성대화 말뭉치: Angry, Fear, Happy, Tender, Sad)
EMOTION_LABELS = ["분노", "두려움", "기쁨", "평온", "슬픔"]

# koBERT-Senti5 라벨 매핑 (영문 → 한글)
ID2LABEL_KO = {0: "분노", 1: "두려움", 2: "기쁨", 3: "평온", 4: "슬픔"}

# 전역 모델/토크나이저 (최초 요청 시 로드)
model = None
tokenizer = None
id2label = None


def get_model():
    global model, tokenizer, id2label
    if model is None:
        # AIHub 감성대화 말뭉치 기반 5종 감정 분류 (연애·일상 대화에 적합)
        model_name = "rkdaldus/ko-sent5-classification"
        tokenizer_name = "monologg/kobert"  # KoBERT 토크나이저
        logger.info("Loading model: %s, tokenizer: %s", model_name, tokenizer_name)
        try:
            tokenizer = AutoTokenizer.from_pretrained(tokenizer_name, trust_remote_code=True)
        except Exception:
            tokenizer = AutoTokenizer.from_pretrained(model_name)
        model = AutoModelForSequenceClassification.from_pretrained(model_name)
        model.eval()
        # 모델이 영문 라벨(Angry, Fear 등)을 쓸 수 있으므로 한글로 통일
        id2label = dict(ID2LABEL_KO)
    return model, tokenizer, id2label


class AnalyzeRequest(BaseModel):
    text: str


class AnalyzeResponse(BaseModel):
    emotion: str
    confidence: float
    scores: dict


@app.post("/analyze", response_model=AnalyzeResponse)
def analyze(req: AnalyzeRequest):
    text = (req.text or "").strip()
    if not text:
        raise HTTPException(status_code=400, detail="text required")
    try:
        model, tokenizer, id2label = get_model()
        inputs = tokenizer(
            text,
            return_tensors="pt",
            truncation=True,
            max_length=128,
            padding=True,
        )
        with torch.no_grad():
            logits = model(**inputs).logits
        probs = torch.softmax(logits, dim=-1).squeeze().tolist()
        if isinstance(probs, float):
            probs = [1.0 - probs, probs]
        pred_idx = int(torch.argmax(logits, dim=-1).item())
        emotion = id2label.get(pred_idx, EMOTION_LABELS[0])
        confidence = float(probs[pred_idx]) if pred_idx < len(probs) else 0.0
        scores = {id2label.get(i, "기쁨"): round(float(p), 4) for i, p in enumerate(probs)}
        return AnalyzeResponse(emotion=emotion, confidence=round(confidence, 4), scores=scores)
    except Exception as e:
        logger.exception("analyze failed: %s", e)
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/health")
def health():
    return {"status": "ok"}


@app.get("/labels")
def get_labels():
    """서비스에서 사용하는 감정 5종 (분노, 두려움, 기쁨, 평온, 슬픔)"""
    return {"emotions": EMOTION_LABELS}
