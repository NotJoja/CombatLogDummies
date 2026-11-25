package io.github.notjoja.combatlogdummies.config;

import io.github.notjoja.combatlogdummies.CombatLogDummies;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PluginConfig {
    public enum TagMode { BOTH, ATTACKER, VICTIM }

    private final CombatLogDummies plugin;

    public int durationSeconds;
    public boolean bossbar;
    public boolean elytraDisabled;
    public boolean teleportDisabled;
    public boolean allowEnderpearl;
    public boolean mendingDisabled;
    public boolean riptideStop;
    public long riptideCooldownMs;
    public double punishmentDamage;
    public TagMode tagMode;
    public double startBelowHealth;
    public boolean pearlSetOnLand;
    public boolean pearlOnlyIfInCombat;
    public boolean untagOnKill;
    public boolean explosionsTag;
    public List<String> ignoredWorlds;
    public boolean commandWhitelist;
    public List<String> commands;
    public List<PotionEffect> tagEffects;
    public boolean dummyEnabled;
    public String dummyNameFormat;
    public boolean useAxGraves;
    public boolean respectKeepInventory;

    public PluginConfig(CombatLogDummies plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        FileConfiguration c = plugin.getConfig();
        String p = "combat-log.";
        durationSeconds = Math.max(1, c.getInt(p + "timer.duration-seconds", 15));
        bossbar = "bossbar".equalsIgnoreCase(c.getString(p + "timer.display", "actionbar"));
        elytraDisabled = c.getBoolean(p + "restrictions.elytra.disabled-in-combat", true);
        teleportDisabled = c.getBoolean(p + "restrictions.teleporting.disabled-in-combat", false);
        allowEnderpearl = c.getBoolean(p + "restrictions.teleporting.allow-enderpearl", false);
        mendingDisabled = c.getBoolean(p + "restrictions.mending.disabled-in-combat", true);
        riptideStop = c.getBoolean(p + "restrictions.riptide.stop", false);
        riptideCooldownMs = c.getLong(p + "restrictions.riptide.cooldown", 10000);
        punishmentDamage = c.getDouble(p + "punishment.damage", 5.0);
        try {
            tagMode = TagMode.valueOf(c.getString(p + "triggers.tag-mode", "both").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            tagMode = TagMode.BOTH;
        }
        startBelowHealth = c.getDouble(p + "triggers.start-below-health", 0);
        pearlSetOnLand = c.getBoolean(p + "triggers.enderpearl.set-combat-on-land", true);
        pearlOnlyIfInCombat = c.getBoolean(p + "triggers.enderpearl.only-if-already-in-combat", false);
        untagOnKill = c.getBoolean(p + "triggers.untag-on-kill", true);
        explosionsTag = c.getBoolean(p + "triggers.explosions.set-combat-on-explosion", true);
        ignoredWorlds = lower(c.getStringList(p + "ignored-worlds"));
        commandWhitelist = "whitelist".equalsIgnoreCase(c.getString(p + "command-restrictions.mode", "blacklist"));
        commands = lower(c.getStringList(p + "command-restrictions.commands"));
        tagEffects = readEffects(c.getMapList(p + "tag-effects"));
        dummyEnabled = c.getBoolean("dummy.enabled", true);
        dummyNameFormat = c.getString("dummy.name-format", "{player}");
        useAxGraves = c.getBoolean("dummy.use-axgraves", true);
        respectKeepInventory = c.getBoolean("dummy.respect-keep-inventory", true);
    }

    private static List<String> lower(List<String> in) {
        List<String> out = new ArrayList<>();
        for (String s : in) out.add(s.toLowerCase(Locale.ROOT));
        return out;
    }

    private List<PotionEffect> readEffects(List<Map<?, ?>> raw) {
        List<PotionEffect> out = new ArrayList<>();
        for (Map<?, ?> m : raw) {
            Object t = m.get("type");
            if (t == null) continue;
            PotionEffectType type = Registry.EFFECT.get(NamespacedKey.minecraft(t.toString().toLowerCase(Locale.ROOT)));
            if (type == null) {
                plugin.getLogger().warning("Unknown tag effect type: " + t);
                continue;
            }
            int dur = m.get("duration-seconds") instanceof Number n ? n.intValue() : durationSeconds;
            int amp = m.get("amplifier") instanceof Number n ? n.intValue() : 0;
            boolean particles = !(m.get("show-particles") instanceof Boolean b) || b;
            out.add(new PotionEffect(type, dur * 20, amp, false, particles));
        }
        return out;
    }
}
