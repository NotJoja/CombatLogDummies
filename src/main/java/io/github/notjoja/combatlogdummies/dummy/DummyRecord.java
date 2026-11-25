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
    public UUID entityId;
    public State state = State.ALIVE;

    public DummyRecord(UUID owner) {
        this.owner = owner;
    }
}
