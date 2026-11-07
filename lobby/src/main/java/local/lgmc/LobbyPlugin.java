package local.lgmc;

import fr.lightshoro.lgmc.Lgmc;
import fr.lightshoro.lgmc.managers.GameManager;
import java.util.*;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.event.server.RemoteServerCommandEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

/** Trial lobby. All online players participate, matching LGMC 5.1.3. */
public final class LobbyPlugin extends JavaPlugin implements Listener {
    private final Set<UUID> ready = new HashSet<>();
    private final Map<UUID, Long> deadlines = new HashMap<>();
    private final Map<UUID, Integer> notices = new HashMap<>();
    private GameManager game;
    private PreviewMenu preview;
    private boolean wasPlaying;
    private boolean dispatchingStart;
    private int timeout;
    private YamlConfiguration messages;

    private static final class ReadyMenu implements InventoryHolder {
        private Inventory inventory;
        public Inventory getInventory() { return inventory; }
    }

    @Override public void onEnable() {
        saveDefaultConfig();
        if (!new File(getDataFolder(), "messages.yml").exists()) saveResource("messages.yml", false);
        messages = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "messages.yml"));
        try (InputStreamReader reader = new InputStreamReader(
                Objects.requireNonNull(getResource("messages.yml")), StandardCharsets.UTF_8)) {
            messages.setDefaults(YamlConfiguration.loadConfiguration(reader));
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Cannot load bundled messages", e);
        }
        timeout = Math.max(30, getConfig().getInt("ready-timeout-seconds", 120));
        Lgmc lgmc = (Lgmc) getServer().getPluginManager().getPlugin("lgmc");
        if (lgmc == null || !lgmc.getDescription().getVersion().equals("5.1.3-bedrock-trial.2")) {
            getLogger().severe("Unsupported LGMC build; lobby disabled to avoid unsafe starts");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        game = lgmc.getGameManager();
        preview = new PreviewMenu(this, lgmc, (key, values) -> msg(key, values));
        getServer().getPluginManager().registerEvents(preview, this);
        Objects.requireNonNull(getCommand("lgpreview")).setExecutor(preview);
        wasPlaying = game.isInGame();
        getServer().getPluginManager().registerEvents(this, this);
        for (Player p : getServer().getOnlinePlayers()) resetPlayer(p);
        getServer().getScheduler().runTaskTimer(this, this::tick, 20L, 20L);
    }

    @Override public void onDisable() {
        if (preview != null) preview.closeAll();
        if (game != null && game.getIsStarting()) game.cancelStart();
    }

    private String msg(String key, Object... replacements) {
        String text = ChatColor.translateAlternateColorCodes('&', messages.getString(key, key));
        for (int i = 0; i < replacements.length; i += 2) {
            text = text.replace("{" + replacements[i] + "}", String.valueOf(replacements[i + 1]));
        }
        return text;
    }

    private Location lobby() {
        World world = getServer().getWorld(getConfig().getString("lobby.world", "world"));
        if (world == null) throw new IllegalStateException("Lobby world missing");
        return new Location(world, getConfig().getDouble("lobby.x", -4.5),
                getConfig().getDouble("lobby.y", -20), getConfig().getDouble("lobby.z", -4.5));
    }

    private void resetPlayer(Player p) {
        ready.remove(p.getUniqueId());
        deadlines.put(p.getUniqueId(), System.nanoTime() + timeout * 1_000_000_000L);
        notices.remove(p.getUniqueId());
        if (!game.isInGame()) {
            p.teleport(lobby());
            p.setGameMode(GameMode.ADVENTURE);
            p.setHealth(20); p.setFoodLevel(20);
            p.sendMessage(msg("join-instructions", "seconds", timeout));
        }
    }

    private void cancelCountdown(String reason) {
        if (game.getIsStarting()) {
            game.cancelStart();
            Bukkit.broadcastMessage(msg("start-cancelled", "reason", reason));
        }
    }

    @EventHandler public void join(PlayerJoinEvent e) {
        cancelCountdown(msg("reason-join"));
        getServer().getScheduler().runTask(this, () -> {
            if (e.getPlayer().isOnline()) resetPlayer(e.getPlayer());
        });
    }

    @EventHandler public void quit(PlayerQuitEvent e) {
        cancelCountdown(msg("reason-quit"));
        UUID id = e.getPlayer().getUniqueId();
        ready.remove(id); deadlines.remove(id); notices.remove(id);
    }

    private void setReady(Player p, boolean value) {
        if (game.isInGame()) { p.sendMessage(msg("ready-blocked")); return; }
        if (ready.contains(p.getUniqueId()) == value) return;
        cancelCountdown(msg("reason-ready-change"));
        if (value) ready.add(p.getUniqueId());
        else {
            ready.remove(p.getUniqueId());
            // Do not renew the deadline by repeatedly toggling ready.
            p.sendMessage(msg("ready-cancel-notice"));
        }
        Bukkit.broadcastMessage(msg(value ? "ready-announcement" : "cancel-announcement",
                "player", p.getName(), "ready", ready.size(), "online", Bukkit.getOnlinePlayers().size()));
        getLogger().info("ready player=" + p.getUniqueId() + " value=" + value);
    }

    private boolean allReady() {
        Collection<? extends Player> online = Bukkit.getOnlinePlayers();
        return online.size() >= 4 && online.size() <= 12
                && online.stream().allMatch(p -> ready.contains(p.getUniqueId()));
    }

    private void tick() {
        boolean playing = game.isInGame();
        if (playing || game.getIsStarting()) preview.closeAll();
        if (wasPlaying && !playing) {
            ready.clear(); deadlines.clear(); notices.clear();
            for (Player p : Bukkit.getOnlinePlayers()) resetPlayer(p);
            Bukkit.broadcastMessage(msg("game-ended"));
        }
        wasPlaying = playing;
        if (playing) return;
        for (Player p : new ArrayList<>(Bukkit.getOnlinePlayers())) {
            UUID id = p.getUniqueId();
            if (ready.contains(id)) continue;
            long remaining = Math.max(0, (deadlines.getOrDefault(id, System.nanoTime()) - System.nanoTime() + 999_999_999L) / 1_000_000_000L);
            if (remaining == 0) {
                cancelCountdown(msg("reason-timeout"));
                getLogger().info("ready-timeout player=" + id);
                p.kickPlayer(msg("timeout-kick"));
            } else if (remaining <= 30) {
                int bucket = remaining <= 10 ? 10 : 30;
                if (notices.getOrDefault(id, 999) > bucket) {
                    notices.put(id, bucket);
                    p.sendMessage(msg("timeout-warning", "seconds", remaining));
                }
            }
        }
        if (game.getIsStarting()) {
            if (!allReady()) cancelCountdown(msg("reason-conditions"));
            return;
        }
        if (allReady()) {
            Lgmc lgmc = (Lgmc) Bukkit.getPluginManager().getPlugin("lgmc");
            if (!lgmc.getLocationManager().isProperlyConfigured()) return;
            preview.closeAll();
            dispatchingStart = true;
            try { Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lg start"); }
            finally { dispatchingStart = false; }
            if (!game.getIsStarting() && !game.isInGame()) {
                ready.clear();
                Bukkit.broadcastMessage(msg("start-failed"));
            }
        }
    }

    private boolean startCommand(String raw) {
        String[] parts = raw.replaceFirst("^/", "").trim().toLowerCase(Locale.ROOT).split("\\s+");
        String root = parts[0].replaceFirst("^lgmc:", "");
        return Set.of("lg", "loupgarou", "werewolf").contains(root)
                && parts.length > 1 && parts[1].equals("start");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void playerCommand(PlayerCommandPreprocessEvent e) {
        if (startCommand(e.getMessage())) {
            e.setCancelled(true); e.getPlayer().sendMessage(msg("automatic-start"));
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void consoleCommand(ServerCommandEvent e) {
        if (startCommand(e.getCommand()) && !dispatchingStart) {
            e.setCancelled(true); e.getSender().sendMessage(msg("automatic-start-console"));
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void remoteCommand(RemoteServerCommandEvent e) { consoleCommand(e); }

    @Override public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("online=" + Bukkit.getOnlinePlayers().size() + " ready=" + ready.size()
                    + " starting=" + game.getIsStarting() + " playing=" + game.isInGame()
                    + " timeout=" + timeout);
            return true;
        }
        if (cmd.getName().equals("ready")) setReady(p, !ready.contains(p.getUniqueId()));
        else openMenu(p);
        return true;
    }

    private void openMenu(Player p) {
        if (game.isInGame()) { p.sendMessage(msg("game-in-progress")); return; }
        ReadyMenu holder = new ReadyMenu();
        holder.inventory = Bukkit.createInventory(holder, 9, msg("menu-title"));
        holder.inventory.setItem(3, item(Material.LIME_WOOL, msg("menu-ready")));
        holder.inventory.setItem(5, item(Material.RED_WOOL, msg("menu-cancel")));
        p.openInventory(holder.inventory);
    }
    private ItemStack item(Material material, String title) {
        ItemStack stack = new ItemStack(material); ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(title); stack.setItemMeta(meta); return stack;
    }
    @EventHandler public void click(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof ReadyMenu)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getRawSlot() == 3 || e.getRawSlot() == 5) {
            setReady(p, e.getRawSlot() == 3); p.closeInventory();
        }
    }
    @EventHandler public void drag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof ReadyMenu) e.setCancelled(true);
    }
    @EventHandler public void damage(EntityDamageEvent e) {
        if (!game.isInGame() && e.getEntity() instanceof Player p) {
            e.setCancelled(true);
            if (e.getCause() == EntityDamageEvent.DamageCause.VOID) p.teleport(lobby());
        }
    }
    @EventHandler public void respawn(PlayerRespawnEvent e) {
        if (!game.isInGame()) e.setRespawnLocation(lobby());
    }
}
