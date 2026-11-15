package com.otectus.immersivesmithing.event;

import com.otectus.immersivesmithing.ImmersiveSmithing;
import com.otectus.immersivesmithing.block.SmithsAnvilBlock;
import com.otectus.immersivesmithing.block.SmithsForgeBlock;
import com.otectus.immersivesmithing.block.SmithsGrindstoneBlock;
import com.otectus.immersivesmithing.block.SmithsTroughBlock;
import com.otectus.immersivesmithing.blockentity.SmithsAnvilBlockEntity;
import com.otectus.immersivesmithing.blockentity.SmithsForgeBlockEntity;
import com.otectus.immersivesmithing.command.SmithingCommands;
import com.otectus.immersivesmithing.config.ServerConfig;
import com.otectus.immersivesmithing.config.ServerConfigSource;
import com.otectus.immersivesmithing.material.MaterialDefinitionLoader;
import com.otectus.immersivesmithing.material.RecyclingOverrideLoader;
import com.otectus.immersivesmithing.quality.QualityInheritance;
import com.otectus.immersivesmithing.quality.GrindstonePreview;
import com.otectus.immersivesmithing.network.ModNetwork;
import com.otectus.immersivesmithing.network.packet.ConfigSyncPacket;
import com.otectus.immersivesmithing.network.packet.GrindstonePreviewPacket;
import com.otectus.immersivesmithing.minigame.PatternLoader;
import com.otectus.immersivesmithing.minigame.SessionManager;
import com.otectus.immersivesmithing.recipe.SmithingData;
import com.otectus.immersivesmithing.registry.ModItems;
import com.otectus.immersivesmithing.util.Feedback;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraft.world.phys.BlockHitResult;

/** Gameplay events (Forge bus). */
@Mod.EventBusSubscriber(modid = ImmersiveSmithing.MOD_ID)
public final class CommonEvents {
    private static final String GUIDE_GRANTED = "immersive_smithing_guide_granted";

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new MaterialDefinitionLoader(event.getConditionContext()));
        event.addListener(new RecyclingOverrideLoader(event.getConditionContext()));
        event.addListener(new com.otectus.immersivesmithing.compat.CoveragePolicies(event.getConditionContext()));
        event.addListener(new com.otectus.immersivesmithing.recipe.SourceRecipePolicies(event.getConditionContext()));
        event.addListener(new PatternLoader(PatternLoader.Kind.FORGE));
        event.addListener(new PatternLoader(PatternLoader.Kind.ANVIL));
        event.addListener(new com.otectus.immersivesmithing.work.ProcessProfiles.Loader(event.getConditionContext()));
    }

    /** Forge has bound the world's own server config by now; a shared file in config/ replaces it for this run. */
    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        ServerConfigSource.onServerAboutToStart(event.getServer());
    }

    /** First build, once tags are bound and before any player can join. */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        SmithingData.rebuild(event.getServer());
    }

    /**
     * After /reload the new tags are bound and this fires before the recipe packet is sent, so rebuilding here
     * means clients receive the updated (filtered and generated) recipe list. Remote clients get the effective server
     * config with it (all of them after a reload, the joining one on login), ahead of the tag and recipe packets.
     */
    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        MinecraftServer server = event.getPlayerList().getServer();
        ServerPlayer joining = event.getPlayer();
        if (joining == null || SmithingData.isDirty()) SmithingData.rebuild(server);
        byte[] toml = ServerConfigSource.effectiveToml();
        if (toml == null) return;
        for (ServerPlayer player : joining != null ? java.util.List.of(joining) : event.getPlayerList().getPlayers()) {
            if (isRemote(player)) ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ConfigSyncPacket(toml));
        }
    }

    /** A connection that does not share this server's JVM (a dedicated server's players, LAN guests). */
    private static boolean isRemote(ServerPlayer player) {
        return !(player instanceof FakePlayer) && player.connection != null && !player.connection.connection.isMemoryConnection();
    }

    /** A shared config that could not be read: the host of an integrated server is told, in red, which file and why. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || ServerConfigSource.mode() != ServerConfigSource.Mode.SHARED_INVALID) return;
        MinecraftServer server = player.getServer();
        if (server == null || !server.isSingleplayerOwner(player.getGameProfile())) return;
        ServerConfigSource.Status status = ServerConfigSource.status();
        player.sendSystemMessage(Component.translatable("message.immersive_smithing.config_shared_invalid",
                String.valueOf(status.lastError()), String.valueOf(status.activePath())).withStyle(ChatFormatting.RED));
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        SessionManager.onServerStopping(event.getServer());
        com.otectus.immersivesmithing.work.WorkLeases.onServerStopping();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        SmithingData.reset();
        ServerConfigSource.reset();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        SessionManager.tick(event.getServer());
        com.otectus.immersivesmithing.work.WorkLeases.tick(event.getServer());
    }

    /** Jade 11 cannot request data for a block without a block entity. Send the looked-at grindstone's quote ourselves. */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)
                || player instanceof FakePlayer || player.tickCount % 10 != 0) return;
        if (player.pick(ServerConfig.get(ServerConfig.MAX_STATION_DISTANCE), 1F, false) instanceof BlockHitResult hit) {
            GrindstonePreview.forPlayer(player, hit.getBlockPos()).ifPresent(preview ->
                    ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new GrindstonePreviewPacket(preview)));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SessionManager.onLogout(player);
            com.otectus.immersivesmithing.work.WorkLeases.onLogout(player);
            com.otectus.immersivesmithing.work.ForgeActions.onLogout(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SessionManager.onDeath(player);
            com.otectus.immersivesmithing.work.WorkLeases.onDeath(player);
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        SmithingCommands.register(event.getDispatcher(), event.getBuildContext());
    }

    /**
     * Vanilla skips a block's use() when the player sneaks with an item in hand. Station interactions such as
     * "sneak with tongs to melt a workpiece back" or "sneak with ingots to insert the stack" need it, so allow
     * it for non-block items. Sneaking with a block item still places blocks as usual.
     */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (!player.isSecondaryUseActive()) return;
        Block block = event.getLevel().getBlockState(event.getPos()).getBlock();
        if (!(block instanceof SmithsForgeBlock || block instanceof SmithsAnvilBlock || block instanceof SmithsTroughBlock
                || block instanceof SmithsGrindstoneBlock)) return;
        ItemStack held = event.getItemStack();
        if (!held.isEmpty() && !(held.getItem() instanceof BlockItem)) event.setUseBlock(Event.Result.ALLOW);
    }

    /**
     * A forge holding molten metal or a committed job cannot be broken in survival; a station another smith is working at is
     * protected while their lease lasts.
     */
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (player == null || player.getAbilities().instabuild) return;
        var be = event.getLevel().getBlockEntity(event.getPos());
        if (be instanceof SmithsForgeBlockEntity forge && (forge.moltenUnits() > 0 || forge.isLocked() || forge.job() != null)) {
            event.setCanceled(true);
            Feedback.fail(player, "empty_forge_before_breaking");
        } else if (be != null && com.otectus.immersivesmithing.work.WorkLeases.heldByOther(player.level(), event.getPos(), player.getUUID())) {
            event.setCanceled(true);
            Feedback.fail(player, "station_in_use");
        }
    }

    /**
     * Quality inheritance when a crafted result is taken. The crafting preview normally carries it already
     * ({@code CraftingMenuMixin}); this covers crafting screens that replace the vanilla grid. See {@link QualityInheritance}.
     */
    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (event.getEntity().level().isClientSide) return;
        QualityInheritance.inherit(event.getCrafting(), event.getInventory());
    }

    /** The first smithing advancement hands out the Smithing Guide, once per player. */
    @SubscribeEvent
    public static void onAdvancement(AdvancementEvent.AdvancementEarnEvent event) {
        if (!event.getAdvancement().getId().equals(ImmersiveSmithing.id("the_trade"))) return;
        if (!(event.getEntity() instanceof ServerPlayer player) || !ServerConfig.get(ServerConfig.GRANT_GUIDE_BOOK_AUTOMATICALLY)) return;
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (persisted.getBoolean(GUIDE_GRANTED)) return;
        persisted.putBoolean(GUIDE_GRANTED, true);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        ItemHandlerHelper.giveItemToPlayer(player, new ItemStack(ModItems.SMITHING_GUIDE.get()));
        Feedback.info(player, "guide_granted");
    }

    private CommonEvents() {}
}
