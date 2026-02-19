"""
AI 오케스트레이션 서버: 페르소나, 대화(LLM), 감정(BERT), 리포트.
실행: uvicorn app:app --host 0.0.0.0 --port 5001
"""
import logging

from fastapi import FastAPI

from routers import analyze, ai_chat

logging.basicConfig(level=logging.INFO)

app = FastAPI(title="AI Orchestration API")

app.include_router(analyze.router)
app.include_router(ai_chat.router)


@app.get("/health")
def health():
    return {"status": "ok"}
