import json
import os

class StatsReader:
    def __init__(self, stats_dir="/mc-logs/world/stats", usercache_path="/mc-logs/usercache.json"):
        self.stats_dir = stats_dir
        self.usercache_path = usercache_path
        self.uuid_map = {}

    def load_usercache(self):
        """usercache.json 파일을 로드하여 플레이어 이름을 UUID로 매핑합니다."""
        if not os.path.exists(self.usercache_path):
            print(f"[WARN] {self.usercache_path} 에서 usercache를 찾을 수 없습니다.")
            return

        try:
            with open(self.usercache_path, 'r') as f:
                data = json.load(f)
                # usercache 형식: [{"name": "Player", "uuid": "..."}]
                for entry in data:
                    self.uuid_map[entry['name']] = entry['uuid']
        except Exception as e:
            print(f"[ERROR] usercache 로드 실패: {e}")

    def get_mined_counts(self, player_name):
        """플레이어의 광물 채굴 통계를 반환합니다."""
        if player_name not in self.uuid_map:
            print(f"[DEBUG] {player_name} not in cache. Reloading...")
            self.load_usercache()
        
        if player_name not in self.uuid_map:
            print(f"[DEBUG] {player_name} not found in usercache.")
            return {}

        uuid = self.uuid_map[player_name]
        stats_file = os.path.join(self.stats_dir, f"{uuid}.json")
        print(f"[DEBUG] Reading stats from {stats_file}")

        if not os.path.exists(stats_file):
            print(f"[DEBUG] Stats file not found: {stats_file}")
            return {}

        try:
            with open(stats_file, 'r') as f:
                data = json.load(f)
                stats = data.get('stats', {})
                mined = stats.get('minecraft:mined', {})
                
                def get_count(items):
                    return sum(mined.get(item, 0) for item in items)
                
                # Calculate total blocks broken
                total_blocks_broken = sum(mined.values())
                
                # Extract playtime (ticks)
                custom_stats = stats.get('minecraft:custom', {})
                play_time_ticks = custom_stats.get('minecraft:play_time', 0)

                return {
                    "diamonds_mined": get_count(['minecraft:diamond_ore', 'minecraft:deepslate_diamond_ore']),
                    "coal_mined": get_count(['minecraft:coal_ore', 'minecraft:deepslate_coal_ore']),
                    "iron_mined": get_count(['minecraft:iron_ore', 'minecraft:deepslate_iron_ore']),
                    "gold_mined": get_count(['minecraft:gold_ore', 'minecraft:deepslate_gold_ore', 'minecraft:nether_gold_ore']),
                    "emerald_mined": get_count(['minecraft:emerald_ore', 'minecraft:deepslate_emerald_ore']),
                    "lapis_mined": get_count(['minecraft:lapis_ore', 'minecraft:deepslate_lapis_ore']),
                    "redstone_mined": get_count(['minecraft:redstone_ore', 'minecraft:deepslate_redstone_ore']),
                    "netherite_mined": get_count(['minecraft:ancient_debris']),
                    "blocks_broken": total_blocks_broken,
                    "playtime": play_time_ticks
                }
        except Exception as e:
            print(f"[ERROR] {player_name}의 통계 읽기 실패: {e}")
            return {}
