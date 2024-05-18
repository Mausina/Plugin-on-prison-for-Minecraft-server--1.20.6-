package damfli.prison;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.GameMode;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

public final class Prison1_0 extends JavaPlugin implements Listener {

    private final Map<String, Location> jailLocations = new HashMap<>();
    private final Map<UUID, PlayerData> imprisonedPlayers = new HashMap<>();
    private Location selectionStart;
    private Location selectionEnd;
    private final List<ProtectedRegion> protectedRegions = new ArrayList<>();
    private final Map<UUID, String> pendingImprisonment = new HashMap<>();

    @Override
    public void onEnable() {
        getLogger().info("Prison 1.0 plugin has been enabled!");
        getServer().getPluginManager().registerEvents(this, this);

        // Register commands
        getCommand("set_jail").setExecutor(this::setJailLocation);
        getCommand("prison_stick").setExecutor(this::givePrisonStick);
        getCommand("imprison").setExecutor(this::imprisonCommand);
        getCommand("set_jail_area").setExecutor(this::setJailArea);
        getCommand("free").setExecutor(this::freeCommand);
    }

    @Override
    public void onDisable() {
        getLogger().info("Prison 1.0 plugin has been disabled!");
    }

    private boolean setJailLocation(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can execute this command.");
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage("Usage: /set_jail <name>");
            return true;
        }
        Player player = (Player) sender;
        String jailName = args[0];
        jailLocations.put(jailName, player.getLocation());
        player.sendMessage("Jail location '" + jailName + "' set!");
        return true;
    }

    private boolean givePrisonStick(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can execute this command.");
            return true;
        }
        Player player = (Player) sender;

        ItemStack stick = new ItemStack(Material.STICK);
        ItemMeta meta = stick.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("Prison Stick");
            stick.setItemMeta(meta);
            player.getInventory().addItem(stick);
            player.sendMessage("Prison Stick given. Use it to select two points for the jail area.");
        }
        return true;
    }

    private boolean imprisonCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 3) {
            sender.sendMessage("Usage: /imprison <player> <time> <jail>");
            return true;
        }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage("Player not found.");
            return true;
        }
        int time;
        try {
            time = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage("Invalid time format.");
            return true;
        }
        String jailName = args[2];
        Location jailLocation = jailLocations.get(jailName);
        if (jailLocation == null) {
            sender.sendMessage("Jail location not found.");
            return true;
        }
        imprisonPlayer(sender instanceof Player ? (Player) sender : null, target, time, jailLocation);
        sender.sendMessage("Player " + target.getName() + " has been imprisoned for " + time + " seconds at " + jailName + ".");
        return true;
    }

    private boolean setJailArea(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can execute this command.");
            return true;
        }
        Player player = (Player) sender;

        if (selectionStart == null || selectionEnd == null) {
            player.sendMessage("You must select two points first using the Prison Stick.");
            return true;
        }

        protectedRegions.add(new ProtectedRegion(selectionStart, selectionEnd));
        player.sendMessage("Jail area set from " + selectionStart.toVector() + " to " + selectionEnd.toVector() + ".");
        selectionStart = null;
        selectionEnd = null;
        return true;
    }

    private boolean freeCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("all")) {
            for (UUID playerId : new ArrayList<>(imprisonedPlayers.keySet())) {
                releasePlayer(Bukkit.getPlayer(playerId));
            }
            sender.sendMessage("All imprisoned players have been released.");
        } else if (args.length == 1) {
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage("Player not found.");
                return true;
            }
            releasePlayer(target);
            sender.sendMessage("Player " + target.getName() + " has been released.");
        } else {
            sender.sendMessage("Usage: /free <player> or /free all");
        }
        return true;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.LEFT_CLICK_BLOCK) {
            if (item.getType() == Material.STICK && item.getItemMeta() != null && "Prison Stick".equals(item.getItemMeta().getDisplayName())) {
                if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
                    selectionStart = event.getClickedBlock().getLocation();
                    player.sendMessage("First point selected at " + selectionStart.toVector() + ".");
                } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                    selectionEnd = event.getClickedBlock().getLocation();
                    player.sendMessage("Second point selected at " + selectionEnd.toVector() + ".");
                }
            }
        }
    }

    private void imprisonPlayer(Player guard, Player target, int time, Location jailLocation) {
        UUID targetUUID = target.getUniqueId();
        Location originalLocation = target.getLocation();
        ItemStack[] originalInventory = target.getInventory().getContents();

        target.teleport(jailLocation);
        target.setGameMode(GameMode.ADVENTURE);
        target.getInventory().clear();
        target.setFoodLevel(0);
        target.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, Integer.MAX_VALUE, 1, true, false));
        target.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, Integer.MAX_VALUE, 255, true, false));

        imprisonedPlayers.put(targetUUID, new PlayerData(originalLocation, originalInventory));

        new BukkitRunnable() {
            @Override
            public void run() {
                releasePlayer(target);
            }
        }.runTaskLater(this, time * 20L);
    }

    private void releasePlayer(Player player) {
        UUID playerUUID = player.getUniqueId();
        PlayerData data = imprisonedPlayers.remove(playerUUID);
        if (data != null) {
            player.teleport(data.getOriginalLocation());
            player.getInventory().setContents(data.getOriginalInventory());
            player.setGameMode(GameMode.SURVIVAL);
            player.removePotionEffect(PotionEffectType.SATURATION);
            player.removePotionEffect(PotionEffectType.REGENERATION);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        for (ProtectedRegion region : protectedRegions) {
            if (region.isInside(player.getLocation()) && !player.isOp()) {
                event.setCancelled(true);
                player.sendMessage("You cannot break blocks in this area.");
                return;
            }
        }
    }

    private static class PlayerData {
        private final Location originalLocation;
        private final ItemStack[] originalInventory;

        public PlayerData(Location originalLocation, ItemStack[] originalInventory) {
            this.originalLocation = originalLocation;
            this.originalInventory = originalInventory;
        }

        public Location getOriginalLocation() {
            return originalLocation;
        }

        public ItemStack[] getOriginalInventory() {
            return originalInventory;
        }
    }

    private static class ProtectedRegion {
        private final Location start;
        private final Location end;

        public ProtectedRegion(Location start, Location end) {
            this.start = start;
            this.end = end;
        }

        public boolean isInside(Location loc) {
            return loc.getX() >= Math.min(start.getX(), end.getX()) && loc.getX() <= Math.max(start.getX(), end.getX())
                    && loc.getY() >= Math.min(start.getY(), end.getY()) && loc.getY() <= Math.max(start.getY(), end.getY())
                    && loc.getZ() >= Math.min(start.getZ(), end.getZ()) && loc.getZ() <= Math.max(start.getZ(), end.getZ());
        }
    }
}
