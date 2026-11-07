package local.lgmc;

import fr.lightshoro.lgmc.Lgmc;
import java.util.*;
import java.util.function.BiFunction;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;

/** Read-only samples. Never invokes LGMC GUI callbacks or changes player equipment. */
final class PreviewMenu implements Listener, CommandExecutor {
    private final Lgmc lgmc;
    private final org.bukkit.plugin.Plugin owner;
    private final BiFunction<String,Object[],String> messages;
    private static final String[] ROLES = {"loup-garou", "villageois", "petite-fille", "voyante",
        "sorciere", "cupidon", "chasseur", "ange", "voleur"};
    private static final class View implements InventoryHolder {
        Inventory inventory;
        final Map<Integer,String> actions = new HashMap<>();
        public Inventory getInventory() { return inventory; }
    }
    PreviewMenu(org.bukkit.plugin.Plugin owner, Lgmc lgmc, BiFunction<String,Object[],String> messages) {
        this.owner=owner; this.lgmc=lgmc; this.messages=messages;
    }
    private String msg(String key) { return messages.apply(key, new Object[0]); }
    private boolean idle() { return !lgmc.getGameManager().isInGame() && !lgmc.getGameManager().getIsStarting(); }
    private String sample(String key) {
        return lgmc.getLanguageManager().getMessage(key)
            .replace("{player}", "견본참가자").replace("{time}", "10").replace("{count}", "1")
            .replace("{alive}", "3").replace("{total}", "4").replace("{nights}", "1").replace("{days}", "1");
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("lgmclobby.preview")) { sender.sendMessage(msg("preview-denied")); return true; }
        if (args.length>1) { sender.sendMessage(msg("preview-usage")); return true; }
        Player target=args.length==1 ? Bukkit.getPlayerExact(args[0]) : sender instanceof Player p ? p : null;
        if (target==null) { sender.sendMessage(msg("preview-player-required")); return true; }
        if (!idle()) { sender.sendMessage(msg("preview-idle-only")); return true; }
        open(target, "main"); target.sendMessage(msg("preview-notice"));
        lgmc.getLogger().info("preview requested-by="+sender.getName()+" target="+target.getUniqueId());
        return true;
    }
    private void add(View view,int slot,Material material,String title,String action,String... lore) {
        ItemStack stack=new ItemStack(material); ItemMeta meta=stack.getItemMeta();
        meta.setDisplayName(title); meta.setLore(Arrays.asList(lore)); stack.setItemMeta(meta);
        view.inventory.setItem(slot,stack);
        if(action!=null) view.actions.put(slot,action);
    }
    private void open(Player p,String page) {
        if(!idle()) return;
        View view=new View();
        String title = switch(page) {
            case "vote" -> sample("gui.vote");
            case "witch" -> sample("gui.sorciere");
            default -> msg("preview-title");
        };
        view.inventory=Bukkit.createInventory(view,54,title);
        if(page.equals("main")) {
            for(int i=0;i<ROLES.length;i++) {
                String role=ROLES[i];
                Material material=lgmc.getConfigManager().getRoleHelmetItemStack(role).getType();
                if(material.isAir()) material=Material.PAPER;
                add(view,i,material,sample("roles."+role+".name"),"role:"+role,
                    sample("roles."+role+".subtitle"),msg("preview-role-hint"));
            }
            add(view,18,Material.PAPER,msg("preview-vote"),"page:vote");
            add(view,20,Material.SUGAR,msg("preview-witch"),"page:witch");
            add(view,22,Material.CLOCK,msg("preview-phases"),"phases");
            add(view,24,Material.EMERALD,msg("preview-victory"),"victory");
        } else if(page.equals("vote")) {
            add(view,10,Material.PLAYER_HEAD,msg("preview-player-a"),"sample",msg("preview-no-action"));
            add(view,12,Material.PLAYER_HEAD,msg("preview-player-b"),"sample",msg("preview-no-action"));
            add(view,44,Material.FEATHER,sample("gui.items.skip"),"sample");
        } else if(page.equals("witch")) {
            add(view,20,Material.SUGAR,sample("gui.items.life-potion"),"sample",sample("gui.items.life-potion-lore"));
            add(view,22,Material.FEATHER,sample("gui.items.skip-gray"),"sample");
            add(view,24,Material.GUNPOWDER,sample("gui.items.death-potion"),"sample",sample("gui.items.death-potion-lore"));
        }
        add(view,45,Material.ARROW,msg("preview-back"),"page:main");
        add(view,49,Material.BARRIER,msg("preview-close"),"close");
        p.openInventory(view.inventory);
    }
    @EventHandler public void click(InventoryClickEvent event) {
        if(!(event.getView().getTopInventory().getHolder() instanceof View view)) return;
        event.setCancelled(true); // includes bottom inventory, hotbar swaps, shift/double clicks
        if(!(event.getWhoClicked() instanceof Player p)) return;
        if(!idle()) { p.closeInventory(); return; }
        String action=view.actions.get(event.getRawSlot()); if(action==null) return;
        // Inventory transitions are deferred until the click transaction has finished.
        Bukkit.getScheduler().runTask(owner,()-> {
            if(!p.isOnline() || !idle() || p.getOpenInventory().getTopInventory()!=view.inventory) return;
            if(action.startsWith("page:")) open(p,action.substring(5));
            else if(action.equals("close")) p.closeInventory();
            else if(action.startsWith("role:")) {
                String role=action.substring(5); p.closeInventory();
                p.sendMessage(msg("preview-notice"));
                p.sendTitle(sample("roles."+role+".title"),sample("roles."+role+".subtitle"),5,60,10);
            } else if(action.equals("phases")) {
                p.closeInventory(); p.sendMessage(msg("preview-notice"));
                for(String key:List.of("phases.night","phases.night-falls","phases.day","phases.day-breaks")) p.sendMessage(sample(key));
                p.sendActionBar(sample("general.game-starting"));
            } else if(action.equals("victory")) {
                p.closeInventory(); p.sendMessage(msg("preview-notice"));
                p.sendTitle(sample("victory.village.title"),sample("victory.village.subtitle"),5,60,10);
                p.sendMessage(sample("victory.village.broadcast-stats"));
                p.sendMessage(sample("victory.village.broadcast-duration"));
            } else p.sendMessage(msg("preview-no-action"));
        });
    }
    @EventHandler public void drag(InventoryDragEvent event) {
        if(event.getView().getTopInventory().getHolder() instanceof View) event.setCancelled(true);
    }
    void closeAll() {
        for(Player p:Bukkit.getOnlinePlayers()) {
            if(p.getOpenInventory().getTopInventory().getHolder() instanceof View) p.closeInventory();
        }
    }
}
