package com.otectus.immersivesmithing.packtest;

import com.google.gson.GsonBuilder;
import com.otectus.immersivesmithing.ImmersiveSmithing;
import com.otectus.immersivesmithing.network.ModNetwork;
import com.otectus.immersivesmithing.material.MaterialResolver;
import com.otectus.immersivesmithing.block.SmithsAnvilBlock;
import com.otectus.immersivesmithing.block.SmithsForgeBlock;
import com.otectus.immersivesmithing.block.SmithsGrindstoneBlock;
import com.otectus.immersivesmithing.block.SmithsTroughBlock;
import com.otectus.immersivesmithing.blockentity.SmithsAnvilBlockEntity;
import com.otectus.immersivesmithing.blockentity.SmithsForgeBlockEntity;
import com.otectus.immersivesmithing.blockentity.SmithsTroughBlockEntity;
import com.otectus.immersivesmithing.client.screen.PatternBookScreen;
import com.otectus.immersivesmithing.client.work.FocusController;
import com.otectus.immersivesmithing.client.work.WorkClient;
import com.otectus.immersivesmithing.client.screen.GuideScreen;
import com.otectus.immersivesmithing.config.ServerConfig;
import com.otectus.immersivesmithing.registry.ModLootModifiers;
import com.otectus.immersivesmithing.recipe.SmithingRecipe;
import com.otectus.immersivesmithing.recipe.AuxiliaryIngredient;
import com.otectus.immersivesmithing.client.screen.MakersMarkScreen;
import com.otectus.immersivesmithing.quality.MakersMark;
import com.otectus.immersivesmithing.config.ClientConfig;
import com.otectus.immersivesmithing.item.SmithingTier;
import com.otectus.immersivesmithing.network.packet.OpenForgeScreenPacket;
import com.otectus.immersivesmithing.work.AnvilActions;
import com.otectus.immersivesmithing.work.ForgeActions;
import com.otectus.immersivesmithing.work.ForgeJob;
import com.otectus.immersivesmithing.work.WorkAction;
import com.otectus.immersivesmithing.work.WorkLeases;
import com.otectus.immersivesmithing.quality.QualityData;
import com.otectus.immersivesmithing.recipe.SmithingData;
import com.otectus.immersivesmithing.registry.ModBlocks;
import com.otectus.immersivesmithing.registry.ModItems;
import com.otectus.immersivesmithing.registry.ModTags;
import com.otectus.immersivesmithing.util.StationEffects;
import com.otectus.immersivesmithing.workpiece.WorkpieceCodec;
import com.otectus.immersivesmithing.workpiece.WorkpieceData;
import com.otectus.immersivesmithing.workpiece.WorkpieceState;
import net.minecraft.Util;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Drives a client from the title screen into a fresh flat world, checks that Immersive Smithing built its data inside
 * whatever modpack is loaded, stages every station and screen for framebuffer screenshots, writes
 * {@code packtest-result.json} into the output directory and quits.
 */
@Mod.EventBusSubscriber(modid = PackTestAgent.MOD_ID, value = Dist.CLIENT)
public final class ClientPackTest {
    private static final String OUTPUT = System.getProperty(PackTestAgent.OUTPUT_PROPERTY);
    private static final boolean CREATE_WORLD = !"false".equals(System.getProperty("immersive_smithing.packtest.world"));
    private static final Set<String> EXPECT = Set.of(System.getProperty("immersive_smithing.packtest.expect", "").split(","));
    private static final boolean MULTIPLAYER = Boolean.getBoolean("immersive_smithing.packtest.multiplayer");
    private static final String MULTIPLAYER_ROLE = System.getProperty("immersive_smithing.packtest.role", "");
    private static final String MULTIPLAYER_SERVER = System.getProperty("immersive_smithing.packtest.server", "");
    private static final Path MULTIPLAYER_SHARED = Path.of(System.getProperty("immersive_smithing.packtest.shared", "."));
    private static final boolean REQUIRE_EXACT_MINIMUM = Boolean.getBoolean("immersive_smithing.packtest.requireExactMinimum");

    private static final Map<String, Object> checks = new LinkedHashMap<>();
    private static long startMs = Util.getMillis();
    private static long titleMs = -1;
    private static long worldMs = -1;
    private static int titleTicks = -1;
    private static int worldTicks = -1;
    private static int settledAt = -1;
    private static int quietTicks;
    private static boolean worldRequested;
    private static boolean finished;
    private static int originalGuiScale;
    private static int originalWindowWidth;
    private static int originalWindowHeight;
    private static boolean originalReducedShake;
    private static boolean originalReducedFlashes;
    private static boolean originalHighContrast;
    private static boolean originalLargeTargets;
    private static boolean originalColorblind;
    private static ClientConfig.ParticleAmount originalParticles;
    private static boolean originalVsync;
    private static int originalFrameLimit;
    private static int originalRenderDistance;
    private static CompletableFuture<Void> resourceReload;
    private static int advancedPhase;
    private static int advancedAt;
    private static FrameMeasurement frameMeasurement;
    private static int multiplayerTicks;
    private static boolean multiplayerConnected;
    private static boolean remoteSwingObserved;
    private static boolean remoteHammerSwingObserved;
    private static int multiplayerJoinedAt = -1;
    private static int multiplayerHammerReadyAt = -1;
    private static boolean multiplayerReady;
    private static BlockPos galleryBase;
    private static BlockPos performanceBase;
    private static final Map<Direction, BlockPos> galleryCenters = new LinkedHashMap<>();
    private static int galleryCaptureIndex;
    private static int overlayClearedAt = -1;
    private static BlockPos pickupAnvil;
    private static volatile ItemStack pickupTongs = ItemStack.EMPTY;
    private static volatile Boolean pickupServerState;
    private static int anvilPhaseTicks = -1;
    private static int anvilSeenAt = -1;
    private static int anvilPickedAt = -1;
    private static int anvilShotAt = -1;
    /** The hands-on run: while it plays, the fixed timeline below waits. */
    private static SmithDriver smithDriver;
    private static int pausedTicks;
    private static BlockPos workForge, workAnvil, workTrough, workGrindstone;
    private static Vec3 workStand;
    private static ListTag savedInventory;
    private static ClientConfig.CameraMode originalCameraMode;
    private static boolean originalReducedMotion;
    private static boolean originalExpandedStatus;
    private static int worldViewProgress = -1;
    private static final ResourceLocation IRON_SWORD = ImmersiveSmithing.id("smithing/iron_sword");

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (OUTPUT == null || finished || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (MULTIPLAYER) {
                multiplayerStep(mc);
                return;
            }
            if (mc.player == null || mc.level == null) {
                if (mc.screen instanceof TitleScreen) {
                    if (titleMs < 0) {
                        titleMs = Util.getMillis() - startMs;
                        log("title screen reached after " + titleMs + " ms");
                    }
                    titleTicks++;
                    if (titleTicks == 1) sizeWindow(mc, 1280, 720);
                    if (titleTicks == 60) {
                        if (!CREATE_WORLD) {
                            finish(mc, true, "title only");
                        } else if (!worldRequested) {
                            worldRequested = true;
                            createWorld(mc);
                        }
                    }
                }
                return;
            }
            worldTicks++;
            if (worldMs < 0) {
                worldMs = Util.getMillis() - startMs;
                log("world joined after " + worldMs + " ms");
            }
            if (settledAt < 0 && !settle(mc)) return;
            if (ShowcaseCapture.enabled()) {
                ShowcaseCapture.tick(mc);
                return;
            }
            if (StoreScreenshots.enabled()) {
                StoreScreenshots.tick(mc);
                return;
            }
            if (StationBenchmark.enabled()) {
                StationBenchmark.tick(mc);
                return;
            }
            if (smithDriver != null && !smithDriver.done()) {
                pausedTicks++;
                if (smithDriver.tick(mc)) finishHandsOn(mc);
                return;
            }
            step(mc, worldTicks - settledAt + 40 - pausedTicks);
        } catch (Exception e) {
            ImmersiveSmithing.LOGGER.error("PACKTEST agent failed at world tick {}", worldTicks, e);
            checks.put("agent_exception", e.toString());
            if (smithDriver != null) checks.put(checks.containsKey("hands_on_flow_completed") ? "grindstone_pass" : "hands_on_flow", smithDriver.evidence);
            finish(mc, false, "agent exception");
        }
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (ShowcaseCapture.enabled() && event.phase == TickEvent.Phase.END) ShowcaseCapture.captureFrame(Minecraft.getInstance());
        if (frameMeasurement == null) return;
        if (event.phase == TickEvent.Phase.START) frameMeasurement.beginFrame(System.nanoTime());
        else frameMeasurement.endFrame(System.nanoTime());
    }

    /** Waits until the pack's first-join screens are dealt with (2 s without a screen), so staging and shots see the world. */
    private static boolean settle(Minecraft mc) {
        if (worldTicks % 5 == 0) settlePackScreen(mc);
        quietTicks = mc.screen == null ? quietTicks + 1 : 0;
        boolean quiet = worldTicks >= 40 && quietTicks >= 40;
        if (quiet || worldTicks >= 1200) {
            settledAt = worldTicks;
            checks.put("pack_screens_settled_ticks", settledAt);
            log("pack screens " + (quiet ? "settled" : "still open") + " after " + worldTicks + " ticks");
            return true;
        }
        return false;
    }

    private static void step(Minecraft mc, int t) {
        if (t > 40 && t <= 226 && t % 5 == 1) settlePackScreen(mc);
        switch (t) {
            case 40 -> {
                originalGuiScale = mc.options.guiScale().get();
                originalWindowWidth = mc.getWindow().getWidth();
                originalWindowHeight = mc.getWindow().getHeight();
                originalReducedShake = ClientConfig.REDUCED_SCREEN_SHAKE.get();
                originalReducedFlashes = ClientConfig.REDUCED_FLASHES.get();
                originalHighContrast = ClientConfig.HIGH_CONTRAST_MINIGAMES.get();
                originalLargeTargets = ClientConfig.LARGE_MINIGAME_TARGETS.get();
                originalColorblind = ClientConfig.COLORBLIND_SAFE_TARGETS.get();
                originalParticles = ClientConfig.PARTICLE_AMOUNT.get();
                originalCameraMode = ClientConfig.CAMERA_MODE.get();
                originalReducedMotion = ClientConfig.REDUCED_MOTION.get();
                originalExpandedStatus = ClientConfig.EXPANDED_STATUS.get();
                converge(ClientConfig.CAMERA_MODE, ClientConfig.CameraMode.CINEMATIC);
                originalVsync = mc.options.enableVsync().get();
                originalFrameLimit = mc.options.framerateLimit().get();
                originalRenderDistance = mc.options.renderDistance().get();
                mc.options.enableVsync().set(false);
                mc.options.framerateLimit().set(120);
                runChecks(mc);
                stageWorld(mc);
            }
            // Tongs pickup through the real block interaction; the client's own block entity must follow each change.
            case 100 -> tongsAtPickupAnvil(mc, true);
            case 110 -> checkPickupAnvil(mc, "anvil_pickup_1", false);
            case 112 -> tongsAtPickupAnvil(mc, false);
            case 122 -> checkPickupAnvil(mc, "anvil_place_back", true);
            case 124 -> tongsAtPickupAnvil(mc, true);
            case 134 -> checkPickupAnvil(mc, "anvil_pickup_2", false);
            case 196 -> mc.options.hideGui = true;
            case 200 -> shot(mc, "packtest_world");
            case 201 -> camera(mc, 1.9, -2.4, 47F, true);
            case 222 -> shot(mc, "packtest_world_top");
            case 223 -> {
                mc.options.hideGui = false;
                camera(mc, 0, 0, 20F, false);
            }
            // Tongs stab. A swing lasts 6 ticks and a shot grabs the frame before this tick, so swing + 5 is mid-thrust.
            case 226 -> shot(mc, "packtest_tongs_first_person_idle");
            case 228 -> mc.player.swing(InteractionHand.MAIN_HAND);
            case 233 -> shot(mc, "packtest_tongs_first_person");
            case 234 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                mc.player.setXRot(5F);
            }
            case 237 -> shot(mc, "packtest_tongs_third_person_idle");
            case 238 -> mc.player.swing(InteractionHand.MAIN_HAND);
            case 243 -> shot(mc, "packtest_tongs_third_person");
            // Control: an ordinary sword swing in the same view shows what the pack's own animation mods do.
            case 244 -> mc.player.getInventory().selected = 1;
            case 246 -> mc.player.swing(InteractionHand.MAIN_HAND);
            case 251 -> shot(mc, "packtest_sword_third_person");
            case 252 -> {
                mc.player.getInventory().selected = 0;
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            }
            case 254 -> mc.player.swing(InteractionHand.MAIN_HAND);
            case 259 -> {
                shot(mc, "packtest_tongs_third_person_back");
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.player.setXRot(20F);
            }
            case 260 -> mc.setScreen(new PatternBookScreen(forgeOffer()));
            case 285 -> shot(mc, "packtest_pattern_book");
            // The whole piece, played from this client through the focused views; the timeline waits until it is done.
            case 290 -> beginHandsOn(mc);
            // World view: the same station status without a camera change, and a real left-click blow.
            case 291 -> stageWorldView(mc);
            case 300 -> {
                worldViewProgress = anvilProgress(mc);
                shot(mc, "packtest_world_view_anvil");
            }
            case 302 -> KeyMapping.click(mc.options.keyAttack.getKey());
            case 320 -> {
                int after = anvilProgress(mc);
                checks.put("world_view_progress", worldViewProgress + " -> " + after);
                checks.put("world_view_strike_accepted", worldViewProgress >= 0 && after > worldViewProgress);
                converge(ClientConfig.CAMERA_MODE, ClientConfig.CameraMode.SHOULDER);
                FocusController.request(workAnvil);
            }
            case 340 -> {
                checks.put("shoulder_view_opened", FocusController.focusedOn(workAnvil));
                shot(mc, "packtest_anvil_shoulder_view");
                FocusController.exit(null, true);
                converge(ClientConfig.CAMERA_MODE, ClientConfig.CameraMode.CINEMATIC);
            }
            case 345 -> restoreInventory(mc);
            // A real grindstone pass on the staged graded sword (94/82): the hands-on sword above came out perfect.
            case 350 -> {
                smithDriver = SmithDriver.refineOnly(workGrindstone, workStand, Items.IRON_SWORD, name -> handsOnMoment(mc, name));
                log("grindstone pass started");
            }
            case 351 -> {
                checks.put("grindstone_pass", smithDriver.evidence);
                checks.put("grindstone_pass_refined", smithDriver.evidence.keySet().stream().anyMatch(k -> k.startsWith("refined_")));
                mc.setScreen(new GuideScreen(2));
            }
            case 370 -> shot(mc, "packtest_guide");
            case 375 -> mc.setScreen(new InventoryScreen(mc.player));
            case 410 -> shot(mc, "packtest_inventory");
            case 412 -> mc.setScreen(new MakersMarkScreen(0, signablePiece(mc)));
            case 418 -> shot(mc, "packtest_makers_mark");
            case 420 -> mc.setScreen(null);
            case 430 -> {
                setGuiScale(mc, 1);
                mc.setScreen(new PatternBookScreen(longForgeOffer()));
            }
            case 450 -> shot(mc, "packtest_pattern_book_long_scale_1");
            case 455 -> setGuiScale(mc, 2);
            case 475 -> shot(mc, "packtest_pattern_book_long_scale_2");
            case 480 -> setGuiScale(mc, 3);
            case 500 -> shot(mc, "packtest_pattern_book_long_scale_3");
            // Every station's focused view and status at GUI scale 3.
            case 505 -> focusStation(mc, workAnvil);
            case 525 -> focusShot(mc, workAnvil, "packtest_anvil_focus_scale_3");
            case 530 -> focusStation(mc, workForge);
            case 550 -> focusShot(mc, workForge, "packtest_forge_focus_scale_3");
            case 555 -> focusStation(mc, workTrough);
            case 575 -> focusShot(mc, workTrough, "packtest_trough_focus_scale_3");
            case 580 -> focusStation(mc, workGrindstone);
            case 600 -> {
                focusShot(mc, workGrindstone, "packtest_grindstone_focus_scale_3");
                FocusController.exit(null, true);
            }
            case 605 -> mc.setScreen(new GuideScreen(2));
            case 625 -> {
                shot(mc, "packtest_guide_scale_3");
                setGameMode(mc, GameType.SURVIVAL);
            }
            case 630 -> {
                setGuiScale(mc, 1);
                mc.setScreen(new FixedInventoryScreen(mc.player, false));
            }
            case 650 -> shot(mc, "packtest_inventory_tools_scale_1");
            case 655 -> setGuiScale(mc, 2);
            case 675 -> shot(mc, "packtest_inventory_tools_scale_2");
            case 680 -> {
                sizeWindow(mc, 960, 720);
                setGuiScale(mc, 3);
            }
            case 700 -> {
                boolean exact = mc.getWindow().getGuiScaledWidth() == 320 && mc.getWindow().getGuiScaledHeight() == 240;
                Map<String, Object> minimum = new LinkedHashMap<>();
                minimum.put("requested_framebuffer", "960x720");
                minimum.put("logical_width", mc.getWindow().getGuiScaledWidth());
                minimum.put("logical_height", mc.getWindow().getGuiScaledHeight());
                minimum.put("exact_320x240", exact);
                checks.put("gui_minimum_probe", minimum);
                if (REQUIRE_EXACT_MINIMUM) checks.put("gui_exact_320x240_required", exact);
                checks.put("gui_fitted_minimum_viewport", exact);
                shot(mc, "packtest_inventory_tools_scale_3");
            }
            case 701 -> mc.setScreen(new FixedInventoryScreen(mc.player, true));
            case 704 -> shot(mc, "packtest_inventory_quality_tooltip");
            case 705 -> {
                setGameMode(mc, GameType.CREATIVE);
                mc.setScreen(new PatternBookScreen(longForgeOffer()));
            }
            case 725 -> shot(mc, "packtest_pattern_book_long_320x240");
            case 730 -> focusStation(mc, workAnvil);
            case 750 -> {
                focusShot(mc, workAnvil, "packtest_anvil_focus_320x240");
                FocusController.exit(null, true);
            }
            case 755 -> mc.setScreen(new GuideScreen(2));
            case 775 -> shot(mc, "packtest_guide_320x240");
            case 776 -> exerciseGuideNavigation(mc);
            case 777 -> {
                if (mc.screen instanceof GuideScreen guide) {
                    var contents = guideButton(guide, "screen.immersive_smithing.guide.contents");
                    if (contents != null) contents.onPress();
                    guide.children().stream().filter(net.minecraft.client.gui.components.EditBox.class::isInstance)
                            .map(net.minecraft.client.gui.components.EditBox.class::cast).findFirst().orElseThrow().setValue("grindstone");
                }
            }
            case 779 -> shot(mc, "packtest_guide_search");
            case 780 -> {
                accessibility(true);
                checks.put("accessibility_mode", "high contrast, colorblind-safe, large targets, reduced flash/shake/motion, "
                        + "expanded status, minimal particles");
                sizeWindow(mc, originalWindowWidth, originalWindowHeight);
                setGuiScale(mc, 2);
                focusStation(mc, workForge);
            }
            case 795 -> accessibility(true);
            case 805 -> {
                checks.put("accessibility_mode_applied", accessibility(true));
                focusShot(mc, workForge, "packtest_forge_accessibility");
            }
            case 810 -> focusStation(mc, workAnvil);
            case 835 -> {
                focusShot(mc, workAnvil, "packtest_anvil_accessibility");
                FocusController.exit(null, true);
            }
            case 840 -> {
                accessibility(false);
                openJei(mc);
            }
            case 870 -> {
                if (PackTestJeiPlugin.runtime() != null) shot(mc, "packtest_jei_smithing");
            }
            case 875 -> {
                mc.setScreen(null);
                resourceReload = mc.reloadResourcePacks();
            }
            case 895 -> advancedAt = t;
            default -> {
            }
        }
        if (t >= 895) advancedStep(mc, t);
    }

    /**
     * A predictable test window. A tiling Wayland compositor ignores the requested size, so on Hyprland this
     * client's own window (matched by process id) is floated and sized; nothing else on the desktop is touched.
     */
    static void sizeWindow(Minecraft mc, int width, int height) {
        mc.getWindow().setWindowed(width, height);
        if (System.getenv("HYPRLAND_INSTANCE_SIGNATURE") == null) return;
        String target = "window = \"pid:" + ProcessHandle.current().pid() + "\"";
        Thread thread = new Thread(() -> {
            // Hyprland 0.55+ takes Lua dispatchers.
            for (String dispatch : new String[]{"hl.dsp.window.float({ action = \"enable\", " + target + " })",
                    "hl.dsp.window.resize({ x = " + width + ", y = " + height + ", " + target + " })", "hl.dsp.window.move({ x = 40, y = 40, " + target + " })"}) {
                try {
                    new ProcessBuilder("hyprctl", "dispatch", dispatch).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                            .start().waitFor();
                } catch (IOException e) {
                    log("hyprctl unavailable: " + e.getMessage());
                    return;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }, "smoketest-window");
        thread.setDaemon(true);
        thread.start();
    }

    private static void createWorld(Minecraft mc) {
        String name = "PackTest" + System.currentTimeMillis();
        log("creating flat world " + name);
        LevelSettings settings = new LevelSettings(name, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(),
                WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(name, settings, new WorldOptions(20260923L, false, false),
                access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
    }

    /** A freshly quenched, unsigned piece as the trough would hand it over. */
    private static ItemStack signablePiece(Minecraft mc) {
        ItemStack piece = new ItemStack(Items.IRON_SWORD);
        new QualityData(82, 88, false).apply(piece);
        if (mc.player != null) MakersMark.stamp(piece, mc.player);
        return piece;
    }

    private static void runChecks(Minecraft mc) {
        SmithingData data = SmithingData.current();
        IntegratedServer server = mc.getSingleplayerServer();
        checks.put("summary", data.report().summary());
        checks.put("smithing_data_built", data.recipes().size() >= 28);
        checks.put("vanilla_explicit_recipes", data.recipe(ImmersiveSmithing.id("smithing/iron_sword")) != null);
        checks.put("material_families", data.materials().families().size());
        if (server != null) {
            checks.put("iron_sword_crafting_disabled", server.getRecipeManager().byKey(new ResourceLocation("minecraft", "iron_sword")).isEmpty());
        }
        if (EXPECT.contains("spartan")) {
            checks.put("spartan_iron_longsword", data.recipe(ImmersiveSmithing.id("compat/spartanweaponry/iron_longsword")) != null);
            checks.put("spartan_netherite_longsword", data.recipe(ImmersiveSmithing.id("compat/spartanweaponry/netherite_longsword")) != null);
            checks.put("spartan_iron_javelin", data.recipe(ImmersiveSmithing.id("compat/spartanweaponry/iron_javelin")) != null);
        }
        if (EXPECT.contains("shields")) {
            checks.put("spartan_shields_iron_basic", !data.recipesProducing(item("spartanshields:iron_basic_shield")).isEmpty());
        }
        if (EXPECT.contains("upgrades")) {
            checks.put("spartan_shields_netherite_upgrade", !data.recipesProducing(item("spartanshields:netherite_basic_shield")).isEmpty());
        }
        if (EXPECT.contains("cataclysm")) {
            checks.put("cataclysm_family_ignitium", data.materials().family(new ResourceLocation("cataclysm", "ignitium")).isPresent());
            checks.put("cataclysm_black_steel_sword", !data.recipesProducing(item("cataclysm:black_steel_sword")).isEmpty());
            List<SmithingRecipe> ignitium = data.recipesProducing(item("cataclysm:ignitium_helmet"));
            checks.put("cataclysm_ignitium_helmet_reworks_netherite", !ignitium.isEmpty() && ignitium.get(0).metalUnits() == 9
                    && ignitium.get(0).auxiliary().stream().anyMatch(AuxiliaryIngredient::consumesEquipment));
        }
        if (EXPECT.contains("dragonsteel")) {
            checks.put("dragonsteel_family", data.materials().family(new ResourceLocation("forge", "fire_dragonsteel")).isPresent());
            checks.put("dragonsteel_spartanfire_javelin", data.recipe(ImmersiveSmithing.id("compat/spartanfire/fire_dragonsteel_javelin")) != null);
            checks.put("dragonsteel_iceandfire_sword", !data.recipesProducing(item("iceandfire:dragonsteel_fire_sword")).isEmpty());
        }
        if (EXPECT.contains("botania")) {
            List<SmithingRecipe> terrasteel = data.recipesProducing(item("botania:terrasteel_helmet"));
            checks.put("botania_terrasteel_helmet_chain", !terrasteel.isEmpty()
                    && terrasteel.get(0).auxiliary().stream().anyMatch(AuxiliaryIngredient::consumesEquipment));
        }
        if (EXPECT.contains("epicfight")) {
            List<SmithingRecipe> longsword = data.recipesProducing(item("epicfight:iron_longsword"));
            checks.put("epicfight_iron_longsword_36_units", !longsword.isEmpty() && longsword.get(0).metalUnits() == 36);
            if (server != null) {
                checks.put("epicfight_longsword_crafting_replaced", server.getRecipeManager().byKey(new ResourceLocation("epicfight", "iron_longsword")).isEmpty());
                // Sword plus sticks adds no metal: a remake of a finished piece, kept beside the forge recipe.
                checks.put("epicfight_spear_remake_kept", server.getRecipeManager().byKey(new ResourceLocation("epicfight", "iron_spear")).isPresent());
            }
            List<SmithingRecipe> uchigatana = data.recipesProducing(item("epicfight:uchigatana"));
            checks.put("epicfight_uchigatana_forged_36_iron", !uchigatana.isEmpty() && uchigatana.get(0).metalUnits() == 36
                    && uchigatana.get(0).family().equals(new ResourceLocation("minecraft", "iron")));
        }
        if (EXPECT.contains("epicknights")) {
            List<SmithingRecipe> armet = data.recipesProducing(item("magistuarmory:armet"));
            checks.put("epicknights_armet_27_steel_and_barbute", !armet.isEmpty() && armet.get(0).metalUnits() == 27
                    && armet.get(0).auxiliary().stream().anyMatch(a -> a.consumesEquipment() && a.ingredient().test(new ItemStack(item("magistuarmory:barbute"))))
                    && armet.get(0).family().equals(new ResourceLocation("forge", "steel")));
            checks.put("epicknights_steel_plate_melts_for_9", MaterialResolver.resolve(new ItemStack(item("magistuarmory:steel_plate")))
                    .filter(m -> m.family().id().equals(new ResourceLocation("forge", "steel")) && m.unitsEach() == 9).isPresent());
            if (server != null) {
                RecipeManager recipes = server.getRecipeManager();
                checks.put("epicknights_armet_crafting_replaced", recipes.byKey(new ResourceLocation("magistuarmory", "armet")).isEmpty());
                // Gilding adds gold to a finished armet: a native transformation, kept beside the forge recipe.
                checks.put("epicknights_ceremonial_gilding_kept", recipes.byKey(new ResourceLocation("magistuarmory", "ceremonialarmet")).isPresent());
                if (mc.getConnection() != null) {
                    // The client's recipe book and JEI see the rewritten recipe set, not the datapack's original one.
                    RecipeManager client = mc.getConnection().getRecipeManager();
                    checks.put("epicknights_client_recipes_match_server", client.getRecipes().size() == recipes.getRecipes().size()
                            && client.byKey(new ResourceLocation("magistuarmory", "armet")).isEmpty()
                            && client.byKey(ImmersiveSmithing.id("compat/magistuarmory/armet/from/magistuarmory/armet")).isPresent());
                }
            }
        }
        if (EXPECT.contains("simplyswords")) {
            List<SmithingRecipe> twinblade = data.recipesProducing(item("simplyswords:iron_twinblade"));
            checks.put("simplyswords_iron_twinblade_pattern", !twinblade.isEmpty() && twinblade.get(0).anvilPattern().equals(ImmersiveSmithing.id("twinblade")));
            if (server != null) {
                checks.put("simplyswords_twinblade_crafting_replaced", server.getRecipeManager().byKey(new ResourceLocation("simplyswords", "iron_twinblade")).isEmpty());
                checks.put("simplyswords_runic_reroll_kept", server.getRecipeManager().byKey(new ResourceLocation("simplyswords", "runic2_longsword")).isPresent());
            }
            ItemStack unique = new ItemStack(item("simplyswords:brimstone_claymore"));
            checks.put("simplyswords_unique_quality_eligible", unique.is(ModTags.QUALITY_ELIGIBLE));
            checks.put("simplyswords_unique_non_recyclable", unique.is(ModTags.NON_RECYCLABLE));
        }
        if (EXPECT.contains("signing")) {
            checks.put("signing_enabled", ServerConfig.get(ServerConfig.ENABLE_SIGNING));
        }
        if (EXPECT.contains("loot")) {
            checks.put("loot_quality_mode", ServerConfig.get(ServerConfig.LOOT_QUALITY_MODE).name());
            checks.put("loot_modifier_registered", ModLootModifiers.FORGED_LOOT.isPresent());
        }
        checks.put("skipped_equipment", data.report().skippedCount());
        checks.put("mods_loaded", ModList.get().size());
        try {
            data.report().write(Path.of(OUTPUT));
        } catch (IOException e) {
            checks.put("report_written", false);
        }
        log("checks: " + checks);
    }

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(new ResourceLocation(id));
    }

    /**
     * Packs open their own screens on first join, and the world shots need them gone. Origins (Ultima's heritage
     * choice) reopens its screen until an origin is chosen, so its Select button takes the offered origin as a player
     * would; any other screen is closed.
     */
    private static void settlePackScreen(Minecraft mc) {
        Screen screen = mc.screen;
        if (screen == null) return;
        if (screen.getClass().getName().contains(".origins.")) {
            for (GuiEventListener child : screen.children()) {
                if (child instanceof Button button && button.active && "Select".equals(button.getMessage().getString())) {
                    log("choosing the offered origin on " + screen.getClass().getName());
                    button.onPress();
                    return;
                }
            }
            return;
        }
        log("closing pack screen " + screen.getClass().getName());
        mc.setScreen(null);
    }

    private static void camera(Minecraft mc, double up, double back, float pitch, boolean spectator) {
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null) return;
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayers().get(0);
            player.setGameMode(spectator ? GameType.SPECTATOR : GameType.CREATIVE);
            if (spectator) player.teleportTo(server.overworld(), player.getX(), player.getY() + up, player.getZ() + back, 180F, pitch);
        });
    }

    private static void stageWorld(Minecraft mc) {
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null) return;
        server.execute(() -> {
            ServerLevel level = server.overworld();
            ServerPlayer player = server.getPlayerList().getPlayers().get(0);
            level.setDayTime(6000);
            BlockPos base = player.blockPosition();
            galleryBase = base.offset(0, 0, -14);
            performanceBase = base.offset(0, 0, 48);
            // The hands-on row behind the player: forge, anvil, trough and grindstone, all worked from one spot.
            BlockPos row = base.offset(0, 0, 12);
            workForge = row.offset(-2, 0, 0);
            workAnvil = row;
            workTrough = row.offset(2, 0, 0);
            workGrindstone = row.offset(4, 0, 0);
            workStand = new Vec3(row.getX() + 1.0, row.getY(), row.getZ() + 2.3);
            for (int x = -34; x <= 34; x++) for (int z = -27; z <= 62; z++) {
                level.setBlockAndUpdate(base.offset(x, -1, z), Blocks.POLISHED_ANDESITE.defaultBlockState());
                for (int y = 0; y <= 5; y++) level.setBlockAndUpdate(base.offset(x, y, z), Blocks.AIR.defaultBlockState());
            }
            BlockPos f1 = base.offset(-4, 0, -4);
            level.setBlockAndUpdate(f1, ModBlocks.SMITHS_FORGE.get().defaultBlockState().setValue(SmithsForgeBlock.FACING, Direction.SOUTH));
            SmithsForgeBlockEntity forge1 = (SmithsForgeBlockEntity) level.getBlockEntity(f1);
            forge1.insertMetal(new ItemStack(Items.IRON_INGOT, 12), 12, false, null);
            forge1.insertMetal(new ItemStack(Items.IRON_BLOCK, 2), 2, false, null);
            forge1.insertFuel(new ItemStack(Items.COAL, 24), 24, false, null);
            BlockPos f2 = base.offset(-2, 0, -4);
            level.setBlockAndUpdate(f2, ModBlocks.SMITHS_FORGE.get().defaultBlockState().setValue(SmithsForgeBlock.FACING, Direction.SOUTH));
            SmithsForgeBlockEntity forge2 = (SmithsForgeBlockEntity) level.getBlockEntity(f2);
            forge2.addLavaBucket(false);
            forge2.acceptWorkpiece(workpiece(Items.IRON_CHESTPLATE, 216, WorkpieceState.FORGED, "chestplate"));
            BlockPos a = base.offset(0, 0, -4);
            level.setBlockAndUpdate(a, ModBlocks.SMITHS_ANVIL.get().defaultBlockState().setValue(SmithsAnvilBlock.FACING, Direction.EAST));
            ((SmithsAnvilBlockEntity) level.getBlockEntity(a)).setWorkpiece(workpiece(Items.IRON_SWORD, 18, WorkpieceState.FORGED, "sword"));
            BlockPos t = base.offset(2, 0, -4);
            level.setBlockAndUpdate(t, ModBlocks.SMITHS_TROUGH.get().defaultBlockState().setValue(SmithsTroughBlock.FACING, Direction.SOUTH));
            SmithsTroughBlockEntity trough = (SmithsTroughBlockEntity) level.getBlockEntity(t);
            trough.addBucket();
            trough.addBucket();
            level.setBlockAndUpdate(base.offset(4, 0, -4), ModBlocks.SMITHS_GRINDSTONE.get().defaultBlockState().setValue(SmithsGrindstoneBlock.FACING, Direction.SOUTH));
            pickupAnvil = base.offset(6, 0, -4);
            level.setBlockAndUpdate(pickupAnvil, ModBlocks.SMITHS_ANVIL.get().defaultBlockState().setValue(SmithsAnvilBlock.FACING, Direction.SOUTH));
            ((SmithsAnvilBlockEntity) level.getBlockEntity(pickupAnvil)).setWorkpiece(workpiece(Items.IRON_SWORD, 18, WorkpieceState.FORGED, "sword"));

            ItemStack tongs = new ItemStack(ModItems.tongs(SmithingTier.IRON));
            WorkpieceCodec.setHeld(tongs, workpiece(Items.IRON_AXE, 27, WorkpieceState.SHAPED, "axe"));
            player.setItemInHand(InteractionHand.MAIN_HAND, tongs);
            ItemStack sword = new ItemStack(Items.IRON_SWORD);
            new QualityData(94, 82, false).apply(sword);
            player.getInventory().setItem(1, sword);
            player.getInventory().setItem(2, new ItemStack(ModItems.hammer(SmithingTier.DIAMOND)));
            player.getInventory().setItem(3, new ItemStack(ModItems.SMITHING_GUIDE.get()));
            player.getInventory().setItem(4, new ItemStack(ModItems.tongs(SmithingTier.NETHERITE)));
            int slot = 9;
            for (SmithingTier tier : SmithingTier.values()) {
                player.getInventory().setItem(slot++, new ItemStack(ModItems.hammer(tier)));
                player.getInventory().setItem(slot++, new ItemStack(ModItems.tongs(tier)));
                ItemStack loaded = new ItemStack(ModItems.tongs(tier));
                WorkpieceCodec.setHeld(loaded, workpiece(Items.IRON_SWORD, 18, WorkpieceState.SHAPED, "sword"));
                player.getInventory().setItem(slot++, loaded);
            }
            int cluster = 0;
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                BlockPos center = galleryBase.offset(-24 + cluster * 16, 0, 0);
                galleryCenters.put(facing, center);
                stageStationCluster(level, center, facing);
                cluster++;
            }
            stagePerformance(level, false);
            checks.put("gallery_station_count", 52);
            checks.put("inventory_tool_variants", slot - 9);
            player.teleportTo(level, base.getX() + 0.5, base.getY(), base.getZ() + 1.5, 180F, 20F);
        });
    }

    /**
     * Starts the hands-on run: the four stations in a row, molten iron and a fire, empty tongs and a hammer, then an iron
     * sword from the pattern book to a grindstone pass, played by {@link SmithDriver} through this client's own inputs.
     */
    private static void beginHandsOn(Minecraft mc) {
        mc.setScreen(null);
        converge(ClientConfig.CAMERA_MODE, ClientConfig.CameraMode.CINEMATIC);
        IntegratedServer server = mc.getSingleplayerServer();
        SmithingRecipe recipe = SmithingData.current().recipe(IRON_SWORD);
        if (server == null || recipe == null || workAnvil == null) {
            checks.put("hands_on_flow_completed", false);
            checks.put("hands_on_flow", server == null ? "no integrated server" : "no iron sword recipe");
            return;
        }
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayers().get(0);
            savedInventory = player.getInventory().save(new ListTag());
            SmithDriver.stage(server.overworld(), player, recipe, workForge, workAnvil, workTrough, workGrindstone, Direction.SOUTH,
                    workStand, GameType.CREATIVE, SmithingTier.NETHERITE);
        });
        Map<BlockPos, Vec3> stands = Map.of(workForge, workStand, workAnvil, workStand, workTrough, workStand, workGrindstone, workStand);
        smithDriver = new SmithDriver(workForge, workAnvil, workTrough, workGrindstone, stands, IRON_SWORD, "iron sword",
                Items.IRON_SWORD, true, name -> handsOnMoment(mc, name));
        log("hands-on run started");
    }

    private static void handsOnMoment(Minecraft mc, String name) {
        log("hands-on: " + name);
        String file = switch (name) {
            case "forge_focus" -> "packtest_forge_focus";
            case "pattern_book_live" -> "packtest_pattern_book_live";
            case "forge_heating" -> "packtest_forge_heating";
            case "billet_ready" -> "packtest_forge_billet_ready";
            case "anvil_first_blows" -> "packtest_anvil_shaping";
            case "anvil_half_shaped" -> "packtest_anvil_half_shaped";
            case "reheat_on_rest" -> "packtest_forge_reheat_rest";
            case "quench_hiss" -> "packtest_trough_quench";
            case "fitting" -> "packtest_anvil_fitting";
            case "grind_contact" -> "packtest_grindstone_pass";
            default -> null;
        };
        if (file != null) shot(mc, file);
    }

    private static void finishHandsOn(Minecraft mc) {
        if (checks.containsKey("hands_on_flow_completed")) return; // the grindstone pass reports itself
        checks.put("hands_on_flow", smithDriver.evidence);
        checks.put("hands_on_flow_completed", true);
        log("hands-on run finished: " + smithDriver.evidence);
    }

    /**
     * World view: a second iron billet is committed, brought to heat, drawn and set on the anvil through the server's
     * station actions, and a third is left heating on the tray for the later forge views. The smith takes the hammer.
     */
    private static void stageWorldView(Minecraft mc) {
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null || workAnvil == null) return;
        converge(ClientConfig.CAMERA_MODE, ClientConfig.CameraMode.WORLD);
        mc.player.getInventory().selected = 1;
        server.execute(() -> {
            ServerLevel level = server.overworld();
            ServerPlayer player = server.getPlayerList().getPlayers().get(0);
            SmithingRecipe recipe = SmithingData.current().recipe(IRON_SWORD);
            SmithsForgeBlockEntity forge = (SmithsForgeBlockEntity) level.getBlockEntity(workForge);
            SmithsAnvilBlockEntity anvil = (SmithsAnvilBlockEntity) level.getBlockEntity(workAnvil);
            player.getInventory().clearContent();
            player.getInventory().setItem(0, new ItemStack(ModItems.tongs(SmithingTier.NETHERITE)));
            player.getInventory().setItem(1, new ItemStack(ModItems.hammer(SmithingTier.NETHERITE)));
            player.getInventory().setItem(9, new ItemStack(Items.STICK, 2));
            player.getInventory().selected = 0;
            forge.refundCast(recipe.metalUnits() * 2, recipe.family());
            forge.setPlan(recipe.getId());
            ForgeActions.commit(player, forge, InteractionHand.MAIN_HAND);
            ForgeJob job = forge.job();
            if (job != null) java.util.Arrays.fill(job.heat, (job.thermal.optimalMin() + job.thermal.optimalMax()) / 2);
            ForgeActions.draw(player, forge, InteractionHand.MAIN_HAND, -1);
            AnvilActions.place(player, anvil, InteractionHand.MAIN_HAND);
            forge.setPlan(recipe.getId());
            ForgeActions.commit(player, forge, InteractionHand.MAIN_HAND);
            WorkLeases.release(player, null);
            player.getInventory().selected = 1;
            player.inventoryMenu.broadcastChanges();
            SmithDriver.lookFrom(player, level, workStand, Vec3.atCenterOf(workAnvil).add(0, 0.45, 0));
            checks.put("world_view_staged", anvil.workpiece() != null && forge.job() != null);
        });
    }

    private static int anvilProgress(Minecraft mc) {
        return mc.level != null && mc.level.getBlockEntity(workAnvil) instanceof SmithsAnvilBlockEntity anvil && anvil.workpiece() != null
                ? anvil.workpiece().process().progress() : -1;
    }

    /** The inventory the visual checks were staged with (tool variants, the quality sword). */
    private static void restoreInventory(Minecraft mc) {
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null || savedInventory == null) return;
        mc.player.getInventory().selected = 0;
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayers().get(0);
            player.getInventory().clearContent();
            player.getInventory().load(savedInventory);
            player.getInventory().selected = 0;
            player.inventoryMenu.broadcastChanges();
        });
    }

    /** Back to the hands-on row as a smith (not a spectator camera), for a focused view. */
    private static void returnToWorkStand(Minecraft mc) {
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null || workStand == null) return;
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayers().get(0);
            player.setGameMode(GameType.CREATIVE);
            SmithDriver.lookFrom(player, server.overworld(), workStand, Vec3.atCenterOf(workAnvil));
        });
    }

    /** Asks for the focused (Cinematic) view of a station, leaving any other one first. */
    private static void focusStation(Minecraft mc, BlockPos pos) {
        if (pos == null) return;
        if (mc.screen != null && !FocusController.active()) mc.setScreen(null);
        if (FocusController.active()) FocusController.exit(null, true);
        converge(ClientConfig.CAMERA_MODE, ClientConfig.CameraMode.CINEMATIC);
        FocusController.request(pos);
    }

    private static void focusShot(Minecraft mc, BlockPos pos, String name) {
        boolean open = pos != null && FocusController.focusedOn(pos);
        checks.put(name + "_view_open", open);
        shot(mc, name);
    }

    private static void stageStationCluster(ServerLevel level, BlockPos center, Direction facing) {
        Direction right = facing.getClockWise();
        for (int r = -7; r <= 7; r++) for (int back = -1; back <= 8; back++) {
            BlockPos floor = local(center, right, facing, r, back).below();
            level.setBlockAndUpdate(floor, ((r + back) & 1) == 0 ? Blocks.POLISHED_ANDESITE.defaultBlockState()
                    : Blocks.SMOOTH_STONE.defaultBlockState());
        }
        BlockPos[] forges = new BlockPos[4];
        int index = 0;
        for (int r : new int[]{-5, -2, 1, 4}) {
            BlockPos pos = local(center, right, facing, r, 1);
            forges[index++] = pos;
            level.setBlockAndUpdate(pos, ModBlocks.SMITHS_FORGE.get().defaultBlockState().setValue(SmithsForgeBlock.FACING, facing));
        }
        SmithsForgeBlockEntity loaded = (SmithsForgeBlockEntity) level.getBlockEntity(forges[1]);
        loaded.insertMetal(new ItemStack(Items.IRON_INGOT, 12), 12, false, null);
        SmithsForgeBlockEntity burning = (SmithsForgeBlockEntity) level.getBlockEntity(forges[2]);
        burning.insertMetal(new ItemStack(Items.IRON_INGOT, 12), 12, false, null);
        burning.insertFuel(new ItemStack(Items.COAL, 16), 16, false, null);
        burning.ignite();
        SmithsForgeBlockEntity ready = (SmithsForgeBlockEntity) level.getBlockEntity(forges[3]);
        ready.acceptWorkpiece(workpiece(Items.IRON_PICKAXE, 27, WorkpieceState.FORGED, "pickaxe"));

        BlockPos forged = local(center, right, facing, -4, 4);
        level.setBlockAndUpdate(forged, ModBlocks.SMITHS_ANVIL.get().defaultBlockState().setValue(SmithsAnvilBlock.FACING, facing));
        ((SmithsAnvilBlockEntity) level.getBlockEntity(forged)).setWorkpiece(
                workpiece(Items.IRON_SWORD, 18, WorkpieceState.FORGED, "sword"));
        BlockPos shaped = local(center, right, facing, 0, 4);
        level.setBlockAndUpdate(shaped, ModBlocks.SMITHS_ANVIL.get().defaultBlockState().setValue(SmithsAnvilBlock.FACING, facing));
        ((SmithsAnvilBlockEntity) level.getBlockEntity(shaped)).setWorkpiece(
                workpiece(Items.IRON_AXE, 27, WorkpieceState.SHAPED, "axe"));
        level.setBlockAndUpdate(local(center, right, facing, 4, 4),
                ModBlocks.SMITHS_GRINDSTONE.get().defaultBlockState().setValue(SmithsGrindstoneBlock.FACING, facing));

        index = 0;
        for (int r : new int[]{-5, -2, 1, 4}) {
            BlockPos pos = local(center, right, facing, r, 7);
            level.setBlockAndUpdate(pos, ModBlocks.SMITHS_TROUGH.get().defaultBlockState().setValue(SmithsTroughBlock.FACING, facing));
            SmithsTroughBlockEntity trough = (SmithsTroughBlockEntity) level.getBlockEntity(pos);
            for (int bucket = 0; bucket < index; bucket++) trough.addBucket();
            index++;
        }
    }

    private static BlockPos local(BlockPos center, Direction right, Direction facing, int across, int back) {
        Direction rear = facing.getOpposite();
        return center.offset(right.getStepX() * across + rear.getStepX() * back, 0,
                right.getStepZ() * across + rear.getStepZ() * back);
    }

    private static void advancedStep(Minecraft mc, int t) {
        if (t < advancedAt) return;
        switch (advancedPhase) {
            case 0 -> {
                if (resourceReload == null || !resourceReload.isDone()) return;
                if (mc.getOverlay() != null) {
                    overlayClearedAt = -1;
                    return;
                }
                if (overlayClearedAt < 0) {
                    overlayClearedAt = t;
                    return;
                }
                if (t - overlayClearedAt < 20) return;
                resourceReload.join();
                checks.put("resource_reload", true);
                checks.put("jei_runtime_available", PackTestJeiPlugin.runtime() != null);
                accessibility(false);
                setGuiScale(mc, originalGuiScale);
                Direction facing = galleryCenters.keySet().iterator().next();
                setDayAndCamera(mc, 6000, galleryCenters.get(facing), facing);
                advance(t, 25);
            }
            case 1 -> {
                List<Direction> directions = List.copyOf(galleryCenters.keySet());
                Direction facing = directions.get(galleryCaptureIndex);
                shot(mc, "packtest_stations_" + facing.getName() + "_day");
                galleryCaptureIndex++;
                if (galleryCaptureIndex < directions.size()) {
                    Direction next = directions.get(galleryCaptureIndex);
                    setDayAndCamera(mc, 6000, galleryCenters.get(next), next);
                    advancedAt = t + 25;
                } else {
                    Direction nightFacing = directions.get(0);
                    setDayAndCamera(mc, 18000, galleryCenters.get(nightFacing), nightFacing);
                    advance(t, 30);
                }
            }
            case 2 -> {
                shot(mc, "packtest_stations_night");
                prepareEffectCamera(mc, "quench");
                advance(t, 10);
            }
            case 3 -> {
                fireEffect(mc, "quench");
                // Steam rises once the piece crosses the water line (half a second after it is lowered).
                advance(t, 14);
            }
            case 4 -> {
                shot(mc, "packtest_effect_quench_real_action");
                prepareEffectCamera(mc, "grind");
                advance(t, 10);
            }
            case 5 -> {
                fireEffect(mc, "grind");
                advance(t, 6);
            }
            case 6 -> {
                shot(mc, "packtest_effect_grind_real_action");
                prepareEffectCamera(mc, "anvil_packet");
                advance(t, 10);
            }
            case 7 -> {
                fireEffect(mc, "anvil_packet");
                advance(t, 2);
            }
            case 8 -> {
                shot(mc, "packtest_effect_anvil_server_packet");
                mc.options.enableVsync().set(false);
                mc.options.framerateLimit().set(260);
                mc.options.renderDistance().set(6);
                Map<String, Object> settings = new LinkedHashMap<>();
                settings.put("vsync", false);
                settings.put("frame_limit", "260 (vanilla maximum/unlimited)");
                settings.put("render_distance", 6);
                settings.put("camera", "fixed spectator");
                settings.put("gl_vendor", org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_VENDOR));
                settings.put("gl_renderer", org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER));
                settings.put("gl_version", org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_VERSION));
                checks.put("frame_measurement_settings", settings);
                stagePerformance(mc, false);
                performanceCamera(mc);
                advance(t, 80);
            }
            case 9 -> {
                frameMeasurement = new FrameMeasurement("empty", 60, 180, 32);
                advance(t, 20);
            }
            case 10 -> {
                if (!frameMeasurement.done()) return;
                checks.put("frame_times_empty", frameMeasurement.result());
                stagePerformance(mc, true);
                advance(t, 80);
            }
            case 11 -> {
                frameMeasurement = new FrameMeasurement("active", 60, 180, 32);
                advance(t, 20);
            }
            case 12 -> {
                if (!frameMeasurement.done()) return;
                checks.put("frame_times_32_active", frameMeasurement.result());
                checks.put("frame_measurement_real_render_frames", true);
                shot(mc, "packtest_performance_32_active");
                // GUI scale 4 needs a 1280 x 960 framebuffer (320 x 240 logical). The option clamps to what the
                // current window allows, so the window is resized first and the scale set once it has settled.
                mc.setScreen(null);
                sizeWindow(mc, 1280, 960);
                advance(t, 20);
            }
            case 13 -> {
                setGuiScale(mc, 4);
                checks.put("gui_scale_4_rendered", mc.getWindow().getGuiScale() == 4 && mc.getWindow().getGuiScaledWidth() >= 320
                        && mc.getWindow().getGuiScaledHeight() >= 240);
                mc.setScreen(new PatternBookScreen(longForgeOffer()));
                advance(t, 15);
            }
            case 14 -> {
                shot(mc, "packtest_pattern_book_long_scale_4");
                mc.setScreen(null);
                returnToWorkStand(mc);
                advance(t, 20);
            }
            case 15 -> {
                focusStation(mc, workAnvil);
                advance(t, 20);
            }
            case 16 -> {
                focusShot(mc, workAnvil, "packtest_anvil_focus_scale_4");
                FocusController.exit(null, true);
                mc.setScreen(new GuideScreen(2));
                advance(t, 15);
            }
            case 17 -> {
                shot(mc, "packtest_guide_scale_4");
                mc.setScreen(new MakersMarkScreen(0, signablePiece(mc)));
                advance(t, 10);
            }
            case 18 -> {
                shot(mc, "packtest_makers_mark_scale_4");
                mc.setScreen(null);
                sizeWindow(mc, originalWindowWidth, originalWindowHeight);
                // Not setGuiScale: that would overwrite the measured gui_scale_N entry with this window's size.
                mc.options.guiScale().set(originalGuiScale);
                mc.resizeDisplay();
                advance(t, 10);
            }
            case 19 -> {
                checks.put("workpiece_surface_geometry_and_picking", WorkpieceGallery.surfacesAgree());
                mc.setScreen(new WorkpieceGallery());
                advance(t, 15);
            }
            case 20 -> {
                shot(mc, "packtest_workpiece_profiles");
                checks.put("target_blade_silhouettes_loaded", AnvilVisualGallery.silhouettesAvailable());
                mc.setScreen(new AnvilVisualGallery());
                advance(t, 15);
            }
            case 21 -> {
                shot(mc, "packtest_anvil_alignment");
                for (SmithingTier tier : SmithingTier.values()) {
                    checks.put("tongs_model_" + tier.name().toLowerCase(java.util.Locale.ROOT),
                            com.otectus.immersivesmithing.client.renderer.TongsItemRenderer.modelContract(new ItemStack(ModItems.tongs(tier))));
                }
                setGuiScale(mc, 1);
                mc.setScreen(new TongsGallery());
                advance(t, 15);
            }
            case 22 -> {
                shot(mc, "packtest_tongs_alignment");
                mc.setScreen(null);
                mc.options.guiScale().set(originalGuiScale);
                mc.resizeDisplay();
                advance(t, 2);
            }
            case 23 -> {
                mc.options.enableVsync().set(originalVsync);
                mc.options.framerateLimit().set(originalFrameLimit);
                mc.options.renderDistance().set(originalRenderDistance);
                checks.put("accessibility_settings_restored", accessibility(false));
                if (EXPECT.contains("epicknights")) {
                    stageEpicGallery(mc);
                    advance(t, 25);
                    return;
                }
                boolean pass = checks.values().stream().noneMatch(v -> v instanceof Boolean b && !b);
                finish(mc, pass, pass ? "all visual, data and runtime checks passed" : "a check failed");
            }
            case 24 -> {
                mc.setScreen(new InventoryScreen(mc.player));
                advance(t, 20);
            }
            case 25 -> {
                shot(mc, "packtest_epic_decorated_armor_shield_weapons");
                mc.setScreen(new PatternBookScreen(epicUpgradeOffer()));
                advance(t, 20);
            }
            case 26 -> {
                // The book's detail panel shows the complete inputs and the personal preview of the chosen upgrade.
                shot(mc, "packtest_epic_upgrade_complete_inputs");
                advance(t, 2);
            }
            case 27 -> {
                PackTestJeiPlugin.openRecipes(new ItemStack(item("magistuarmory:knight_chestplate")));
                advance(t, 25);
            }
            case 28 -> {
                shot(mc, "packtest_epic_jei_knight_upgrade");
                mc.setScreen(null);
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                mc.player.getInventory().selected = 0;
                mc.player.setXRot(0);
                advance(t, 20);
            }
            case 29 -> {
                shot(mc, "packtest_epic_long_weapon_and_shield");
                mc.player.getInventory().selected = 2;
                if (net.minecraftforge.fml.ModList.get().isLoaded("quick_swap") && net.minecraftforge.fml.ModList.get().isLoaded("epicknights_open_helms")) {
                    mc.getSingleplayerServer().execute(() -> {
                        var player = mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
                        com.otectus.immersivesmithing.compat.epicknights.VisorSwap.apply(player, player.getId(), "epicknights_open_helms:open_armet_helmet");
                    });
                }
                advance(t, 20);
            }
            case 30 -> {
                shot(mc, "packtest_epic_firearm_and_open_visor");
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                swapGalleryHelmet(mc, "magistuarmory:armet");
                advance(t, 20);
            }
            case 31 -> {
                shot(mc, "packtest_epic_closed_visor_first_person");
                swapGalleryHelmet(mc, "epicknights_open_helms:open_armet_helmet");
                advance(t, 20);
            }
            case 32 -> {
                shot(mc, "packtest_epic_open_visor_first_person");
                stageEpicHorse(mc);
                advance(t, 25);
            }
            case 33 -> {
                shot(mc, "packtest_epic_horse_armor");
                boolean pass = checks.values().stream().noneMatch(v -> v instanceof Boolean b && !b);
                finish(mc, pass, pass ? "all runtime checks and Epic Knights presentation checks passed" : "a check failed");
            }
            default -> {
            }
        }
    }

    private static void advance(int tick, int delay) {
        advancedPhase++;
        advancedAt = tick + delay;
    }

    private static void swapGalleryHelmet(Minecraft mc, String target) {
        if (!net.minecraftforge.fml.ModList.get().isLoaded("quick_swap") || !net.minecraftforge.fml.ModList.get().isLoaded("epicknights_open_helms")) return;
        mc.getSingleplayerServer().execute(() -> {
            var player = mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
            com.otectus.immersivesmithing.compat.epicknights.VisorSwap.apply(player, player.getId(), target);
        });
    }

    private static void stageEpicHorse(Minecraft mc) {
        mc.getSingleplayerServer().execute(() -> {
            var player = mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
            var level = player.serverLevel();
            BlockPos pos = galleryBase.offset(0, 1, 10);
            var horse = net.minecraft.world.entity.EntityType.HORSE.create(level);
            if (horse == null) throw new IllegalStateException("Could not create horse render fixture");
            horse.setNoAi(true); horse.setTamed(true); horse.setNoGravity(true);
            horse.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 70, 0);
            ItemStack armor = new ItemStack(item("magistuarmory:chainmail_horse_armor"));
            if (!horse.getSlot(401).set(armor)) throw new IllegalStateException("Native horse armor slot rejected its item");
            level.addFreshEntity(horse);
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, ItemStack.EMPTY);
            player.setGameMode(GameType.SPECTATOR);
            teleportLookAt(player, level, pos.getX() + 3.5, pos.getY() + 1.5, pos.getZ() + 4.5,
                    pos.getX() + .5, pos.getY() + .7, pos.getZ() + .5);
        });
    }

    private static void stageEpicGallery(Minecraft mc) {
        mc.getSingleplayerServer().execute(() -> {
            var player = mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
            player.serverLevel().setBlockAndUpdate(player.blockPosition().below(), Blocks.POLISHED_ANDESITE.defaultBlockState());
            player.setGameMode(GameType.SURVIVAL);
            player.serverLevel().setDayTime(6000);
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION, 2400, 0, false, false));
            ItemStack helmet = new ItemStack(item("magistuarmory:armet"));
            net.minecraft.nbt.ListTag decorations = new net.minecraft.nbt.ListTag();
            net.minecraft.nbt.CompoundTag plume = new net.minecraft.nbt.CompoundTag();
            plume.putString("name", "magistuarmory:plume_middle"); plume.putInt("color", 0x9c263b); decorations.add(plume);
            helmet.getOrCreateTagElement("ArmorDecoration").put("Items", decorations);
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, helmet);
            ItemStack chest = new ItemStack(item("magistuarmory:knight_chestplate"));
            chest.getOrCreateTagElement("BlockEntityTag").putInt("Base", 14);
            new QualityData(90, 90, false).apply(chest);
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, chest);
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(item("magistuarmory:steel_pavese")));
            player.getInventory().setItem(0, new ItemStack(item("magistuarmory:steel_zweihander")));
            player.getInventory().setItem(1, new ItemStack(item("magistuarmory:steel_halberd")));
            String gun = net.minecraftforge.fml.ModList.get().isLoaded("thirtyyearswar") ? "thirtyyearswar:baroque_wheellock_pistol"
                    : net.minecraftforge.fml.ModList.get().isLoaded("artillery_addon") ? "artillery_addon:handgonne" : "magistuarmory:steel_chivalrylance";
            player.getInventory().setItem(2, new ItemStack(item(gun)));
        });
    }

    private static OpenForgeScreenPacket epicUpgradeOffer() {
        SmithingRecipe recipe = SmithingData.current().recipesProducing(item("magistuarmory:knight_chestplate")).get(0);
        List<ItemStack> inputs = recipe.auxiliary().stream().map(AuxiliaryIngredient::displayStack).toList();
        inputs.get(0).setHoverName(Component.literal("The Reserved Oath"));
        inputs.get(0).setDamageValue(19);
        inputs.get(0).enchant(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING, 2);
        ItemStack preview = com.otectus.immersivesmithing.recipe.ResultAssembler.assemble(recipe, inputs).orElseThrow();
        var row = new OpenForgeScreenPacket.Entry(recipe.getId(), preview, recipe.metalUnits(), inputs, 7, 0, Component.empty(),
                recipe.auxiliary().stream().map(a -> java.util.Arrays.stream(a.ingredient().getItems()).map(s -> s.copyWithCount(a.count())).toList()).toList(), true);
        return new OpenForgeScreenPacket(7, BlockPos.ZERO, SmithingData.current().materials().family(recipe.family()).orElseThrow().displayName(),
                45, null, List.of(row), List.of());
    }

    private static void setDayAndCamera(Minecraft mc, long dayTime, BlockPos center, Direction facing) {
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null) return;
        server.execute(() -> {
            ServerLevel level = server.overworld();
            level.setDayTime(dayTime);
            level.setWeatherParameters(60_000, 0, false, false);
            ServerPlayer player = server.getPlayerList().getPlayers().get(0);
            restageBurningForge(level, center, facing);
            player.setGameMode(GameType.SPECTATOR);
            double x = center.getX() + .5 + facing.getStepX() * 13;
            double y = center.getY() + 6;
            double z = center.getZ() + .5 + facing.getStepZ() * 13;
            teleportLookAt(player, level, x, y, z, center.getX() + .5, center.getY() + 1, center.getZ() + .5);
        });
    }

    private static void restageBurningForge(ServerLevel level, BlockPos center, Direction facing) {
        Direction right = facing.getClockWise();
        BlockPos pos = local(center, right, facing, 1, 1);
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(pos, ModBlocks.SMITHS_FORGE.get().defaultBlockState().setValue(SmithsForgeBlock.FACING, facing));
        SmithsForgeBlockEntity forge = (SmithsForgeBlockEntity) level.getBlockEntity(pos);
        forge.insertMetal(new ItemStack(Items.IRON_INGOT, 12), 12, false, null);
        forge.insertFuel(new ItemStack(Items.COAL, 16), 16, false, null);
        forge.ignite();
    }

    private static void prepareEffectCamera(Minecraft mc, String effect) {
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null || galleryBase == null) return;
        server.execute(() -> {
            ServerLevel level = server.overworld();
            ServerPlayer player = server.getPlayerList().getPlayers().get(0);
            BlockPos originalBase = galleryBase.offset(0, 0, 14);
            BlockPos pos = "quench".equals(effect) ? originalBase.offset(2, 0, -4)
                    : "grind".equals(effect) ? originalBase.offset(4, 0, -4) : originalBase.offset(0, 0, -4);
            player.setGameMode(GameType.SPECTATOR);
            teleportLookAt(player, level, pos.getX() + .5, pos.getY() + 2.8, pos.getZ() + 5.5,
                    pos.getX() + .5, pos.getY() + .7, pos.getZ() + .5);
        });
    }

    private static void fireEffect(Minecraft mc, String effect) {
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null || galleryBase == null) return;
        server.execute(() -> {
            ServerLevel level = server.overworld();
            ServerPlayer player = server.getPlayerList().getPlayers().get(0);
            BlockPos originalBase = galleryBase.offset(0, 0, 14);
            if ("quench".equals(effect)) {
                BlockPos pos = originalBase.offset(2, 0, -4);
                ItemStack tongs = new ItemStack(ModItems.tongs(SmithingTier.DIAMOND));
                WorkpieceCodec.setHeld(tongs, workpiece(Items.IRON_SWORD, 18, WorkpieceState.SHAPED, "sword"));
                player.setItemInHand(InteractionHand.MAIN_HAND, tongs);
                useStation(level, player, pos);
                checks.put("quench_effect_source", "real SmithsTroughBlock.use server action (lowering into the water)");
            } else if ("grind".equals(effect)) {
                BlockPos pos = originalBase.offset(4, 0, -4);
                ItemStack sword = new ItemStack(Items.IRON_SWORD);
                new QualityData(80, 80, false).apply(sword);
                player.setItemInHand(InteractionHand.MAIN_HAND, sword);
                useStation(level, player, pos);
                useStation(level, player, pos);
                checks.put("grind_effect_source", "real SmithsGrindstoneBlock.use: rest the piece, then hold its edge to the wheel");
            } else {
                BlockPos pos = originalBase.offset(0, 0, -4);
                StationEffects.send(level, pos, StationEffects.SPARKS, 1.0F);
                checks.put("anvil_effect_source", "server StationEffectPacket injection; blows themselves are tested in the hands-on run");
            }
        });
    }

    /** A fake player right-clicks the pickup anvil with the same tongs: empty tongs take, loaded tongs place. */
    private static void tongsAtPickupAnvil(Minecraft mc, boolean expectEmptyTongs) {
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null || pickupAnvil == null) return;
        server.execute(() -> {
            ServerLevel level = server.overworld();
            if (pickupTongs.isEmpty()) pickupTongs = new ItemStack(ModItems.tongs(SmithingTier.IRON));
            if (WorkpieceCodec.isHolding(pickupTongs) == expectEmptyTongs) checks.put("anvil_pickup_tongs_state", false);
            FakePlayer fake = FakePlayerFactory.getMinecraft(level);
            fake.setItemInHand(InteractionHand.MAIN_HAND, pickupTongs);
            level.getBlockState(pickupAnvil).use(level, fake, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(pickupAnvil), Direction.UP, pickupAnvil, false));
            pickupTongs = fake.getMainHandItem();
            fake.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            pickupServerState = level.getBlockEntity(pickupAnvil) instanceof SmithsAnvilBlockEntity anvil && anvil.hasWorkpiece();
        });
    }

    /** Server, tongs and the rendering client agree: exactly one workpiece, either on the anvil or in the tongs. */
    private static void checkPickupAnvil(Minecraft mc, String name, boolean expectOnAnvil) {
        Boolean server = pickupServerState;
        boolean client = mc.level != null && mc.level.getBlockEntity(pickupAnvil) instanceof SmithsAnvilBlockEntity anvil && anvil.hasWorkpiece();
        boolean tongs = WorkpieceCodec.isHolding(pickupTongs);
        boolean pass = server != null && server == expectOnAnvil && client == expectOnAnvil && tongs != expectOnAnvil;
        checks.put(name + "_server_client_tongs", server + "/" + client + "/" + tongs);
        checks.put(name, pass);
        log(name + ": server=" + server + " client=" + client + " tongs=" + tongs + " -> " + (pass ? "ok" : "MISMATCH"));
    }

    private static void useStation(ServerLevel level, ServerPlayer player, BlockPos pos) {
        player.setGameMode(GameType.CREATIVE);
        level.getBlockState(pos).use(level, player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    }

    private static void performanceCamera(Minecraft mc) {
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null || performanceBase == null) return;
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayers().get(0);
            player.setGameMode(GameType.SPECTATOR);
            teleportLookAt(player, server.overworld(), performanceBase.getX() + .5, performanceBase.getY() + 11,
                    performanceBase.getZ() + 19.5, performanceBase.getX() + .5, performanceBase.getY() + .5,
                    performanceBase.getZ() + 3.5);
        });
    }

    private static void teleportLookAt(ServerPlayer player, ServerLevel level, double x, double y, double z,
                                       double targetX, double targetY, double targetZ) {
        double dx = targetX - x;
        double dy = targetY - y;
        double dz = targetZ - z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        player.teleportTo(level, x, y, z, yaw, pitch);
    }

    private static void stagePerformance(Minecraft mc, boolean active) {
        IntegratedServer server = mc.getSingleplayerServer();
        if (server != null) server.execute(() -> stagePerformance(server.overworld(), active));
    }

    private static void stagePerformance(ServerLevel level, boolean active) {
        if (performanceBase == null) return;
        int count = 0;
        for (int row = 0; row < 4; row++) for (int column = 0; column < 8; column++) {
            BlockPos pos = performanceBase.offset((column - 4) * 2, 0, row * 2);
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(pos, ModBlocks.SMITHS_FORGE.get().defaultBlockState()
                    .setValue(SmithsForgeBlock.FACING, Direction.SOUTH));
            if (active) {
                SmithsForgeBlockEntity forge = (SmithsForgeBlockEntity) level.getBlockEntity(pos);
                forge.insertMetal(new ItemStack(Items.IRON_INGOT, 16), 16, false, null);
                forge.insertFuel(new ItemStack(Items.COAL, 16), 16, false, null);
                forge.ignite();
            }
            count++;
        }
        checks.put(active ? "active_station_count" : "empty_station_count", count);
    }

    private static void setGuiScale(Minecraft mc, int scale) {
        mc.options.guiScale().set(scale);
        mc.resizeDisplay();
        Map<String, Object> dimensions = new LinkedHashMap<>();
        dimensions.put("scale", scale);
        dimensions.put("width", mc.getWindow().getGuiScaledWidth());
        dimensions.put("height", mc.getWindow().getGuiScaledHeight());
        checks.put("gui_scale_" + scale, dimensions);
        if (scale == 3) checks.put("gui_minimum_320x240", mc.getWindow().getGuiScaledWidth() >= 320
                && mc.getWindow().getGuiScaledHeight() >= 240);
    }

    private static void setGameMode(Minecraft mc, GameType mode) {
        IntegratedServer server = mc.getSingleplayerServer();
        if (server != null) server.execute(() -> server.getPlayerList().getPlayers().get(0).setGameMode(mode));
    }

    /** Makes inventory screenshots deterministic even when the host compositor ignores cursor warps. */
    private static final class FixedInventoryScreen extends InventoryScreen {
        private final boolean qualityTooltip;

        private FixedInventoryScreen(net.minecraft.world.entity.player.Player player, boolean qualityTooltip) {
            super(player);
            this.qualityTooltip = qualityTooltip;
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int fixedX = qualityTooltip ? leftPos + 8 + 18 + 8 : -100;
            int fixedY = qualityTooltip ? topPos + 142 + 8 : -100;
            super.render(graphics, fixedX, fixedY, partialTick);
            if (qualityTooltip) checks.put("inventory_quality_tooltip_rendered", true);
        }
    }

    /**
     * Sets the eight accessibility options to the full accessibility mode, or back to the player's own values, and
     * returns true when every one already matched. Forge autosaves each option and its file watcher reloads the
     * file, so a reload landing between two quick writes can revert one of them; callers converge on later ticks.
     */
    private static boolean accessibility(boolean mode) {
        boolean settled = converge(ClientConfig.REDUCED_SCREEN_SHAKE, mode || originalReducedShake);
        settled &= converge(ClientConfig.REDUCED_FLASHES, mode || originalReducedFlashes);
        settled &= converge(ClientConfig.HIGH_CONTRAST_MINIGAMES, mode || originalHighContrast);
        settled &= converge(ClientConfig.LARGE_MINIGAME_TARGETS, mode || originalLargeTargets);
        settled &= converge(ClientConfig.COLORBLIND_SAFE_TARGETS, mode || originalColorblind);
        settled &= converge(ClientConfig.PARTICLE_AMOUNT, mode ? ClientConfig.ParticleAmount.MINIMAL : originalParticles);
        settled &= converge(ClientConfig.REDUCED_MOTION, mode || originalReducedMotion);
        settled &= converge(ClientConfig.EXPANDED_STATUS, mode || originalExpandedStatus);
        return settled;
    }

    /** Writes the value only when it differs; true when it already matched. */
    private static <T> boolean converge(ForgeConfigSpec.ConfigValue<T> value, T target) {
        if (Objects.equals(value.get(), target)) return true;
        value.set(target);
        return false;
    }

    private static void openJei(Minecraft mc) {
        if (PackTestJeiPlugin.runtime() == null) {
            checks.put("jei_status", "runtime unavailable");
            return;
        }
        PackTestJeiPlugin.openSmithingRecipes();
        checks.put("jei_status", "smithing category opened");
    }

    private static ItemStack named(Item item, String name) {
        ItemStack stack = new ItemStack(item);
        stack.setHoverName(Component.literal(name));
        return stack;
    }

    private static void multiplayerStep(Minecraft mc) throws IOException {
        multiplayerTicks++;
        if (!multiplayerConnected && mc.screen instanceof TitleScreen && multiplayerTicks >= 60) {
            multiplayerConnected = true;
            ConnectScreen.startConnecting(mc.screen, mc, ServerAddress.parseString(MULTIPLAYER_SERVER),
                    new ServerData("Immersive Smithing visual multiplayer", MULTIPLAYER_SERVER, false), false);
            return;
        }
        if (mc.player == null || mc.level == null || mc.getConnection() == null) return;
        if (multiplayerJoinedAt < 0) multiplayerJoinedAt = multiplayerTicks;
        int joinedTicks = multiplayerTicks - multiplayerJoinedAt;
        // The observer exits immediately after acknowledging the hammer. Consume that acknowledgement
        // before requiring its entity to remain in the level, or disconnect timing can strand the leader.
        if (anvilPhaseTicks >= 0 || ("leader".equals(MULTIPLAYER_ROLE) && multiplayerReady
                && Files.exists(MULTIPLAYER_SHARED.resolve("hammer-observed"))
                && checks.containsKey("leader_first_person_hammer_captured"))) {
            checks.put("leader".equals(MULTIPLAYER_ROLE) ? "leader_remote_tongs_and_hammer_observed" : "remote_hammer_swing_observed", true);
            multiplayerAnvilPhase(mc);
            return;
        }
        if (mc.level.players().size() < 2 || !Files.exists(MULTIPLAYER_SHARED.resolve("server-ready"))) {
            if (joinedTicks > 1200) throw new IllegalStateException("two clients did not become visible to each other");
            return;
        }
        Files.createDirectories(MULTIPLAYER_SHARED);
        if ("leader".equals(MULTIPLAYER_ROLE)) {
            if (!multiplayerReady && joinedTicks >= 160) {
                multiplayerReady = true;
                recipeSyncChecks(mc);
                checks.put("leader_loaded_tongs", WorkpieceCodec.isHolding(mc.player.getMainHandItem()));
                shot(mc, "packtest_multiplayer_leader_loaded_tongs");
                marker("leader-ready");
            }
            if (multiplayerReady && Files.exists(MULTIPLAYER_SHARED.resolve("peer-ready"))) {
                boolean hammerReady = Files.exists(MULTIPLAYER_SHARED.resolve("hammer-ready"));
                if (hammerReady) {
                    if (multiplayerHammerReadyAt < 0) multiplayerHammerReadyAt = joinedTicks;
                    // Vanilla keeps the previous held model until its equip transition completes.
                    if (joinedTicks - multiplayerHammerReadyAt < 30) return;
                }
                if (!hammerReady && mc.player.swingTime == 3
                        && !checks.containsKey("leader_first_person_tongs_captured")) {
                    shot(mc, "packtest_multiplayer_first_person_tongs");
                    checks.put("leader_first_person_tongs_captured", true);
                }
                if (hammerReady && mc.player.getMainHandItem().is(ModItems.hammer(SmithingTier.NETHERITE))
                        && mc.player.swingTime == 3 && !checks.containsKey("leader_first_person_hammer_captured")) {
                    shot(mc, "packtest_multiplayer_first_person_hammer");
                    checks.put("leader_first_person_hammer_captured", true);
                }
                if (joinedTicks % 8 == 0) {
                    mc.player.swing(InteractionHand.MAIN_HAND);
                    marker(hammerReady ? "leader-hammer-swinging" : "leader-tongs-swinging");
                }
                if (Files.exists(MULTIPLAYER_SHARED.resolve("hammer-observed"))
                        && checks.containsKey("leader_first_person_hammer_captured")) {
                    checks.put("leader_remote_tongs_and_hammer_observed", true);
                    multiplayerAnvilPhase(mc);
                }
            }
        } else if ("peer".equals(MULTIPLAYER_ROLE)) {
            net.minecraft.world.entity.player.Player remote = mc.level.players().stream()
                    .filter(player -> player != mc.player).findFirst().orElse(null);
            if (remote == null) return;
            if (!multiplayerReady && joinedTicks >= 160) {
                multiplayerReady = true;
                recipeSyncChecks(mc);
                checks.put("remote_sky_light", mc.level.getBrightness(net.minecraft.world.level.LightLayer.SKY, remote.blockPosition()));
                checks.put("remote_block_light", mc.level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, remote.blockPosition()));
                checks.put("fixture_lighting", "vanilla night vision for pose inspection; station lighting tested separately");
                checks.put("remote_loaded_tongs", WorkpieceCodec.isHolding(remote.getMainHandItem()));
                shot(mc, "packtest_multiplayer_remote_idle");
                marker("peer-ready");
            }
            if (multiplayerReady && !remoteSwingObserved
                    && Files.exists(MULTIPLAYER_SHARED.resolve("leader-tongs-swinging"))
                    && remote.swingTime >= 3 && remote.swingTime <= 4) {
                remoteSwingObserved = true;
                checks.put("remote_loaded_tongs_swing_observed", true);
                checks.put("remote_tongs_capture_swing_tick", remote.swingTime);
                shot(mc, "packtest_multiplayer_remote_tongs_swing");
                marker("tongs-observed");
            }
            if (remoteSwingObserved && !remoteHammerSwingObserved
                    && Files.exists(MULTIPLAYER_SHARED.resolve("leader-hammer-swinging"))
                    && remote.getMainHandItem().is(ModItems.hammer(SmithingTier.NETHERITE))
                    && remote.swingTime >= 3 && remote.swingTime <= 4) {
                remoteHammerSwingObserved = true;
                checks.put("remote_hammer_swing_observed", true);
                checks.put("remote_hammer_capture_swing_tick", remote.swingTime);
                shot(mc, "packtest_multiplayer_remote_hammer_swing");
                marker("hammer-observed");
            }
            if (remoteHammerSwingObserved) {
                multiplayerAnvilPhase(mc);
            } else if (joinedTicks >= 1200) {
                checks.putIfAbsent("remote_loaded_tongs_swing_observed", remoteSwingObserved);
                checks.putIfAbsent("remote_hammer_swing_observed", false);
                finish(mc, false, "remote loaded-tongs or hammer swing was not observed");
            }
        } else {
            throw new IllegalStateException("unknown multiplayer role " + MULTIPLAYER_ROLE);
        }
    }

    /**
     * Both clients: the server places a workpiece on an anvil between the players; each client must render it, then,
     * after the leader's empty tongs take it, each client's own block entity must be empty (and the leader's tongs
     * loaded) - the report was a workpiece still drawn on the anvil after a tongs pickup.
     */
    private static void multiplayerAnvilPhase(Minecraft mc) throws IOException {
        String role = MULTIPLAYER_ROLE;
        if (anvilPhaseTicks < 0) {
            anvilPhaseTicks = 0;
            marker(role + "-swings-done");
        }
        anvilPhaseTicks++;
        if (anvilShotAt >= 0) {
            if (EXPECT.contains("epicknights")) {
                marker(role + "-anvil-done");
                multiplayerUpgradePhase(mc);
                return;
            }
            if (anvilPhaseTicks - anvilShotAt >= 10) {
                boolean pass = checks.values().stream().noneMatch(v -> v instanceof Boolean b && !b);
                finish(mc, pass, pass ? "remote swings synchronized and the anvil pickup cleared on this client"
                        : "a multiplayer check failed");
            }
            return;
        }
        if (anvilPhaseTicks > 1200) {
            checks.putIfAbsent(role + "_anvil_empty_after_pickup", false);
            finish(mc, false, "the anvil pickup phase did not complete");
            return;
        }
        if (!Files.exists(MULTIPLAYER_SHARED.resolve("anvil-placed"))) return;
        BlockPos anvilPos = MultiplayerPackTestFixture.ANVIL;
        boolean occupied = mc.level.getBlockEntity(anvilPos) instanceof SmithsAnvilBlockEntity anvil && anvil.hasWorkpiece();
        if (anvilSeenAt < 0) {
            if (!occupied) return;
            mc.player.setXRot("leader".equals(role) ? 45F : 30F);
            checks.put(role + "_anvil_visible_before_pickup", true);
            anvilSeenAt = anvilPhaseTicks;
            return;
        }
        if (anvilPhaseTicks == anvilSeenAt + 10) {
            shot(mc, "packtest_multiplayer_" + role + "_anvil_before_pickup");
            marker(role + "-anvil-seen");
            return;
        }
        if (!Files.exists(MULTIPLAYER_SHARED.resolve("anvil-picked"))) return;
        if (anvilPickedAt < 0) anvilPickedAt = anvilPhaseTicks;
        if (anvilPhaseTicks - anvilPickedAt < 20) return;
        String server = Files.readString(MULTIPLAYER_SHARED.resolve("anvil-picked"), StandardCharsets.UTF_8).trim();
        checks.put(role + "_anvil_server_state", server);
        checks.put(role + "_anvil_server_empty_and_tongs_loaded", server.contains("server_empty=true") && server.contains("tongs_loaded=true"));
        checks.put(role + "_anvil_empty_after_pickup", !occupied);
        if ("leader".equals(role)) checks.put("leader_tongs_loaded_after_pickup", WorkpieceCodec.isHolding(mc.player.getMainHandItem()));
        shot(mc, "packtest_multiplayer_" + role + "_anvil_after_pickup");
        anvilShotAt = anvilPhaseTicks;
    }

    /**
     * Dedicated server: the recipes this client received are the server's set after the smithing rewrite (forge recipes
     * added, the crafting recipes they replace removed), so the recipe book and JEI show what the server allows.
     */
    private static void recipeSyncChecks(Minecraft mc) throws IOException {
        String role = MULTIPLAYER_ROLE;
        String server = Files.readString(MULTIPLAYER_SHARED.resolve("server-recipes"), StandardCharsets.UTF_8);
        RecipeManager client = mc.getConnection().getRecipeManager();
        checks.put(role + "_recipe_count", client.getRecipes().size());
        checks.put(role + "_recipes_match_server", server.contains("count=" + client.getRecipes().size() + "\n"));
        if (EXPECT.contains("epicknights")) {
            checks.put(role + "_epicknights_armet_forged_not_crafted", client.byKey(ImmersiveSmithing.id("compat/magistuarmory/armet/from/magistuarmory/armet")).isPresent()
                    && client.byKey(new ResourceLocation("magistuarmory", "armet")).isEmpty());
            checks.put(role + "_epicknights_ceremonial_gilding_kept", client.byKey(new ResourceLocation("magistuarmory", "ceremonialarmet")).isPresent());
        }
        if (EXPECT.contains("epicfight")) {
            checks.put(role + "_epicfight_uchigatana_forged", client.byKey(ImmersiveSmithing.id("compat/epicfight/uchigatana")).isPresent());
            checks.put(role + "_epicfight_bokken_crafted", client.byKey(ImmersiveSmithing.id("compat/epicfight/bokken")).isPresent());
            checks.put(role + "_epicfight_longsword_forged_not_crafted", client.byKey(ImmersiveSmithing.id("compat/epicfight/iron_longsword")).isPresent()
                    && client.byKey(new ResourceLocation("epicfight", "iron_longsword")).isEmpty());
        }
    }

    private static void marker(String name) throws IOException {
        Files.writeString(MULTIPLAYER_SHARED.resolve(name), MULTIPLAYER_ROLE + "\n", StandardCharsets.UTF_8);
    }

    private static boolean upgradeSelected, upgradeCommitted, upgradeContended, upgradeCancelled, upgradeSynced;
    private static int upgradeReloadSeen = -1;

    /**
     * Checks the real server offer and preview, then pins and commits the decorated base through real client packets; the
     * other smith's inputs (block use, the leader's book session, draw and cancel requests) must not touch the job, a
     * reload keeps it, and the owner's confirmed cancel refunds the base exactly once.
     */
    private static void multiplayerUpgradePhase(Minecraft mc) throws IOException {
        String role = MULTIPLAYER_ROLE;
        if (Files.exists(MULTIPLAYER_SHARED.resolve("upgrade-result"))) {
            String result = Files.readString(MULTIPLAYER_SHARED.resolve("upgrade-result"));
            checks.put(role + "_upgrade_server_checks", result.contains("pass=true"));
            shot(mc, "packtest_multiplayer_" + role + "_upgrade_refund");
            boolean pass = checks.values().stream().noneMatch(v -> v instanceof Boolean b && !b);
            finish(mc, pass, pass ? "two clients verified reservation, authoritative preview, reload and exact refund" : "a multiplayer upgrade check failed");
            return;
        }
        if (anvilPhaseTicks - anvilShotAt > 1200) { finish(mc, false, "multiplayer upgrade timed out"); return; }
        Path offered = MULTIPLAYER_SHARED.resolve("upgrade-offered");
        if (!Files.exists(offered)) return;
        List<String> request = Files.readAllLines(offered);
        int session = Integer.parseInt(request.get(0)); ResourceLocation recipe = new ResourceLocation(request.get(1));
        BlockPos forgePos = MultiplayerPackTestFixture.UPGRADE_FORGE;
        SmithsForgeBlockEntity forge = mc.level.getBlockEntity(forgePos) instanceof SmithsForgeBlockEntity f ? f : null;
        if (role.equals("leader") && !upgradeSelected && mc.screen instanceof PatternBookScreen screen) {
            try {
                var field = PatternBookScreen.class.getDeclaredField("offer"); field.setAccessible(true);
                OpenForgeScreenPacket offer = (OpenForgeScreenPacket) field.get(screen);
                var row = offer.entries().stream().filter(e -> e.recipeId().equals(recipe)).findFirst().orElseThrow();
                checks.put("leader_upgrade_offer_exact_cost_and_base", row.metalUnits() == 45 && row.upgrade()
                        && row.auxiliary().stream().anyMatch(s -> s.is(item("magistuarmory:halfarmor_chestplate"))));
                checks.put("leader_upgrade_offer_personal_name", row.result().getHoverName().getString().equals("The Reserved Oath"));
                shot(mc, "packtest_multiplayer_leader_upgrade_offer");
                ModNetwork.CHANNEL.sendToServer(new com.otectus.immersivesmithing.network.packet.SelectForgeRecipePacket(session, recipe));
                upgradeSelected = true;
            } catch (ReflectiveOperationException failure) { throw new IOException(failure); }
        }
        if (role.equals("leader") && upgradeSelected && !upgradeCommitted && forge != null && recipe.equals(forge.plan())
                && !(mc.screen instanceof PatternBookScreen)) {
            // The plan is pinned beside the tray: commit it, as the tray part of the forge does.
            WorkClient.send(forgePos, WorkAction.COMMIT);
            upgradeCommitted = true;
        }
        if (role.equals("peer") && !upgradeContended && Files.exists(MULTIPLAYER_SHARED.resolve("upgrade-reserved"))) {
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new net.minecraft.world.phys.BlockHitResult(
                    Vec3.atCenterOf(forgePos), Direction.UP, forgePos, false));
            ModNetwork.CHANNEL.sendToServer(new com.otectus.immersivesmithing.network.packet.SelectForgeRecipePacket(session, recipe));
            ModNetwork.CHANNEL.sendToServer(new com.otectus.immersivesmithing.network.packet.CancelSessionPacket(session));
            WorkClient.send(forgePos, WorkAction.DRAW);
            WorkClient.send(forgePos, WorkAction.CANCEL_JOB);
            WorkClient.send(forgePos, WorkAction.CANCEL_JOB);
            marker("peer-upgrade-attempt"); upgradeContended = true;
        }
        if (!Files.exists(MULTIPLAYER_SHARED.resolve("upgrade-reloaded"))) return;
        if (upgradeReloadSeen < 0) upgradeReloadSeen = anvilPhaseTicks;
        if (anvilPhaseTicks - upgradeReloadSeen < 20) return;
        if (!upgradeSynced) {
            String counts = Files.readString(MULTIPLAYER_SHARED.resolve("upgrade-reloaded"));
            checks.put(role + "_upgrade_reload_recipes_match", counts.contains("count=" + mc.getConnection().getRecipeManager().getRecipes().size() + "\n")
                    && mc.getConnection().getRecipeManager().byKey(recipe).isPresent());
            upgradeSynced = true; marker(role + "-upgrade-synced");
        }
        if (role.equals("leader") && !upgradeCancelled) {
            ForgeJob job = forge != null ? forge.job() : null;
            if (job == null) { finish(mc, false, "the committed upgrade was lost on reload"); return; }
            // The billet on the tray carries the server-assembled result: the base's name, wear and enchantments.
            ItemStack preview = job.target;
            checks.put("leader_upgrade_authoritative_preview", preview.is(item("magistuarmory:knight_chestplate"))
                    && preview.getHoverName().getString().equals("The Reserved Oath") && preview.isEnchanted() && preview.getDamageValue() > 0);
            shot(mc, "packtest_multiplayer_leader_upgrade_after_reload");
            WorkClient.send(forgePos, WorkAction.CANCEL_JOB); // asks for confirmation
            WorkClient.send(forgePos, WorkAction.CANCEL_JOB); // confirms
            marker("leader-upgrade-cancelled"); upgradeCancelled = true;
        }
    }

    private static final class FrameMeasurement {
        private final String label;
        private final int warmupFrames;
        private final int sampleFrames;
        private final int stationCount;
        private final List<Double> intervals = new ArrayList<>();
        private final List<Double> durations = new ArrayList<>();
        private long frameStarted;
        private long previousFrameStarted;
        private long sampleStarted;
        private long sampleEnded;
        private int seen;

        private FrameMeasurement(String label, int warmupFrames, int sampleFrames, int stationCount) {
            this.label = label;
            this.warmupFrames = warmupFrames;
            this.sampleFrames = sampleFrames;
            this.stationCount = stationCount;
        }

        private void beginFrame(long now) {
            frameStarted = now;
        }

        private void endFrame(long now) {
            if (frameStarted == 0) return;
            if (seen >= warmupFrames && durations.size() < sampleFrames) {
                if (sampleStarted == 0) sampleStarted = frameStarted;
                durations.add((now - frameStarted) / 1_000_000.0);
                if (previousFrameStarted != 0) intervals.add((frameStarted - previousFrameStarted) / 1_000_000.0);
                sampleEnded = now;
            } else if (seen >= warmupFrames) {
                durations.add((now - frameStarted) / 1_000_000.0);
                intervals.add((frameStarted - previousFrameStarted) / 1_000_000.0);
                sampleEnded = now;
            }
            previousFrameStarted = frameStarted;
            frameStarted = 0;
            seen++;
        }

        private boolean done() {
            return durations.size() >= sampleFrames && sampleStarted != 0 && sampleEnded - sampleStarted >= 3_000_000_000L;
        }

        private Map<String, Object> result() {
            List<Double> sortedIntervals = intervals.stream().sorted().toList();
            List<Double> sortedDurations = durations.stream().sorted().toList();
            double average = intervals.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double averageDuration = durations.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("label", label);
            result.put("station_count", stationCount);
            result.put("warmup_frames", warmupFrames);
            result.put("minimum_sample_frames", sampleFrames);
            result.put("sample_frames", durations.size());
            result.put("elapsed_sample_ms", round((sampleEnded - sampleStarted) / 1_000_000.0));
            result.put("average_interval_ms", round(average));
            result.put("p50_interval_ms", round(percentile(sortedIntervals, .50)));
            result.put("p95_interval_ms", round(percentile(sortedIntervals, .95)));
            result.put("average_render_tick_ms", round(averageDuration));
            result.put("p50_render_tick_ms", round(percentile(sortedDurations, .50)));
            result.put("p95_render_tick_ms", round(percentile(sortedDurations, .95)));
            result.put("average_fps", round(average == 0 ? 0 : 1000 / average));
            return result;
        }

        private static double percentile(List<Double> values, double quantile) {
            if (values.isEmpty()) return 0;
            return values.get(Math.min(values.size() - 1, (int) Math.ceil(values.size() * quantile) - 1));
        }

        private static double round(double value) {
            return Math.round(value * 100.0) / 100.0;
        }
    }

    private static WorkpieceData workpiece(Item item, int units, WorkpieceState state, String pattern) {
        return new WorkpieceData(new ItemStack(item), ImmersiveSmithing.id("packtest"), new ResourceLocation("minecraft", "iron"), units,
                60, state == WorkpieceState.SHAPED ? 88 : -1, false, state, ImmersiveSmithing.id(pattern));
    }

    /** A recipe row the smith can choose. */
    private static OpenForgeScreenPacket.Entry entry(ResourceLocation id, ItemStack result, int units, List<ItemStack> auxiliary) {
        return new OpenForgeScreenPacket.Entry(id, result, units, auxiliary,
                OpenForgeScreenPacket.AFFORDABLE | OpenForgeScreenPacket.HAS_COMPONENTS | OpenForgeScreenPacket.ALLOWED, 0, Component.empty());
    }

    /** Iron's cast rows for 108 molten units: 12 ingots, or 108 nuggets. */
    private static List<OpenForgeScreenPacket.CastOption> ironCasts(int molten) {
        return List.of(new OpenForgeScreenPacket.CastOption(new ItemStack(Items.IRON_INGOT), 9, molten / 9),
                new OpenForgeScreenPacket.CastOption(new ItemStack(Items.IRON_NUGGET), 1, molten));
    }

    private static OpenForgeScreenPacket forgeOffer() {
        List<ItemStack> sticks = List.of(new ItemStack(Items.STICK, 2));
        return new OpenForgeScreenPacket(7, BlockPos.ZERO, Component.translatable("material.immersive_smithing.iron"), 108, null, List.of(
                entry(ImmersiveSmithing.id("smithing/iron_axe"), new ItemStack(Items.IRON_AXE), 27, sticks),
                entry(ImmersiveSmithing.id("smithing/iron_boots"), new ItemStack(Items.IRON_BOOTS), 36, List.of()),
                entry(ImmersiveSmithing.id("smithing/iron_pickaxe"), new ItemStack(Items.IRON_PICKAXE), 27, sticks),
                entry(ImmersiveSmithing.id("smithing/iron_sword"), new ItemStack(Items.IRON_SWORD), 18, List.of(new ItemStack(Items.STICK))),
                entry(ImmersiveSmithing.id("smithing/iron_helmet"), new ItemStack(Items.IRON_HELMET), 45, List.of()),
                entry(ImmersiveSmithing.id("smithing/shears"), new ItemStack(Items.SHEARS), 18, List.of())), ironCasts(108));
    }

    private static OpenForgeScreenPacket longForgeOffer() {
        List<ItemStack> sticks = List.of(named(Items.STICK, "Hand Carved Ash Wood Handle with a Very Long Name"));
        return new OpenForgeScreenPacket(7, BlockPos.ZERO,
                Component.literal("A Very Long Localized Tempered Iron Material Family Name"), 108, null, List.of(
                entry(ImmersiveSmithing.id("packtest/long_pickaxe"),
                        named(Items.IRON_PICKAXE, "An Exceptionally Long Tempered Iron Pickaxe Name"), 27, sticks),
                entry(ImmersiveSmithing.id("packtest/long_chestplate"),
                        named(Items.IRON_CHESTPLATE, "Reinforced Ceremonial Tempered Iron Chestplate"), 72, List.of()),
                entry(ImmersiveSmithing.id("packtest/long_sword"),
                        named(Items.IRON_SWORD, "Master Smith's Exceptionally Long Tempered Iron Sword"), 18, sticks)), ironCasts(108));
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), msg -> log("screenshot " + name));
    }

    /** Exercise actual focusable controls at both book layouts, including search and page boundaries. */
    private static void exerciseGuideNavigation(Minecraft mc) {
        boolean passed = true;
        for (int[] size : new int[][]{{320, 240}, {540, 360}}) {
            for (int chapter = 1; chapter <= com.otectus.immersivesmithing.guide.GuideData.chapterCount(); chapter++) {
                GuideScreen book = new GuideScreen(chapter);
                book.init(mc, size[0], size[1]);
                passed &= guidePreservesText(book, chapter);
            }
            GuideScreen guide = new GuideScreen(1);
            mc.setScreen(guide);
            guide.init(mc, size[0], size[1]);
            var previous = guide.pageButton(false);
            passed &= previous != null && !previous.active;
            guide.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN, 0, 0);
            previous = guide.pageButton(false);
            passed &= previous != null && previous.active;
            var contents = guideButton(guide, "screen.immersive_smithing.guide.contents");
            if (contents != null) contents.onPress();
            var search = guide.children().stream().filter(net.minecraft.client.gui.components.EditBox.class::isInstance)
                    .map(net.minecraft.client.gui.components.EditBox.class::cast).findFirst().orElseThrow();
            passed &= search.visible;
            search.setValue("no-such-smithing-topic");
            passed &= guide.children().stream().filter(net.minecraft.client.gui.components.Button.class::isInstance)
                    .map(net.minecraft.client.gui.components.Button.class::cast)
                    .noneMatch(b -> b.getMessage().getString().startsWith("1."));
            search.setValue("grindstone");
            int grindstoneChapter = java.util.Arrays.asList(com.otectus.immersivesmithing.guide.GuideData.CHAPTERS).indexOf("smiths_grindstone") + 1;
            var chapter = guide.children().stream().filter(net.minecraft.client.gui.components.Button.class::isInstance)
                    .map(net.minecraft.client.gui.components.Button.class::cast)
                    .filter(b -> b.getMessage().getString().startsWith(grindstoneChapter + ".")).findFirst().orElseThrow();
            guide.setFocused(chapter);
            passed &= guide.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, 0, 0);
            passed &= guide.pageButton(true).visible;
            guide.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_HOME, 0, 0);
            passed &= !guide.pageButton(false).active;
            for (var child : guide.children()) {
                if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget && widget.visible) {
                    passed &= widget.getX() >= 0 && widget.getY() >= 0
                            && widget.getX() + widget.getWidth() <= size[0] && widget.getY() + widget.getHeight() <= size[1];
                }
            }
        }
        checks.put("guide_search_keyboard_pagination_both_layouts", passed);
        mc.setScreen(new GuideScreen(2));
    }

    /** Read the rendered page model to catch dropped paragraphs and orphaned headings after wrapping. */
    private static boolean guidePreservesText(GuideScreen guide, int chapter) {
        try {
            var pagesField = GuideScreen.class.getDeclaredField("pages");
            pagesField.setAccessible(true);
            StringBuilder rendered = new StringBuilder();
            for (Object page : (List<?>) pagesField.get(guide)) {
                List<?> lines = (List<?>) page;
                for (Object line : lines) {
                    var text = line.getClass().getDeclaredMethod("text");
                    text.setAccessible(true);
                    ((net.minecraft.util.FormattedCharSequence) text.invoke(line)).accept((index, style, codePoint) -> {
                        rendered.appendCodePoint(codePoint);
                        return true;
                    });
                    rendered.append(' ');
                }
                var heading = lines.get(lines.size() - 1).getClass().getDeclaredMethod("heading");
                heading.setAccessible(true);
                if ((boolean) heading.invoke(lines.get(lines.size() - 1))) return false;
            }
            // The book opens each chapter with its title, then the chapter text.
            String source = com.otectus.immersivesmithing.guide.GuideData.chapterTitle(chapter).getString()
                    + Component.translatable(com.otectus.immersivesmithing.guide.GuideData.textKey(chapter)).getString().replace("## ", "");
            return source.replaceAll("\\s+", "").equals(rendered.toString().replaceAll("\\s+", ""));
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Guide page inspection failed", exception);
        }
    }

    private static net.minecraft.client.gui.components.Button guideButton(GuideScreen guide, String key) {
        String label = Component.translatable(key).getString();
        return guide.children().stream().filter(net.minecraft.client.gui.components.Button.class::isInstance)
                .map(net.minecraft.client.gui.components.Button.class::cast)
                .filter(b -> b.getMessage().getString().equals(label)).findFirst().orElse(null);
    }

    private static void finish(Minecraft mc, boolean pass, String reason) {
        finished = true;
        if (FocusController.active()) FocusController.exit(null, true);
        if (originalCameraMode != null) converge(ClientConfig.CAMERA_MODE, originalCameraMode);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pass", pass);
        result.put("reason", reason);
        result.put("title_ms", titleMs);
        result.put("world_ms", worldMs);
        result.put("checks", checks);
        try {
            Files.createDirectories(Path.of(OUTPUT));
            Files.writeString(Path.of(OUTPUT, "packtest-result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(result),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            ImmersiveSmithing.LOGGER.error("PACKTEST could not write result", e);
        }
        log("finished: pass=" + pass + " (" + reason + ")");
        mc.stop();
    }

    private static void log(String message) {
        ImmersiveSmithing.LOGGER.info("PACKTEST {}", message);
    }

    private ClientPackTest() {}
}
