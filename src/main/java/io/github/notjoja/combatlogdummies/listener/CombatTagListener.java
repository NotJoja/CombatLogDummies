package io.github.notjoja.combatlogdummies.listener;

import io.github.notjoja.combatlogdummies.CombatLogDummies;
import io.github.notjoja.combatlogdummies.config.PluginConfig;
import org.bukkit.Bukkit;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class CombatTagListener implements Listener {
    private final CombatLogDummies plugin;
    /** End crystal -> player who last damaged it (so crystal explosions can be attributed). */
    private final Map<UUID, UUID> crystalOwners = new HashMap<>();

    public CombatTagListener(CombatLogDummies plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (e.getEntity() instanceof EnderCrystal crystal) {
            Player p = resolvePlayer(e.getDamager());
            if (p != null) {
                if (crystalOwners.size() > 512) crystalOwners.clear();
                crystalOwners.put(crystal.getUniqueId(), p.getUniqueId());
            }
            return;
        }
        if (e.getEntity() instanceof Zombie dummy) {
            onDummyHit(e, dummy);
            return;
        }
        if (!(e.getEntity() instanceof Player victim)) return;

        Entity damager = e.getDamager();
        boolean explosion = damager instanceof EnderCrystal || damager instanceof TNTPrimed;
        if (explosion && !plugin.settings().explosionsTag) return;

        Player attacker = resolvePlayer(damager);
        if (attacker == null || attacker.equals(victim)) return;

        var cfg = plugin.settings();
        double threshold = cfg.startBelowHealth;
        if (threshold > 0 && victim.getHealth() - e.getFinalDamage() > threshold) return;

        if (cfg.tagMode != PluginConfig.TagMode.VICTIM) plugin.combat().tag(attacker, victim.getUniqueId());
        if (cfg.tagMode != PluginConfig.TagMode.ATTACKER) plugin.combat().tag(victim, attacker.getUniqueId());
    }

    /** A player hitting a combat-log dummy behaves like hitting the (offline) owner. */
    private void onDummyHit(EntityDamageByEntityEvent e, Zombie dummy) {
        UUID owner = plugin.dummies().ownerOf(dummy);
        if (owner == null) return;
        Player attacker = resolvePlayer(e.getDamager());
        if (attacker == null) return;
        var cfg = plugin.settings();
        if (cfg.startBelowHealth > 0 && dummy.getHealth() - e.getFinalDamage() > cfg.startBelowHealth) return;
        if (cfg.tagMode != PluginConfig.TagMode.VICTIM) plugin.combat().tag(attacker, owner);
        if (cfg.tagMode != PluginConfig.TagMode.ATTACKER
                && !cfg.ignoredWorlds.contains(dummy.getWorld().getName().toLowerCase(java.util.Locale.ROOT))) {
            plugin.dummies().refresh(owner);
        }
    }

    private Player resolvePlayer(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile proj) return fromSource(proj.getShooter());
        if (damager instanceof AreaEffectCloud cloud) return fromSource(cloud.getSource());
        if (damager instanceof TNTPrimed tnt) return tnt.getSource() instanceof Player p ? p : null;
        if (damager instanceof Tameable pet) return pet.getOwner() instanceof Player p ? p : null;
        if (damager instanceof EnderCrystal crystal) {
            UUID id = crystalOwners.get(crystal.getUniqueId());
            return id == null ? null : Bukkit.getPlayer(id);
        }
        return null;
    }

    private Player fromSource(ProjectileSource source) {
        return source instanceof Player p ? p : null;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPearlLand(ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof EnderPearl pearl) || !(pearl.getShooter() instanceof Player p)) return;
        var cfg = plugin.settings();
        if (!cfg.pearlSetOnLand) return;
        if (cfg.pearlOnlyIfInCombat && !plugin.combat().isInCombat(p)) return;
        plugin.combat().tag(p);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        plugin.combat().untag(victim.getUniqueId());
        Player killer = victim.getKiller();
        if (killer != null && !killer.equals(victim)) plugin.combat().opponentKilled(killer, victim.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuitCleanup(PlayerQuitEvent e) {
        // DummyListener reads the combat state at NORMAL priority; clear whatever is left afterwards.
        plugin.combat().untag(e.getPlayer().getUniqueId());
    }
}
