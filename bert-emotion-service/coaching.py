"""
사용자 중심 관계 코칭 리포트: 감정 밸런스, 기복, 대화 습관, 인사이트·코칭 문장 생성.
분석 결과는 Rule-based. 인사이트·코칭 문장은 OpenAI로 생성(실패 시 하드코딩 폴백).
"""
import json
import logging
import random
import re
from typing import Any, Dict, List, Optional, Tuple

from openai import OpenAI

from config import DEFAULT_CHAT_MODEL, get_openai_api_key

logger = logging.getLogger(__name__)

# 부정 감정 (비율 비교용)
NEGATIVE_EMOTIONS = {"분노", "두려움", "슬픔"}

# 감정 분석이 부정(분노/슬픔/두려움)으로 나온 메시지에서만 집계 → "부정적 발화 습관" 판단용
STRONG_WORDS = [
    "항상", "맨날", "짜증", "제발", "진작", "안돼", "미쳤", "미쳤어",
    "왜", "진짜", "너무", "그냥", "아니", "못", "또", "계속", "왜냐",
]
# 질문 패턴
QUESTION_PATTERN = re.compile(r"[?？]\s*$")
# 느낌표 반복
EXCLAMATION_PATTERN = re.compile(r"!+|\uFF01+")
# ㅋㅋ ㅠㅠ 등
LAUGH_PATTERN = re.compile(r"[ㅋㅎ]{2,}|[ㅠㅜ]{2,}|ㅋ+$|ㅠ+$")

# 코칭/인사이트 문장 최대 길이 (표기용으로 짧게)
MAX_COACHING_SENTENCE_LENGTH = 120
# 코칭 문장 개수 고정
COACHING_COUNT = 2
_COACHING_PAD = "조금만 천천히 말해 보면 대화가 편해질 거예요."


def _coaching_fix_count(coaching: List[str]) -> List[str]:
    """코칭을 정확히 2개로 맞춤. 부족하면 패딩, 많으면 앞 2개만."""
    coaching = [s for s in coaching if s][:COACHING_COUNT]
    while len(coaching) < COACHING_COUNT:
        coaching.append(_COACHING_PAD)
    return coaching[:COACHING_COUNT]


def _shorten_to_one_or_two_sentences(text: str, max_length: int = MAX_COACHING_SENTENCE_LENGTH) -> str:
    """한 항목을 1~2문장으로 자르고, 전체 길이도 제한. 화면 표기용."""
    if not text or not text.strip():
        return ""
    text = text.strip()
    # 문장 구분: . ? ! 로 나누기 (한글/영문 모두)
    parts = re.split(r"(?<=[.!?。？！])\s*", text)
    sentences = [p.strip() for p in parts if p.strip()]
    if not sentences:
        return text[:max_length] if len(text) > max_length else text
    out = sentences[0]
    if len(sentences) >= 2 and len(out) + len(sentences[1]) + 1 <= max_length:
        out = out + " " + sentences[1]
    if len(out) > max_length:
        out = out[: max_length - 1].rsplit(" ", 1)[0] or out[: max_length - 1]
        if not out.endswith((".", "!", "?", "요")):
            out = out + "…"
    return out


def _negative_ratio(emotion_counts: Dict[str, int]) -> float:
    """부정 감정 비율 (0~1)."""
    total = sum(emotion_counts.values())
    if total == 0:
        return 0.0
    neg = sum(emotion_counts.get(e, 0) for e in NEGATIVE_EMOTIONS)
    return round(neg / total, 4)


def emotion_balance(
    user_counts: Dict[str, int],
    assistant_counts: Dict[str, int],
) -> Dict[str, Any]:
    """
    사용자 vs 상대방 부정 감정 비율 비교.
    결과 문장은 항상 사용자 중심.
    """
    user_neg = _negative_ratio(user_counts)
    asst_neg = _negative_ratio(assistant_counts)
    diff = round(user_neg - asst_neg, 4)

    if diff > 0.15:
        sentence = "당신이 상대보다 감정을 더 강하게 표현하는 경향이 있습니다."
    elif diff < -0.15:
        sentence = "상대가 당신보다 감정을 더 강하게 표현하는 경향이 있습니다."
    else:
        sentence = "당신과 상대의 감정 표현 강도가 비슷합니다."

    return {
        "userNegativeRatio": user_neg,
        "assistantNegativeRatio": asst_neg,
        "difference": diff,
        "sentence": sentence,
    }


def volatility(user_emotions: List[str]) -> Dict[str, Any]:
    """
    사용자 메시지 기준 감정 전환 횟수.
    0~1: 안정적, 2~3: 자연스러운 기복, 4+: 기복 다소 높음
    """
    if len(user_emotions) <= 1:
        return {
            "transitionCount": 0,
            "level": "안정적",
            "sentence": "메시지가 적어 기복을 판단하기 어렵습니다.",
        }

    transitions = 0
    for i in range(1, len(user_emotions)):
        if user_emotions[i] != user_emotions[i - 1]:
            transitions += 1

    if transitions <= 1:
        level = "안정적"
        sentence = "감정이 안정적으로 유지되는 편입니다."
    elif transitions <= 3:
        level = "자연스러운 기복"
        sentence = "대화 흐름에 따른 자연스러운 감정 기복이 있습니다."
    else:
        level = "기복 다소 높음"
        sentence = "감정이 자주 바뀌는 편입니다. 한 템포 쉬어가면 도움이 됩니다."

    return {
        "transitionCount": transitions,
        "level": level,
        "sentence": sentence,
    }


def habit_analysis(
    user_texts: List[str],
    user_emotion_results: Optional[List[Dict[str, Any]]] = None,
) -> Dict[str, Any]:
    """
    사용자 대화 습관 (Rule-based).
    - 강한 표현: 체크포인트로 문장을 돌렸을 때 분노/슬픔/두려움이 나온 메시지에서만
      STRONG_WORDS 검사. 그때 strong words가 있으면 사용자 발화 습관이 부정적이라고 집계.
    - 감탄사/ㅋㅋ/ㅠㅠ, 질문 비율, 느낌표 반복은 전체 메시지 기준.
    """
    total = len(user_texts)
    if total == 0:
        return {
            "strongExpressions": [],
            "strongExpressionCount": 0,
            "negativeHabitMessageCount": 0,
            "laughOrCryCount": 0,
            "questionCount": 0,
            "questionRatio": 0.0,
            "exclamationRepeatCount": 0,
        }

    emotions = (
        [r.get("emotion", "평온") for r in user_emotion_results]
        if user_emotion_results and len(user_emotion_results) >= len(user_texts)
        else [None] * len(user_texts)
    )

    strong_found: List[str] = []
    negative_habit_count = 0  # 부정 감정 + 강한 표현 동시에 나온 메시지 수
    laugh_cry = 0
    question_count = 0
    exclamation_repeat = 0

    for i, text in enumerate(user_texts):
        t = (text or "").strip()
        if not t:
            continue
        is_negative = emotions[i] in NEGATIVE_EMOTIONS if emotions[i] else False

        # 부정 감정으로 나온 메시지에서만 강한 표현 집계 → 발화 습관이 부정적
        if is_negative:
            for w in STRONG_WORDS:
                if w in t:
                    strong_found.append(w)
                    negative_habit_count += 1
                    break

        if LAUGH_PATTERN.search(t):
            laugh_cry += 1
        if QUESTION_PATTERN.search(t):
            question_count += 1
        if EXCLAMATION_PATTERN.search(t):
            exclamation_repeat += 1

    return {
        "strongExpressions": list(set(strong_found)),
        "strongExpressionCount": len(strong_found),
        "negativeHabitMessageCount": negative_habit_count,
        "laughOrCryCount": laugh_cry,
        "questionCount": question_count,
        "questionRatio": round(question_count / total, 4) if total else 0.0,
        "exclamationRepeatCount": exclamation_repeat,
    }


def _build_coaching_context(
    emotion_balance_result: Dict[str, Any],
    volatility_result: Dict[str, Any],
    habit_result: Dict[str, Any],
) -> str:
    """AI 프롬프트용 요약 텍스트 생성."""
    parts = []
    ub = emotion_balance_result
    parts.append(
        f"[감정 밸런스] 사용자 부정비율={ub.get('userNegativeRatio', 0):.2f}, "
        f"상대방 부정비율={ub.get('assistantNegativeRatio', 0):.2f}, 차이={ub.get('difference', 0):.2f}. "
        f"해석: {ub.get('sentence', '')}"
    )
    vb = volatility_result
    parts.append(
        f"[감정 기복] 전환 횟수={vb.get('transitionCount', 0)}, 수준={vb.get('level', '')}. "
        f"해석: {vb.get('sentence', '')}"
    )
    strong = habit_result.get("strongExpressions") or []
    neg_habit = habit_result.get("negativeHabitMessageCount", 0)
    q_ratio = habit_result.get("questionRatio", 0)
    lc = habit_result.get("laughOrCryCount", 0)
    parts.append(
        f"[대화 습관] 부정감정+강한표현 메시지 수={neg_habit}, 강한표현 예시={strong[:5] if strong else []}, "
        f"질문 비율={q_ratio:.2f}, ㅋㅋ/ㅠㅠ 사용 횟수={lc}."
    )
    return "\n".join(parts)


def _generate_insights_and_coaching_with_ai(
    emotion_balance_result: Dict[str, Any],
    volatility_result: Dict[str, Any],
    habit_result: Dict[str, Any],
) -> Optional[Tuple[List[str], List[str]]]:
    """
    OpenAI로 인사이트·코칭 문장 생성.
    반환: (insights, coaching) 또는 실패 시 None.
    """
    api_key = get_openai_api_key()
    if not api_key or not api_key.strip():
        logger.warning("OPENAI_API_KEY 없음 또는 비어 있음, 코칭은 폴백 사용 (config 로드 경로: bert-emotion-service/.env 확인)")
        return None

    context = _build_coaching_context(
        emotion_balance_result, volatility_result, habit_result
    )
    system_prompt = """당신은 연애/관계 대화 코치입니다. 아래는 사용자와 상대방의 대화를 분석한 수치와 해석입니다.
이 데이터만 보고, 사용자에게 도움이 되는 인사이트(관찰)와 코칭(제안) 문장을 생성해 주세요.

규칙:
- 반드시 JSON만 출력하세요. 다른 설명 없이 {"insights": [...], "coaching": [...]} 형식으로.
- insights: 대화에서 읽어낸 관찰 2~5개. 사용자 중심, 자연스러운 한국어. 한 항목당 1~2문장, 짧게.
- coaching: 반드시 정확히 2개만. 구체적인 말/행동 제안. 지키세요:
  * 코칭 항목은 정확히 2개. 한 항목당 1~2문장, 짧게.
  * 각 코칭 문장은 서로 다른 말투로.
  * "이렇게 말해보면 어때요?" 같은 문구는 넣지 마세요. 분석 데이터에 맞춰 짧게 작성."""
    user_prompt = f"분석 결과:\n{context}"

    try:
        client = OpenAI(api_key=api_key)
        resp = client.chat.completions.create(
            model=DEFAULT_CHAT_MODEL,
            messages=[
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_prompt},
            ],
            max_tokens=600,
            temperature=0.9,
        )
        content = (resp.choices[0].message.content if resp.choices else "").strip()
        if not content:
            logger.warning("코칭 AI: 응답 content 비어 있음, 폴백 사용")
            return None
        # ```json ... ``` 또는 앞뒤 설명 제거 후 JSON 추출
        if "```" in content:
            for block in ("```json", "```"):
                if block in content:
                    content = content.split(block, 1)[-1].rsplit("```", 1)[0].strip()
        # 앞뒤 설명이 붙은 경우 첫 { ~ 마지막 } 만 사용
        start = content.find("{")
        end = content.rfind("}")
        if start != -1 and end != -1 and end > start:
            content = content[start : end + 1]
        try:
            data = json.loads(content)
        except json.JSONDecodeError as je:
            logger.warning("코칭 AI: JSON 파싱 실패 (%s), raw 앞 200자: %s", je, content[:200])
            return None
        insights = data.get("insights")
        coaching = data.get("coaching")
        if not isinstance(insights, list):
            insights = []
        if not isinstance(coaching, list):
            coaching = []
        insights = [_shorten_to_one_or_two_sentences(str(s).strip()) for s in insights if s]
        coaching = [_shorten_to_one_or_two_sentences(str(s).strip()) for s in coaching if s]
        insights = [s for s in insights if s]
        coaching = [s for s in coaching if s]
        if not insights and not coaching:
            logger.warning("코칭 AI: insights/coaching 둘 다 비어 있음, 폴백 사용")
            return None
        coaching = _coaching_fix_count(coaching)
        logger.info("코칭 AI 생성 성공: insights=%d, coaching=%d", len(insights), len(coaching))
        return (insights, coaching)
    except Exception as e:
        logger.warning("코칭 AI 생성 실패, 폴백 사용: %s", e, exc_info=True)
        return None


# 폴백용 코칭 문장 후보 (1~2문장, 짧게 표기용)
_FALLBACK_COACHING = {
    "negative_ratio": [
        "한 번에 하나씩 말해 보면 상대도 이해하기 쉬워요.",
        "감정이 올라갔을 땐 한 가지씩만 말해 보는 걸 추천해요.",
        "복잡한 느낌은 나눠서 말하면 좋아요.",
    ],
    "volatility": [
        "말하기 전에 한 숨 돌리면 더 편해요.",
        "한 템포 쉬었다가 말해 보면 도움이 될 거예요.",
        "심호흡 한 번 한 뒤 말해 보는 건 어떨까요?",
    ],
    "strong_habit": [
        "'항상', '맨날' 대신 '이번에는', '요즘'처럼 말해 보면 오해가 줄어들어요.",
        "강한 표현 대신 '이번에', '요즘'처럼 말하면 부드러워져요.",
        "'이번에는'처럼 구체적으로 말해 보면 좋아요.",
    ],
    "question_ratio": [
        "내 생각을 먼저 조금 말해 보는 것도 좋아요.",
        "먼저 내 생각 한두 문장 말해 보면 대화가 풍성해져요.",
        "궁금한 것만 말하기보다 내 느낌을 먼저 말해 보면 좋아요.",
    ],
}


def _fallback_insights_and_coaching(
    emotion_balance_result: Dict[str, Any],
    volatility_result: Dict[str, Any],
    habit_result: Dict[str, Any],
) -> Tuple[List[str], List[str]]:
    """OpenAI 미사용 시 인사이트·코칭 (후보 중 랜덤 선택으로 다양성 확보)."""
    insights: List[str] = []
    coaching: List[str] = []

    ub = emotion_balance_result
    insights.append(ub.get("sentence", ""))
    if ub.get("userNegativeRatio", 0) > 0.5:
        coaching.append(random.choice(_FALLBACK_COACHING["negative_ratio"]))

    vb = volatility_result
    insights.append(vb.get("sentence", ""))
    if vb.get("transitionCount", 0) >= 4:
        coaching.append(random.choice(_FALLBACK_COACHING["volatility"]))

    strong = habit_result.get("strongExpressions") or []
    neg_habit = habit_result.get("negativeHabitMessageCount", 0)
    if strong and neg_habit > 0:
        insights.append(
            "감정 분석(체크포인트)에서 분노·슬픔·두려움으로 나온 말에 강한 표현이 쓰였어요. "
            "사용자의 발화 습관이 부정적으로 이어질 수 있어요."
        )
        coaching.append(random.choice(_FALLBACK_COACHING["strong_habit"]))

    q_ratio = habit_result.get("questionRatio") or 0
    if q_ratio > 0.6:
        insights.append("질문이 많은 대화였어요.")
        coaching.append(random.choice(_FALLBACK_COACHING["question_ratio"]))

    lc = habit_result.get("laughOrCryCount") or 0
    if lc > 0:
        insights.append(f"감탄사나 이모티콘 표현이 {lc}번 사용되었어요.")

    coaching = _coaching_fix_count(coaching)
    return insights, coaching


def generate_insights_and_coaching(
    emotion_balance_result: Dict[str, Any],
    volatility_result: Dict[str, Any],
    habit_result: Dict[str, Any],
) -> Tuple[List[str], List[str]]:
    """
    인사이트(관찰) + 코칭(제안) 문장 생성.
    OpenAI 사용 가능하면 AI 생성, 실패·키 없음 시 하드코딩 폴백.
    """
    result = _generate_insights_and_coaching_with_ai(
        emotion_balance_result, volatility_result, habit_result
    )
    if result is not None:
        insights, coaching = result
        return insights, _coaching_fix_count(coaching)
    return _fallback_insights_and_coaching(
        emotion_balance_result, volatility_result, habit_result
    )
