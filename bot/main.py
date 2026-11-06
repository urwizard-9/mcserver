import asyncio
import os
import threading
from contextlib import asynccontextmanager
from fastapi import FastAPI, BackgroundTasks, Request
import discord
from discord.ext import commands
from core.config import Config
from core.db import db
from core.log_parser import LogParser

# 디스코드 봇 설정
intents = discord.Intents.default()
intents.message_content = True
# intents.members = True # 개발자 포털에서 승인 필요 (User requested fix for crash)
bot = commands.Bot(command_prefix="!", intents=intents)

# 로그 파서 인스턴스
# 로그 파서 인스턴스는 lifespan에서 초기화됩니다

@bot.event
async def on_ready():
    print(f'Logged in as {bot.user} (ID: {bot.user.id})')
    # Extensions are now loaded in lifespan startup to avoid reloading on reconnects

@bot.event
async def on_command_error(ctx, error):
    if isinstance(error, commands.CommandNotFound):
        return
    if isinstance(error, commands.CheckFailure):
        # check_admin or cog_check failed
        # Usually checking handles messaging, but this catches bubbles
        return
    
    print(f"[ERROR] Command Error: {error}")
    # await ctx.send(f"❌ **오류 발생**: `{error}`") # 유저에게 에러 보여주기 (옵션)

# 관리자 권한 확인 함수
async def check_admin(ctx):
    # DM Check
    if not ctx.guild:
        await ctx.send("⛔ **DM에서는 사용할 수 없습니다.**")
        return False

    user_roles = [role.name.lower().strip() for role in ctx.author.roles]
    is_admin = ctx.author.guild_permissions.administrator
    
    print(f"[DEBUG] Check Admin for {ctx.author} (ID: {ctx.author.id})")
    print(f"[DEBUG] Admin Perm: {is_admin}, Roles: {user_roles}")

    # [Strict Mode] 관리자 권한(Administrator)이 있어도 이름이 일치하는 역할이 없으면 차단
    # 만약 서버 주인도 차단된다면 이 주석을 해제하세요.
    # if is_admin:
    #     print("[DEBUG] Access Granted (Administrator)")
    #     return True
    
    allowed_roles = ["admin", "minecraft admin", "operator", "op", "관리자", "운영자"]
    
    if any(role in allowed_roles for role in user_roles):
        print("[DEBUG] Access Granted (Role Match)")
        return True

    print("[DEBUG] Access Denied")
    await ctx.send("⛔ **권한이 없습니다.** 'Admin' 또는 '관리자' 역할이 필요합니다.")
    return False

@bot.command()
@commands.check(check_admin)
async def ping(ctx):
    print(f"[DEBUG] Executing PING command for {ctx.author}")
    await ctx.send('Pong!')

@bot.command()
@commands.check(check_admin)
async def health(ctx):
    """봇의 상태를 확인합니다."""
    print(f"[DEBUG] Executing HEALTH command for {ctx.author}")
    latency = round(bot.latency * 1000, 2)
    await ctx.send(f"✅ **System Healthy**\nLatency: `{latency}ms`")

# 킬 스트릭 추적기
kill_streaks = {}

async def handle_log_event(event_type, data):
    # 1. DB 업데이트 (비동기)
    if event_type == 'login':
        await db.update_timestamp(data['player'], "last_login")
        
    elif event_type == 'logout':
        player = data['player']
        # 플레이 타임 계산 로직은 DB로 이동하거나 여기서 처리해야 함
        await db.update_timestamp(player, "last_logout")
        
        # 광물 통계 업데이트
        if 'mined_stats' in data:
            for stat, value in data['mined_stats'].items():
                await db.set_stat(player, stat, value)

    elif event_type == 'advancement':
        await db.update_stat(data['player'], "advancements")

    elif event_type == 'death':
        victim = data['victim']
        killer = data['killer']
        is_pvp = data['is_pvp']
        
        await db.update_stat(victim, "deaths")
        if is_pvp and killer:
            await db.update_stat(killer, "kills")

    # 2. 디스코드 알림
    channel = bot.get_channel(1445330465530576938)
    if not channel:
        print(f"Warning: !!! No channel found to broadcast {event_type}")
        return

    if event_type == 'death':
        victim = data['victim']
        killer = data['killer']
        reason = data['reason']
        is_pvp = data['is_pvp']
        
        # Reset victim's streak
        if victim in kill_streaks:
            if kill_streaks[victim] >= 3:
                await channel.send(f"🛑 **{victim}**님의 {kill_streaks[victim]}연속 킬이 **{killer if killer else reason}**에 의해 저지당했습니다!")
            del kill_streaks[victim]

        # PvP 처리
        if is_pvp and killer:
            kill_streaks[killer] = kill_streaks.get(killer, 0) + 1
            streak = kill_streaks[killer]
            
            if streak == 3:
                await channel.send(f"🔥 **{killer}**님이 **학살**을 시작했습니다! (3킬)")
            elif streak == 5:
                await channel.send(f"🩸 **{killer}**님을 **막을 수 없습니다**! (5킬)")
            elif streak >= 10:
                await channel.send(f"💀 **{killer}**님은 **신**입니다! ({streak}킬)")

            await channel.send(f"⚔️ **{victim}**님이 **{killer}**님에게 살해당했습니다.")

        # 굴욕적인 죽음 처리 (PvE)
        if not is_pvp:
            if "cactus" in reason or "pricked" in reason:
                await channel.send(f"🌵 **{victim}**님이 선인장이나 가시에 찔려 사망했습니다. 따끔하네요.")
            elif "berry" in reason:
                await channel.send(f"🫐 **{victim}**님이 달콤한 베리 덤불에 찔려 죽었습니다. 부끄럽군요.")
            elif "high place" in reason or "hit the ground" in reason:
                await channel.send(f"📉 **{victim}**님이 날 수 있다고 믿었습니다.")
            elif "drowned" in reason:
                await channel.send(f"💧 **{victim}**님이 숨쉬는 법을 까먹었습니다.")
            elif "lava" in reason:
                await channel.send(f"🔥 **{victim}**님이 용암 수영을 시도했습니다.")
            elif "starved" in reason:
                await channel.send(f"🍖 **{victim}**님이 배고픔을 이기지 못했습니다. 밥 좀 챙겨 드세요.")
            elif "suffocated" in reason:
                await channel.send(f"🧱 **{victim}**님이 벽 속에 갇혔습니다.")
            elif "squashed" in reason or "anvil" in reason:
                await channel.send(f"🔨 **{victim}**님이 떨어지는 모루에 납작해졌습니다. 머리 조심!")
            elif "world" in reason or "void" in reason:
                await channel.send(f"🌌 **{victim}**님이 공허로 떠났습니다. 사요나라.")
            elif "kinetic" in reason:
                await channel.send(f"🚀 **{victim}**님이 너무 빨리 날았습니다. (운동 에너지)")
            elif "lightning" in reason:
                await channel.send(f"⚡ **{victim}**님이 천벌을 받았습니다.")
            elif "frozen" in reason:
                await channel.send(f"🥶 **{victim}**님이 동태가 되었습니다.")
            elif "stung" in reason:
                await channel.send(f"🐝 **{victim}**님이 벌집을 건드렸나 봅니다.")
            else:
                await channel.send(f"☠️ **{victim}**님이 사망했습니다. ({reason})")

    elif event_type == 'advancement':
        player = data['player']
        advancement = data['advancement']
        await channel.send(f"🏆 **{player}**님이 **[{advancement}]** 업적을 달성했습니다!")

    elif event_type == 'login':
        await channel.send(f"👋 **{data['player']}**님이 서버에 접속했습니다!")

    elif event_type == 'logout':
        await channel.send(f"🚪 **{data['player']}**님이 서버에서 나갔습니다.")

# FastAPI 설정
@asynccontextmanager
async def lifespan(app: FastAPI):
    # 시작
    print("Initializing Database...")
    await db.connect()
    await db.init_db() # Recreate tables
    
    # Load all extensions here
    extensions = ["cogs.admin", "cogs.stats", "cogs.grafana"]
    for ext in extensions:
        try:
            await bot.load_extension(ext)
            print(f"Loaded extension: {ext}")
        except Exception as e:
            print(f"Failed to load extension {ext}: {e}")
    
    print("Starting Log Parser...")
    
    # Define callback wrapper for thread safety
    def event_callback(event_type, data):
        if Config.DISCORD_TOKEN and bot.is_ready():
            asyncio.run_coroutine_threadsafe(handle_log_event(event_type, data), bot.loop)
            
    # Initialize parser with callback
    global log_parser
    log_parser = LogParser(event_callback=event_callback)
    
    parser_thread = threading.Thread(target=log_parser.start, daemon=True)
    parser_thread.start()
    
    # Run Discord Bot in background
    if Config.DISCORD_TOKEN:
        asyncio.create_task(bot.start(Config.DISCORD_TOKEN))
    else:
        print("WARNING: DISCORD_TOKEN not set. Bot will not start.")
        
    yield
    
    # 종료
    print("Stopping Log Parser...")
    log_parser.stop()
    
    if not bot.is_closed():
        await bot.close()
        
    await db.close()

app = FastAPI(lifespan=lifespan)

@app.get("/")
async def read_root():
    return {"status": "ok", "message": "Minecraft Bot Server is Running"}

@app.get("/health")
async def health_check():
    """
    봇 서버 상태를 확인하는 헬스 체크 엔드포인트입니다.
    마인크래프트 서버 상태와는 독립적입니다.
    """
    return {
        "status": "healthy",
        "bot_connected": not bot.is_closed() and bot.is_ready(),
        "bot_latency_ms": round(bot.latency * 1000, 2) if bot.is_ready() else None
    }

@app.post("/alert")
async def receive_alert(request: Request):
    """
    Prometheus Alertmanager로부터 경고를 수신하는 엔드포인트입니다.
    """
    data = await request.json()
    print(f"Received alert: {data}")
    
    # Extract alert info
    alerts = data.get('alerts', [])
    for alert in alerts:
        status = alert.get('status')
        labels = alert.get('labels', {})
        annotations = alert.get('annotations', {})
        
        alert_name = labels.get('alertname', 'Unknown Alert')
        description = annotations.get('description', 'No description')
        
        # Send to a specific Discord channel (You need to set this ID)
        # channel = bot.get_channel(YOUR_CHANNEL_ID)
        # if channel:
        #     await channel.send(f"🚨 **{alert_name}** ({status})\n{description}")
            
    return {"status": "received"}
