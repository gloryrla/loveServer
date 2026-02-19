"""
대화 종료 후 결과 보고서: checkpoint_epoch_7(BERT)로 사용자·상대방 메시지 감정 분석.
사용자 중심 관계 코칭 리포트(userFocusReport) 포함.
"""
from collections import Counter
from typing import Any, Dict, List

from coaching import (
    emotion_balance,
    generate_insights_and_coaching,
    habit_analysis,
    volatility,
)


# 감정 5종 (config.EMOTIONS와 동일)
EMOTIONS = ["분노", "두려움", "기쁨", "평온", "슬픔"]


def build_report(emotion_result: Dict[str, Any]) -> Dict[str, Any]:
    """단일 감정 결과 → 요약 한 줄 + details."""
    emotion = emotion_result.get("emotion", "평온")
    confidence = emotion_result.get("confidence", 0.0)
    scores = emotion_result.get("scores", {})
    summary = f"이번 답변 감정: {emotion} ({confidence:.1f}%)"
    details: Dict[str, Any] = {"emotion": emotion, "scores": scores}
    return {"summary": summary, "details": details}


def _scores_to_percent(scores: Dict[str, float]) -> Dict[str, float]:
    """scores 값이 0~1이면 0~100 퍼센트로 변환."""
    if not scores:
        return {}
    return {k: round((v * 100) if v <= 1.0 else v, 2) for k, v in scores.items()}


def _message_analysis(text: str, emotion_result: Dict[str, Any]) -> Dict[str, Any]:
    """한 메시지: 텍스트 + 판정 감정 + 확신도(%) + 5종 감정 퍼센트 전부."""
    raw_scores = emotion_result.get("scores") or {}
    scores_pct = _scores_to_percent(raw_scores)
    return {
        "text": text,
        "emotion": emotion_result.get("emotion", "평온"),
        "confidence": round(emotion_result.get("confidence", 0.0), 2),
        "scores": raw_scores,
        "scoresPct": scores_pct,
    }


def _counts_and_summary(emotion_results: List[Dict[str, Any]], label: str) -> tuple[Dict[str, int], str]:
    """감정 결과 리스트 → { 감정: 횟수 } + 요약 문장."""
    if not emotion_results:
        return {}, f"{label}: 분석할 메시지 없음"
    emotions = [r.get("emotion", "평온") for r in emotion_results]
    counts = dict(Counter(emotions))
    order = [e for e in EMOTIONS if e in counts] + [e for e in counts if e not in EMOTIONS]
    parts = [f"{e} {counts[e]}회" for e in order]
    summary = f"{label} {len(emotions)}개: " + ", ".join(parts)
    return counts, summary


def build_report_from_emotions(
    user_texts: List[str],
    user_emotion_results: List[Dict[str, Any]],
    assistant_texts: List[str],
    assistant_emotion_results: List[Dict[str, Any]],
) -> Dict[str, Any]:
    """
    대화 종료 후 결과 보고서 생성.
    - 사용자(USER) 메시지별 감정 분석 결과
    - 상대방(ASSISTANT) 메시지별 감정 분석 결과
    - 각각 요약 + 전체 요약
    checkpoint_epoch_7(BERT)로 이미 분석된 결과를 받아서 보고서 구조만 만듦.
    """
    user_messages = [
        _message_analysis(t, r)
        for t, r in zip(user_texts, user_emotion_results)
    ]
    assistant_messages = [
        _message_analysis(t, r)
        for t, r in zip(assistant_texts, assistant_emotion_results)
    ]

    user_counts, user_summary = _counts_and_summary(user_emotion_results, "사용자")
    assistant_counts, assistant_summary = _counts_and_summary(assistant_emotion_results, "상대방")

    all_emotions = [r.get("emotion", "평온") for r in user_emotion_results] + [
        r.get("emotion", "평온") for r in assistant_emotion_results
    ]
    if not all_emotions:
        overall_summary = "분석할 대화가 없습니다."
    else:
        parts_user = [f"{e} {c}회" for e, c in sorted(user_counts.items(), key=lambda x: -x[1])] if user_counts else []
        parts_asst = [f"{e} {c}회" for e, c in sorted(assistant_counts.items(), key=lambda x: -x[1])] if assistant_counts else []
        overall_summary = "사용자: " + (", ".join(parts_user) or "없음") + " / 상대방: " + (", ".join(parts_asst) or "없음")

    # 사용자 중심 코칭 리포트
    user_emotions_ordered = [r.get("emotion", "평온") for r in user_emotion_results]
    balance = emotion_balance(user_counts, assistant_counts)
    vol = volatility(user_emotions_ordered)
    habits = habit_analysis(user_texts, user_emotion_results)
    insights_list, coaching_list = generate_insights_and_coaching(balance, vol, habits)

    user_focus_report: Dict[str, Any] = {
        "emotionBalance": balance,
        "volatility": vol,
        "habitAnalysis": habits,
        "insights": insights_list,
        "coaching": coaching_list,
    }

    details: Dict[str, Any] = {
        "userReport": {
            "summary": user_summary,
            "counts": user_counts,
            "messages": user_messages,
        },
        "assistantReport": {
            "summary": assistant_summary,
            "counts": assistant_counts,
            "messages": assistant_messages,
        },
        "userFocusReport": user_focus_report,
    }
    return {
        "summary": overall_summary,
        "details": details,
    }
