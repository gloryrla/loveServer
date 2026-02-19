"""
체크포인트 기반 BERT 감정 분석. (Colab 학습 구조와 동일)
"""
import logging
from typing import Dict, Any

import torch
import torch.nn as nn
import torch.nn.functional as F
from transformers import AutoModel, AutoTokenizer

from config import CHECKPOINT_PATH, MODEL_NAME, EMOTIONS

logger = logging.getLogger(__name__)
device = torch.device("cuda" if torch.cuda.is_available() else "cpu")

# 싱글톤 (최초 요청 시 로드)
_model = None
_tokenizer = None


class EmotionBERT(nn.Module):
    """Colab 학습 시 사용한 구조: pooler_output + Dropout(0.1) + Linear(5)."""

    def __init__(self, model_name: str = MODEL_NAME):
        super().__init__()
        self.bert = AutoModel.from_pretrained(model_name)
        self.classifier = nn.Linear(self.bert.config.hidden_size, 5)
        self.dropout = nn.Dropout(0.1)

    def forward(self, ids, mask):
        out = self.bert(ids, mask)
        return self.classifier(self.dropout(out.pooler_output))


def get_model():
    """모델·토크나이저 지연 로드 (최초 1회)."""
    global _model, _tokenizer
    if _model is None:
        if not CHECKPOINT_PATH.is_file():
            raise FileNotFoundError(
                f"체크포인트가 없습니다. {CHECKPOINT_PATH} 를 넣어주세요."
            )
        logger.info("Loading tokenizer: %s", MODEL_NAME)
        _tokenizer = AutoTokenizer.from_pretrained(MODEL_NAME)
        logger.info("Loading EmotionBERT from checkpoint: %s", CHECKPOINT_PATH)
        checkpoint = torch.load(CHECKPOINT_PATH, map_location=device)
        _model = EmotionBERT(MODEL_NAME).to(device)
        _model.load_state_dict(checkpoint["model_state_dict"])
        _model.eval()
    return _model, _tokenizer


def analyze_emotion(text: str) -> Dict[str, Any]:
    """
    한 문장 감정 분석. 반환: emotion, confidence, scores.
    빈 문자열이면 평온 0%, 예외 시에도 평온으로 폴백.
    """
    text = (text or "").strip()
    if not text:
        return {"emotion": "평온", "confidence": 0.0, "scores": {e: 0.0 for e in EMOTIONS}}

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
        return {
            "emotion": emotion,
            "confidence": round(confidence_pct, 4),
            "scores": scores,
        }
    except Exception as e:
        logger.exception("analyze_emotion failed: %s", e)
        return {"emotion": "평온", "confidence": 0.0, "scores": {e: 0.0 for e in EMOTIONS}}
