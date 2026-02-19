"""AI 채팅: POST /ai/chat (채팅만). 결과 보고서: POST /ai/report (감정 분석 + 리포트)."""
import logging
from fastapi import APIRouter, HTTPException

from config import get_openai_api_key
from persona import get_system_prompt
from emotion import analyze_emotion
from chat import create_chat_completion
from report import build_report, build_report_from_emotions
from schemas import AiChatRequest, AiChatResponse, ReportRequest, ReportResponse

logger = logging.getLogger(__name__)
router = APIRouter(prefix="/ai", tags=["ai"])


@router.post("/chat", response_model=AiChatResponse)
def ai_chat(req: AiChatRequest):
    """자바에서 호출. OpenAI로 여친 톤 답변만 생성 (감정/리포트는 결과 보고서에서만)."""
    logger.info("POST /ai/chat conversationId=%s messages=%d", getattr(req, "conversationId", None), len(req.messages or []))
    system_prompt = get_system_prompt(req.partnerType)
    api_key = get_openai_api_key()

    if not api_key:
        logger.warning("OPENAI_API_KEY not set, returning placeholder")
        placeholder = "지금은 답변을 생성할 수 없어요. (OPENAI_API_KEY 설정 필요)"
        return AiChatResponse(
            assistantMessage=placeholder,
            emotionAnalysis=None,
            report=None,
            tokenUsage=None,
        )

    messages = [{"role": "system", "content": system_prompt}]
    for m in req.messages or []:
        role = "user" if m.role == "USER" else "assistant"
        messages.append({"role": role, "content": (m.content or "").strip()})

    try:
        assistant_message, token_usage = create_chat_completion(messages)
    except Exception as e:
        logger.exception("OpenAI call failed: %s", e)
        raise HTTPException(status_code=502, detail=str(e))

    return AiChatResponse(
        assistantMessage=assistant_message,
        emotionAnalysis=None,
        report=None,
        tokenUsage=token_usage,
    )


@router.post("/report", response_model=ReportResponse)
def ai_report(req: ReportRequest):
    """
    대화 종료 후 결과 보고서. checkpoint_epoch_7(BERT)로 사용자·상대방 메시지 감정 분석.
    자바는 userTexts, assistantTexts만 넘기고, 분석·보고서 생성은 전부 여기서 수행.
    """
    user_texts = [t.strip() for t in (req.userTexts or []) if t and t.strip()]
    assistant_texts = [t.strip() for t in (req.assistantTexts or []) if t and t.strip()]
    if not user_texts and not assistant_texts:
        raise HTTPException(status_code=400, detail="userTexts or assistantTexts required")

    user_emotions = [analyze_emotion(t) for t in user_texts]
    assistant_emotions = [analyze_emotion(t) for t in assistant_texts]
    report = build_report_from_emotions(
        user_texts, user_emotions,
        assistant_texts, assistant_emotions,
    )
    return ReportResponse(
        userEmotionAnalyses=user_emotions,
        assistantEmotionAnalyses=assistant_emotions,
        report=report,
    )
