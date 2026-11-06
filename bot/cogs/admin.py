import discord
from discord.ext import commands
from core.rcon_client import rcon

class Admin(commands.Cog):
    def __init__(self, bot):
        self.bot = bot

    async def cog_check(self, ctx):
        # DM Check
        if not ctx.guild:
            await ctx.send("⛔ **DM에서는 사용할 수 없습니다.**")
            return False

        user_roles = [role.name.lower().strip() for role in ctx.author.roles]
        
        print(f"[DEBUG][AdminCog] Check for {ctx.author} (ID: {ctx.author.id})")
        print(f"[DEBUG][AdminCog] Roles: {user_roles}")

        # 1. 관리자 권한(Administrator) 확인 -> [Strict Mode] 비활성화
        # if ctx.author.guild_permissions.administrator:
        #     return True
            
        # 2. 특정 역할(Role) 확인 (대소문자 구분 없음)
        allowed_roles = ["admin", "minecraft admin", "operator", "op", "관리자", "운영자"]
        
        if any(role in allowed_roles for role in user_roles):
            print("[DEBUG][AdminCog] Access Granted (Role Match)")
            return True

        print("[DEBUG][AdminCog] Access Denied")
        await ctx.send("⛔ **권한이 없습니다.** 'Admin' 또는 '관리자' 역할이 필요합니다.")
        return False

    @commands.command(name="say")
    async def say(self, ctx, *, message: str):
        """마인크래프트 서버에 메시지를 방송합니다."""
        print(f"[DEBUG] Executing SAY command for {ctx.author}")
        response = rcon.send_command(f"say {message}")
        await ctx.send(f"서버 응답: `{response}`")

    @commands.command(name="kick")
    async def kick(self, ctx, player: str, *, reason: str = "관리자에 의해 추방됨"):
        """서버에서 플레이어를 추방합니다."""
        print(f"[DEBUG] Executing KICK command for {ctx.author}")
        response = rcon.send_command(f"kick {player} {reason}")
        await ctx.send(f"서버 응답: `{response}`")

    @commands.command(name="whitelist")
    async def whitelist(self, ctx, action: str, player: str = None):
        """화이트리스트 관리. 사용법: !whitelist add <플레이어> 또는 !whitelist list"""
        print(f"[DEBUG] Executing WHITELIST command for {ctx.author}")
        if action.lower() == "list":
            response = rcon.send_command("whitelist list")
        elif player:
            response = rcon.send_command(f"whitelist {action} {player}")
        else:
            response = "사용법: !whitelist add <플레이어> | remove <플레이어> | list"
        
        await ctx.send(f"서버 응답: `{response}`")

    @commands.command(name="ban")
    async def ban(self, ctx, player: str, *, reason: str = "관리자에 의해 차단됨"):
        """플레이어를 영구 차단합니다."""
        print(f"[DEBUG] Executing BAN command for {ctx.author}")
        response = rcon.send_command(f"ban {player} {reason}")
        await ctx.send(f"🔨 **{player}**님을 차단했습니다.\n서버 응답: `{response}`")

    @commands.command(name="unban")
    async def unban(self, ctx, player: str):
        """플레이어 차단을 해제합니다."""
        print(f"[DEBUG] Executing UNBAN command for {ctx.author}")
        response = rcon.send_command(f"pardon {player}")
        await ctx.send(f"🔓 **{player}**님의 차단을 해제했습니다.\n서버 응답: `{response}`")

async def setup(bot):
    await bot.add_cog(Admin(bot))
