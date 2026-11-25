package io.github.notjoja.combatlogdummies.listener;

import io.github.notjoja.combatlogdummies.CombatLogDummies;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemMendEvent;
import org.bukkit.event.player.PlayerRiptideEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

public final class CombatRestrictionListener implements Listener {
    private final CombatLogDummies plugin;

    public CombatRestrictionListener(CombatLogDummies plugin) {
        this.plugin = plugin;
    }

    private boolean restricted(Player p) {
        return plugin.combat().isInCombat(p) && !p.hasPermission("combatlog.bypass");
    }

    private void punish(Player p, String messagePath) {
        p.sendMessage(plugin.messages().get("combat-log.messages." + messagePath));
        double dmg = plugin.settings().punishmentDamage;
        if (dmg > 0) p.damage(dmg);
    }

    @EventHandler(ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent e) {
        if (!(e.getEntity() instanceof Player p) || !e.isGliding()) return;
        if (!plugin.settings().elytraDisabled || !restricted(p)) return;
        e.setCancelled(true);
        p.setGliding(false);
        punish(p, "elytra-use-denied");
    }

    @EventHandler(ignoreCancelled = true)
    public void onMend(PlayerItemMendEvent e) {
        if (plugin.settings().mendingDisabled && restricted(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        Player p = e.getPlayer();
        if (!plugin.settings().teleportDisabled || !restricted(p)) return;
        switch (e.getCause()) {
            case ENDER_PEARL -> {
                if (plugin.settings().allowEnderpearl) return;
            }
            case COMMAND, PLUGIN, SPECTATE -> { }
            default -> {
                return;
            }
        }
        e.setCancelled(true);
        p.sendMessage(plugin.messages().get("combat-log.messages.teleporting-denied"));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRiptideUse(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!plugin.settings().riptideStop) return;
        Player p = e.getPlayer();
        ItemStack item = e.getItem();
        if (item == null || item.getType() != Material.TRIDENT || !item.containsEnchantment(Enchantment.RIPTIDE)) return;
        if (!restricted(p)) return;
        e.setUseItemInHand(Event.Result.DENY);
        e.setCancelled(true);
        punish(p, "riptide-denied");
    }

    @EventHandler
    public void onRiptide(PlayerRiptideEvent e) {
        Player p = e.getPlayer();
        long cd = plugin.settings().riptideCooldownMs;
        if (cd > 0 && restricted(p)) {
            p.setCooldown(e.getItem(), (int) Math.max(1, cd / 50));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (!restricted(p)) return;
        String msg = e.getMessage().substring(1).trim().toLowerCase(Locale.ROOT);
        int space = msg.indexOf(' ');
        String label = space == -1 ? msg : msg.substring(0, space);
        int colon = label.indexOf(':');
        if (colon != -1) {
            msg = msg.substring(colon + 1);
        }
        boolean listed = false;
        for (String c : plugin.settings().commands) {
            if (msg.equals(c) || msg.startsWith(c + " ")) {
                listed = true;
                break;
            }
        }
        boolean block = plugin.settings().commandWhitelist ? !listed : listed;
        if (block) {
            e.setCancelled(true);
            p.sendMessage(plugin.messages().get("combat-log.messages.blocked-command"));
        }
    }
}
