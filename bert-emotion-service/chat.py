"""
OpenAI API 키로 대화(채팅) 생성. 토큰 사용량 반환.
"""
import logging
from typing import Any, Dict, List, Optional, Tuple

from openai import OpenAI

from config import get_openai_api_key, DEFAULT_CHAT_MODEL, DEFAULT_MAX_TOKENS

logger = logging.getLogger(__name__)


def create_chat_completion(
    messages: List[Dict[str, str]],
    model: str = DEFAULT_CHAT_MODEL,
    max_tokens: int = DEFAULT_MAX_TOKENS,
) -> Tuple[str, Optional[Dict[str, int]]]:
    """
    OpenAI Chat Completions 호출.
    반환: (assistant_content, token_usage_dict or None).
    token_usage_dict: { promptTokens, completionTokens, totalTokens }
    """
    api_key = get_openai_api_key()
    if not api_key:
        return "", None

    client = OpenAI(api_key=api_key)
    resp = client.chat.completions.create(
        model=model,
        messages=messages,
        max_tokens=max_tokens,
        temperature=0.8,
    )
    choice = resp.choices[0] if resp.choices else None
    content = (choice.message.content if choice and choice.message else "").strip() or "(응답 없음)"
    usage = resp.usage
    token_usage = None
    if usage:
        token_usage = {
            "promptTokens": getattr(usage, "prompt_tokens", None) or 0,
            "completionTokens": getattr(usage, "completion_tokens", None) or 0,
            "totalTokens": getattr(usage, "total_tokens", None) or 0,
        }
    return content, token_usage
