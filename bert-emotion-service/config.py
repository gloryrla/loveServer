"""
환경 설정. .env 파일 또는 환경 변수에서 OPENAI_API_KEY 등 로드.
"""
import os
from pathlib import Path
from typing import Optional

from dotenv import load_dotenv

# 프로젝트 루트(bert-emotion-service/)의 .env 로드 (__file__ 기준)
load_dotenv(Path(__file__).resolve().parent / ".env")
# 실행 경로(cwd)의 .env도 로드 (uvicorn 실행 위치에 .env 있을 때)
load_dotenv()

# 경로
BASE_DIR = Path(__file__).resolve().parent
CHECKPOINT_DIR = BASE_DIR / "checkpoints"
CHECKPOINT_PATH = CHECKPOINT_DIR / "checkpoint_epoch_7.pt"

# BERT 감정 모델
MODEL_NAME = "klue/bert-base"
EMOTIONS = ["분노", "두려움", "기쁨", "평온", "슬픔"]

# OpenAI (환경 변수 필수)
def get_openai_api_key() -> Optional[str]:
    return os.environ.get("OPENAI_API_KEY")

# 채팅 기본값
DEFAULT_CHAT_MODEL = "gpt-4o-mini"
DEFAULT_MAX_TOKENS = 512
