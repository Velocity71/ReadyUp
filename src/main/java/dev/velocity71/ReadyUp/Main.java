package dev.velocity71.ReadyUp;

import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class Main extends JavaPlugin implements Listener {

    private World queueWorld;
    private World overworld;
    private World nether;
    private World end;

    private final int threshold = 2;

    private final Set<UUID> waitingPlayers = new HashSet<>();

    private final Map<UUID, PlayerState> savedPlayerData = new HashMap<>();

    private boolean isActive = false;

    @Override
    public void onEnable() {
        loadOrCreateQueueWorld();

        // Load and validate worlds
        //queueWorld = Bukkit.getWorld("queue");
        overworld = Bukkit.getWorld("world");
        nether = Bukkit.getWorld("world_the_nether");
        end = Bukkit.getWorld("world_the_end");

        if (queueWorld == null) {
            getLogger().severe("The queue world cannot be found.");
            return;
        }

        if (overworld == null) {
            getLogger().severe("The overworld cannot be found.");
            return;
        }

        if (nether == null) {
            getLogger().severe("The nether cannot be found.");
        }

        if (end == null) {
            getLogger().severe("The end cannot be found.");
        }

        // Register player join/quit events;
        getServer().getPluginManager().registerEvents(this, this);

        // Register command
        getCommand("forcejoin").setExecutor((sender, cmd, label, args) -> {
            checkAndEnteroverworld();
            return true;
        });

        // Start repeating task to monitor player count and fallback if needed
        startAutoMonitor();
    }

    private void startAutoMonitor() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (isActive && Bukkit.getOnlinePlayers().size() < threshold) {
                    getLogger()
                        .info(
                            "Player count below threshold - reverting all players to the queue world"
                        );
                    returnAllToQueueWorld();
                }
            }
        }
            .runTaskTimer(this, 100L, 100L); // 100 ticks = 5 seconds
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();

        // Save player state only if not yet tracked
        if (!savedPlayerData.containsKey(p.getUniqueId())) {
            savePlayerState(p);
        }

        // Place in waiting queue
        sendToQueueWorld(p);

        // Check if enough players are ready
        checkAndEnteroverworld();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID uuid = e.getPlayer().getUniqueId();

        // Remove player from queue tracking
        waitingPlayers.remove(uuid);

        // If in main world, check if the playere count dropped to low
        if (isActive && Bukkit.getOnlinePlayers().size() - 1 < threshold) {
            getLogger().info("A player left. Below threshold.");
            returnAllToQueueWorld();
        }
    }

    private void checkAndEnteroverworld() {
        if (!isActive && waitingPlayers.size() >= threshold) {
            isActive = true;

            for (UUID uuid : waitingPlayers) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) {
                    restorePlayerState(p);
                    p.sendTitle("Entering Game!", "Have fun!", 10, 40, 10);
                }
            }

            waitingPlayers.clear();
            getLogger().info("Threshold met. All players moved to main world.");
        }
    }

    private void returnAllToQueueWorld() {
        isActive = false;

        for (Player p : Bukkit.getOnlinePlayers()) {
            savePlayerState(p);
            sendToQueueWorld(p);
            waitingPlayers.add(p.getUniqueId());
            p.sendTitle(
                "Too few players",
                "Returning to the queue world...",
                10,
                40,
                10
            );
        }
    }

    private void savePlayerState(Player p) {
        // PlayerInventory is a pointer to a player's real-time inventory, so we cannot save the data, we need to capture it's pieces.
        PlayerInventory i = p.getInventory();
        PlayerState s = new PlayerState(
            p.getLocation(),
            i.getContents(),
            i.getHelmet(),
            i.getChestplate(),
            i.getLeggings(),
            i.getBoots(),
            i.getItemInOffHand()
        );

        savedPlayerData.put(p.getUniqueId(), s);
    }

    private void restorePlayerState(Player p) {
        PlayerState s = savedPlayerData.get(p.getUniqueId());

        if (s != null) {
            PlayerInventory i = p.getInventory();

            i.setContents(s.getInventory());
            i.setHelmet(s.getHelmet());
            i.setChestplate(s.getChestplate());
            i.setLeggings(s.getLeggings());
            i.setBoots(s.getBoots());
            i.setItemInOffHand(s.getOffHand());

            p.teleport(s.getLocation());
        } else {
            p.teleport(overworld.getSpawnLocation());
        }

        p.setGameMode(GameMode.SURVIVAL);
    }

    private void sendToQueueWorld(Player p) {
        p.teleport(queueWorld.getSpawnLocation());
        p.getInventory().clear();
        p.setGameMode(GameMode.ADVENTURE);
    }

    private void loadOrCreateQueueWorld() {
        WorldCreator c = new WorldCreator("queue_world");
        c.environment(World.Environment.NORMAL);
        c.type(WorldType.FLAT);
        queueWorld = c.createWorld();
    }

    private static class PlayerState {

        private final Location location;
        private final ItemStack[] inventory;
        private final ItemStack helmet;
        private final ItemStack chestplate;
        private final ItemStack leggings;
        private final ItemStack boots;
        private final ItemStack offHand;

        public PlayerState(
            Location location,
            ItemStack[] inventory,
            ItemStack helmet,
            ItemStack chestplate,
            ItemStack leggings,
            ItemStack boots,
            ItemStack offHand
        ) {
            this.location = location;
            this.inventory = inventory;
            this.helmet = helmet;
            this.chestplate = chestplate;
            this.leggings = leggings;
            this.boots = boots;
            this.offHand = offHand;
        }

        public Location getLocation() {
            return location;
        }

        public ItemStack[] getInventory() {
            return inventory;
        }

        public ItemStack getHelmet() {
            return helmet;
        }

        public ItemStack getChestplate() {
            return chestplate;
        }

        public ItemStack getLeggings() {
            return leggings;
        }

        public ItemStack getBoots() {
            return boots;
        }

        public ItemStack getOffHand() {
            return offHand;
        }
    }
}
