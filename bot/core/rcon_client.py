from mcrcon import MCRcon
from .config import Config

class RconClient:
    def __init__(self):
        self.host = Config.RCON_HOST
        self.port = Config.RCON_PORT
        self.password = Config.RCON_PASSWORD

    def send_command(self, command: str) -> str:
        """
        RCON을 통해 마인크래프트 서버로 명령어를 전송합니다.
        서버로부터의 응답을 반환합니다.
        """
        try:
            with MCRcon(self.host, self.password, port=self.port) as mcr:
                response = mcr.command(command)
                return response
        except Exception as e:
            return f"RCON 연결 오류: {str(e)}"

# 싱글톤 인스턴스
rcon = RconClient()
