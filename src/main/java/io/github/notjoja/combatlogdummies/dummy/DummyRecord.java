package io.github.notjoja.combatlogdummies.dummy;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public final class DummyRecord {
    public enum State { ALIVE, EXPIRED, DEAD }

    public final UUID owner;
    public String name;
    public Location location;
    public double health;
    public long expiryMillis;
    public ItemStack[] contents;
    public int xp;
    /** Base64 skin texture of the owner, so AxGraves can show their head while they are offline. */
    public String texture;
    public UUID entityId;
    /** Set when the dummy dies: what the owner loses on their next login (false = kept via keepInventory). */
    public boolean clearItems = true;
    public boolean clearXp = true;
    public State state = State.ALIVE;

    public DummyRecord(UUID owner) {
        this.owner = owner;
    }
}
