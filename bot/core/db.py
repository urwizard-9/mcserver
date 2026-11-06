import asyncpg
import os
from .config import Config

class Database:
    def __init__(self):
        self.pool = None

    async def connect(self):
        """데이터베이스 연결 풀을 생성합니다."""
        if not self.pool:
            try:
                self.pool = await asyncpg.create_pool(
                    user=Config.DB_USER,
                    password=Config.DB_PASSWORD,
                    database=Config.DB_NAME,
                    host=Config.DB_HOST,
                    port=Config.DB_PORT
                )
                print("Connected to Database (Async)")
            except Exception as e:
                print(f"Database Connection Error: {e}")
                raise e

    async def close(self):
        """데이터베이스 연결을 종료합니다."""
        if self.pool:
            await self.pool.close()
            print("Database Connection Closed")

    async def init_db(self):
        """데이터베이스 테이블을 초기화합니다 (기존 데이터 삭제)."""
        if not self.pool:
            await self.connect()

        # 기존 테이블 삭제 및 재생성 방지
        # queries = ["DROP TABLE IF EXISTS player_stats;"] 
        queries = [
            """
            CREATE TABLE IF NOT EXISTS player_stats (
                player_name VARCHAR(50) PRIMARY KEY,
                kills INT DEFAULT 0,
                deaths INT DEFAULT 0,
                blocks_broken INT DEFAULT 0,
                playtime BIGINT DEFAULT 0,
                last_login TIMESTAMP,
                last_logout TIMESTAMP,
                advancements INT DEFAULT 0,
                diamonds_mined INT DEFAULT 0,
                coal_mined INT DEFAULT 0,
                iron_mined INT DEFAULT 0,
                gold_mined INT DEFAULT 0,
                emerald_mined INT DEFAULT 0,
                lapis_mined INT DEFAULT 0,
                redstone_mined INT DEFAULT 0,
                netherite_mined INT DEFAULT 0,
                last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );
            """
        ]

        async with self.pool.acquire() as conn:
            for query in queries:
                await conn.execute(query)
        
        print("Database initialized (Tables recreated)")

    async def update_stat(self, player, stat_type, value=1):
        """플레이어의 통계를 증가시킵니다."""
        query = f"""
        INSERT INTO player_stats (player_name, {stat_type}, last_updated)
        VALUES ($1, $2, CURRENT_TIMESTAMP)
        ON CONFLICT (player_name)
        DO UPDATE SET {stat_type} = player_stats.{stat_type} + $2, last_updated = CURRENT_TIMESTAMP;
        """
        async with self.pool.acquire() as conn:
            await conn.execute(query, player, value)

    async def set_stat(self, player, stat_type, value):
        """통계의 특정 값을 설정합니다 (절대값 업데이트)."""
        query = f"""
        INSERT INTO player_stats (player_name, {stat_type}, last_updated)
        VALUES ($1, $2, CURRENT_TIMESTAMP)
        ON CONFLICT (player_name)
        DO UPDATE SET {stat_type} = $2, last_updated = CURRENT_TIMESTAMP;
        """
        async with self.pool.acquire() as conn:
            await conn.execute(query, player, value)

    async def update_timestamp(self, player, column):
        """플레이어의 타임스탬프 컬럼을 업데이트합니다."""
        query = f"""
        INSERT INTO player_stats (player_name, {column}, last_updated)
        VALUES ($1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        ON CONFLICT (player_name)
        DO UPDATE SET {column} = CURRENT_TIMESTAMP, last_updated = CURRENT_TIMESTAMP;
        """
        async with self.pool.acquire() as conn:
            await conn.execute(query, player)

    async def get_top_players(self, stat_type, limit=10):
        """특정 통계의 상위 플레이어 목록을 가져옵니다."""
        query = f"""
        SELECT player_name, {stat_type} 
        FROM player_stats 
        ORDER BY {stat_type} DESC 
        LIMIT $1;
        """
        async with self.pool.acquire() as conn:
            rows = await conn.fetch(query, limit)
            return [dict(row) for row in rows]

    async def get_player_stat(self, player, stat_type):
        """특정 플레이어의 특정 통계 값을 가져옵니다."""
        query = f"SELECT {stat_type} FROM player_stats WHERE player_name = $1;"
        async with self.pool.acquire() as conn:
            val = await conn.fetchval(query, player)
            return val if val is not None else 0

db = Database()
