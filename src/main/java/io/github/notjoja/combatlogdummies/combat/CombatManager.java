package io.github.notjoja.combatlogdummies.combat;

import io.github.notjoja.combatlogdummies.CombatLogDummies;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class CombatManager {
    private final CombatLogDummies plugin;
    private final Map<UUID, Long> expiry = new HashMap<>();
    private final Map<UUID, BossBar> bars = new HashMap<>();
    /** player -> (opponent -> time the fight with that opponent runs out). Used to untag once all opponents are dead. */
    private final Map<UUID, Map<UUID, Long>> opponents = new HashMap<>();
    private BukkitTask task;

    public CombatManager(CombatLogDummies plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void shutdown() {
        if (task != null) task.cancel();
        for (UUID id : bars.keySet().toArray(UUID[]::new)) hideBar(id);
    }

    public boolean canBeTagged(Player p) {
        if (p.hasPermission("combatlog.bypass")) return false;
        if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) return false;
        return !plugin.settings().ignoredWorlds.contains(p.getWorld().getName().toLowerCase(Locale.ROOT));
    }

    /** Tags {@code p} as fighting {@code opponent} (a player UUID, or a dummy owner's UUID). */
    public void tag(Player p, UUID opponent) {
        if (!canBeTagged(p)) return;
        opponents.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>())
                .put(opponent, System.currentTimeMillis() + plugin.settings().durationSeconds * 1000L);
        tag(p);
    }

    /**
     * {@code killer} killed {@code dead}. Drops that opponent; if the killer has no other active opponents
     * left, their combat tag is removed (when untag-on-kill is enabled).
     */
    public void opponentKilled(Player killer, UUID dead) {
        if (!plugin.settings().untagOnKill) return;
        Map<UUID, Long> map = opponents.get(killer.getUniqueId());
        if (map == null) return; // not fighting anyone we know about (e.g. tagged by a pearl): leave the timer alone
        map.remove(dead);
        long now = System.currentTimeMillis();
        map.values().removeIf(t -> t <= now);
        if (map.isEmpty()) untag(killer, true);
    }

    public void tag(Player p) {
        if (!canBeTagged(p)) return;
        var cfg = plugin.settings();
        long fullMs = cfg.durationSeconds * 1000L;
        expiry.put(p.getUniqueId(), System.currentTimeMillis() + fullMs);
        if (cfg.elytraDisabled && p.isGliding()) p.setGliding(false);
        for (PotionEffect e : cfg.tagEffects) p.addPotionEffect(e);
        display(p);
    }

    /** Puts a player into combat with a specific remaining time (used when rejoining after a combat log). */
    public void tagFor(Player p, long millis) {
        if (millis <= 0) return;
        expiry.put(p.getUniqueId(), System.currentTimeMillis() + millis);
        display(p);
    }

    public boolean isInCombat(Player p) {
        return isInCombat(p.getUniqueId());
    }

    public boolean isInCombat(UUID id) {
        Long e = expiry.get(id);
        return e != null && e > System.currentTimeMillis();
    }

    /** Remaining millis, 0 if not in combat. */
    public long remainingMillis(UUID id) {
        Long e = expiry.get(id);
        return e == null ? 0 : Math.max(0, e - System.currentTimeMillis());
    }

    /** Removes the tag silently. Returns true if the player was tagged. */
    public boolean untag(UUID id) {
        boolean was = expiry.remove(id) != null;
        opponents.remove(id);
        hideBar(id);
        return was;
    }

    public void untag(Player p, boolean notify) {
        boolean was = untag(p.getUniqueId());
        if (was && notify) p.sendActionBar(plugin.messages().get("combat-log.messages.timer.ended"));
    }

    private void tick() {
        Iterator<Map.Entry<UUID, Long>> it = expiry.entrySet().iterator();
        long now = System.currentTimeMillis();
        while (it.hasNext()) {
            var en = it.next();
            Player p = Bukkit.getPlayer(en.getKey());
            if (p == null) {
                it.remove();
                opponents.remove(en.getKey());
                hideBar(en.getKey());
            } else if (en.getValue() <= now) {
                it.remove();
                opponents.remove(en.getKey());
                hideBar(en.getKey());
                p.sendActionBar(plugin.messages().get("combat-log.messages.timer.ended"));
            } else {
                display(p);
            }
        }
    }

    private void display(Player p) {
        long ms = remainingMillis(p.getUniqueId());
        int left = (int) Math.ceil(ms / 1000.0);
        int max = plugin.settings().durationSeconds;
        Map<String, String> ph = Map.of("timeLeft", String.valueOf(left), "maxTime", String.valueOf(max));
        if (plugin.settings().bossbar) {
            Component title = plugin.messages().get("combat-log.messages.timer.bossbar-title", ph);
            float progress = Math.max(0f, Math.min(1f, ms / (max * 1000f)));
            BossBar bar = bars.get(p.getUniqueId());
            if (bar == null) {
                bar = BossBar.bossBar(title, progress, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
                bars.put(p.getUniqueId(), bar);
                p.showBossBar(bar);
            } else {
                bar.name(title);
                bar.progress(progress);
            }
        } else {
            p.sendActionBar(plugin.messages().get("combat-log.messages.timer.actionbar", ph));
        }
    }

    private void hideBar(UUID id) {
        BossBar bar = bars.remove(id);
        if (bar == null) return;
        Player p = Bukkit.getPlayer(id);
        if (p != null) p.hideBossBar(bar);
    }
}
