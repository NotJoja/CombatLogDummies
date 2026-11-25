package io.github.notjoja.combatlogdummies.dummy;

import io.github.notjoja.combatlogdummies.CombatLogDummies;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class DummyListener implements Listener {
    private final CombatLogDummies plugin;
    /** Players being killed on join because their dummy died; their death must not drop anything. */
    private final Set<UUID> silentDeaths = new HashSet<>();

    public DummyListener(CombatLogDummies plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        var combat = plugin.combat();
        if (!plugin.settings().dummyEnabled || !combat.isInCombat(p)) return;
        if (p.isDead() || p.getHealth() <= 0) return;
        if (e.getReason() == PlayerQuitEvent.QuitReason.KICKED) return; // kicks/shutdowns are not combat logging
        plugin.dummies().spawn(p, combat.remainingMillis(p.getUniqueId()));
        Bukkit.broadcast(plugin.messages().get("combat-log.messages.combat-log",
                Map.of("player", p.getName())));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        DummyRecord r = plugin.dummies().get(p.getUniqueId());
        if (r == null) return;

        if (r.state == DummyRecord.State.DEAD) {
            plugin.dummies().remove(p.getUniqueId());
            silentDeaths.add(p.getUniqueId());
            p.getInventory().clear();
            p.setTotalExperience(0);
            p.setLevel(0);
            p.setExp(0f);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (p.isOnline()) {
                    p.setHealth(0);
                    p.sendMessage(plugin.messages().get("combat-log.messages.dummy-killed"));
                }
            });
            return;
        }

        // ALIVE or EXPIRED: resume where the dummy stood, with the dummy's health.
        double health = r.health;
        var loc = r.location;
        var zombie = r.state == DummyRecord.State.ALIVE ? plugin.dummies().findEntity(r) : null;
        if (zombie != null) {
            health = zombie.getHealth();
            loc = zombie.getLocation();
        }
        long remaining = r.state == DummyRecord.State.ALIVE ? r.expiryMillis - System.currentTimeMillis() : 0;
        plugin.dummies().remove(p.getUniqueId());
        plugin.combat().untag(p.getUniqueId());
        if (remaining > 0) plugin.combat().tagFor(p, remaining); // still in combat after logging back in
        if (loc != null && loc.getWorld() != null) p.teleport(loc);
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        double cap = max == null ? 20 : max.getValue();
        p.setHealth(Math.max(0.5, Math.min(health, cap)));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent e) {
        if (!silentDeaths.remove(e.getEntity().getUniqueId())) return;
        e.setKeepInventory(true);
        e.setKeepLevel(true);
        e.getDrops().clear();
        e.setDroppedExp(0);
        e.deathMessage(null);
    }

    @EventHandler
    public void onDummyDeath(EntityDeathEvent e) {
        if (!(e.getEntity() instanceof Zombie z)) return;
        UUID owner = plugin.dummies().ownerOf(z);
        if (owner == null) return;
        e.getDrops().clear();
        e.setDroppedExp(0);
        plugin.dummies().handleDeath(z, owner);
    }

    /** Removes dummy zombies that no longer have a matching live record (e.g. record expired while chunk was unloaded). */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent e) {
        for (Entity ent : e.getEntities()) {
            if (!(ent instanceof Zombie z)) continue;
            UUID owner = plugin.dummies().ownerOf(z);
            if (owner == null) continue;
            DummyRecord r = plugin.dummies().get(owner);
            boolean valid = r != null && r.state == DummyRecord.State.ALIVE
                    && z.getUniqueId().equals(r.entityId) && r.expiryMillis > System.currentTimeMillis();
            if (!valid) z.remove();
        }
    }
}
