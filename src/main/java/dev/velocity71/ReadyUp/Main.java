package dev.velocity71.ReadyUp;

import java.util.Set;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Hello world!
 */
public class Main extends JavaPlugin {

    private static final int MIN_PLAYERS = 3;
    private static final String LOADING_WORLD = "loading_world";
    private static final String MAIN_WORLD = "world"; // default world name

    @Override
    public void onEnable() {
        // Schedule task every few seconds
        new BukkitRunnable() {
            @Override
            public void run() {
                managePlayers();
            }
        }
            .runTaskTimer(this, 20L, 100L); // Run every 5 seconds
    }

    private void managePlayers() {
        World loadingWorld = Bukkit.getWorld(LOADING_WORLD);
        World mainWorld = Bukkit.getWorld(MAIN_WORLD);

        if (loadingWorld == null || mainWorld == null) {
            getLogger().warning("One of the required worlds is missing.");
            return;
        }

        Set<Player> loadingPlayers = loadingWorld
            .getPlayers()
            .stream()
            .collect(Collectors.toSet());
        Set<Player> mainPlayers = mainWorld
            .getPlayers()
            .stream()
            .collect(Collectors.toSet());

        // If enough players in the loading world, send all to main world
        if (loadingPlayers.size() >= MIN_PLAYERS) {
            for (Player p : loadingPlayers) {
                p.teleport(mainWorld.getSpawnLocation());
                p.sendMessage(
                    "Enough players have joined. Entering the main world."
                );
            }
        }

        // If not enough players in the main world, pull them back to the loading world
        if (mainPlayers.size() > 0 && mainPlayers.size() < MIN_PLAYERS) {
            for (Player p : mainPlayers) {
                p.teleport(loadingWorld.getSpawnLocation());
                p.sendMessage(
                    "Not enough players. Returning to loading screen..."
                );
            }
        }
    }
}
