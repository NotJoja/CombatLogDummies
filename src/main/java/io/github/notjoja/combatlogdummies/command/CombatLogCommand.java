package io.github.notjoja.combatlogdummies.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.github.notjoja.combatlogdummies.CombatLogDummies;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

public final class CombatLogCommand {
    private CombatLogCommand() {}

    public static LiteralCommandNode<CommandSourceStack> build(CombatLogDummies plugin) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("combatlog")
                .requires(src -> src.getSender().hasPermission("combatlog.admin"))
                .executes(ctx -> {
                    ctx.getSource().getSender().sendMessage(plugin.messages().get("commands.combatlog.usage"));
                    return 1;
                });

        root.then(Commands.literal("reload").executes(ctx -> {
            plugin.reload();
            ctx.getSource().getSender().sendMessage(plugin.messages().get("commands.combatlog.reload-success"));
            return 1;
        }));

        root.then(Commands.literal("status")
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((ctx, b) -> {
                            Bukkit.getOnlinePlayers().forEach(p -> b.suggest(p.getName()));
                            return b.buildFuture();
                        })
                        .executes(ctx -> {
                            CommandSender s = ctx.getSource().getSender();
                            Player p = target(plugin, s, StringArgumentType.getString(ctx, "player"));
                            if (p == null) return 0;
                            boolean in = plugin.combat().isInCombat(p);
                            long secs = (long) Math.ceil(plugin.combat().remainingMillis(p.getUniqueId()) / 1000.0);
                            s.sendMessage(plugin.messages().get("commands.combatlog.status", Map.of(
                                    "player", p.getName(), "value", String.valueOf(in), "time", String.valueOf(secs))));
                            return 1;
                        })));

        root.then(Commands.literal("untag")
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((ctx, b) -> {
                            Bukkit.getOnlinePlayers().forEach(p -> b.suggest(p.getName()));
                            return b.buildFuture();
                        })
                        .executes(ctx -> {
                            CommandSender s = ctx.getSource().getSender();
                            Player p = target(plugin, s, StringArgumentType.getString(ctx, "player"));
                            if (p == null) return 0;
                            boolean was = plugin.combat().untag(p.getUniqueId());
                            s.sendMessage(plugin.messages().get(
                                    was ? "commands.combatlog.untag-success" : "commands.combatlog.untag-not-in-combat",
                                    Map.of("player", p.getName())));
                            return 1;
                        })));

        return root.build();
    }

    private static Player target(CombatLogDummies plugin, CommandSender sender, String name) {
        Player p = Bukkit.getPlayerExact(name);
        if (p == null) {
            sender.sendMessage(plugin.messages().get("commands.combatlog.player-not-found", Map.of("player", name)));
        }
        return p;
    }
}
