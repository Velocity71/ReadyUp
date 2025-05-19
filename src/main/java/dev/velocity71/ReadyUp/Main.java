package dev.velocity71.ReadyUp;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Main class for the ReadyUp program.
 */
public class Main extends JavaPlugin implements Listener {

    /** Acts as a queue until enough players populate the server. */
    private World queueWorld;

    /** The overworld for the server. */
    private World overworld;

    /** The nether for the server. */
    private World nether;

    /** The end for the server */
    private World end;

    /** The minimum amount of players that must be in the server for players to be allowed into the main world. */
    private final int MIN_PLAYERS = 2;

    private final long MONITOR_INTERVAL = 100L; // 100 ticks = 5 seconds.

    /** A set of players waiting in the queue. */
    private final HashSet<UUID> waitingPlayers = new HashSet<UUID>();

    /** Is the server active */
    private boolean isActive = false;

    @Override
    public void onEnable() {
        // Find/create the 'players' folder, which holds all the saved data for the players.
        File dataFolder = new File(getDataFolder(), "players");
        if (!dataFolder.exists()) dataFolder.mkdirs();

        loadOrCreateQueueWorld();

        // Load and validate worlds
        //queueWorld = Bukkit.getWorld("queue");
        overworld = Bukkit.getWorld("world");
        nether = Bukkit.getWorld("world_nether");
        end = Bukkit.getWorld("world_the_end");

        // Check that all the proper worlds are found and loaded.
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
            return;
        }

        if (end == null) {
            getLogger().severe("The end cannot be found.");
            return;
        }

        // Register player join/quit events;
        getServer().getPluginManager().registerEvents(this, this);

        // Register command that forces players to join the overworld.
        getCommand("forcejoin").setExecutor((sender, cmd, label, args) -> {
            checkAndEnterOverworld();
            return true;
        });

        // Start repeating task to monitor player count and fallback if needed
        startAutoMonitor();
        getLogger().info("Initiated automatic player count monitoring.");
    }

    /**
     * Every 100 ticks (5 seconds), check if the player count is below the threshold.
     */
    private void startAutoMonitor() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (
                    isActive && Bukkit.getOnlinePlayers().size() < MIN_PLAYERS
                ) {
                    getLogger()
                        .info(
                            "Player count below threshold - reverting all players to the queue world."
                        );
                    returnAllToQueueWorld();
                }
            }
        }
            .runTaskTimer(this, MONITOR_INTERVAL, MONITOR_INTERVAL);
    }

    /**
     * Add a player to the savedPlayerData map, send them to the queue world, and check if there are enough players to enter the main world.
     */
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        getLogger().info("Player " + p.getUniqueId() + " has joined.");

        File f = new File(
            getDataFolder() + "/players",
            p.getUniqueId() + ".yml"
        );

        if (!f.exists()) {
            savePlayerState(p);
        }

        // Place in waiting queue
        sendToQueueWorld(p);

        // Check if enough players are present, and if so all enter the main world.
        checkAndEnterOverworld();
    }

    /**
     * Remove a player from the queue list, and if there aren't enough players, pull everyone to the queue world.
     */
    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        getLogger()
            .info("Player " + e.getPlayer().getUniqueId() + " has left.");

        // Remove player from queue tracking
        waitingPlayers.remove(e.getPlayer().getUniqueId());

        // If in main world, check if the playere count dropped to low
        if (isActive && Bukkit.getOnlinePlayers().size() - 1 < MIN_PLAYERS) {
            getLogger().info("A player left. Player count is below threshold.");
            returnAllToQueueWorld();
        }
    }

    /**
     * Check if the player count is above the threshold, and if so push everyone to the main world.
     */
    private void checkAndEnterOverworld() {
        // If the world is not active and the player list is at or above the threshold.
        if (!isActive && waitingPlayers.size() >= MIN_PLAYERS) {
            isActive = true;

            // Send everyone to the main world.
            for (UUID uuid : waitingPlayers) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) {
                    restorePlayerState(p);
                    p.sendTitle("Entering Game!", "Have fun!", 10, 40, 10);
                }
            }

            waitingPlayers.clear();
            getLogger()
                .info("MIN_PLAYERS met. All players moved to main world.");
        }
    }

    /**
     * Pull all players back to the queue world.
     */
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
            getLogger().info("All players pulled to the queue world.");
        }
    }

    /**
     * Save the player's inventory and location into a map, using the uuid.
     */
    private void savePlayerState(Player p) {
        File f = new File(
            getDataFolder() + "/players",
            p.getUniqueId() + ".yml"
        );
        FileConfiguration c = YamlConfiguration.loadConfiguration(f);

        c.set("location", p.getLocation());
        c.set("gamemode", p.getGameMode().name());
        c.set("inventory.contents", p.getInventory().getContents());
        c.set("inventory.armor", p.getInventory().getArmorContents());
        c.set("inventory.offhand", p.getInventory().getItemInOffHand());
        c.set("inventory.slot", p.getInventory().getHeldItemSlot());

        try {
            c.save(f);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Restore itmes and location to a player.
     */
    private void restorePlayerState(Player p) {
        File f = new File(
            getDataFolder() + "/players",
            p.getUniqueId() + ".yml"
        );
        if (!f.exists()) return;

        FileConfiguration c = YamlConfiguration.loadConfiguration(f);

        Location l = c.getLocation("location");
        GameMode m = GameMode.SURVIVAL;

        ItemStack[] i =
            ((List<ItemStack>) c.get("inventory.contents")).toArray(
                    new ItemStack[0]
                );
        ItemStack[] a =
            ((List<ItemStack>) c.get("inventory.armor")).toArray(
                    new ItemStack[0]
                );
        ItemStack o = c.getItemStack("inventory.offhand");
        int s = c.getInt("inventory.slog", 0);

        p.teleport(l);
        p.setGameMode(m);
        p.getInventory().setContents(i);
        p.getInventory().setArmorContents(a);
        p.getInventory().setItemInOffHand(o);
        p.getInventory().setHeldItemSlot(s);
    }

    /**
     * Pull each player to the queue world.
     */
    private void sendToQueueWorld(Player p) {
        p.teleport(queueWorld.getSpawnLocation());
        p.getInventory().clear(); // No items in queue world.
        p.setGameMode(GameMode.ADVENTURE); // Queue world is in adventure mode.
    }

    /**
     * Create the queue world (not permenant).
     */
    private void loadOrCreateQueueWorld() {
        WorldCreator c = new WorldCreator("queue_world");
        c.environment(World.Environment.NORMAL);
        c.type(WorldType.FLAT);
        queueWorld = c.createWorld();
    }
}
