package io.github.notjoja.combatlogdummies;

import io.github.notjoja.combatlogdummies.combat.CombatManager;
import io.github.notjoja.combatlogdummies.command.CombatLogCommand;
import io.github.notjoja.combatlogdummies.config.Messages;
import io.github.notjoja.combatlogdummies.config.PluginConfig;
import io.github.notjoja.combatlogdummies.dummy.DummyListener;
import io.github.notjoja.combatlogdummies.dummy.DummyManager;
import io.github.notjoja.combatlogdummies.listener.CombatRestrictionListener;
import io.github.notjoja.combatlogdummies.listener.CombatTagListener;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;

public final class CombatLogDummies extends JavaPlugin {

    private PluginConfig pluginConfig;
    private Messages messages;
    private CombatManager combat;
    private DummyManager dummies;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("messages.yml", false);
        pluginConfig = new PluginConfig(this);
        messages = new Messages(this);
        combat = new CombatManager(this);
        dummies = new DummyManager(this);

        getServer().getPluginManager().registerEvents(new CombatTagListener(this), this);
        getServer().getPluginManager().registerEvents(new CombatRestrictionListener(this), this);
        getServer().getPluginManager().registerEvents(new DummyListener(this), this);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                event -> event.registrar().register(CombatLogCommand.build(this), "CombatLogDummies admin command"));

        combat.start();
        dummies.start();
    }

    @Override
    public void onDisable() {
        if (combat != null) combat.shutdown();
        if (dummies != null) dummies.save();
    }

    public void reload() {
        reloadConfig();
        pluginConfig.load();
        messages.load();
    }

    public PluginConfig settings() { return pluginConfig; }
    public Messages messages() { return messages; }
    public CombatManager combat() { return combat; }
    public DummyManager dummies() { return dummies; }
}
