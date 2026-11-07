package fr.lightshoro.lgmc.managers;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import fr.lightshoro.lgmc.Lgmc;
import net.kyori.adventure.text.Component;

/**
 * Gestionnaire centralisé pour les packs de ressources
 * Gère le téléchargement et l'application des packs en fonction de la version du client
 */
public class ResourcePackManager implements Listener {
    private final Lgmc plugin;
    private final Map<Integer, String> protocolVersionMap;
    private static final String RESOURCE_PACK_URL_TEMPLATE = "https://cdn.eradium.fr/lgrsp/public/%s.zip";
    private static final String RESOURCE_PACK_HASH_URL_TEMPLATE = "https://cdn.eradium.fr/lgrsp/public/%s.txt";

    public ResourcePackManager(Lgmc plugin) {
        this.plugin = plugin;
        this.protocolVersionMap = initializeProtocolVersionMap();
    }

    /**
     * Initialise la correspondance entre version de protocole et version Minecraft
     */
    private Map<Integer, String> initializeProtocolVersionMap() {
        Map<Integer, String> map = new HashMap<>();
        map.put(767, "1.21");
        map.put(768, "1.21.1");
        map.put(769, "1.21.2");
        map.put(770, "1.21.3");
        map.put(771, "1.21.4");
        map.put(772, "1.21.5");
        map.put(773, "1.21.6");
        map.put(774, "1.21.7");
        map.put(775, "1.21.8");
        map.put(776, "1.21.9");
        map.put(777, "1.21.10");
        return map;
    }

    /**
     * Configure le pack de ressources pour un joueur
     * En cas d'erreur, utilise le pack par défaut 1.21
     */
    public void applyResourcePack(Player player) {
        if (isFloodgatePlayer(player)) {
            plugin.getLogger().info("Skipping Java resource pack for authenticated Bedrock player " + player.getName());
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String minecraftVersion = getPlayerMinecraftVersion(player);
                String packUrl = String.format(RESOURCE_PACK_URL_TEMPLATE, minecraftVersion);
                String hashUrl = String.format(RESOURCE_PACK_HASH_URL_TEMPLATE, minecraftVersion);

                String sha1Hash = downloadHash(hashUrl);
                if (sha1Hash == null || sha1Hash.isEmpty()) {
                    plugin.getLogger().warning("Impossible de récupérer le hash SHA1 pour la version " + minecraftVersion + ", utilisation du pack par défaut 1.21");
                    // Fallback sur 1.21
                    minecraftVersion = "1.21";
                    packUrl = String.format(RESOURCE_PACK_URL_TEMPLATE, minecraftVersion);
                    hashUrl = String.format(RESOURCE_PACK_HASH_URL_TEMPLATE, minecraftVersion);
                    sha1Hash = downloadHash(hashUrl);

                    if (sha1Hash == null || sha1Hash.isEmpty()) {
                        plugin.getLogger().warning("Impossible de récupérer le hash SHA1 pour le pack par défaut 1.21 pour le joueur " + player.getName());
                        return;
                    }
                }

                // Créer des variables final pour la lambda
                final String finalPackUrl = packUrl;
                final String finalSha1Hash = sha1Hash;

                // Appliquer le pack sur le thread principal
                Bukkit.getScheduler().runTask(plugin, () -> player.setResourcePack(finalPackUrl, finalSha1Hash));
                plugin.getLogger().info("Pack de ressources appliqué au joueur " + player.getName() + " (version " + minecraftVersion + ")");
            } catch (Exception e) {
                plugin.getLogger().warning("Erreur lors de la configuration du pack de ressources pour le joueur " + player.getName() + ": " + e.getMessage());
            }
        });
    }

    /**
     * Récupère la version Minecraft du joueur en fonction de sa version de protocole
     * En cas d'erreur, retourne par défaut la version 1.21
     */
    private String getPlayerMinecraftVersion(Player player) {
        try {
            int protocolVersion = getProtocolVersion(player);
            return protocolVersionMap.getOrDefault(protocolVersion, "1.21");
        } catch (Exception e) {
            plugin.getLogger().warning("Impossible de déterminer la version du joueur " + player.getName() + ", utilisation du pack par défaut 1.21: " + e.getMessage());
            return "1.21";
        }
    }

    /**
     * Récupère la version de protocole du joueur via réflexion
     */
    private int getProtocolVersion(Player player) throws Exception {
        Class<?> craftPlayerClass = getCraftPlayerClass();
        Object craftPlayer = craftPlayerClass.cast(player);
        Object entityPlayer = craftPlayerClass.getMethod("getHandle").invoke(craftPlayer);
        Object playerConnection = entityPlayer.getClass().getField("c").get(entityPlayer);

        // Essayer différents noms de champ pour la version du protocole
        Class<?> playerConnectionClass = playerConnection.getClass();

        // Essayer les différents noms de champ possibles
        String[] fieldNames = {"b", "protocolVersion", "version", "a"};
        for (String fieldName : fieldNames) {
            try {
                int protocolVersion = (int) playerConnectionClass.getField(fieldName).get(playerConnection);
                plugin.getLogger().info("Version de protocole trouvée via le champ '" + fieldName + "': " + protocolVersion);
                return protocolVersion;
            } catch (NoSuchFieldException ignored) {
                // Continuer au champ suivant
            }
        }

        // Si aucun champ n'a été trouvé, essayer via getDeclaredField (accès aux champs privés)
        for (String fieldName : fieldNames) {
            try {
                java.lang.reflect.Field field = playerConnectionClass.getDeclaredField(fieldName);
                field.setAccessible(true);
                int protocolVersion = (int) field.get(playerConnection);
                plugin.getLogger().info("Version de protocole trouvée via getDeclaredField '" + fieldName + "': " + protocolVersion);
                return protocolVersion;
            } catch (NoSuchFieldException ignored) {
                // Continuer au champ suivant
            }
        }

        throw new Exception("Impossible de trouver le champ de version de protocole sur PlayerConnection");
    }

    /**
     * Récupère la classe CraftPlayer avec gestion de la version du serveur
     */
    private Class<?> getCraftPlayerClass() throws ClassNotFoundException {
        String packageName = plugin.getServer().getClass().getPackage().getName();
        String[] parts = packageName.split("\\.");
        String cbVersion = parts.length >= 4 ? parts[3] : "";
        String className = "org.bukkit.craftbukkit" + (cbVersion.isEmpty() ? "" : "." + cbVersion) + ".entity.CraftPlayer";
        return Class.forName(className);
    }

    /**
     * Télécharge le hash SHA1 depuis l'URL fournie
     */
    private String downloadHash(String hashUrl) {
        try {
            URI hashFileUri = new URI(hashUrl);
            Path tempFile = Files.createTempFile("hash", ".txt");
            Files.copy(hashFileUri.toURL().openStream(), tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            String hash = Files.readString(tempFile).trim();
            Files.deleteIfExists(tempFile);
            return hash;
        } catch (Exception e) {
            plugin.getLogger().warning("Impossible de télécharger le hash depuis " + hashUrl + ": " + e.getMessage());
            return null;
        }
    }

    @EventHandler
    public void onPlayerResourcePackStatus(PlayerResourcePackStatusEvent event) {
        Player player = event.getPlayer();
        if (isFloodgatePlayer(player)) {
            return;
        }
        PlayerResourcePackStatusEvent.Status status = event.getStatus();

        switch (status) {
            case SUCCESSFULLY_LOADED:
                plugin.getLogger().info("Le pack de ressources a été appliqué avec succès pour le joueur " + player.getName());
                break;
            case FAILED_DOWNLOAD:
                plugin.getLogger().warning("Échec du téléchargement du pack de ressources pour le joueur " + player.getName());
                kickPlayer(player, plugin.getLanguageManager().getMessage("errors.ressource-pack-failed"));
                break;
            case ACCEPTED:
                plugin.getLogger().info("Le joueur " + player.getName() + " a accepté le pack de ressources");
                break;
            case DISCARDED:
            case DECLINED:
            case FAILED_RELOAD:
                plugin.getLogger().info("Le joueur " + player.getName() + " a décliné le pack de ressources - Expulsion en cours...");
                kickPlayer(player, plugin.getLanguageManager().getMessage("errors.ressource-pack-needed"));
                break;
            case DOWNLOADED:
                plugin.getLogger().info("Le pack de ressources a été téléchargé pour le joueur " + player.getName());
                break;
            case INVALID_URL:
                plugin.getLogger().warning("URL du pack de ressources invalide pour le joueur " + player.getName());
                kickPlayer(player, plugin.getLanguageManager().getMessage("errors.ressource-pack-failed"));
                break;
            
        }
    }

    /**
     * Expulse un joueur du serveur avec un message personnalisé
     */
    private boolean isFloodgatePlayer(Player player) {
        org.bukkit.plugin.Plugin floodgate = Bukkit.getPluginManager().getPlugin("floodgate");
        if (floodgate == null || !floodgate.isEnabled()) {
            return false;
        }
        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi", true,
                    floodgate.getClass().getClassLoader());
            Object api = apiClass.getMethod("getInstance").invoke(null);
            return Boolean.TRUE.equals(apiClass.getMethod("isFloodgatePlayer", java.util.UUID.class)
                    .invoke(api, player.getUniqueId()));
        } catch (ReflectiveOperationException | LinkageError exception) {
            plugin.getLogger().warning("Cannot verify Floodgate identity; keeping Java pack policy: " + exception);
            return false;
        }
    }

    private void kickPlayer(Player player, String reason) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            player.kick(Component.text(reason));
            plugin.getLogger().warning("Le joueur " + player.getName() + " a été expulsé : " + reason);
        });
    }
}
