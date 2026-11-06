import time
import re
import os
from .stats_reader import StatsReader

class LogParser:
    def __init__(self, log_path="/mc-logs/logs/latest.log", event_callback=None):
        self.log_path = log_path
        self._running = False
        self.event_callback = event_callback
        self.stats_reader = StatsReader()
        self.known_players = set()
        
        # Regex Patterns
        self.patterns = {
            'login': re.compile(r': (\w+) joined the game'),
            'logout': re.compile(r': (\w+) left the game'),
            'advancement': re.compile(r': (\w+) has (?:made the advancement|reached the goal|completed the challenge) \[(.+?)\]'),
            'death': re.compile(r': (.+?) (was slain by|was pricked to death|walked into a cactus|burned to death|drowned|fell from a high place|tried to swim in lava|blew up|was shot by|withered away|died|was killed by|starved to death|suffocated in a wall|was squashed by a falling anvil|fell out of the world|fell from a high place|experienced kinetic energy|was struck by lightning|discovered the floor was lava|was impaled|froze to death|was stung to death|hit the ground too hard) ?(.*)'),
        }

    def follow(self):
        """로그 파일에서 새로운 라인을 생성하는 제너레이터입니다. 파일 로테이션을 처리합니다."""
        self.log_file.seek(0, os.SEEK_END)
        
        # 현재 파일의 inode 저장
        try:
            current_inode = os.fstat(self.log_file.fileno()).st_ino
        except Exception as e:
            print(f"[ERROR] Failed to get inode: {e}")
            return

        while self._running:
            line = self.log_file.readline()
            if not line:
                # EOF 도달 시 파일 로테이션 확인
                try:
                    if os.path.exists(self.log_path):
                        new_inode = os.stat(self.log_path).st_ino
                        if new_inode != current_inode:
                            print(f"[INFO] Log rotation detected. Reopening {self.log_path}...")
                            self.log_file.close()
                            self.log_file = open(self.log_path, "r", encoding="utf-8", errors='ignore')
                            current_inode = new_inode
                            # 새 파일의 처음부터 읽기 시작
                            continue
                except Exception as e:
                    print(f"[ERROR] Error checking for log rotation: {e}")
                
                time.sleep(0.1)
                continue
            yield line

    def start(self):
        print(f"Starting Log Parser on {self.log_path}...")
        self._running = True
        
        # Load initial players from usercache
        self.stats_reader.load_usercache()
        self.known_players.update(self.stats_reader.uuid_map.keys())
        print(f"[INFO] Loaded {len(self.known_players)} players from usercache.")
        
        # Wait for file to exist
        while not os.path.exists(self.log_path):
            print(f"Waiting for log file: {self.log_path}")
            time.sleep(5)

        try:
            with open(self.log_path, "r", encoding="utf-8", errors='ignore') as f:
                self.log_file = f # Assign the opened file object
                for line in self.follow():
                    if not self._running: break
                    self.process_line(line)
        except Exception as e:
            print(f"Log Parser Error: {e}")

    def process_line(self, line):
        # 메시지 내용을 추출하기 위한 기본 파싱
        # 로그 형식: [Time] [Thread/Level]: Message
        parts = line.split(']: ', 1)
        if len(parts) < 2:
            return
        
        message = ": " + parts[1].strip() # 정규식 일관성을 위해 콜론 추가
        
        # 1. 로그인
        match = self.patterns['login'].search(message)
        if match:
            player = match.group(1)
            self.known_players.add(player)
            if self.event_callback:
                self.event_callback("login", {"player": player})
            return

        # 2. 로그아웃
        match = self.patterns['logout'].search(message)
        if match:
            player = match.group(1)
            
            # 광물 채굴 통계 업데이트
            print(f"[DEBUG] Logout detected for {player}. Fetching stats...")
            mined_stats = self.stats_reader.get_mined_counts(player)
            print(f"[DEBUG] Stats for {player}: {mined_stats}")
            
            if self.event_callback:
                self.event_callback("logout", {"player": player, "mined_stats": mined_stats})
            return

        # 3. 발전 과제
        match = self.patterns['advancement'].search(message)
        if match:
            player = match.group(1)
            advancement = match.group(2)
            if self.event_callback:
                self.event_callback("advancement", {"player": player, "advancement": advancement})
            return

        # 4. 사망
        match = self.patterns['death'].search(message)
        if match:
            victim = match.group(1)
            reason_part = match.group(2)
            killer_part = match.group(3)
            
            is_pvp = False
            killer = None
            
            # 킬러가 플레이어인지 확인 (known_players 목록 대조)
            if "was slain by" in reason_part and killer_part:
                clean_killer_part = killer_part.strip()
                for player in self.known_players:
                    # "Alex" 또는 "Alex using [Sword]" 형태 확인
                    if clean_killer_part == player or clean_killer_part.startswith(f"{player} "):
                        is_pvp = True
                        killer = player
                        break
            
            if self.event_callback:
                self.event_callback("death", {
                    "victim": victim, 
                    "killer": killer, 
                    "is_pvp": is_pvp, 
                    "reason": f"{reason_part} {killer_part}".strip()
                })
            return

    def stop(self):
        self._running = False
        self._running = False
