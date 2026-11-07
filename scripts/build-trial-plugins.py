"""Build trial plugins without modifying installed plugins or starting the server."""
from pathlib import Path
import hashlib
import json
import subprocess
import zipfile

root = Path(__file__).resolve().parents[1]
data = root / "trial-data"
build = data / "lobby-build"
build.mkdir(exist_ok=True)
original = data / "original-plugins/lgmc-5.1.3.jar"
assert hashlib.sha256(original.read_bytes()).hexdigest() == "696096f5d555d9e223fba6a4324099d5a38a55277f58b00aebce1c3b305eb20a"
image = "eclipse-temurin@sha256:ebfbb18d11ff163ee83b21258acf12248af48d22a0fdfdc68fa1a1516c05976a"
classpath = ["/data/original-plugins/lgmc-5.1.3.jar"] + ["/data/" + str(p.relative_to(data)) for p in sorted((data / "libraries").rglob("*.jar"))]
sources = sorted((root / "patches/lgmc/src").rglob("*.java")) + sorted((root / "lobby/src/main/java").rglob("*.java"))
args = ["--release", "21", "-proc:none", "-encoding", "UTF-8", "-cp", ":".join(classpath), "-d", "/data/lobby-build/classes"]
args += ["/source/" + str(p.relative_to(root)) for p in sources]
(build / "javac.args").write_text("\n".join(args) + "\n")
subprocess.run(["docker", "run", "--rm", "--network", "none", "--mount", f"type=bind,source={data},target=/data", "--mount", f"type=bind,source={root},target=/source,readonly", image, "javac", "@/data/lobby-build/javac.args"], check=True)
classes = build / "classes"
replacements = {str(p.relative_to(classes)): p.read_bytes() for p in (classes / "fr/lightshoro/lgmc/managers").glob("*.class")}
patched = build / "lgmc-5.1.3-bedrock-trial.2.jar"
with zipfile.ZipFile(original) as src, zipfile.ZipFile(patched, "w", zipfile.ZIP_DEFLATED) as dst:
    for entry in src.infolist():
        content = replacements.pop(entry.filename, src.read(entry.filename))
        if entry.filename == "plugin.yml":
            content = content.decode().replace("version: '5.1.3'", "version: '5.1.3-bedrock-trial.2'").replace('softdepend: [ "SkinsRestorer" ]', 'softdepend: [ "SkinsRestorer", "floodgate" ]').encode()
        dst.writestr(entry, content)
    for name, content in replacements.items():
        dst.writestr(name, content)
with zipfile.ZipFile(original) as src, zipfile.ZipFile(patched) as dst:
    changed = [n for n in src.namelist() if src.read(n) != dst.read(n)]
    assert all(n == "plugin.yml" or n.startswith("fr/lightshoro/lgmc/managers/ResourcePackManager") or n.startswith("fr/lightshoro/lgmc/managers/GameManager") for n in changed), changed
lobby = build / "lgmc-lobby-0.3.0-trial.jar"
with zipfile.ZipFile(lobby, "w", zipfile.ZIP_DEFLATED) as dst:
    for p in (classes / "local").rglob("*.class"):
        dst.write(p, str(p.relative_to(classes)))
    for p in (root / "lobby/src/main/resources").glob("*.yml"):
        dst.write(p, p.name)
report = {"jdk_image": image, "changed_lgmc_entries": changed, "artifacts": {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in [patched, lobby]}}
(build / "build-report.json").write_text(json.dumps(report, indent=2) + "\n")
print(json.dumps(report, indent=2))
