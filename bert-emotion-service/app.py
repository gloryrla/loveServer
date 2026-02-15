"""
BERT 기반 한국어 감정 분류 API (Spring에서 HTTP로 호출)
Colab 학습 코드와 동일: EmotionBERT(pooler_output + dropout) + klue/bert-base
실행: uvicorn app:app --host 0.0.0.0 --port 5001
"""
import logging
from pathlib import Path

import torch
import torch.nn as nn
import torch.nn.functional as F
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from transformers import AutoModel, AutoTokenizer

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(title="BERT Emotion API")

CHECKPOINT_PATH = Path(__file__).resolve().parent / "checkpoints" / "checkpoint_epoch_7.pt"
MODEL_NAME = "klue/bert-base"
EMOTIONS = ["분노", "두려움", "기쁨", "평온", "슬픔"]
device = torch.device("cuda" if torch.cuda.is_available() else "cpu")

# 전역 (최초 요청 시 로드)
model = None
tokenizer = None


class EmotionBERT(nn.Module):
    """Colab 학습 시 사용한 구조와 동일: pooler_output + Dropout(0.1) + Linear(5)."""

    def __init__(self, model_name=MODEL_NAME):
        super().__init__()
        self.bert = AutoModel.from_pretrained(model_name)
        self.classifier = nn.Linear(self.bert.config.hidden_size, 5)
        self.dropout = nn.Dropout(0.1)

    def forward(self, ids, mask):
        out = self.bert(ids, mask)
        return self.classifier(self.dropout(out.pooler_output))


def get_model():
    global model, tokenizer
    if model is None:
        if not CHECKPOINT_PATH.is_file():
            raise FileNotFoundError(
                "체크포인트가 없습니다. checkpoints/checkpoint_epoch_7.pt 를 넣어주세요."
            )
        logger.info("Loading tokenizer: %s", MODEL_NAME)
        _tok = AutoTokenizer.from_pretrained(MODEL_NAME)
        logger.info("Loading EmotionBERT from checkpoint: %s", CHECKPOINT_PATH)
        checkpoint = torch.load(CHECKPOINT_PATH, map_location=device)
        _model = EmotionBERT(MODEL_NAME).to(device)
        _model.load_state_dict(checkpoint["model_state_dict"])
        _model.eval()
        tokenizer = _tok
        model = _model
    return model, tokenizer


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
        model, tokenizer = get_model()
        inputs = tokenizer(
            text,
            return_tensors="pt",
            padding=True,
            truncation=True,
            max_length=128,
        )
        inputs = {k: v.to(device) for k, v in inputs.items()}
        with torch.no_grad():
            logits = model(inputs["input_ids"], inputs["attention_mask"])
        probs = F.softmax(logits, dim=1)
        score, idx = torch.max(probs, dim=1)
        all_probs = probs[0].cpu().numpy()
        emotion = EMOTIONS[idx.item()]
        confidence_pct = score.item() * 100
        scores = {EMOTIONS[i]: round(float(all_probs[i]), 4) for i in range(len(EMOTIONS))}
        return AnalyzeResponse(
            emotion=emotion,
            confidence=round(confidence_pct, 4),
            scores=scores,
        )
    except Exception as e:
        logger.exception("analyze failed: %s", e)
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/health")
def health():
    return {"status": "ok"}


@app.get("/labels")
def get_labels():
    """서비스에서 사용하는 감정 5종 (분노, 두려움, 기쁨, 평온, 슬픔)"""
    return {"emotions": EMOTIONS}
