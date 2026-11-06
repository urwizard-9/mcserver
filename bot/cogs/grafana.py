import discord
from discord.ext import commands
import aiohttp
import io
import os
import json

class Grafana(commands.Cog):
    def __init__(self, bot):
        self.bot = bot
        self.grafana_url = os.getenv("GRAFANA_URL", "http://grafana:3000")
        self.grafana_token = os.getenv("GRAFANA_TOKEN")
        # Default Dashboard UID
        self.dashboard_uid = "minecraft-server" 
        self.charts = {} # Name -> ID mapping

    async def cog_check(self, ctx):
        # DM Check
        if not ctx.guild:
            await ctx.send("⛔ **DM에서는 사용할 수 없습니다.**")
            return False

        user_roles = [role.name.lower().strip() for role in ctx.author.roles]
        
        print(f"[DEBUG][GrafanaCog] Check for {ctx.author} (ID: {ctx.author.id})")
        print(f"[DEBUG][GrafanaCog] Roles: {user_roles}")

        # 1. 관리자 권한(Administrator) 확인 -> [Strict Mode] 비활성화
        # if ctx.author.guild_permissions.administrator:
        #     return True
            
        # 2. 특정 역할(Role) 확인 (대소문자 구분 없음)
        allowed_roles = ["admin", "minecraft admin", "operator", "op", "관리자", "운영자"]
        
        if any(role in allowed_roles for role in user_roles):
            print("[DEBUG][GrafanaCog] Access Granted (Role Match)")
            return True

        print("[DEBUG][GrafanaCog] Access Denied")
        await ctx.send("⛔ **권한이 없습니다.** 'Admin' 또는 '관리자' 역할이 필요합니다.")
        return False

    async def sync_dashboard_panels(self):
        """
        Fetches the dashboard definition from Grafana and updates the chart mapping.
        """
        if not self.grafana_token:
            print("WARNING: GRAFANA_TOKEN not set. Cannot sync charts.")
            return False

        api_url = f"{self.grafana_url}/api/dashboards/uid/{self.dashboard_uid}"
        headers = {
            "Authorization": f"Bearer {self.grafana_token}",
            "Content-Type": "application/json"
        }

        async with aiohttp.ClientSession() as session:
            async with session.get(api_url, headers=headers) as resp:
                if resp.status != 200:
                    text = await resp.text()
                    print(f"Failed to fetch dashboard: {resp.status} | URL: {api_url}")
                    print(f"Response: {text}")
                    return False

                data = await resp.json()
                dashboard = data.get("dashboard", {})
                panels = dashboard.get("panels", [])

                new_charts = {}
                for panel in panels:
                    # Handle Row panels which might contain other panels (if nested)
                    # But usually top-level panels are what we want.
                    # We use the panel 'title' as the key.
                    title = panel.get("title")
                    p_id = panel.get("id")

                    if title and p_id is not None:
                        # Normalize title: lowercase, remove spaces for easier typing?
                        # Let's keep it simple: lowercase.
                        key = title.lower()
                        new_charts[key] = p_id

                self.charts = new_charts
                print(f"Synced {len(self.charts)} charts from Grafana: {list(self.charts.keys())}")
                return True

    @commands.Cog.listener()
    async def on_ready(self):
        # Try to sync on startup
        await self.sync_dashboard_panels()

    @commands.command(name="sync")
    async def sync_charts_cmd(self, ctx):
        """
        Manually triggers a sync with Grafana Dashboard.
        Use this if you added new panels to Grafana.
        """
        print(f"[DEBUG] Executing SYNC command for {ctx.author}")
        success = await self.sync_dashboard_panels()
        if success:
            available = ", ".join(self.charts.keys())
            await ctx.send(f"✅ **Synced Successfully!**\nFound {len(self.charts)} charts: `{available}`")
        else:
            await ctx.send("❌ **Sync Failed.** Check logs or Token.")

    @commands.command(name="set_dashboard")
    async def set_dashboard(self, ctx, uid: str):
        """
        Sets the Grafana Dashboard UID and re-syncs.
        """
        print(f"[DEBUG] Executing SET_DASHBOARD command for {ctx.author}")
        self.dashboard_uid = uid
        await self.sync_dashboard_panels()
        await ctx.send(f"✅ Dashboard UID set to `{uid}` and synced.")

    @commands.command(name="chart")
    async def get_chart(self, ctx, *, chart_name: str = None):
        """
        Fetches a chart from Grafana.
        Usage: !chart <name> (e.g., !chart players)
        """
        print(f"[DEBUG] Executing CHART command for {ctx.author}")
        if not self.grafana_token:
            await ctx.send("⚠️ **Configuration Error**: `GRAFANA_TOKEN` is not set.")
            return

        if not self.charts:
            # Try syncing if empty
            await self.sync_dashboard_panels()

        if not chart_name:
            available = ", ".join(self.charts.keys())
            await ctx.send(f"ℹ️ **Available Charts**: {available}\nUsage: `!chart <name>`")
            return

        # Fuzzy match or exact match
        target_id = self.charts.get(chart_name.lower())

        if not target_id:
            await ctx.send(f"⚠️ **Unknown Chart**: `{chart_name}`\nAvailable: {', '.join(self.charts.keys())}\nTry `!sync` if you just added it.")
            return

        # Construct Render URL
        render_url = (
            f"{self.grafana_url}/render/d-solo/{self.dashboard_uid}/dashboard"
            f"?panelId={target_id}&from=now-1h&to=now&width=1000&height=500&tz=Asia%2FSeoul"
        )

        headers = {
            "Authorization": f"Bearer {self.grafana_token}"
        }

        async with ctx.typing():
            try:
                async with aiohttp.ClientSession() as session:
                    async with session.get(render_url, headers=headers) as resp:
                        if resp.status != 200:
                            await ctx.send(f"❌ **Grafana Error** ({resp.status})")
                            return

                        data = await resp.read()
                        file = discord.File(io.BytesIO(data), filename=f"{chart_name}.png")
                        await ctx.send(f"📊 **{chart_name.upper()}**", file=file)
            except Exception as e:
                await ctx.send(f"❌ **System Error**: {str(e)}")

async def setup(bot):
    await bot.add_cog(Grafana(bot))