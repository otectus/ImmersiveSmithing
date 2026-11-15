package com.otectus.immersivesmithing.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.otectus.immersivesmithing.ImmersiveSmithing;
import com.otectus.immersivesmithing.config.ServerConfigSource;
import com.otectus.immersivesmithing.material.MaterialFamily;
import com.otectus.immersivesmithing.material.MaterialRegistry;
import com.otectus.immersivesmithing.material.MaterialResolver;
import com.otectus.immersivesmithing.material.RecyclingValue;
import com.otectus.immersivesmithing.minigame.SessionManager;
import com.otectus.immersivesmithing.minigame.SmithingSession;
import com.otectus.immersivesmithing.quality.QualityCalculator;
import com.otectus.immersivesmithing.quality.QualityData;
import com.otectus.immersivesmithing.recipe.AuxiliaryIngredient;
import com.otectus.immersivesmithing.recipe.CompatibilityReport;
import com.otectus.immersivesmithing.recipe.RecipeDecisions;
import com.otectus.immersivesmithing.recipe.SmithingData;
import com.otectus.immersivesmithing.recipe.SmithingRecipe;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.commands.ReloadCommand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** {@code /immersivesmithing reload|config|material|recipe|quality|report|catalog|session} for administrators and pack authors. */
public final class SmithingCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(Commands.literal("immersivesmithing")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("reload").executes(SmithingCommands::reload))
                .then(Commands.literal("config").executes(SmithingCommands::config))
                .then(Commands.literal("material").then(Commands.argument("item", ItemArgument.item(context))
                        .executes(c -> material(c, ItemArgument.getItem(c, "item").getItem()))))
                .then(Commands.literal("recipe").then(Commands.argument("item", ItemArgument.item(context))
                        .executes(c -> recipe(c, ItemArgument.getItem(c, "item").getItem()))))
                .then(Commands.literal("quality").executes(SmithingCommands::quality))
                .then(Commands.literal("report").executes(SmithingCommands::report))
                .then(Commands.literal("process").executes(SmithingCommands::process))
                .then(Commands.literal("catalog").then(Commands.argument("modid", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                                net.minecraftforge.fml.ModList.get().getMods().stream().map(m -> m.getModId()), builder))
                        .executes(SmithingCommands::catalog)))
                .then(Commands.literal("session").executes(SmithingCommands::sessions)));
    }

    /**
     * Re-reads the server config, then a full datapack reload: tags, materials, patterns, recipes and recipe suppression
     * are all rebuilt with the new values, and remote clients receive them. A config that cannot be read aborts before
     * anything changes; the last good one stays in use.
     */
    private static int reload(CommandContext<CommandSourceStack> c) {
        CommandSourceStack source = c.getSource();
        ServerConfigSource.ReloadResult config = ServerConfigSource.reload();
        if (!config.ok()) {
            source.sendFailure(Component.translatable("command.immersive_smithing.reload.config_failed", config.error()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.immersive_smithing.reload"), true);
        ReloadCommand.reloadPacks(source.getServer().getPackRepository().getSelectedIds(), source);
        return 1;
    }

    /** Which file the server config comes from, and what a restart would change. */
    private static int config(CommandContext<CommandSourceStack> c) {
        ServerConfigSource.Status status = ServerConfigSource.status();
        send(c, Component.translatable("command.immersive_smithing.config.source", status.mode().name(), String.valueOf(status.activePath())));
        send(c, Component.translatable(status.sharedFileExists() ? "command.immersive_smithing.config.shared_present"
                : "command.immersive_smithing.config.shared_absent", status.sharedPath().toString()));
        if (status.lastError() != null) {
            c.getSource().sendFailure(Component.translatable("command.immersive_smithing.config.error", status.lastError()));
        }
        if (status.mode() == ServerConfigSource.Mode.WORLD && status.sharedFileExists()) {
            send(c, Component.translatable("command.immersive_smithing.config.restart_for_shared"));
        } else if (status.mode() == ServerConfigSource.Mode.SHARED && !status.sharedFileExists()) {
            send(c, Component.translatable("command.immersive_smithing.config.restart_for_world"));
        }
        return 1;
    }

    private static int material(CommandContext<CommandSourceStack> c, Item item) {
        SmithingData data = SmithingData.current();
        MaterialRegistry.Entry entry = data.materials().lookup(item);
        ItemStack stack = new ItemStack(item);
        if (entry != null) {
            Component family = data.materials().family(entry.family()).map(MaterialFamily::displayName).orElse(Component.literal("?"));
            send(c, Component.translatable("command.immersive_smithing.material.source", stack.getHoverName(), family,
                    entry.family().toString(), entry.units(), entry.rule()));
            return 1;
        }
        if (data.materials().isAmbiguous(item)) {
            send(c, Component.translatable("command.immersive_smithing.material.ambiguous", stack.getHoverName()));
            return 0;
        }
        Optional<RecyclingValue> value = MaterialResolver.recyclingValue(stack);
        if (value.isPresent()) {
            send(c, Component.translatable("command.immersive_smithing.material.recyclable", stack.getHoverName(),
                    value.get().family().toString(), MaterialResolver.recycledUnits(value.get().units()), value.get().units(), value.get().rule()));
            return 1;
        }
        send(c, Component.translatable("command.immersive_smithing.material.none", stack.getHoverName()));
        return 0;
    }

    private static int recipe(CommandContext<CommandSourceStack> c, Item item) {
        SmithingData data = SmithingData.current();
        ItemStack stack = new ItemStack(item);
        List<SmithingRecipe> recipes = data.recipesProducing(item);
        if (recipes.isEmpty()) {
            send(c, Component.translatable("command.immersive_smithing.recipe.none", stack.getHoverName()));
        }
        for (SmithingRecipe r : recipes) {
            send(c, Component.translatable(r.isAuto() ? "command.immersive_smithing.recipe.auto" : "command.immersive_smithing.recipe.explicit",
                    r.getId().toString(), r.family().toString(), r.metalUnits(),
                    r.forgePattern().toString(), r.anvilPattern().toString()));
            boolean upgrade = r.auxiliary().stream().anyMatch(AuxiliaryIngredient::consumesEquipment);
            send(c, Component.translatable("command.immersive_smithing.recipe.operation",
                    Component.translatable(upgrade ? "command.immersive_smithing.recipe.upgrade" : "command.immersive_smithing.recipe.new"), r.recoverableUnits()));
            for (AuxiliaryIngredient aux : r.auxiliary()) {
                var alternatives = Component.empty();
                for (ItemStack option : aux.ingredient().getItems()) {
                    if (!alternatives.getSiblings().isEmpty()) alternatives.append(" / ");
                    alternatives.append(option.getHoverName());
                }
                send(c, Component.translatable("command.immersive_smithing.recipe.auxiliary", aux.count(), alternatives));
            }
            if (r.sourceRecipe() != null) {
                send(c, Component.translatable("command.immersive_smithing.recipe.source", r.sourceRecipe().toString()));
            }
        }
        for (Map.Entry<ResourceLocation, Item> suppressed : data.suppressedRecipes().entrySet()) {
            if (suppressed.getValue() == item) {
                send(c, Component.translatable("command.immersive_smithing.recipe.suppressed", suppressed.getKey().toString()));
            }
        }
        // Every recipe the rebuild considered for the item: what came of it, why, and the option that decided.
        List<RecipeDecisions.Decision> decisions = data.decisionsFor(item);
        if (!decisions.isEmpty()) {
            send(c, Component.translatable("command.immersive_smithing.recipe.decisions", stack.getHoverName()));
            for (RecipeDecisions.Decision d : decisions) {
                send(c, Component.translatable("command.immersive_smithing.recipe.decision", data.report().decisions().describe(d)));
            }
        } else if (recipes.isEmpty()) {
            send(c, Component.translatable("command.immersive_smithing.recipe.no_decisions", stack.getHoverName()));
        }
        return recipes.size();
    }

    private static int quality(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        ItemStack held = player.getMainHandItem();
        Optional<QualityData> data = QualityData.get(held);
        if (data.isEmpty()) {
            send(c, Component.translatable("command.immersive_smithing.quality.none", held.getHoverName()));
            return 0;
        }
        QualityData q = data.get();
        send(c, Component.translatable("command.immersive_smithing.quality.info", held.getHoverName(), q.quality().displayName(),
                q.forgeScore(), q.anvilScore(), q.faulty(),
                String.format("%.3f", QualityCalculator.durabilityMultiplier(q)),
                String.format("%.3f", QualityCalculator.efficacyMultiplier(q))));
        return 1;
    }

    private static int report(CommandContext<CommandSourceStack> c) {
        CompatibilityReport report = SmithingData.current().report();
        Path dir = c.getSource().getServer().getServerDirectory().toPath().resolve("logs");
        try {
            Path file = report.write(dir);
            report.lines().forEach(ImmersiveSmithing.LOGGER::info);
            send(c, Component.translatable("command.immersive_smithing.report", report.summary(), file.toString()));
            return 1;
        } catch (IOException e) {
            c.getSource().sendFailure(Component.translatable("command.immersive_smithing.report.failed", e.getMessage()));
            return 0;
        }
    }

    /** Recipe-to-process coverage of the running server. */
    private static int process(CommandContext<CommandSourceStack> c) {
        Path dir = c.getSource().getServer().getServerDirectory().toPath().resolve("logs");
        try {
            Path file = com.otectus.immersivesmithing.work.ProcessReport.write(dir);
            send(c, Component.translatable("command.immersive_smithing.process.written", file.toString(),
                    com.otectus.immersivesmithing.work.ProcessReport.rows().size()));
            return 1;
        } catch (IOException e) {
            c.getSource().sendFailure(Component.translatable("command.immersive_smithing.report.failed", e.getMessage()));
            return 0;
        }
    }

    private static int catalog(CommandContext<CommandSourceStack> c) {
        String modId = com.mojang.brigadier.arguments.StringArgumentType.getString(c, "modid");
        Path dir = c.getSource().getServer().getServerDirectory().toPath().resolve("logs");
        try {
            var catalog = com.otectus.immersivesmithing.recipe.WeaponCatalog.build(modId, c.getSource().getServer().registryAccess());
            Path file = com.otectus.immersivesmithing.recipe.WeaponCatalog.write(dir, modId, c.getSource().getServer().registryAccess());
            send(c, Component.translatable("command.immersive_smithing.catalog", modId, catalog.getAsJsonArray("items").size(), file.toString()));
            return 1;
        } catch (IOException e) {
            c.getSource().sendFailure(Component.translatable("command.immersive_smithing.report.failed", e.getMessage()));
            return 0;
        }
    }

    /** Open pattern books and every smith's hold on a station (the work itself is in the stations, not here). */
    private static int sessions(CommandContext<CommandSourceStack> c) {
        var sessions = SessionManager.all();
        var leases = com.otectus.immersivesmithing.work.WorkLeases.all();
        if (sessions.isEmpty() && leases.isEmpty()) {
            send(c, Component.translatable("command.immersive_smithing.session.none"));
            return 0;
        }
        for (SmithingSession s : sessions) {
            send(c, Component.translatable("command.immersive_smithing.session.entry", name(c, s.playerId), "pattern_book",
                    s.pos.toShortString(), s.dimension.location().toString(), "-"));
        }
        for (var lease : leases) {
            send(c, Component.translatable("command.immersive_smithing.session.entry", name(c, lease.player),
                    lease.kind.name().toLowerCase(java.util.Locale.ROOT), lease.station.pos().toShortString(),
                    lease.station.dimension().location().toString(), lease.focused() ? "focused" : "world view"));
        }
        return sessions.size() + leases.size();
    }

    private static String name(CommandContext<CommandSourceStack> c, java.util.UUID id) {
        ServerPlayer player = c.getSource().getServer().getPlayerList().getPlayer(id);
        return player != null ? player.getGameProfile().getName() : id.toString();
    }

    private static void send(CommandContext<CommandSourceStack> c, Component message) {
        c.getSource().sendSuccess(() -> message.copy().withStyle(ChatFormatting.GRAY), false);
    }

    private SmithingCommands() {}
}
