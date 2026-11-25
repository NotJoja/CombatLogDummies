package io.github.notjoja.combatlogdummies.api;

import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Called after a combat-log dummy died and its items/xp were handed off (grave or ground drops).
 */
public final class DummyDeathEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID owner;
    private final Zombie dummy;
    private final Player killer;

    public DummyDeathEvent(@NotNull UUID owner, @NotNull Zombie dummy, @Nullable Player killer) {
        this.owner = owner;
        this.dummy = dummy;
        this.killer = killer;
    }

    /** UUID of the player the dummy represents. */
    public @NotNull UUID getOwner() { return owner; }

    public @NotNull Zombie getDummy() { return dummy; }

    public @Nullable Player getKiller() { return killer; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}
