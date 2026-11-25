package io.github.notjoja.combatlogdummies.config;

import io.github.notjoja.combatlogdummies.CombatLogDummies;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Map;

public final class Messages {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private final CombatLogDummies plugin;
    private FileConfiguration cfg;

    public Messages(CombatLogDummies plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        cfg = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "messages.yml"));
    }

    public String raw(String path) {
        String s = cfg.getString(path);
        return s == null ? path : s;
    }

    public Component get(String path, Map<String, String> placeholders) {
        String s = raw(path);
        for (var e : placeholders.entrySet()) {
            s = s.replace("{" + e.getKey() + "}", e.getValue());
        }
        return MM.deserialize(s);
    }

    public Component get(String path) {
        return get(path, Map.of());
    }
}
