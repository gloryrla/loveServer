"""API 요청/응답 Pydantic 모델."""
from typing import List, Optional

from pydantic import BaseModel


# ----- /analyze -----
class AnalyzeRequest(BaseModel):
    text: str


class AnalyzeResponse(BaseModel):
    emotion: str
    confidence: float
    scores: dict


# ----- /ai/chat -----
class MessageItem(BaseModel):
    role: str
    content: str


class AiChatRequest(BaseModel):
    conversationId: int
    userId: int
    partnerType: Optional[str] = None
    mode: Optional[str] = None
    messages: List[MessageItem]


class AiChatResponse(BaseModel):
    assistantMessage: str
    emotionAnalysis: Optional[dict] = None
    report: Optional[dict] = None
    tokenUsage: Optional[dict] = None


# ----- /ai/report (결과 보고서: 내 대화(USER) + 상대방(AI) 감정 분석, 전부 Python에서) -----
class ReportRequest(BaseModel):
    userTexts: List[str] = []
    assistantTexts: List[str] = []


class ReportResponse(BaseModel):
    userEmotionAnalyses: List[dict] = []
    assistantEmotionAnalyses: List[dict] = []
    report: dict
