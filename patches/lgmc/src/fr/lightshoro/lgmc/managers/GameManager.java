package fr.lightshoro.lgmc.managers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Lightable;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import fr.lightshoro.lgmc.Lgmc;
import fr.lightshoro.lgmc.models.GamePlayer;
import fr.lightshoro.lgmc.models.Role;

@SuppressWarnings("deprecation")
public class GameManager {
    private final Lgmc plugin;
    private final RoleFinishers finishers;
    private boolean inGame;
    private boolean freezeAll;
    private int nightCount;
    private int dayCount;
    private String gameStep;
    private String queueMode;

    private final Map<UUID, GamePlayer> gamePlayers;
    private final List<Player> loupGarous;
    private final List<Player> villageois;
    private final List<Player> playersAlive;
    private final List<Player> playersDead;
    private final List<Player> playingPlayers;
    private final List<Player> goodGuys;
    private final List<Player> badGuys;
    private final List<Player> waitList;
    private final LinkedList<String> actionQueue;
    private final List<Player> lesAmoureux;

    private Player cupidon;
    private Player petiteFille;
    private Player chasseur;
    private Player sorciere;
    private Player voyante;
    private Player capitaine;
    private Player ange;
    private Player voleur;

    private Player nextLGTarget;
    private Player chasseurTarget;
    private Location chasseurOldPos;
    private boolean voyanteSondage;
    private boolean potionVie;
    private boolean potionMort;
    private boolean interceptChasseur;
    private boolean interceptCapitaine;
    private boolean capitaineVoteInProg;
    private boolean capitaineTieBreakerInProg;
    private boolean capitaineSuccession;
    private boolean successorChosen;
    private boolean voteInProg;
    @SuppressWarnings("unused")
    private boolean voteWasMade;
    private boolean chasseurDidLastKill;
    private boolean cupidonAction;
    private boolean sorciereAction;
    private boolean voleurAction;

    private Player dyingCapitaine;
    private final Map<Player, Integer> voteCount;
    private final Map<Player, Integer> voteCapitaine;
    private final Map<Player, Integer> designatedPlayers;
    private int designationCount;
    private int goodGuysCount;
    @SuppressWarnings("unused")
    private int badGuysCount;

    private Player mostVoted;
    @SuppressWarnings("unused")
    private int mostVotedNum;
    private Player doubleMostVoted;
    @SuppressWarnings("unused")
    private int doubleMostVotedNum;
    private boolean isStarting;
    private org.bukkit.scheduler.BukkitTask startTask;

    public GameManager(Lgmc plugin) {
        this.plugin = plugin;
        this.finishers = new RoleFinishers(plugin, this);
        this.gamePlayers = new HashMap<>();
        this.loupGarous = new ArrayList<>();
        this.villageois = new ArrayList<>();
        this.playersAlive = new ArrayList<>();
        this.playersDead = new ArrayList<>();
        this.playingPlayers = new ArrayList<>();
        this.goodGuys = new ArrayList<>();
        this.badGuys = new ArrayList<>();
        this.waitList = new ArrayList<>();
        this.actionQueue = new LinkedList<>();
        this.lesAmoureux = new ArrayList<>();
        this.voteCount = new HashMap<>();
        this.voteCapitaine = new HashMap<>();
        this.designatedPlayers = new HashMap<>();
    }

    public void gameReset(boolean isAdmin) {
        cancelStart();
        inGame = false;
        nightCount = 0;
        dayCount = 0;
        gameStep = null;
        queueMode = null;

        gamePlayers.clear();
        loupGarous.clear();
        villageois.clear();
        playersAlive.clear();
        playersDead.clear();
        playingPlayers.clear();
        goodGuys.clear();
        badGuys.clear();
        waitList.clear();
        actionQueue.clear();
        lesAmoureux.clear();
        voteCount.clear();
        voteCapitaine.clear();
        designatedPlayers.clear();

        cupidon = null;
        petiteFille = null;
        chasseur = null;
        sorciere = null;
        voyante = null;
        capitaine = null;
        ange = null;
        voleur = null;
        nextLGTarget = null;
        chasseurTarget = null;
        chasseurOldPos = null;
        dyingCapitaine = null;
        mostVoted = null;
        doubleMostVoted = null;

        voyanteSondage = false;
        potionVie = false;
        potionMort = false;
        interceptChasseur = false;
        interceptCapitaine = false;
        capitaineVoteInProg = false;
        capitaineTieBreakerInProg = false;
        capitaineSuccession = false;
        successorChosen = false;
        voteInProg = false;
        voteWasMade = false;
        chasseurDidLastKill = false;

        designationCount = 0;
        goodGuysCount = 0;
        badGuysCount = 0;
        mostVotedNum = 0;
        doubleMostVotedNum = 0;

        unSeparatePlayers();

        World world = Bukkit.getWorld("world");
        if (world != null) {
            world.setTime(6000);
        }

        plugin.getTimerManager().clearTimer();
        plugin.getWebsocketManager().sendDemuteAll();
        plugin.getScoreboardManager().clearScoreboards();
        plugin.getChatManager().setLoupGarouChatActive(false);
        plugin.getVoteDisplayManager().reset();

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
            player.getInventory().setHelmet(null);
            player.removePotionEffect(PotionEffectType.BLINDNESS);

            for (Player other : Bukkit.getOnlinePlayers()) {
                player.showPlayer(plugin, other);
            }

            // Clear relevant items from inventory (including netherite scrap)
            clearRelevantItems(player);

            // If the player have any kind of inventory GUI open, close it
            player.closeInventory();
        }

        freezeAll = false;
        plugin.getWebsocketManager().sendReset();
        lightCampfire();
        plugin.getSkinManager().restoreAllSkins();

        if (isAdmin) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendActionBar(plugin.getLanguageManager().getMessage("general.game-reset"));
            }
        }
    }

    public void separatePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            for (Player other : Bukkit.getOnlinePlayers()) {
                player.hidePlayer(plugin, other);
            }
        }
    }

    public void unSeparatePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            for (Player other : Bukkit.getOnlinePlayers()) {
                player.showPlayer(plugin, other);
            }
        }
    }

    public void actionQueueAdd(String action) {
        actionQueue.add(action);
    }

    public void actionQueuePocketNext(String action) {
        actionQueue.addFirst(action);
    }

    public void actionQueueRemove(String action) {
        actionQueue.remove(action);
    }

    public void actionQueueClear() {
        actionQueue.clear();
    }

    public boolean isActionQueueEmpty() {
        return actionQueue.isEmpty();
    }

    public void waitListAdd(Player player, String reason) {
        if (!waitList.contains(player)) {
            waitList.add(player);
            GamePlayer gp = getGamePlayer(player);
            if (gp != null) {
                gp.setDeathReason(reason != null ? reason : "default");
            }
        }
    }

    public void waitListRemove(Player player) {
        waitList.remove(player);
        GamePlayer gp = getGamePlayer(player);
        if (gp != null) {
            gp.setDeathReason(null);
        }
    }

    public void waitListKill() {
        if (waitList.isEmpty()) {
            Bukkit.broadcastMessage(plugin.getLanguageManager().getMessage("death.no-deaths"));
            return;
        }

        List<Player> toKill = new ArrayList<>(waitList);
        waitList.clear();

        for (Player player : toKill) {
            GamePlayer gp = getGamePlayer(player);
            String deathType = gp != null ? gp.getDeathReason() : "default";
            killPlayer(player, deathType);
        }
    }

    public void killPlayer(Player player, String deathType) {
        if (player == null) return;

        // Si c'est une déconnexion, pas d'interception spéciale
        boolean isDisconnect = "disconnect".equals(deathType);

        if (!isDisconnect && player.equals(capitaine) && !interceptCapitaine) {
            if (goodGuysCount > 1) {
                interceptCapitaine = true;
                GamePlayer gp = getGamePlayer(player);
                if (gp != null) {
                    gp.setDeathReason(deathType);
                }
                actionQueuePocketNext("doCapitaineSuccessor");

                if (actionQueue.size() == 1) {
                    if (!player.equals(chasseur)) {
                        actionQueueAdd("endDay");
                    } else {
                        actionQueueAdd("skip");
                    }
                }
                return;
            }
        }

        if (!isDisconnect && player.equals(chasseur) && !interceptChasseur) {
            interceptChasseur = true;
            GamePlayer gp = getGamePlayer(player);
            if (gp != null) {
                gp.setDeathReason(deathType);
            }
            actionQueuePocketNext("doChasseur");

            if (actionQueue.size() == 1) {
                actionQueueAdd("endDay");
            }
            return;
        }

        GamePlayer gp = getGamePlayer(player);
        Role playerRole = gp != null ? gp.getRole() : Role.NOT_IN_GAME;

        if (playerRole == Role.LOUP_GAROU) {
            badGuysCount--;
        } else {
            goodGuysCount--;
        }

        playersAlive.remove(player);
        playersDead.add(player);

        player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
        player.getInventory().setHelmet(plugin.getConfigManager().getRoleHelmetItemStack("mort"));
        
        // Mark player as dead in scoreboard memory
        plugin.getScoreboardManager().markPlayerAsDead(player);

        // Cacher le joueur mort de tous les joueurs vivants
        for (Player alive : playersAlive) {
            alive.hidePlayer(plugin, player);
        }

        // Le joueur mort peut voir tous les autres joueurs morts
        for (Player dead : playersDead) {
            player.showPlayer(plugin, dead);
            dead.showPlayer(plugin, player);
        }

        player.getWorld().strikeLightningEffect(player.getLocation());

        String message = getDeathMessage(player, deathType, playerRole);
        if (message != null) {
            Bukkit.broadcastMessage(message);
        }

        if (gp != null) {
            gp.setRole(Role.DEAD);
        }

        if (lesAmoureux.contains(player)) {
            lesAmoureux.remove(player);
            if (!lesAmoureux.isEmpty()) {
                Player lover = lesAmoureux.get(0);
                killPlayer(lover, "love");
                lesAmoureux.clear();
            }
        }

        removePlayerFromRole(player);

        // Si le joueur déconnecté était le capitaine, choisir un nouveau capitaine aléatoirement
        if (isDisconnect && player.equals(capitaine)) {
            List<Player> availablePlayers = new ArrayList<>(playersAlive);
            if (!availablePlayers.isEmpty()) {
                Player newCapitaine = availablePlayers.get((int)(Math.random() * availablePlayers.size()));
                setCapitaine(newCapitaine);

                // Donner le casque de capitaine
                newCapitaine.getInventory().setHelmet(plugin.getConfigManager().getRoleHelmetItemStack("capitaine"));

                Bukkit.broadcastMessage(
                    plugin.getLanguageManager().getMessage("general.new-capitaine-random")
                        .replace("{player}", newCapitaine.getName())
                );
            } else {
                setCapitaine(null);
            }
        }

        // Clear the offhand, and hotbar slot 0-8 items
        for (int i = 0; i <= 8; i++) {
            player.getInventory().setItem(i, new ItemStack(Material.AIR));
        }
        player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
        player.getInventory().setHelmet(new ItemStack(Material.CARVED_PUMPKIN));

        checkForWinCondition();
        plugin.getWebsocketManager().sendPlayerDied(player.getName());
    }

    private String getDeathMessage(Player player, String deathType, Role role) {
        String roleName = role.getFormattedName();
        String playerName = "" + ChatColor.GOLD + ChatColor.BOLD + player.getName();

        if (deathType == null || deathType.equals("default")) {
            return plugin.getLanguageManager().getMessage("death.default")
                .replace("{player}", playerName)
                .replace("{role}", roleName);
        } else if (deathType.equals("love")) {
            return plugin.getLanguageManager().getMessage("death.love")
                .replace("{player}", playerName)
                .replace("{role}", roleName);
        } else if (deathType.equals("loupGarou")) {
            return plugin.getLanguageManager().getMessage("death.werewolf")
                .replace("{player}", playerName)
                .replace("{role}", roleName);
        } else if (deathType.equals("sorciere")) {
            return plugin.getLanguageManager().getMessage("death.witch")
                .replace("{player}", playerName)
                .replace("{role}", roleName);
        } else if (deathType.equals("chasseur")) {
            return plugin.getLanguageManager().getMessage("death.hunter")
                .replace("{player}", playerName)
                .replace("{role}", roleName);
        } else if (deathType.equals("vote")) {
            return plugin.getLanguageManager().getMessage("death.vote")
                .replace("{player}", playerName)
                .replace("{role}", roleName);
        } else if (deathType.equals("disconnect")) {
            return plugin.getLanguageManager().getMessage("death.disconnect")
                .replace("{player}", playerName)
                .replace("{role}", roleName);
        } else if (deathType.equals("silent")) {
            return null;
        }
        return null;
    }

    public void removePlayerFromRole(Player player) {
        if (player.equals(cupidon)) cupidon = null;
        if (player.equals(voyante)) voyante = null;
        if (player.equals(sorciere)) sorciere = null;
        if (player.equals(chasseur) && goodGuysCount != 0) chasseur = null;
        if (player.equals(petiteFille)) petiteFille = null;
        if (player.equals(ange)) ange = null;
        if (player.equals(voleur)) voleur = null;
        loupGarous.remove(player);
        villageois.remove(player);
    }

    /**
     * Add a player to a specific role in GameManager
     */
    public void addPlayerToRole(Player player, Role role) {
        switch (role) {
            case LOUP_GAROU:
                if (!loupGarous.contains(player)) {
                    loupGarous.add(player);
                    goodGuys.remove(player);
                    if (!badGuys.contains(player)) {
                        badGuys.add(player);
                    }
                }
                break;
            case VOYANTE:
                voyante = player;
                break;
            case SORCIERE:
                sorciere = player;
                break;
            case CHASSEUR:
                chasseur = player;
                break;
            case PETITE_FILLE:
                petiteFille = player;
                break;
            case CUPIDON:
                cupidon = player;
                break;
            case ANGE:
                ange = player;
                break;
            case VOLEUR:
                voleur = player;
                break;
            case VILLAGEOIS:
                if (!villageois.contains(player)) {
                    villageois.add(player);
                }
                break;
            default:
                break;
        }
    }

    /**
     * Vérifie si l'Ange gagne (éliminé par vote au premier jour)
     * @param player Le joueur qui vient d'être éliminé par vote
     * @return true si l'Ange a gagné, false sinon
     */
    public boolean checkAngeVictory(Player player) {
        if (!inGame) return false;
        
        // Vérifier si le joueur éliminé est l'Ange au premier jour
        if (dayCount == 1 && ange != null && ange.equals(player)) {
            // L'Ange gagne !
            announceAngeVictory(player);
            return true;
        }
        
        return false;
    }

    /**
     * Transforme l'Ange en villageois s'il survit au premier jour
     * À appeler à la fin du premier jour
     */
    public void checkAngeTransformation() {
        if (!inGame || dayCount != 1 || ange == null) return;
        
        // Si l'Ange est toujours vivant après le premier jour, il devient villageois
        if (playersAlive.contains(ange)) {
            GamePlayer gp = getGamePlayer(ange);
            gp.setRole(Role.VILLAGEOIS);
            ange.sendMessage(plugin.getLanguageManager().getMessage("roles.ange.becomes-villager"));
            
            // Changer son item en main gauche pour celui de villageois
            ange.getInventory().setItemInOffHand(plugin.getConfigManager().getRoleHelmetItemStack("villageois"));
            
            // L'ange n'existe plus en tant que rôle spécial
            ange = null;
        }
    }

    /**
     * Annonce la victoire de l'Ange
     */
    private void announceAngeVictory(Player ange) {
        // Play the kill animation (lightning strike) before announcing victory
        ange.getWorld().strikeLightningEffect(ange.getLocation());

        for (Player player : playingPlayers) {
            if (player.equals(ange)) {
                player.sendTitle(plugin.getLanguageManager().getMessage("victory.ange.title"),
                                plugin.getLanguageManager().getMessage("victory.ange.subtitle"),
                                10, 70, 20);
            } else {
                player.sendTitle(plugin.getLanguageManager().getMessage("victory.defeat.title"),
                                plugin.getLanguageManager().getMessage("victory.ange.subtitle"),
                                10, 70, 20);
            }
        }

        Bukkit.broadcastMessage(plugin.getLanguageManager().getMessage("victory.ange.broadcast-header"));
        Bukkit.broadcastMessage(plugin.getLanguageManager().getMessage("victory.ange.broadcast-title"));
        Bukkit.broadcastMessage(plugin.getLanguageManager().getMessage("victory.ange.broadcast-message")
                               .replace("{player}", ange.getName()));
        Bukkit.broadcastMessage(plugin.getLanguageManager().getMessage("victory.ange.broadcast-footer"));

        plugin.getWebsocketManager().sendGameOver();
        gameReset(false);
    }

    public void checkForWinCondition() {
        if (!inGame) return;

        // Special case: Chasseur killed everyone in a tie
        if (chasseurDidLastKill) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendTitle(plugin.getLanguageManager().getMessage("victory.stalemate.title"),
                                plugin.getLanguageManager().getMessage("victory.stalemate.subtitle"), 10, 70, 20);
            }
            plugin.getWebsocketManager().sendGameOver();
            gameReset(false);
            return;
        }

        int numLG = loupGarous.size();
        int numVillage = goodGuysCount;

        // Check for lovers win condition (both lovers alive and everyone else dead)
        if (checkLoversWinCondition()) {
            return;
        }

        // Village wins: All werewolves are dead
        if (numLG == 0) {
            announceVillageVictory();
            gameReset(false);
            return;
        }

        // Werewolves win: Werewolves >= Village OR only werewolves left
        if (numLG >= numVillage || playersAlive.size() == numLG) {
            // Exception: If chasseur is still alive and hasn't been intercepted, wait for his shot
            if (chasseur != null && !interceptChasseur && playersAlive.contains(chasseur)) {
                return;
            }

            announceWerewolvesVictory();
            gameReset(false);
            return;
        }

        // Edge case: Only one player alive (shouldn't happen but handle it)
        if (playersAlive.size() == 1) {
            Player lastPlayer = playersAlive.get(0);
            GamePlayer gp = getGamePlayer(lastPlayer);
            if (gp.getRole().getTeam() == Role.Team.BAD_GUYS) {
                announceWerewolvesVictory();
            } else {
                announceVillageVictory();
            }
            gameReset(false);
        }
    }

    /**
     * Check if lovers should win (both alive and everyone else dead)
     * @return true if lovers won, false otherwise
     */
    private boolean checkLoversWinCondition() {
        if (lesAmoureux.size() != 2) {
            return false;
        }

        Player lover1 = lesAmoureux.get(0);
        Player lover2 = lesAmoureux.get(1);

        // Both lovers must be alive
        if (!playersAlive.contains(lover1) || !playersAlive.contains(lover2)) {
            return false;
        }

        // Only the two lovers should be alive
        if (playersAlive.size() == 2) {
            for (Player player : playingPlayers) {
                if (lesAmoureux.contains(player)) {
                    player.sendTitle(plugin.getLanguageManager().getMessage("victory.lovers.title"),
                                    plugin.getLanguageManager().getMessage("victory.lovers.subtitle"),
                                    10, 70, 20);
                } else {
                    player.sendTitle(plugin.getLanguageManager().getMessage("victory.defeat.title"),
                                    plugin.getLanguageManager().getMessage("victory.lovers.subtitle"),
                                    10, 70, 20);
                }
            }

            Bukkit.broadcastMessage(plugin.getLanguageManager().getMessage("victory.lovers.broadcast-header"));
            Bukkit.broadcastMessage(plugin.getLanguageManager().getMessage("victory.lovers.broadcast-title"));
            Bukkit.broadcastMessage(plugin.getLanguageManager().getMessage("victory.lovers.broadcast-players")
                                   .replace("{lover1}", lover1.getName())
                                   .replace("{lover2}", lover2.getName()));
            Bukkit.broadcastMessage(plugin.getLanguageManager().getMessage("victory.lovers.broadcast-footer"));

            gameReset(false);
            return true;
        }

        return false;
    }

    /**
     * Announce village victory with detailed statistics
     */
    private void announceVillageVictory() {
        for (Player player : goodGuys) {
            if (playingPlayers.contains(player)) {
                player.sendTitle(plugin.getLanguageManager().getMessage("victory.village.title"),
                                plugin.getLanguageManager().getMessage("victory.village.subtitle"),
                                10, 70, 20);
            }
        }
        for (Player player : badGuys) {
            if (playingPlayers.contains(player)) {
                player.sendTitle(plugin.getLanguageManager().getMessage("victory.defeat.title"),
                                plugin.getLanguageManager().getMessage("victory.village.subtitle"),
                                10, 70, 20);
            }
        }

        // Broadcast detailed victory message
        Bukkit.broadcastMessage(plugin.getLanguageManager().getMessage("victory.village.broadcast-header"));
        Bukkit.broadcastMessage(plugin.getLanguageManager().getMessage("victory.village.broadcast-title"));
        Bukkit.broadcastMessage(plugin.getLanguageManager().getMessage("victory.village.broadcast-all-dead"));
        Bukkit.broadcastMessage(plugin.getLanguageManager().getMessage("victory.village.broadcast-stats")
                               .replace("{alive}", String.valueOf(playersAlive.size()))
                               .replace("{total}", String.valueOf(playingPlayers.size())));
        Bukkit.broadcastMessage(plugin.getLanguageManager().getMessage("victory.village.broadcast-duration")
                               .replace("{nights}", String.valueOf(nightCount))
                               .replace("{days}", String.valueOf(dayCount)));
        Bukkit.broadcastMessage(plugin.getLanguageManager().getMessage("victory.village.broadcast-footer"));

        plugin.getWebsocketManager().sendGameOver();
    }

    /**
     * Announce werewolves victory with detailed statistics
     */
    private void announceWerewolvesVictory() {
        for (Player player : goodGuys) {
            if (playingPlayers.contains(player)) {
                player.sendTitle(plugin.getLanguageManager().getMessage("victory.defeat.title"),
                                plugin.getLanguageManager().getMessage("victory.werewolves.subtitle"),
                                10, 70, 20);
            }
        }
        for (Player player : badGuys) {
            if (playingPlayers.contains(player)) {
                player.sendTitle(plugin.getLanguageManager().getMessage("victory.werewolves.title"),
                                plugin.getLanguageManager().getMessage("victory.werewolves.subtitle"),
                                10, 70, 20);
            }
        }

        // Broadcast detailed victory message
        Bukkit.broadcastMessage(plugin.getLanguageManager().getMessage("victory.werewolves.broadcast-header"));
        Bukkit.broadcastMessage(plugin.getLanguageManager().getMessage("victory.werewolves.broadcast-title"));
        Bukkit.broadcastMessage(plugin.getLanguageManager().getMessage("victory.werewolves.broadcast-domination"));
        Bukkit.broadcastMessage(plugin.getLanguageManager().getMessage("victory.werewolves.broadcast-wolves")
                               .replace("{wolves}", String.valueOf(loupGarous.size())));
        Bukkit.broadcastMessage(plugin.getLanguageManager().getMessage("victory.werewolves.broadcast-villagers")
                               .replace("{villagers}", String.valueOf(goodGuysCount)));
        Bukkit.broadcastMessage(plugin.getLanguageManager().getMessage("victory.werewolves.broadcast-duration")
                               .replace("{nights}", String.valueOf(nightCount))
                               .replace("{days}", String.valueOf(dayCount)));
        Bukkit.broadcastMessage(plugin.getLanguageManager().getMessage("victory.werewolves.broadcast-footer"));
        plugin.getWebsocketManager().sendGameOver();
    }

    public void cancelStart() {
        if (startTask != null) {
            startTask.cancel();
            startTask = null;
        }
        isStarting = false;
    }

    public void startGame() {
        if (inGame || isStarting) return;
        int count = Bukkit.getOnlinePlayers().size();
        if (count < 4 || count > 12 || !plugin.getLocationManager().isProperlyConfigured()) {
            plugin.getLogger().warning("Start rejected: require 4-12 players and 12 configured spawns");
            return;
        }
        gameReset(false);
        isStarting = true;
        java.util.Set<UUID> roster = new java.util.HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) roster.add(player.getUniqueId());
        final int[] countdown = {10};
        startTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            java.util.Set<UUID> current = new java.util.HashSet<>();
            for (Player player : Bukkit.getOnlinePlayers()) current.add(player.getUniqueId());
            if (!current.equals(roster) || !plugin.getLocationManager().isProperlyConfigured()) {
                cancelStart();
                Bukkit.broadcastMessage("§e참가자가 변경되어 시작을 취소했습니다. 준비 상태를 확인해 주세요.");
                return;
            }
            if (countdown[0] > 0) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.sendActionBar(plugin.getLanguageManager().getMessage("general.game-starting")
                            .replace("{time}", String.valueOf(countdown[0])));
                }
                countdown[0]--;
            } else {
                cancelStart();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.sendActionBar(plugin.getLanguageManager().getMessage("general.game-started"));
                }
                roleDistribution();
                if (inGame) plugin.getWebsocketManager().sendGameStart();
            }
        }, 0L, 20L);
    }

    public void roleDistribution() {

        List<Player> allPlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
        int playerNumber = allPlayers.size();

        if (playerNumber < 4) {
            Bukkit.broadcastMessage(plugin.getLanguageManager().getMessage("general.not-enough-players"));
            return;
        }

        int maxPlayers = plugin.getLocationManager().getMaxPlayers();
        if (playerNumber > maxPlayers) {
            Bukkit.broadcastMessage(plugin.getLanguageManager().getMessage("general.too-many-players")
                .replace("{max}", String.valueOf(maxPlayers)));
            return;
        }

        // Vérifier si le serveur est correctement configuré (au moins 12 spawns)
        if (!plugin.getLocationManager().isProperlyConfigured()) {
            Bukkit.broadcastMessage(plugin.getLanguageManager().getMessage("general.not-properly-configured"));
            return;
        }

        inGame = true;

        // Téléporter les joueurs aux spawns
        Map<Integer, Location> spawns = plugin.getLocationManager().getSpawnLocations();
        if (!spawns.isEmpty()) {
            int spawnIndex = 1;
            for (Player player : allPlayers) {
                Location spawnLoc = spawns.get(spawnIndex);
                if (spawnLoc != null) {
                    player.teleport(spawnLoc);
                    spawnIndex++;
                    // Réinitialiser l'index si on dépasse le nombre de spawns
                    if (spawnIndex > spawns.size()) {
                        spawnIndex = 1;
                    }
                } else {
                    // Si pas de spawn à cet index, utiliser le feu de camp
                    Location campfire = plugin.getLocationManager().getCampfireLocation();
                    if (campfire != null) {
                        player.teleport(campfire);
                    }
                }
            }
        } else {
            // Si aucun spawn n'est configuré, téléporter au feu de camp
            Location campfire = plugin.getLocationManager().getCampfireLocation();
            if (campfire != null) {
                for (Player player : allPlayers) {
                    player.teleport(campfire);
                }
            }
        }

        // Distribution du premier loup-garou (obligatoire)
        Player loupGarouOne = allPlayers.get((int)(Math.random() * allPlayers.size()));
        allPlayers.remove(loupGarouOne);
        loupGarous.add(loupGarouOne);

        // Distribution du deuxième loup-garou si seuil atteint
        int twoWolvesThreshold = plugin.getConfigManager().getTwoWolvesThreshold();
        if (playerNumber >= twoWolvesThreshold && !allPlayers.isEmpty()) {
            Player loupGarouTwo = allPlayers.get((int)(Math.random() * allPlayers.size()));
            allPlayers.remove(loupGarouTwo);
            loupGarous.add(loupGarouTwo);
        }

        // Distribution du Voleur (seulement si seuil atteint, avec chance configurable et si activé)
        if (plugin.getConfigManager().isRoleEnabled("voleur") && 
            playerNumber >= plugin.getConfigManager().getRoleThreshold("voleur") && 
            Math.random() < plugin.getConfigManager().getRoleChance("voleur") && 
            !allPlayers.isEmpty()) {
            voleur = allPlayers.get((int)(Math.random() * allPlayers.size()));
            allPlayers.remove(voleur);
        }

        // Distribution de Cupidon si seuil atteint, activé et chance respectée
        if (plugin.getConfigManager().isRoleEnabled("cupidon") &&
            playerNumber >= plugin.getConfigManager().getRoleThreshold("cupidon") &&
            Math.random() < plugin.getConfigManager().getRoleChance("cupidon") &&
            !allPlayers.isEmpty()) {
            cupidon = allPlayers.get((int)(Math.random() * allPlayers.size()));
            allPlayers.remove(cupidon);
        }

        // Distribution de l'Ange si seuil atteint, activé et chance respectée
        if (plugin.getConfigManager().isRoleEnabled("ange") &&
            playerNumber >= plugin.getConfigManager().getRoleThreshold("ange") &&
            Math.random() < plugin.getConfigManager().getRoleChance("ange") &&
            !allPlayers.isEmpty()) {
            ange = allPlayers.get((int)(Math.random() * allPlayers.size()));
            allPlayers.remove(ange);
        }

        // Distribution de la Petite Fille si activée, seuil atteint et chance respectée
        if (plugin.getConfigManager().isRoleEnabled("petite-fille") &&
            playerNumber >= plugin.getConfigManager().getRoleThreshold("petite-fille") &&
            Math.random() < plugin.getConfigManager().getRoleChance("petite-fille") &&
            !allPlayers.isEmpty()) {
            petiteFille = allPlayers.get((int)(Math.random() * allPlayers.size()));
            allPlayers.remove(petiteFille);
        }

        // Distribution du Chasseur si activé, seuil atteint et chance respectée
        if (plugin.getConfigManager().isRoleEnabled("chasseur") &&
            playerNumber >= plugin.getConfigManager().getRoleThreshold("chasseur") &&
            Math.random() < plugin.getConfigManager().getRoleChance("chasseur") &&
            !allPlayers.isEmpty()) {
            chasseur = allPlayers.get((int)(Math.random() * allPlayers.size()));
            allPlayers.remove(chasseur);
        }

        // Distribution de la Sorcière si activée, seuil atteint et chance respectée
        if (plugin.getConfigManager().isRoleEnabled("sorciere") &&
            playerNumber >= plugin.getConfigManager().getRoleThreshold("sorciere") &&
            Math.random() < plugin.getConfigManager().getRoleChance("sorciere") &&
            !allPlayers.isEmpty()) {
            sorciere = allPlayers.get((int)(Math.random() * allPlayers.size()));
            allPlayers.remove(sorciere);
        }

        // Distribution de la Voyante si activée, seuil atteint et chance respectée
        if (plugin.getConfigManager().isRoleEnabled("voyante") &&
            playerNumber >= plugin.getConfigManager().getRoleThreshold("voyante") &&
            Math.random() < plugin.getConfigManager().getRoleChance("voyante") &&
            !allPlayers.isEmpty()) {
            voyante = allPlayers.get((int)(Math.random() * allPlayers.size()));
            allPlayers.remove(voyante);
        }

        // Les joueurs restants sont des villageois
        villageois.addAll(allPlayers);

        // Assignation des rôles et envoi des titres
        for (Player lg : loupGarous) {
            GamePlayer gp = getGamePlayer(lg);
            gp.setRole(Role.LOUP_GAROU);
            lg.sendTitle(plugin.getLanguageManager().getMessage("roles.loup-garou.title"),
                        plugin.getLanguageManager().getMessage("roles.loup-garou.subtitle"),
                        10, 70, 20);
            lg.getInventory().setItemInOffHand(plugin.getConfigManager().getRoleHelmetItemStack("loup-garou"));
        }

        for (Player vil : villageois) {
            GamePlayer gp = getGamePlayer(vil);
            gp.setRole(Role.VILLAGEOIS);
            vil.sendTitle(plugin.getLanguageManager().getMessage("roles.villageois.title"),
                         plugin.getLanguageManager().getMessage("roles.villageois.subtitle"),
                         10, 70, 20);
            vil.getInventory().setItemInOffHand(plugin.getConfigManager().getRoleHelmetItemStack("villageois"));
        }

        if (petiteFille != null) {
            GamePlayer gp = getGamePlayer(petiteFille);
            gp.setRole(Role.PETITE_FILLE);
            petiteFille.sendTitle(plugin.getLanguageManager().getMessage("roles.petite-fille.title"),
                                 plugin.getLanguageManager().getMessage("roles.petite-fille.subtitle"),
                                 10, 70, 20);
            petiteFille.getInventory().setItemInOffHand(plugin.getConfigManager().getRoleHelmetItemStack("petite-fille"));
            plugin.getScoreboardManager().setRoleMemory("petiteFille", petiteFille);
        }

        if (cupidon != null) {
            GamePlayer gp = getGamePlayer(cupidon);
            gp.setRole(Role.CUPIDON);
            cupidon.sendTitle(plugin.getLanguageManager().getMessage("roles.cupidon.title"),
                             plugin.getLanguageManager().getMessage("roles.cupidon.subtitle"),
                             10, 70, 20);
            cupidon.getInventory().setItemInOffHand(plugin.getConfigManager().getRoleHelmetItemStack("cupidon"));
            plugin.getScoreboardManager().setRoleMemory("cupidon", cupidon);
        }

        if (ange != null) {
            GamePlayer gp = getGamePlayer(ange);
            gp.setRole(Role.ANGE);
            ange.sendTitle(plugin.getLanguageManager().getMessage("roles.ange.title"),
                          plugin.getLanguageManager().getMessage("roles.ange.subtitle"),
                          10, 70, 20);
            ange.getInventory().setItemInOffHand(plugin.getConfigManager().getRoleHelmetItemStack("ange"));
            plugin.getScoreboardManager().setRoleMemory("ange", ange);
        }

        if (voleur != null) {
            GamePlayer gp = getGamePlayer(voleur);
            gp.setRole(Role.VOLEUR);
            voleur.sendTitle(plugin.getLanguageManager().getMessage("roles.voleur.title"),
                            plugin.getLanguageManager().getMessage("roles.voleur.subtitle"),
                            10, 70, 20);
            voleur.getInventory().setItemInOffHand(plugin.getConfigManager().getRoleHelmetItemStack("voleur"));
            plugin.getScoreboardManager().setRoleMemory("voleur", voleur);
        }

        if (chasseur != null) {
            GamePlayer gp = getGamePlayer(chasseur);
            gp.setRole(Role.CHASSEUR);
            chasseur.sendTitle(plugin.getLanguageManager().getMessage("roles.chasseur.title"),
                              plugin.getLanguageManager().getMessage("roles.chasseur.subtitle"),
                              10, 70, 20);
            chasseur.getInventory().setItemInOffHand(plugin.getConfigManager().getRoleHelmetItemStack("chasseur"));
            plugin.getScoreboardManager().setRoleMemory("chasseur", chasseur);
        }

        if (sorciere != null) {
            GamePlayer gp = getGamePlayer(sorciere);
            gp.setRole(Role.SORCIERE);
            sorciere.sendTitle(plugin.getLanguageManager().getMessage("roles.sorciere.title"),
                              plugin.getLanguageManager().getMessage("roles.sorciere.subtitle"),
                              10, 70, 20);
            sorciere.getInventory().setItemInOffHand(plugin.getConfigManager().getRoleHelmetItemStack("sorciere"));
            plugin.getScoreboardManager().setRoleMemory("sorciere", sorciere);
        }

        if (voyante != null) {
            GamePlayer gp = getGamePlayer(voyante);
            gp.setRole(Role.VOYANTE);
            voyante.sendTitle(plugin.getLanguageManager().getMessage("roles.voyante.title"),
                             plugin.getLanguageManager().getMessage("roles.voyante.subtitle"),
                             10, 70, 20);
            voyante.getInventory().setItemInOffHand(plugin.getConfigManager().getRoleHelmetItemStack("voyante"));
            plugin.getScoreboardManager().setRoleMemory("voyante", voyante);
        }

        // Initialisation des listes
        playersAlive.addAll(Bukkit.getOnlinePlayers());
        playingPlayers.addAll(Bukkit.getOnlinePlayers());
        goodGuys.addAll(Bukkit.getOnlinePlayers());

        // Calcul des équipes
        for (Player lg : loupGarous) {
            goodGuys.remove(lg);
            badGuys.add(lg);
        }

        goodGuysCount = goodGuys.size();
        badGuysCount = badGuys.size();

        // Clear chat
        for (int i = 0; i < 100; i++) {
            Bukkit.broadcastMessage(" ");
        }

        // Tous les joueurs en mode aventure
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setGameMode(GameMode.ADVENTURE);
            player.setHealth(20);
            player.setFoodLevel(20);
            player.setSaturation(20);
        }

        freezeAll = true;
        nightAction();
    }

    public void nightAction() {
        nextLGTarget = null;
        designationCount = 0;
        designatedPlayers.clear();
        voyanteSondage = false;

        // Réinitialiser tous les flags de succession du capitaine
        // pour que tout nouveau capitaine puisse faire sa succession normalement
        successorChosen = false;
        capitaineSuccession = false;
        interceptCapitaine = false;
        dyingCapitaine = null;

        nightCount++;
        queueMode = "night";

        // Muting all players during the night
        plugin.getWebsocketManager().sendMuteAll();

        // Hide all players from each other and apply blindness
        for (Player player : playersAlive) {
            for (Player other : playersAlive) {
                player.hidePlayer(plugin, other);
            }
            player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 999999, 0, false, false));
        }

        Bukkit.broadcastMessage(plugin.getLanguageManager().getMessage("phases.night")
                               .replace("{count}", String.valueOf(nightCount)));

        for (Player player : playingPlayers) {
            player.sendMessage(plugin.getLanguageManager().getMessage("phases.night-falls"));
        }

        World world = Bukkit.getWorld("world");
        if (world != null) {
            world.setTime(18000);
        }

        // Éteindre le feu de camp la nuit
        unlightCampfire();

        // Build action queue for night
        actionQueueClear();
        if (voleur != null && nightCount == 1) {
            actionQueueAdd("doVoleur");
        }
        if (cupidon != null && nightCount == 1) {
            actionQueueAdd("doCupidon");
        }
        if (voyante != null) {
            actionQueueAdd("doVoyante");
        }
        actionQueueAdd("doLoupGarou");
        if (sorciere != null) {
            actionQueueAdd("doSorciere");
        }
        actionQueueAdd("endNight");

        // Start the night sequence after a delay
        Bukkit.getScheduler().runTaskLater(plugin, this::nextStep, 60L);

        // Clear every player's slot 4 inventory
        for (Player player : playersAlive) {
            ItemStack slot4 = player.getInventory().getItem(4);
            if (slot4 != null && (
                    slot4.getType() == Material.PAPER ||
                    slot4.getType() == Material.WOODEN_HOE ||
                    slot4.getType() == Material.IRON_HOE ||
                    slot4.getType() == Material.BOOK)) {
                player.getInventory().setItem(4, new ItemStack(Material.AIR));
            }
            
            // Retirer la plume au slot 8
            ItemStack slot8 = player.getInventory().getItem(8);
            if (slot8 != null && slot8.getType() == Material.FEATHER) {
                player.getInventory().setItem(8, new ItemStack(Material.AIR));
            }
        }
    }

    public void dayAction() {
        dayCount++;
        voteWasMade = false;
        queueMode = "day";

        // Unmuting all players during the day
        plugin.getWebsocketManager().sendDemuteAll();

        // Reveal all players and remove blindness
        for (Player player : playersAlive) {
            for (Player other : playersAlive) {
                player.showPlayer(plugin, other);
            }
            player.removePotionEffect(PotionEffectType.BLINDNESS);
        }

        Bukkit.broadcastMessage(plugin.getLanguageManager().getMessage("phases.day")
                               .replace("{count}", String.valueOf(dayCount)));

        for (Player player : playingPlayers) {
            GamePlayer gp = getGamePlayer(player);
            gp.reset();
            player.sendMessage(plugin.getLanguageManager().getMessage("phases.day-breaks"));
        }

        World world = Bukkit.getWorld("world");
        if (world != null) {
            world.setTime(6000);
        }

        // Allumer le feu de camp le jour
        lightCampfire();

        // Build action queue for day
        actionQueueClear();
        actionQueueAdd("revealDeath");
        if (capitaine == null) {
            actionQueueAdd("doCapitaine");
        }
        actionQueueAdd("doVote");
        actionQueueAdd("endDay");

        // Clear every player's slot 4 inventory
        for (Player player : playersAlive) {
            ItemStack slot4 = player.getInventory().getItem(4);
            if (slot4 != null && (
                    slot4.getType() == Material.PAPER ||
                            slot4.getType() == Material.WOODEN_HOE ||
                            slot4.getType() == Material.IRON_HOE ||
                    slot4.getType() == Material.BOOK)) {
                player.getInventory().setItem(4, new ItemStack(Material.AIR));
            }
            
            // Retirer la plume au slot 8
            ItemStack slot8 = player.getInventory().getItem(8);
            if (slot8 != null && slot8.getType() == Material.FEATHER) {
                player.getInventory().setItem(8, new ItemStack(Material.AIR));
            }
        }

        // Start the day sequence after a delay
        Bukkit.getScheduler().runTaskLater(plugin, this::nextStep, 60L);
    }

    public void nextStep() {
        if (!inGame) return;

        // Finir l'action actuelle avant de passer à la suivante
        if (gameStep != null) {
            executeFinisher(gameStep);
        }

        if (isActionQueueEmpty()) {
            if ("night".equals(queueMode)) {
                dayAction();
            } else if ("day".equals(queueMode)) {
                nightAction();
            }
            return;
        }

        // Get next action
        String nextAction = actionQueue.poll();
        if (nextAction == null) return;

        // Si on passe de doLoupGarou à doSorciere, ajouter un délai supplémentaire
        if ("doLoupGarou".equals(gameStep) && "doSorciere".equals(nextAction)) {
            gameStep = nextAction;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                executeRoleAction(nextAction);
            }, 50L); // 2.5 secondes de délai supplémentaire
            return;
        }

        gameStep = nextAction;
        executeRoleAction(nextAction);
    }

    private void executeRoleAction(String action) {
        // Execute action based on type
        switch (action) {
            case "revealDeath":
                waitListKill();
                Bukkit.getScheduler().runTaskLater(plugin, this::nextStep, 40L);
                break;
            case "doCapitaine":
                doCapitaine();
                break;
            case "doVote":
                doVote();
                break;
            case "doVoleur":
                doVoleur();
                break;
            case "doVoyante":
                doVoyante();
                break;
            case "doLoupGarou":
                doLoupGarou();
                break;
            case "doSorciere":
                doSorciere();
                break;
            case "doChasseur":
                doChasseur();
                break;
            case "doCupidon":
                doCupidon();
                break;
            case "doCapitaineSuccessor":
                doCapitaineSuccessor();
                break;
            case "doCapitaineTieBreaker":
                // Le finisher sera appelé quand le capitaine choisit
                break;
            case "skip":
                Bukkit.getScheduler().runTaskLater(plugin, this::nextStep, 5L);
                break;
            case "endNight":
                for (Player player : playersAlive) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 999999, 0, false, false));
                }
                Bukkit.getScheduler().runTaskLater(plugin, this::nextStep, 100L);
                break;
            case "endDay":
                Bukkit.getScheduler().runTaskLater(plugin, this::nextStep, 100L);
                break;
            default:
                nextStep();
                break;
        }
    }

    private void executeFinisher(String action) {
        switch (action) {
            case "doVoleur":
                finishers.finishVoleur();
                break;
            case "doCupidon":
                finishers.finishCupidon();
                break;
            case "doVoyante":
                finishers.finishVoyante();
                break;
            case "doLoupGarou":
                finishers.finishLoupGarou();
                break;
            case "doSorciere":
                finishers.finishSorciere();
                break;
            case "doCapitaine":
                finishers.finishCapitaine();
                break;
            case "doVote":
                finishers.finishVote();
                break;
            case "doChasseur":
                finishers.finishChasseur();
                break;
            case "doCapitaineSuccessor":
                finishers.finishCapitaineSuccessor();
                break;
            case "doCapitaineTieBreaker":
                finishers.finishTieBreaker();
                break;
        }
    }

    // Placeholder methods for role actions
    private void doVoleur() {
        if (voleur == null || !playersAlive.contains(voleur)) {
            nextStep();
            return;
        }

        Bukkit.broadcastMessage(plugin.getLanguageManager().getMessage("actions.voleur.announce"));
        plugin.getTimerManager().defineTimer(plugin.getLanguageManager().getMessage("actions.voleur.timer"), 60);

        // Remove blindness and show all players to the Voleur
        voleur.removePotionEffect(PotionEffectType.BLINDNESS);
        for (Player player : playersAlive) {
            voleur.showPlayer(plugin, player);
        }

        voleurAction = false;
        
        // En mode clic, ne pas ouvrir le GUI, juste donner les instructions
        if (plugin.getConfigManager().isClickVoteMode()) {
            giveSkipFeather(voleur);
            
            // Donner du blé pour voler
            ItemStack stealItem = new ItemStack(Material.WHEAT);
            ItemMeta meta = stealItem.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(plugin.getLanguageManager().getMessage("gui.items.steal-role"));
                stealItem.setItemMeta(meta);
            }
            voleur.getInventory().setItem(4, stealItem);
        } else {
            // Mode GUI
            new fr.lightshoro.lgmc.gui.VoleurGUI(plugin).open(voleur);
        }
    }

    private void doCupidon() {
        if (cupidon == null || !playersAlive.contains(cupidon)) {
            nextStep();
            return;
        }

        Bukkit.broadcastMessage(plugin.getLanguageManager().getMessage("actions.cupidon.announce"));
        plugin.getTimerManager().defineTimer(plugin.getLanguageManager().getMessage("actions.cupidon.timer"), 60);

        cupidonAction = false;
        
        // Remove blindness and show players to Cupidon
        cupidon.removePotionEffect(PotionEffectType.BLINDNESS);
        for (Player player : playersAlive) {
            cupidon.showPlayer(plugin, player);
        }
        
        // En mode clic, ne pas ouvrir le GUI, juste donner les instructions
        if (plugin.getConfigManager().isClickVoteMode()) {
            // Donner le netherite scrap à Cupidon pour désigner les amoureux
            ItemStack cupidonItem = new ItemStack(Material.NETHERITE_SCRAP);
            ItemMeta cupidonMeta = cupidonItem.getItemMeta();
            if (cupidonMeta != null) {
                cupidonMeta.setDisplayName(plugin.getLanguageManager().getMessage("gui.items.designate-lovers"));
                cupidonItem.setItemMeta(cupidonMeta);
            }
            cupidon.getInventory().setItem(4, cupidonItem);
            //cupidon.sendMessage(plugin.getLanguageManager().getMessage("gui.click-mode.left-click-hint"));
            giveSkipFeather(cupidon);
        } else {
            // Mode GUI: Cupidon utilise le GUI
            new fr.lightshoro.lgmc.gui.CupidonGUI(plugin).open(cupidon);
        }
    }

    private void doVoyante() {
        if (voyante == null || !playersAlive.contains(voyante)) {
            nextStep();
            return;
        }

        for (Player player : playingPlayers) {
            player.sendMessage(plugin.getLanguageManager().getMessage("actions.voyante.announce"));
        }

        voyante.removePotionEffect(PotionEffectType.BLINDNESS);
        for (Player player : playersAlive) {
            voyante.showPlayer(plugin, player);
        }

        plugin.getTimerManager().defineTimer(plugin.getLanguageManager().getMessage("actions.voyante.timer"), 30);

        voyanteSondage = false;
        
        // En mode clic, donner l'instruction et la plume
        if (plugin.getConfigManager().isClickVoteMode()) {
            //voyante.sendMessage(plugin.getLanguageManager().getMessage("gui.click-mode.left-click-hint"));
            giveSkipFeather(voyante);
            
            // Donner un silex (boule de cristal) pour sonder
            ItemStack crystal = new ItemStack(Material.FLINT);
            ItemMeta crystalMeta = crystal.getItemMeta();
            if (crystalMeta != null) {
                crystalMeta.setDisplayName(plugin.getLanguageManager().getMessage("gui.items.crystal"));
                crystal.setItemMeta(crystalMeta);
            }
            voyante.getInventory().setItem(4, crystal);
        } else {
            // En mode GUI, ouvrir le GUI après un délai
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                new fr.lightshoro.lgmc.gui.VoyanteGUI(plugin).open(voyante);
            }, 20L);
        }
    }

    private void doLoupGarou() {
        nextLGTarget = null;

        for (Player player : playingPlayers) {
            player.sendMessage(plugin.getLanguageManager().getMessage("actions.loups-garous.announce"));
        }

        int numLG = loupGarous.size();
        plugin.getTimerManager().defineTimer(plugin.getLanguageManager().getMessage("actions.loups-garous.timer"), 30 * numLG);

        for (Player lg : loupGarous) {
            lg.removePotionEffect(PotionEffectType.BLINDNESS);
            for (Player player : playersAlive) {
                lg.showPlayer(plugin, player);
            }
            
            // Changer le skin de chaque Loup-Garou si SkinsRestorer est activé
            plugin.getSkinManager().setWerewolfSkin(lg);
        }

        if (petiteFille != null && playersAlive.contains(petiteFille)) {
            petiteFille.removePotionEffect(PotionEffectType.BLINDNESS);
            for (Player player : playersAlive) {
                petiteFille.showPlayer(plugin, player);
            }
        }

        plugin.getChatManager().setLoupGarouChatActive(true);

        // Ouvrir le GUI pour chaque loup-garou après un délai
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player lg : loupGarous) {
                // Donner une houe en fer pour voter en mode clic
                ItemStack item = new ItemStack(Material.IRON_HOE);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(plugin.getLanguageManager().getMessage("gui.items.werewolf-attack"));
                    item.setItemMeta(meta);
                }
                lg.getInventory().setItem(4, item);
                
                // En mode clic, donner aussi la plume de skip et l'instruction
                if (plugin.getConfigManager().isClickVoteMode()) {
                    //lg.sendMessage(plugin.getLanguageManager().getMessage("gui.click-mode.left-click-hint"));
                    giveSkipFeather(lg);
                }
            }
        }, 20L);
    }

    private void doSorciere() {
        if (sorciere == null || !playersAlive.contains(sorciere)) {
            nextStep();
            return;
        }

        for (Player player : playingPlayers) {
            player.sendMessage(plugin.getLanguageManager().getMessage("actions.sorciere.announce"));
        }

        sorciere.removePotionEffect(PotionEffectType.BLINDNESS);
        for (Player player : playersAlive) {
            sorciere.showPlayer(plugin, player);
        }

        plugin.getTimerManager().defineTimer(plugin.getLanguageManager().getMessage("actions.sorciere.timer"), 30);

        sorciereAction = false;
        
        // En mode clic, donner la plume de skip dès le début
        if (plugin.getConfigManager().isClickVoteMode()) {
            giveSkipFeather(sorciere);
        }
        
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            new fr.lightshoro.lgmc.gui.SorciereGUI(plugin).open(sorciere);
        }, 20L);
    }

    private void doCapitaine() {
        capitaineVoteInProg = true;

        for (Player player : playingPlayers) {
            player.sendMessage(plugin.getLanguageManager().getMessage("vote.capitaine.announce"));
        }

        plugin.getTimerManager().defineTimer(plugin.getLanguageManager().getMessage("vote.capitaine.timer"), 60);

        for (Player player : playersAlive) {
            player.getInventory().setItem(4, new ItemStack(Material.PAPER));
            ItemStack paper = player.getInventory().getItem(4);
            if (paper != null) {
                ItemMeta meta = paper.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(plugin.getLanguageManager().getMessage("gui.items.vote-paper"));
                    paper.setItemMeta(meta);
                }
            }
            
            // En mode clic, donner aussi la plume et l'instruction
            if (plugin.getConfigManager().isClickVoteMode()) {
                //player.sendMessage(plugin.getLanguageManager().getMessage("gui.click-mode.left-click-hint"));
                giveSkipFeather(player);
            }
        }
    }

    private void doVote() {
        voteWasMade = false;
        voteInProg = true;

        for (Player player : playingPlayers) {
            player.sendMessage(plugin.getLanguageManager().getMessage("vote.day.announce"));
        }

        plugin.getTimerManager().defineTimer(plugin.getLanguageManager().getMessage("vote.day.timer"), 300);
        
        // Initialiser l'affichage des votes au-dessus des têtes
        plugin.getVoteDisplayManager().updateAllVoteDisplays();

        for (Player player : playersAlive) {
            player.getInventory().setItem(4, new ItemStack(Material.PAPER));
            ItemStack paper = player.getInventory().getItem(4);
            if (paper != null) {
                ItemMeta meta = paper.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(plugin.getLanguageManager().getMessage("gui.items.vote-day"));
                    paper.setItemMeta(meta);
                }
            }
            
            // En mode clic, donner aussi la plume et l'instruction
            if (plugin.getConfigManager().isClickVoteMode()) {
                //player.sendMessage(plugin.getLanguageManager().getMessage("gui.click-mode.left-click-hint"));
                giveSkipFeather(player);
            }
        }
    }

    private void doChasseur() {
        interceptChasseur = true;

        for (Player player : playingPlayers) {
            player.sendMessage(plugin.getLanguageManager().getMessage("actions.chasseur.announce")
                             .replace("{player}", chasseur.getName()));
        }

        plugin.getTimerManager().defineTimer(plugin.getLanguageManager().getMessage("actions.chasseur.timer"), 30);

        chasseur.sendMessage(plugin.getLanguageManager().getMessage("actions.chasseur.instruction"));
        chasseur.getInventory().setItem(4, new ItemStack(Material.WOODEN_HOE));
        chasseurOldPos = chasseur.getLocation();
        chasseurTarget = null;

        unlightCampfire();

        // Teleport chasseur to chasseur-tp location
        Location chasseurTpLoc = plugin.getLocationManager().getChasseurTpLocation();
        if (chasseurTpLoc != null) {
            chasseur.teleport(chasseurTpLoc);
        }
    }

    private void doCapitaineSuccessor() {
        if (capitaine == null) {
            nextStep();
            return;
        }

        // Sauvegarder le capitaine mourant AVANT de donner le livre
        dyingCapitaine = capitaine;

        capitaineSuccession = true;
        successorChosen = false; // Réinitialiser le flag
        plugin.getTimerManager().defineTimer(plugin.getLanguageManager().getMessage("succession.timer"), 30);

        // Donner un livre "Testament" au capitaine pour qu'il ouvre le GUI lui-même
        ItemStack testament = new ItemStack(Material.WRITTEN_BOOK);
        ItemMeta meta = testament.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.getLanguageManager().getMessage("gui.items.testament"));
            testament.setItemMeta(meta);
        }
        capitaine.getInventory().setItem(4, testament);

        // Informer le capitaine
        capitaine.sendMessage(plugin.getLanguageManager().getMessage("succession.book-received"));
    }

    public GamePlayer getGamePlayer(Player player) {
        return gamePlayers.computeIfAbsent(player.getUniqueId(), k -> new GamePlayer(player));
    }

    public GamePlayer createGamePlayer(Player player, Role role) {
        GamePlayer gamePlayer = new GamePlayer(player);
        gamePlayer.setRole(role);
        gamePlayers.put(player.getUniqueId(), gamePlayer);
        return gamePlayer;
    }

    /**
     * Allume le feu de camp (pendant la journée)
     */
    public void lightCampfire() {
        Location campfire = plugin.getLocationManager().getCampfireLocation();
        if (campfire != null) {
            Block block = campfire.getBlock();
            if (block.getType() == Material.CAMPFIRE || block.getType() == Material.SOUL_CAMPFIRE) {
                if (block.getBlockData() instanceof Lightable) {
                    Lightable lightable = (Lightable) block.getBlockData();
                    lightable.setLit(true);
                    block.setBlockData(lightable);
                }
            }
        }
    }

    /**
     * Éteint le feu de camp (pendant la nuit)
     */
    public void unlightCampfire() {
        Location campfire = plugin.getLocationManager().getCampfireLocation();
        if (campfire != null) {
            Block block = campfire.getBlock();
            if (block.getType() == Material.CAMPFIRE || block.getType() == Material.SOUL_CAMPFIRE) {
                if (block.getBlockData() instanceof Lightable) {
                    Lightable lightable = (Lightable) block.getBlockData();
                    lightable.setLit(false);
                    block.setBlockData(lightable);
                }
            }
        }
    }

    /**
     * Donne une plume "skip" au joueur au slot 8 (mode clic)
     */
    private void giveSkipFeather(Player player) {
        ItemStack feather = new ItemStack(Material.FEATHER);
        ItemMeta meta = feather.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.getLanguageManager().getMessage("gui.items.feather-skip"));
            feather.setItemMeta(meta);
        }
        player.getInventory().setItem(8, feather);
    }

    // Getters and setters
    public boolean isInGame() { return inGame; }
    public void setInGame(boolean inGame) { this.inGame = inGame; }
    public boolean isFreezeAll() { return freezeAll; }
    public void setFreezeAll(boolean freezeAll) { this.freezeAll = freezeAll; }
    public String getGameStep() { return gameStep; }
    public void setGameStep(String gameStep) { this.gameStep = gameStep; }
    public Player getCapitaine() { return capitaine; }
    public void setCapitaine(Player capitaine) { 
        this.capitaine = capitaine; 
        if (capitaine != null) {
            plugin.getScoreboardManager().setRoleMemory("capitaine", capitaine);
        }
    }
    public void setDyingCapitaine(Player player) { this.dyingCapitaine = player; }
    public Player getDyingCapitaine() { return dyingCapitaine; }
    public Player getChasseur() { return chasseur; }
    public Player getVoyante() { return voyante; }
    public Player getSorciere() { return sorciere; }
    public List<Player> getLoupGarous() { return loupGarous; }
    public List<Player> getPlayersAlive() { return playersAlive; }
    public boolean isCapitaineVoteInProg() { return capitaineVoteInProg; }
    public void setCapitaineVoteInProg(boolean val) { this.capitaineVoteInProg = val; }
    public boolean isVoteInProg() { return voteInProg; }
    public void setVoteInProg(boolean val) { this.voteInProg = val; }
    public boolean isCapitaineSuccession() { return capitaineSuccession; }
    public void setCapitaineSuccession(boolean val) { this.capitaineSuccession = val; }
    public Map<Player, Integer> getVoteCount() { return voteCount; }
    public Map<Player, Integer> getVoteCapitaine() { return voteCapitaine; }
    public Lgmc getPlugin() { return plugin; }

    // Méthodes pour les GUIs
    public void setVoyanteSondage(boolean val) { this.voyanteSondage = val; }
    public boolean isVoyanteSondage() { return voyanteSondage; }

    public void addDesignatedPlayer(Player player) {
        if (!designatedPlayers.containsKey(player)) {
            designatedPlayers.put(player, 0);
        }
    }

    public void incrementDesignationCount() {
        this.designationCount++;
    }

    public int getDesignationCount() {
        return designationCount;
    }

    public void incrementPlayerDesignation(Player player) {
        designatedPlayers.put(player, designatedPlayers.getOrDefault(player, 0) + 1);
    }

    public void designatePlayerAsWolf(Player target, Player wolf) {
        // Récupérer la désignation précédente du loup
        GamePlayer gp = getGamePlayer(wolf);
        Player previousDesignation = gp.getDesignated();
        
        // Si le loup a déjà désigné quelqu'un, décrémenter la désignation précédente
        if (previousDesignation != null) {
            int currentDesignations = designatedPlayers.getOrDefault(previousDesignation, 0);
            if (currentDesignations > 0) {
                designatedPlayers.put(previousDesignation, currentDesignations - 1);
                // Si la désignation tombe à 0, on peut la retirer de la map
                if (designatedPlayers.get(previousDesignation) == 0) {
                    designatedPlayers.remove(previousDesignation);
                }
            }
            // Décrémenter le compteur de désignation (car on remplace, pas on ajoute)
            if (designationCount > 0) {
                designationCount--;
            }
        }
        
        // Ajouter la nouvelle désignation
        addDesignatedPlayer(target);
        incrementDesignationCount();
        incrementPlayerDesignation(target);
        
        // Enregistrer la nouvelle désignation
        gp.setDesignated(target);
        gp.setDidDesignation(true);
    }

    public Player getNextLGTarget() {
        return nextLGTarget;
    }

    public void setNextLGTarget(Player player) {
        this.nextLGTarget = player;
    }

    public boolean isPotionVie() {
        return potionVie;
    }

    public void setPotionVie(boolean val) {
        this.potionVie = val;
    }

    public boolean isPotionMort() {
        return potionMort;
    }

    public void setPotionMort(boolean val) {
        this.potionMort = val;
    }

    public boolean isSorciereAction() {
        return sorciereAction;
    }

    public void setSorciereAction(boolean val) {
        this.sorciereAction = val;
    }

    public void incrementCapitaineVote(Player player) {
        voteCapitaine.put(player, voteCapitaine.getOrDefault(player, 0) + 1);
    }

    public void incrementCapitaineVote(Player target, Player voter) {
        // Récupérer le vote précédent du voteur
        GamePlayer gp = getGamePlayer(voter);
        Player previousVote = gp.getVotedCapitaine();
        
        // Si le joueur a déjà voté, décrémenter le vote précédent
        if (previousVote != null) {
            int currentVotes = voteCapitaine.getOrDefault(previousVote, 0);
            if (currentVotes > 0) {
                voteCapitaine.put(previousVote, currentVotes - 1);
            }
        }
        
        // Incrémenter le nouveau vote
        voteCapitaine.put(target, voteCapitaine.getOrDefault(target, 0) + 1);
        
        // Enregistrer le nouveau vote
        gp.setVotedCapitaine(target);
        gp.setDidVoteForCapitaine(true);
    }

    public void incrementVoteCount(Player target, Player voter) {
        // Récupérer le vote précédent du voteur
        GamePlayer gp = getGamePlayer(voter);
        Player previousVote = gp.getVotedPlayer();
        
        // Calculer la valeur du vote (2 si capitaine, 1 sinon)
        int voteValue = (voter.equals(capitaine)) ? 2 : 1;
        
        // Si le joueur a déjà voté, décrémenter le vote précédent
        if (previousVote != null) {
            int previousVoteValue = (voter.equals(capitaine)) ? 2 : 1;
            int currentVotes = voteCount.getOrDefault(previousVote, 0);
            if (currentVotes >= previousVoteValue) {
                voteCount.put(previousVote, currentVotes - previousVoteValue);
            }
        }
        
        // Incrémenter le nouveau vote
        voteCount.put(target, voteCount.getOrDefault(target, 0) + voteValue);
        
        // Enregistrer le nouveau vote
        gp.setVotedPlayer(target);
        gp.setDidVote(true);
    }

    /**
     * Nettoie tous les items pertinents de l'inventaire d'un joueur
     * (papier, houes, livre, plume, têtes de joueurs, silex, netherite scrap, etc.)
     */
    public void clearRelevantItems(Player player) {
        if (player == null) return;
        
        // Nettoyer les slots 0-8 (hotbar) et slot 8 spécifiquement
        for (int i = 0; i <= 8; i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null) {
                Material type = item.getType();
                // Vérifier si c'est un item pertinent
                if (type == Material.PAPER || 
                    type == Material.WOODEN_HOE || 
                    type == Material.IRON_HOE || 
                    type == Material.BOOK ||
                    type == Material.WRITTEN_BOOK ||
                    type == Material.FEATHER ||
                    type == Material.PLAYER_HEAD ||
                    type == Material.GUNPOWDER ||
                    type == Material.WHEAT ||
                    type == Material.FLINT ||
                    type == Material.NETHERITE_SCRAP) {
                    player.getInventory().setItem(i, new ItemStack(Material.AIR));
                }
            }
        }
    }

    public boolean isCupidonAction() {
        return cupidonAction;
    }

    public void setCupidonAction(boolean val) {
        this.cupidonAction = val;
    }

    public void addLovers(Player lover1, Player lover2) {
        lesAmoureux.clear();
        lesAmoureux.add(lover1);
        lesAmoureux.add(lover2);
    }

    // Getters supplémentaires pour RoleFinishers
    public RoleFinishers getFinishers() { return finishers; }
    public Player getPetiteFille() { return petiteFille; }
    public Player getAnge() { return ange; }
    public List<Player> getPlayingPlayers() { return playingPlayers; }
    public Map<Player, Integer> getDesignatedPlayers() { return designatedPlayers; }
    public Player getChasseurTarget() { return chasseurTarget; }
    public void setChasseurTarget(Player target) { this.chasseurTarget = target; }
    public Location getChasseurOldPos() { return chasseurOldPos; }
    public void setChasseurOldPos(Location loc) { this.chasseurOldPos = loc; }
    public boolean isSuccessorChosen() { return successorChosen; }
    public void setSuccessorChosen(boolean successorChosen) { this.successorChosen = successorChosen; }
    public void setInterceptChasseur(boolean val) { this.interceptChasseur = val; }
    public void decrementBadGuysCount() { this.badGuysCount--; }
    public void setVoteWasMade(boolean val) { this.voteWasMade = val; }
    public boolean isCapitaineTieBreakerInProg() { return capitaineTieBreakerInProg; }
    public void setCapitaineTieBreakerInProg(boolean val) { this.capitaineTieBreakerInProg = val; }
    public void setMostVoted(Player player) { this.mostVoted = player; }
    public void setDoubleMostVoted(Player player) { this.doubleMostVoted = player; }
    public Player getMostVoted() { return mostVoted; }
    public Player getDoubleMostVoted() { return doubleMostVoted; }
    public boolean getIsStarting() { return isStarting; }
    public List<Player> getVillageois() { return villageois; }
    public int getNightCount() { return nightCount; }
    public int getDayCount() { return dayCount; }

    public boolean isNight() {
        return "night".equals(queueMode);
    }

    public List<Player> getLesAmoureux() {
        return lesAmoureux;
    }

    public boolean isVoleurAction() {
        return voleurAction;
    }

    public void setVoleurAction(boolean val) {
        this.voleurAction = val;
    }

    public Player getVoleur() {
        return voleur;
    }

    public Player getCupidon() {
        return cupidon;
    }
}
