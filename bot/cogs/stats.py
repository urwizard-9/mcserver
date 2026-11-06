import discord
from discord.ext import commands
from core.db import db

class Stats(commands.Cog):
    def __init__(self, bot):
        self.bot = bot

    @commands.command(name="leaderboard", aliases=["lb", "랭킹"])
    async def leaderboard(self, ctx, stat_type: str = "playtime"):
        """랭킹을 보여줍니다. 지원: playtime, kills, deaths, diamonds, coal, iron, gold, netherite, blocks"""
        
        # 별칭 매핑 (사용자가 요청한 목록으로 한정)
        aliases = {
            "다이아": "diamonds_mined", "diamonds": "diamonds_mined", "다이아몬드": "diamonds_mined",
            "석탄": "coal_mined", "coal": "coal_mined",
            "철": "iron_mined", "iron": "iron_mined",
            "금": "gold_mined", "gold": "gold_mined",
            "네더라이트": "netherite_mined", "netherite": "netherite_mined",
            "킬": "kills", "kills": "kills",
            "데스": "deaths", "deaths": "deaths",
            "블럭": "blocks_broken", "blocks": "blocks_broken", "blocks_broken": "blocks_broken",
            "플레이타임": "playtime", "playtime": "playtime", "접속시간": "playtime"
        }
        
        target_stat = aliases.get(stat_type.lower(), stat_type.lower())
        
        # 유효성 검사
        valid_columns = list(set(aliases.values()))
        if target_stat not in valid_columns:
            available_korean = ["플레이타임", "킬", "데스", "다이아", "석탄", "철", "금", "네더라이트", "블럭"]
            await ctx.send(f"❌ **지원하지 않는 통계입니다.**\n사용 가능: `{', '.join(available_korean)}`")
            return

        data = await db.get_top_players(target_stat)
        
        if not data:
            await ctx.send("📊 **아직 기록된 데이터가 없습니다.**")
            return

        # 타이틀 정리
        title_map = {
            "diamonds_mined": "💎 다이아몬드 채굴 랭킹",
            "coal_mined": "⚫ 석탄 채굴 랭킹",
            "iron_mined": "⚪ 철 채굴 랭킹",
            "gold_mined": "🟡 금 채굴 랭킹",
            "netherite_mined": "🟣 네더라이트 채굴 랭킹",
            "kills": "⚔️ 최다 킬 랭킹",
            "deaths": "☠️ 최다 사망 랭킹",
            "blocks_broken": "⛏️ 총 채굴량 랭킹",
            "playtime": "⏱️ 플레이 타임 랭킹"
        }
        
        embed = discord.Embed(title=title_map.get(target_stat, f"Ranking: {target_stat}"), color=discord.Color.gold())
        
        description = ""
        for i, row in enumerate(data, 1):
            player = row['player_name']
            raw_value = row[target_stat]
            
            # 값 포맷팅
            if target_stat == "playtime":
                # Ticks -> Time String (1sec = 20ticks)
                total_seconds = raw_value / 20
                hours = int(total_seconds // 3600)
                minutes = int((total_seconds % 3600) // 60)
                value_str = f"{hours}시간 {minutes}분"
            else:
                value_str = f"{raw_value:,}" # 천단위 콤마

            medal = {1: "🥇", 2: "🥈", 3: "🥉"}.get(i, f"**{i}.**")
            description += f"{medal} **{player}**: {value_str}\n"
        
        embed.description = description
        await ctx.send(embed=embed)

async def setup(bot):
    await bot.add_cog(Stats(bot))
