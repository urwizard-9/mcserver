# LGMC 5.1.3 Bedrock resource-pack trial patch

Upstream: https://github.com/DarkShoro/lgmc/tree/v5.1.3 (MIT, see LICENSE).
ResourcePackManager.java and the countdown/validation portion of GameManager.java are modified. Original release SHA-256:
`696096f5d555d9e223fba6a4324099d5a38a55277f58b00aebce1c3b305eb20a`.

LGMC sends Java resource packs to every player and kicks on DECLINED. The
Bedrock connection reaches the server successfully, then is kicked by this
listener. There is no config switch in this release for this behavior.

The patch checks the authenticated UUID using FloodgateApi.isFloodgatePlayer.
It skips Java pack delivery and its status-event handler for Floodgate players.
Java player handling is unchanged. Names and username prefixes are not trusted
for this check. If the API cannot be read, the original policy stays in effect
and a warning is logged. Floodgate is added as a soft dependency to order loading.

This is a connection trial patch, not a Bedrock resource-pack conversion.
Custom models, sounds, textures and role UI still require in-game validation.
Do not mark mixed gameplay supported based only on successful login.

Build with Java 21 against the original LGMC JAR and installed Paper libraries.
Run `python3 scripts/build-trial-plugins.py`. It replaces only ResourcePackManager*.class,
GameManager*.class and plugin.yml in a copy of the original JAR.
Use version `5.1.3-bedrock-trial.2`. Preserve the original in
`trial-data/original-plugins/` and record the patched hash in the trial manifest.
Stop the server before swapping JARs; never hot-reload this change.

Rollback: stop the trial server, move the patched JAR out of plugins, restore
the original JAR from original-plugins, restore its manifest entry, then start.

Required runtime checks: patched version enabled; Java still receives its pack;
Floodgate login logs the skip message; Bedrock stays connected and can use GUI;
neither API lookup warnings nor resource-pack kicks occur for that connection.

## Countdown patch (trial.2)

Tracks the original 10-second countdown task and exposes cancelStart() to the separate
LgmcLobby plugin. Rejects duplicate starts; checks 4–12 players, configured spawns,
and unchanged online UUID roster during countdown. Sets inGame only after role
distribution preconditions pass, preventing failed starts from leaving a stuck game.
Role selection, voting and victory rules remain upstream behavior. Ready deadlines,
Korean ready UI and unready kicks live in lobby/, not in LGMC.

Runtime verified: both plugins enabled, 12 spawns loaded, empty-server state correct,
RCON direct start rejected. Four-player countdown/cancellation and complete gameplay
remain pending real clients.
