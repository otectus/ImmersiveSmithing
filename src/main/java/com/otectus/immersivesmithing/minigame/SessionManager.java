package com.otectus.immersivesmithing.minigame;

import com.otectus.immersivesmithing.api.ImmersiveSmithingAPI;
import com.otectus.immersivesmithing.blockentity.SmithsForgeBlockEntity;
import com.otectus.immersivesmithing.config.ServerConfig;
import com.otectus.immersivesmithing.item.SmithingTier;
import com.otectus.immersivesmithing.item.SmithingToolItem;
import com.otectus.immersivesmithing.item.SmithingTongsItem;
import com.otectus.immersivesmithing.material.CastOutputs;
import com.otectus.immersivesmithing.material.MaterialFamily;
import com.otectus.immersivesmithing.material.MaterialUnits;
import com.otectus.immersivesmithing.network.ModNetwork;
import com.otectus.immersivesmithing.network.packet.CastResultPacket;
import com.otectus.immersivesmithing.network.packet.CloseSessionPacket;
import com.otectus.immersivesmithing.network.packet.OpenForgeScreenPacket;
import com.otectus.immersivesmithing.recipe.AuxInventory;
import com.otectus.immersivesmithing.recipe.ForgeRecipeSelection;
import com.otectus.immersivesmithing.recipe.ResultAssembler;
import com.otectus.immersivesmithing.recipe.SmithingData;
import com.otectus.immersivesmithing.recipe.SmithingRecipe;
import com.otectus.immersivesmithing.registry.ModSounds;
import com.otectus.immersivesmithing.util.Feedback;
import com.otectus.immersivesmithing.util.InventoryRoom;
import com.otectus.immersivesmithing.util.StationEffects;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Owns the forge's pattern-book sessions: the server sends the recipes of the forge's metal (dimmed with a reason when they
 * cannot be made now) and what the metal can be cast into, and validates every reply. Choosing a pattern only pins a plan
 * beside the billet tray; nothing is reserved until the smith commits at the tray ({@link com.otectus.immersivesmithing.work.ForgeActions#commit}).
 * Casting stays a single validated server operation here. The hands-on forge, anvil, trough and grindstone work is in
 * {@code work/}; there are no minigame sessions any more.
 */
public final class SessionManager {
    private static final Map<UUID, SmithingSession> BY_PLAYER = new HashMap<>();
    private static final Map<GlobalPos, SmithingSession> BY_STATION = new HashMap<>();
    private static final Random RANDOM = new Random();
    private static final long SELECTION_TIMEOUT_MS = 120_000L;
    private static int nextId = new Random().nextInt(1 << 24);
    private static int tickCounter;

    public static Optional<SmithingSession> byPlayer(UUID player) {
        return Optional.ofNullable(BY_PLAYER.get(player));
    }

    @Nullable
    public static SmithingSession atStation(Level level, BlockPos pos) {
        return BY_STATION.get(GlobalPos.of(level.dimension(), pos));
    }

    public static Collection<SmithingSession> all() {
        return List.copyOf(BY_PLAYER.values());
    }

    // ------------------------------------------------------------------ forge

    /**
     * Opens the pattern book whenever the ready forge has something to show: every recipe of its metal (those that cannot be
     * chosen now are sent dimmed with the reason) and what the free metal can be cast into. With neither, the smith hears why.
     */
    public static void openForge(ServerPlayer player, SmithsForgeBlockEntity forge, InteractionHand hand, SmithingTier tier) {
        if (forge.isLockedFor(player.getUUID())) {
            Feedback.fail(player, "station_in_use");
            return;
        }
        cancelExisting(player);
        int protectedSlot = ForgeRecipeSelection.protectedSlot(player, hand);
        List<ForgeRecipeSelection.Offer> offers = ForgeRecipeSelection.offers(player, forge, protectedSlot);
        List<OpenForgeScreenPacket.CastOption> casts = castOptions(player, forge);
        if (offers.isEmpty() && casts.isEmpty()) {
            Feedback.fail(player, ForgeRecipeSelection.whyNone(player, forge, protectedSlot));
            return;
        }
        ForgeSession session = new ForgeSession(nextId(), player.getUUID(), player.level().dimension(), forge.getBlockPos(),
                hand, tier, RANDOM.nextLong(), Util.getMillis());
        session.offered = offers.stream().map(o -> o.recipe().getId()).toList();
        session.castOptions = choices(casts);
        register(session);
        forge.lockForSelection(player.getUUID());

        Component familyName = forge.familyData().map(f -> f.displayName()).orElse(Component.literal("?"));
        send(player, new OpenForgeScreenPacket(session.id, forge.getBlockPos(), familyName, forge.availableUnits(),
                forge.plan(), entries(player, session, offers), casts));
        player.level().playSound(null, forge.getBlockPos(), net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.7F, 1.0F);
    }

    private static List<OpenForgeScreenPacket.Entry> entries(ServerPlayer player, ForgeSession session, List<ForgeRecipeSelection.Offer> offers) {
        List<OpenForgeScreenPacket.Entry> entries = new ArrayList<>();
        for (ForgeRecipeSelection.Offer o : offers) {
            int flags = (o.shortfall() == 0 ? OpenForgeScreenPacket.AFFORDABLE : 0) | (o.missing() == null ? OpenForgeScreenPacket.HAS_COMPONENTS : 0)
                    | (o.denial() == null ? OpenForgeScreenPacket.ALLOWED : 0);
            ItemStack preview = AuxInventory.preview(player, o.recipe().auxiliary(), ForgeRecipeSelection.protectedSlot(player, session.hand))
                    .flatMap(stacks -> ResultAssembler.assemble(o.recipe(), stacks)).orElseGet(() -> o.recipe().result().copy());
            entries.add(new OpenForgeScreenPacket.Entry(o.recipe().getId(), preview, o.units(),
                    o.recipe().auxiliary().stream().map(a -> a.displayStack()).toList(), flags, o.shortfall(), o.reason(),
                    o.recipe().auxiliary().stream().map(a -> java.util.Arrays.stream(a.ingredient().getItems())
                            .map(s -> s.copyWithCount(a.count())).toList()).toList(),
                    o.recipe().auxiliary().stream().anyMatch(com.otectus.immersivesmithing.recipe.AuxiliaryIngredient::consumesEquipment)));
        }
        return entries;
    }

    /**
     * What the forge's free metal can be cast into right now, with how many of each this smith can take (whole items of
     * metal, limited by the room in the main inventory). Empty while casting is off or the forge is not ready. Metal held by
     * a committed job is never offered.
     */
    static List<OpenForgeScreenPacket.CastOption> castOptions(ServerPlayer player, SmithsForgeBlockEntity forge) {
        if (!ServerConfig.get(ServerConfig.CASTING_ENABLED) || !forge.isReady()) return List.of();
        MaterialFamily family = forge.familyData().orElse(null);
        if (family == null) return List.of();
        List<OpenForgeScreenPacket.CastOption> out = new ArrayList<>();
        for (CastOutputs.Output o : family.casting().offered(ServerConfig.get(ServerConfig.CASTING_ALLOW_NUGGETS))) {
            ItemStack stack = new ItemStack(o.item());
            int max = Math.min(forge.availableUnits() / o.unitsEach(), InventoryRoom.room(player.getInventory(), stack));
            out.add(new OpenForgeScreenPacket.CastOption(stack, o.unitsEach(), max));
        }
        return out;
    }

    private static List<ForgeSession.CastChoice> choices(List<OpenForgeScreenPacket.CastOption> casts) {
        return casts.stream().map(c -> new ForgeSession.CastChoice(c.item().getItem(), c.unitsEach())).toList();
    }

    /**
     * Casts free molten metal back into an offered ingot or nugget: one validated server operation, no quality, experience
     * or event. The request is checked like every other input (session, player, dimension, distance, forge, a growing
     * sequence number); the output must still be what the current data makes of this metal. The forge gives exactly 9 units
     * per ingot (1 per nugget) for as many as the free metal and the main inventory allow, nothing is dropped, and what the
     * inventory could not take after all goes back into the forge. Casting the forge empty ends the session.
     */
    public static void castIngots(ServerPlayer player, int sessionId, int sequence, ResourceLocation itemId, int count) {
        ForgeSession session = session(player, sessionId, ForgeSession.class);
        if (session == null) return;
        if (sequence <= session.lastCastSequence) return; // duplicate or replayed packet
        session.lastCastSequence = sequence;
        SmithsForgeBlockEntity forge = forgeAt(player.serverLevel(), session);
        if (forge == null) {
            cancel(session, player, message("station_missing"));
            return;
        }
        session.activityMs = Util.getMillis();
        Item item = BuiltInRegistries.ITEM.containsKey(itemId) ? BuiltInRegistries.ITEM.get(itemId) : null;
        MaterialFamily family = forge.familyData().orElse(null);
        CastOutputs.Output output = item == null || family == null ? null : family.casting().find(item)
                .filter(o -> o.kind() == CastOutputs.Kind.INGOT || ServerConfig.get(ServerConfig.CASTING_ALLOW_NUGGETS))
                .filter(o -> session.castOptions.contains(new ForgeSession.CastChoice(o.item(), o.unitsEach())))
                .orElse(null);
        Component refused = null;
        if (!ServerConfig.get(ServerConfig.CASTING_ENABLED)) refused = message("cast_disabled");
        else if (!forge.isReady()) refused = message("cast_not_ready");
        else if (output == null) refused = message("cast_output_unavailable"); // changed by a reload, or never offered
        else if (count <= 0) return;
        else if (forge.availableUnits() < output.unitsEach()) {
            refused = Component.translatable("message.immersive_smithing.cast_not_enough_metal", item.getDescription(),
                    MaterialUnits.describe(output.unitsEach()), MaterialUnits.describe(forge.availableUnits()));
        }
        int room = output == null ? 0 : InventoryRoom.room(player.getInventory(), new ItemStack(item));
        if (refused == null && room <= 0) refused = message("cast_inventory_full");
        if (refused != null) {
            sendSelectionState(player, session, forge, ItemStack.EMPTY, refused, false);
            return;
        }
        int each = output.unitsEach();
        int deliver = Math.min(count, Math.min(forge.availableUnits() / each, room));
        ResourceLocation familyId = family.id();
        if (!forge.castUnits(deliver * each)) return;
        // One legal stack at a time: Inventory.add fills an empty slot up to 64 from an oversized stack, whatever the item's limit.
        int undelivered = 0;
        int max = new ItemStack(item).getMaxStackSize();
        for (int left = deliver; left > 0; left -= Math.min(left, max)) {
            ItemStack part = new ItemStack(item, Math.min(left, max));
            player.getInventory().add(part);
            undelivered += part.getCount();
        }
        if (undelivered > 0) {
            // Defensive: the room was counted over the main inventory, so this should never happen.
            forge.refundCast(undelivered * each, familyId);
            deliver -= undelivered;
        }
        if (deliver > 0) {
            ItemStack held = player.getItemInHand(session.hand);
            if (!player.getAbilities().instabuild && held.getItem() instanceof SmithingTongsItem) SmithingToolItem.wearWithoutBreaking(held);
            player.level().playSound(null, session.pos, ModSounds.FORGE_CAST.get(), SoundSource.BLOCKS, 0.9F, 1.0F);
            StationEffects.send(player.serverLevel(), session.pos, StationEffects.STEAM, 0.7F);
        }
        boolean emptied = forge.availableUnits() == 0;
        Component done = emptied ? Component.translatable("message.immersive_smithing.cast_done_empty", deliver, item.getDescription())
                : Component.translatable("message.immersive_smithing.cast_done", deliver, item.getDescription(), MaterialUnits.describe(forge.availableUnits()));
        if (emptied) {
            unregister(session);
            forge.endSession();
        }
        sendSelectionState(player, session, forge, new ItemStack(item, deliver), done, emptied);
        if (emptied) Feedback.info(player, done);
    }

    /** The selection stage as it stands now (rows, cast options, molten metal), after a cast or a refused choice. */
    private static void sendSelectionState(ServerPlayer player, ForgeSession session, SmithsForgeBlockEntity forge, ItemStack cast,
                                           Component message, boolean closed) {
        List<ForgeRecipeSelection.Offer> offers = closed ? List.of()
                : ForgeRecipeSelection.offers(player, forge, ForgeRecipeSelection.protectedSlot(player, session.hand));
        List<OpenForgeScreenPacket.CastOption> casts = closed ? List.of() : castOptions(player, forge);
        if (!closed) {
            session.offered = offers.stream().map(o -> o.recipe().getId()).toList();
            session.castOptions = choices(casts);
        }
        send(player, new CastResultPacket(session.id, session.lastCastSequence, cast, forge.availableUnits(), entries(player, session, offers), casts, message, closed));
    }

    /**
     * The smith chose a pattern in the book. It is checked again now (rules, levels and the inventory may have moved since
     * the book opened); a pattern that cannot be made now is answered with the reason and the smith stays in the book.
     * Otherwise the plan is pinned beside the tray and the book closes. Nothing is reserved or taken.
     */
    public static void selectRecipe(ServerPlayer player, int sessionId, ResourceLocation recipeId) {
        ForgeSession session = session(player, sessionId, ForgeSession.class);
        if (session == null) return;
        SmithsForgeBlockEntity forge = forgeAt(player.serverLevel(), session);
        if (forge == null) {
            cancel(session, player, message("station_missing"));
            return;
        }
        SmithingRecipe recipe = SmithingData.current().recipe(recipeId);
        if (recipe == null || !session.offered.contains(recipeId)) {
            cancel(session, player, message("recipe_unavailable"));
            return;
        }
        if (!forge.isReady() || !recipe.family().equals(forge.family())) {
            cancel(session, player, message("not_enough_metal_now"));
            return;
        }
        int protectedSlot = ForgeRecipeSelection.protectedSlot(player, session.hand);
        Optional<Component> denied = ImmersiveSmithingAPI.recipeDenial(player, recipe);
        int units = ImmersiveSmithingAPI.metalUnits(player, recipe);
        Component refusal = denied.orElse(null);
        if (refusal == null && forge.availableUnits() < units) {
            refusal = Component.translatable("screen.immersive_smithing.forge.needs_more", MaterialUnits.describe(units - forge.availableUnits()));
        }
        if (refusal == null) {
            refusal = AuxInventory.firstMissing(player, recipe.auxiliary(), protectedSlot)
                    .<Component>map(m -> Component.translatable("screen.immersive_smithing.forge.needs_component", m.count(), m.displayStack().getHoverName()))
                    .orElse(null);
        }
        if (refusal != null) {
            session.activityMs = Util.getMillis();
            sendSelectionState(player, session, forge, ItemStack.EMPTY, refusal, false);
            return;
        }
        unregister(session);
        forge.endSession();
        com.otectus.immersivesmithing.work.ForgeActions.pinPlan(player, forge, recipe);
        send(player, new CloseSessionPacket(session.id, Component.empty()));
    }

    // ------------------------------------------------------------------ cancellation and upkeep

    public static void cancelFromClient(ServerPlayer player, int sessionId) {
        SmithingSession session = BY_PLAYER.get(player.getUUID());
        if (session != null && session.id == sessionId) cancel(session, player, null);
    }

    public static void onLogout(ServerPlayer player) {
        SmithingSession session = BY_PLAYER.get(player.getUUID());
        if (session != null) cancel(session, player, null);
    }

    public static void onDeath(ServerPlayer player) {
        SmithingSession session = BY_PLAYER.get(player.getUUID());
        if (session != null) cancel(session, player, message("session_interrupted"));
    }

    /** The station block was broken or replaced. */
    public static void onStationRemoved(Level level, BlockPos pos) {
        SmithingSession session = atStation(level, pos);
        if (session == null || level.getServer() == null) return;
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(session.playerId);
        cancel(session, player, message("station_missing"));
    }

    /** The station's chunk is unloading: end the book session; the forge's state is already saved. */
    public static void onStationUnloaded(Level level, BlockPos pos) {
        SmithingSession session = atStation(level, pos);
        if (session == null) return;
        unregister(session);
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof SmithsForgeBlockEntity forge) forge.endSession();
        if (level.getServer() != null) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(session.playerId);
            if (player != null) send(player, new CloseSessionPacket(session.id, message("session_interrupted")));
        }
    }

    public static void onServerStopping(MinecraftServer server) {
        for (SmithingSession session : all()) cancel(session, server.getPlayerList().getPlayer(session.playerId), null);
        BY_PLAYER.clear();
        BY_STATION.clear();
    }

    public static void tick(MinecraftServer server) {
        if (BY_PLAYER.isEmpty()) return;
        tickCounter++;
        long now = Util.getMillis();
        for (SmithingSession session : all()) {
            ServerPlayer player = server.getPlayerList().getPlayer(session.playerId);
            if (player == null) {
                cancel(session, null, null);
                continue;
            }
            if (now - session.activityMs > SELECTION_TIMEOUT_MS) cancel(session, player, message("session_expired"));
            else if (tickCounter % 5 == 0 && !stillValid(player, session)) cancel(session, player, message("session_interrupted"));
        }
    }

    private static void cancel(SmithingSession session, @Nullable ServerPlayer player, @Nullable Component reason) {
        unregister(session);
        MinecraftServer server = player != null ? player.getServer() : net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        ServerLevel level = server != null ? server.getLevel(session.dimension) : null;
        if (level != null && level.isLoaded(session.pos) && level.getBlockEntity(session.pos) instanceof SmithsForgeBlockEntity forge) {
            forge.endSession();
        }
        if (player != null && reason != null) send(player, new CloseSessionPacket(session.id, reason));
    }

    private static boolean stillValid(ServerPlayer player, SmithingSession session) {
        if (!player.isAlive() || player.level().dimension() != session.dimension) return false;
        double max = ServerConfig.get(ServerConfig.MAX_STATION_DISTANCE);
        if (player.distanceToSqr(session.pos.getX() + 0.5, session.pos.getY() + 0.5, session.pos.getZ() + 0.5) > max * max) return false;
        return forgeAt(player.serverLevel(), session) != null;
    }

    private static void cancelExisting(ServerPlayer player) {
        SmithingSession existing = BY_PLAYER.get(player.getUUID());
        if (existing != null) cancel(existing, player, null);
    }

    @Nullable
    private static <T extends SmithingSession> T session(ServerPlayer player, int sessionId, Class<T> type) {
        SmithingSession session = BY_PLAYER.get(player.getUUID());
        if (session == null || session.id != sessionId || !type.isInstance(session)) return null;
        if (!player.level().dimension().equals(session.dimension)) return null;
        double max = ServerConfig.get(ServerConfig.MAX_STATION_DISTANCE);
        if (player.distanceToSqr(session.pos.getX() + 0.5, session.pos.getY() + 0.5, session.pos.getZ() + 0.5) > max * max) return null;
        return type.cast(session);
    }

    @Nullable
    private static SmithsForgeBlockEntity forgeAt(ServerLevel level, SmithingSession session) {
        if (!level.isLoaded(session.pos)) return null;
        return level.getBlockEntity(session.pos) instanceof SmithsForgeBlockEntity forge ? forge : null;
    }

    private static void register(SmithingSession session) {
        BY_PLAYER.put(session.playerId, session);
        BY_STATION.put(session.station(), session);
    }

    private static void unregister(SmithingSession session) {
        BY_PLAYER.remove(session.playerId, session);
        BY_STATION.remove(session.station(), session);
    }

    private static int nextId() {
        return ++nextId;
    }

    private static Component message(String key) {
        return Component.translatable("message.immersive_smithing." + key);
    }

    private static void send(ServerPlayer player, Object packet) {
        if (player instanceof net.minecraftforge.common.util.FakePlayer || player.connection == null) return;
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    private SessionManager() {}
}
