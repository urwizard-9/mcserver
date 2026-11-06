import os
from dotenv import load_dotenv

load_dotenv()

class Config:
    DISCORD_TOKEN = os.getenv("DISCORD_TOKEN")
    
    # RCON 설정
    RCON_HOST = os.getenv("RCON_HOST", "mc-server")
    RCON_PORT = int(os.getenv("RCON_PORT", 25575))
    RCON_PASSWORD = os.getenv("RCON_PASSWORD", "password")
    
    # 데이터베이스 설정
    DB_HOST = os.getenv("DB_HOST", "postgres")
    DB_PORT = int(os.getenv("DB_PORT", 5432))
    DB_USER = os.getenv("DB_USER", "user")
    DB_PASSWORD = os.getenv("DB_PASSWORD", "password")
    DB_NAME = os.getenv("DB_NAME", "mc_stats")
