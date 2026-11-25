package io.github.notjoja.combatlogdummies.hook;

import com.artillexstudios.axgraves.grave.Grave;
import com.artillexstudios.axgraves.grave.SpawnedGraves;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.List;

/**
 * Only ever touched when AxGraves is enabled; callers must catch {@link Throwable}
 * (e.g. NoClassDefFoundError) and fall back to dropping items.
 */
public final class AxGravesHook {
    private AxGravesHook() {}

    public static void createGrave(Location loc, OfflinePlayer owner, List<ItemStack> items, int xp,
                                   String texture) {
        // AxGraves only looks up the head of ONLINE owners itself; for offline owners we must hand it the texture.
        // Honour its custom-grave-skull option the same way it does for online players.
        var axgraves = Bukkit.getPluginManager().getPlugin("AxGraves");
        if (axgraves != null) {
            var cfg = YamlConfiguration.loadConfiguration(new File(axgraves.getDataFolder(), "config.yml"));
            String custom = cfg.getString("custom-grave-skull.base64");
            if (cfg.getBoolean("custom-grave-skull.enabled", false) && custom != null) texture = custom;
        }
        Grave grave = new Grave(loc, owner, items, xp, System.currentTimeMillis(), texture);
        SpawnedGraves.addGrave(grave);
    }
}
