package io.github.notjoja.combatlogdummies.hook;

import com.artillexstudios.axgraves.grave.Grave;
import com.artillexstudios.axgraves.grave.SpawnedGraves;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Only ever touched when AxGraves is enabled; callers must catch {@link Throwable}
 * (e.g. NoClassDefFoundError) and fall back to dropping items.
 */
public final class AxGravesHook {
    private AxGravesHook() {}

    public static void createGrave(Location loc, OfflinePlayer owner, List<ItemStack> items, int xp) {
        Grave grave = new Grave(loc, owner, items, xp, System.currentTimeMillis(), null);
        SpawnedGraves.addGrave(grave);
    }
}
