"""감정 분석 API: POST /analyze, GET /labels."""
import logging
from fastapi import APIRouter, HTTPException

from config import EMOTIONS
from emotion import analyze_emotion
from schemas import AnalyzeRequest, AnalyzeResponse

logger = logging.getLogger(__name__)
router = APIRouter(tags=["emotion"])


@router.post("/analyze", response_model=AnalyzeResponse)
def analyze(req: AnalyzeRequest):
    text = (req.text or "").strip()
    if not text:
        raise HTTPException(status_code=400, detail="text required")
    out = analyze_emotion(text)
    return AnalyzeResponse(emotion=out["emotion"], confidence=out["confidence"], scores=out["scores"])


@router.get("/labels")
def get_labels():
    """서비스에서 사용하는 감정 5종."""
    return {"emotions": EMOTIONS}
