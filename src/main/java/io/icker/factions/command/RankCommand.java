package io.icker.factions.command;

import java.util.UUID;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;

import io.icker.factions.api.persistents.Faction;
import io.icker.factions.api.persistents.User;
import io.icker.factions.util.Command;
import io.icker.factions.util.Message;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Formatting;

public class RankCommand implements Command {
    private int promote(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");

        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();

        if (target.getName().getString().equals(player.getName().getString())) {
            new Message("You cannot promote yourself").format(Formatting.RED).send(player, false);

            return 0;
        }

        Faction faction = User.get(player.getName().getString()).getFaction();

        for (User users : faction.getUsers())
            if (users.getName().equals(target.getName().getString())) {

                switch (users.getRank()) {
                    case MEMBER -> users.setRank(User.Rank.SHERIFF);
                    case SHERIFF -> users.setRank(User.Rank.COMMANDER);
                    case COMMANDER -> users.setRank(User.Rank.LEADER);
                    case LEADER -> {
                        new Message("You cannot promote a Leader to Owner").format(Formatting.RED).send(player, false);
                        return 0;
                    }
                    case OWNER -> {
                        new Message("You cannot promote the Owner").format(Formatting.RED).send(player, false);
                        return 0;
                    }
                }

                context.getSource().getServer().getPlayerManager().sendCommandTree(target);

                new Message("Promoted " + target.getName().getString() + " to " + User.get(target.getName().getString()).getRankName())
                    .prependFaction(faction)
                    .send(player, false);
                
                return 1;
            }

        new Message(target.getName().getString() + " is not in your faction").format(Formatting.RED).send(player, false);
        return 0;
    }

    private int demote(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");

        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();

        if (target.getUuid().equals(player.getUuid())) {
            new Message("You cannot demote yourself").format(Formatting.RED).send(player, false);
            return 0;
        }

        Faction faction = User.get(player.getName().getString()).getFaction();

        for (User user : faction.getUsers())
            if (user.getName().equals(target.getName().getString())) {

                switch (user.getRank()) {
                    case MEMBER -> {
                        new Message("You cannot demote a Member").format(Formatting.RED).send(player, false);
                        return 0;
                    }
                    case SHERIFF -> user.setRank(User.Rank.MEMBER);
                    case COMMANDER -> {
                        if(User.get(player.getName().getString()).getRank() == User.Rank.COMMANDER){
                            new Message("You cannot demote your comrade!").format(Formatting.RED).send(player, false);
                            return 0;
                        }
                    }
                    case LEADER -> {
                        if (User.get(player.getName().getString()).getRank() == User.Rank.LEADER) {
                            new Message("You cannot demote a fellow Co-Owner").format(Formatting.RED).send(player, false);
                            return 0;
                        }

                        user.setRank(User.Rank.COMMANDER);
                    }
                    case OWNER -> {
                        new Message("You cannot demote the Owner").format(Formatting.RED).send(player, false);
                        return 0;
                    }
                }

                context.getSource().getServer().getPlayerManager().sendCommandTree(target);

                new Message("Demoted " + target.getName().getString() + " to " + User.get(target.getName().getString()).getRankName())
                    .prependFaction(faction)
                    .send(player, false);
                
                return 1;
            }

        new Message(target.getName().getString() + " is not in your faction").format(Formatting.RED).send(player, false);
        return 0;
    }

    private int transfer(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");

        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();

        if (target.getUuid().equals(player.getUuid())) {
            new Message("You cannot transfer ownership to yourself").format(Formatting.RED).send(player, false);

            return 0;
        }

        User targetUser = User.get(target.getName().getString());
        UUID targetFaction = targetUser.isInFaction() ? targetUser.getFaction().getID() : null;
        if (User.get(player.getName().getString()).getFaction().getID().equals(targetFaction)) {
            targetUser.setRank(User.Rank.OWNER);
            User.get(player.getName().getString()).setRank(User.Rank.LEADER);

            context.getSource().getServer().getPlayerManager().sendCommandTree(player);
            context.getSource().getServer().getPlayerManager().sendCommandTree(target);

            new Message("Transferred ownership to " + target.getName().getString())
                .prependFaction(Faction.get(targetFaction))
                .send(player, false);

            return 1;
        }

        new Message(target.getName().getString() + " is not in your faction").format(Formatting.RED).send(player, false);
        return 0;
    }

    public LiteralCommandNode<ServerCommandSource> getNode() {
        return CommandManager
            .literal("rank")
            .requires(Requires.isLeader())
            .then(
                CommandManager
                .literal("promote")
                .requires(Requires.hasPerms("factions.rank.promote", 0))
                .then(
                    CommandManager.argument("player", EntityArgumentType.player())
                    .executes(this::promote)
                )
            )
            .then(
                CommandManager
                .literal("demote")
                .requires(Requires.hasPerms("factions.rank.demote", 0).and(Requires.isOwner()))
                .then(
                    CommandManager.argument("player", EntityArgumentType.player())
                    .executes(this::demote)
                )
            )
            .then(
                CommandManager
                .literal("transfer")
                .requires(Requires.multiple(Requires.hasPerms("factions.rank.transfer", 0), Requires.isOwner()))
                .then(
                    CommandManager.argument("player", EntityArgumentType.player())
                    .executes(this::transfer)
                )
            )
            .build();
    }
}
