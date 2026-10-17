package com.otectus.immersivesmithing.packtest;

import com.google.gson.GsonBuilder;
import com.otectus.immersivesmithing.block.SmithsForgeBlock;
import com.otectus.immersivesmithing.blockentity.SmithsForgeBlockEntity;
import com.otectus.immersivesmithing.blockentity.SmithsTroughBlockEntity;
import com.otectus.immersivesmithing.client.screen.AnvilMinigameScreen;
import com.otectus.immersivesmithing.client.screen.ForgeMinigameScreen;
import com.otectus.immersivesmithing.client.screen.GuideScreen;
import com.otectus.immersivesmithing.client.screen.MakersMarkScreen;
import com.otectus.immersivesmithing.item.SmithingTier;
import com.otectus.immersivesmithing.minigame.AnvilMinigame;
import com.otectus.immersivesmithing.minigame.AnvilRun;
import com.otectus.immersivesmithing.minigame.ForgeMinigame;
import com.otectus.immersivesmithing.minigame.ForgeRun;
import com.otectus.immersivesmithing.quality.QualityData;
import com.otectus.immersivesmithing.registry.ModBlocks;
import com.otectus.immersivesmithing.registry.ModItems;
import com.otectus.immersivesmithing.workpiece.WorkpieceCodec;
import net.minecraft.Util;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

/**
 * Opt-in CurseForge screenshot capture, confined to the test agent and enabled only by
 * {@code -Dimmersive_smithing.screenshots.output=<dir>}. It builds a smithy, drives real station actions and
 * server-scored minigame inputs, and saves framebuffer captures without HUD, chat or toasts.
 */
@Mod.EventBusSubscriber(modid = PackTestAgent.MOD_ID, value = Dist.CLIENT)
public final class StoreScreenshots {
    private static final String OUTPUT = System.getProperty("immersive_smithing.screenshots.output");
    private static final int WIDTH = 1920, HEIGHT = 1080;
    private static final Map<String, Object> EVIDENCE = new LinkedHashMap<>();
    private static final List<String> SHOTS = new ArrayList<>();
    private static CompletableFuture<Void> pending;
    private static int step, action, baseY, swordSlot;
    private static long entered, started;
    private static boolean finished, hideHud = true, forgeShot, anvilShot, renamed;
    private static BlockPos forge, anvil, trough, grind;
    private static volatile float lockYaw = Float.NaN, lockPitch;

    static boolean enabled() { return OUTPUT != null; }

    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Pre event) {
        if (enabled() && hideHud) event.setCanceled(true);
    }

    /** The player profile is built from the session user when the world is created, so rename it on the title screen. */
    @SubscribeEvent
    public static void onScreen(ScreenEvent.Init.Post event) {
        if (!enabled() || renamed || !(event.getScreen() instanceof TitleScreen)) return;
        renamed = true;
        try {
            var name = net.minecraft.client.User.class.getDeclaredField("name");
            name.setAccessible(true);
            name.set(Minecraft.getInstance().getUser(), "Otectus");
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not rename the showcase player", e);
        }
    }

    /** Forge presses are timed per frame; client ticks are too coarse to land inside the zone's centre. */
    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (!enabled() || event.phase != TickEvent.Phase.START) return;
        lockView(Minecraft.getInstance());
        if (step != 4 || !(Minecraft.getInstance().screen instanceof ForgeMinigameScreen screen)) return;
        try {
            if (!field(screen, "stage").toString().equals("PLAYING")) return;
            ForgeRun run = (ForgeRun) field(screen, "run");
            if (run.isComplete()) return;
            int elapsed = (int) (Util.getMillis() - (long) field(screen, "clientStartMs"));
            var phase = run.phases().get(run.index());
            int local = elapsed - run.phaseStartMs();
            float accuracy = ForgeMinigame.accuracy(phase, ForgeMinigame.position(phase, local / 1000F));
            if (!forgeShot && run.index() == 2) {
                // Hold the last pass until the marker is caught approaching the zone.
                if (local > 700 && accuracy > .3F && accuracy < .85F) {
                    shot(Minecraft.getInstance(), "forge_play");
                    forgeShot = true;
                }
                return;
            }
            if (local > 900 && accuracy >= (local > 6000 ? .8F : .97F)) {
                // The screen ignores held keys, so each work stroke is a full press and release.
                screen.keyPressed(GLFW.GLFW_KEY_SPACE, 0, 0);
                screen.keyReleased(GLFW.GLFW_KEY_SPACE, 0, 0);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Forge input failed", e);
        }
    }

    static void tick(Minecraft mc) throws Exception {
        if (finished) return;
        if (started == 0) started = Util.getMillis();
        if (Util.getMillis() - started > 240_000) throw new IllegalStateException("Store screenshots stalled at step " + step);
        mc.getToasts().clear();
        lockView(mc);
        if (mc.screen != null && !(mc.screen instanceof InventoryScreen)) parkCursor(mc);
        if (pending != null && !pending.isDone()) return;
        if (pending != null) { pending.join(); pending = null; }
        long age = Util.getMillis() - entered;
        switch (step) {
            case 0 -> {
                Files.createDirectories(Path.of(OUTPUT));
                baseY = mc.player.blockPosition().getY();
                forge = new BlockPos(-3, baseY, -3);
                anvil = new BlockPos(-4, baseY, 0);
                trough = new BlockPos(2, baseY, -2);
                grind = new BlockPos(5, baseY, -2);
                mc.getWindow().setWindowed(WIDTH, HEIGHT);
                mc.options.guiScale().set(3);
                mc.resizeDisplay();
                mc.options.fov().set(70);
                mc.options.bobView().set(false);
                mc.options.renderDistance().set(12);
                mc.options.pauseOnLostFocus = false;
                mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0D);
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.options.hideGui = false;
                mc.getTutorial().setStep(TutorialSteps.NONE);
                server(mc, StoreScreenshots::buildSmithy);
                select(mc, 1);
                advance(1);
            }
            case 1 -> {
                if (mc.getWindow().getWidth() != WIDTH && age > 1500 && action == 0) { mc.getWindow().toggleFullScreen(); action++; }
                if (age > 6000) {
                    EVIDENCE.put("framebuffer", mc.getMainRenderTarget().width + "x" + mc.getMainRenderTarget().height);
                    shot(mc, "smithy_wide");
                    server(mc, (level, player) -> {
                        ((SmithsForgeBlockEntity) level.getBlockEntity(forge)).ignite();
                        look(player, -1.4, 1.55, -0.4, -2.5, 0.5, -2.5);
                    });
                    select(mc, 0);
                    advance(2);
                }
            }
            case 2 -> {
                if (age > 5000 && mc.level.getBlockEntity(forge) instanceof SmithsForgeBlockEntity block && block.isReady()) {
                    shot(mc, "forge_burning");
                    select(mc, 0);
                    use(mc, forge);
                    advance(3);
                }
            }
            case 3 -> {
                if (mc.screen instanceof ForgeMinigameScreen screen) {
                    if (action == 0 && age > 1300) { shot(mc, "forge_select"); action++; }
                    if (action == 1 && age > 1600) {
                        screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                                .findFirst().orElseThrow().setValue("iron sword");
                        action++;
                    }
                    if (action == 2 && age > 2600) { shot(mc, "forge_select_search"); screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0); screen.keyReleased(GLFW.GLFW_KEY_ENTER, 0, 0); advance(4); }
                }
            }
            case 4 -> {
                if (!(mc.screen instanceof ForgeMinigameScreen screen)) break;
                // Strokes and the play shot are taken per frame in onRenderTick; this step only waits for the verdict.
                if (field(screen, "stage").toString().equals("RESULT")) {
                    if (action == 0) { EVIDENCE.put("forge_score", field(screen, "resultScore")); action++; entered = Util.getMillis(); }
                    else if (age > 1600) {
                        shot(mc, "forge_result");
                        mc.screen.onClose();
                        advance(5);
                    }
                }
            }
            case 5 -> {
                if (action == 0 && age > 600) {
                    if (!WorkpieceCodec.isHolding(mc.player.getMainHandItem())) throw new IllegalStateException("Forge did not produce a workpiece");
                    server(mc, (level, player) -> look(player, -2.6, 1.75, 2.6, -3.5, 0.6, 0.5));
                    action++;
                }
                if (action == 1 && age > 1400) { use(mc, anvil); action++; }
                if (action == 2 && age > 2300) {
                    select(mc, 1);
                    server(mc, (level, player) -> {
                        // A fresh charge: the forge only lights while it holds unmelted metal.
                        var block = (SmithsForgeBlockEntity) level.getBlockEntity(forge);
                        block.insertMetal(new ItemStack(Items.IRON_INGOT, 3), 3, false, null);
                        block.insertFuel(new ItemStack(Items.COAL, 4), 4, false, null);
                        EVIDENCE.put("hero_reignite", block.ignite().toString());
                        look(player, -1.1, 1.45, 2.3, -3.0, 0.7, -0.5);
                    });
                    action++;
                }
                if (action == 3 && age > 4200) { shot(mc, "anvil_workpiece"); action++; }
                if (action == 4 && age > 4500) {
                    server(mc, (level, player) -> look(player, -2.6, 1.75, 2.6, -3.5, 0.6, 0.5));
                    action++;
                }
                if (action == 5 && age > 5200) { use(mc, anvil); advance(6); }
            }
            case 6 -> {
                if (!(mc.screen instanceof AnvilMinigameScreen screen)) break;
                String stage = field(screen, "stage").toString();
                if (stage.equals("PLAYING")) {
                    AnvilRun run = (AnvilRun) field(screen, "run");
                    int elapsed = (int) (Util.getMillis() - (long) field(screen, "clientStartMs"));
                    if (run.isComplete()) break;
                    int ideal = AnvilMinigame.idealMs(run.pattern());
                    int into = elapsed - run.spawnMs();
                    if (!anvilShot && run.index() == 3 && into >= ideal * 0.55) { shot(mc, "anvil_play"); anvilShot = true; }
                    if (into >= ideal - 40) {
                        var target = run.currentTarget();
                        int x = (int) field(screen, "left") + 12;
                        int y = (int) field(screen, "top") + 28;
                        screen.mouseClicked(x + target.x() * 176, y + target.y() * 176, 0);
                    }
                } else if (stage.equals("RESULT")) {
                    if (action == 0) { EVIDENCE.put("anvil_score", field(screen, "resultScore")); action++; entered = Util.getMillis(); }
                    else if (age > 1600) {
                        shot(mc, "anvil_result");
                        mc.screen.onClose();
                        select(mc, 0);
                        advance(7);
                    }
                }
            }
            case 7 -> {
                if (action == 0 && age > 500) { use(mc, anvil); action++; }
                if (action == 1 && age > 1500) {
                    if (!WorkpieceCodec.isHolding(mc.player.getMainHandItem())) throw new IllegalStateException("Anvil pickup failed");
                    server(mc, (level, player) -> look(player, 2.4, 1.8, 0.7, 2.5, 0.45, -1.5));
                    action++;
                }
                if (action == 2 && age > 2600) { use(mc, trough); action++; }
                if (action == 3 && age > 3300 && mc.screen instanceof MakersMarkScreen screen) {
                    ((EditBox) field(screen, "titleBox")).setValue("Emberfang");
                    @SuppressWarnings("unchecked") var lines = (List<EditBox>) field(screen, "lineBoxes");
                    lines.get(0).setValue("Tempered at dusk in Ashford");
                    lines.get(1).setValue("for the Warden of the Vale");
                    action++;
                }
                if (action == 4 && age > 4300) {
                    shot(mc, "makers_mark");
                    var sign = MakersMarkScreen.class.getDeclaredMethod("sign");
                    sign.setAccessible(true);
                    sign.invoke(mc.screen);
                    action++;
                }
                if (action == 5 && age > 5300) {
                    if (mc.screen != null) mc.screen.onClose();
                    swordSlot = -1;
                    for (int i = 0; i < 9; i++) if (mc.player.getInventory().getItem(i).is(Items.IRON_SWORD)) swordSlot = i;
                    if (swordSlot < 0) throw new IllegalStateException("Quench did not produce the sword");
                    EVIDENCE.put("quenched_quality", QualityData.get(mc.player.getInventory().getItem(swordSlot)).orElseThrow());
                    select(mc, swordSlot);
                    server(mc, (level, player) -> look(player, 4.6, 1.8, 0.8, 5.5, 0.5, -1.5));
                    action++;
                }
                if (action == 6 && age > 6200) { use(mc, grind); action++; }
                if (action == 7 && age > 6900) { use(mc, grind); action++; }
                if (action == 8 && age > 8700) {
                    EVIDENCE.put("refined_quality", QualityData.get(mc.player.getInventory().getItem(swordSlot)).orElseThrow());
                    hideJeiOverlay();
                    mc.setScreen(new InventoryScreen(mc.player) {
                        @Override public void render(GuiGraphics g, int mx, int my, float partial) {
                            super.render(g, leftPos + 8 + swordSlot * 18 + 8, topPos + 142 + 8, partial);
                        }
                    });
                    advance(8);
                }
            }
            case 8 -> {
                if (action == 0 && age > 1500) { shot(mc, "quality_tooltip"); action++; }
                if (action == 1 && age > 1800) {
                    mc.options.guiScale().set(4);
                    mc.resizeDisplay();
                    action++;
                }
                if (action == 2 && age > 3000) { shot(mc, "quality_tooltip_large"); action++; }
                if (action == 3 && age > 3300) {
                    mc.options.guiScale().set(3);
                    mc.resizeDisplay();
                    mc.setScreen(new GuideScreen(1));
                    advance(9);
                }
            }
            case 9 -> {
                if (age > 1800) {
                    shot(mc, "guide");
                    mc.setScreen(null);
                    select(mc, 1);
                    server(mc, (level, player) -> look(player, 3.2, 2.6, 5.6, -0.8, 0.6, -1.6));
                    advance(10);
                }
            }
            case 10 -> {
                if (age > 3000) {
                    shot(mc, "smithy_outro");
                    EVIDENCE.put("shots", SHOTS);
                    EVIDENCE.put("pass", true);
                    Files.writeString(Path.of(OUTPUT, "result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(EVIDENCE));
                    finished = true;
                    mc.stop();
                }
            }
        }
    }

    private static void shot(Minecraft mc, String name) {
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            String file = String.format("%02d_%s.png", SHOTS.size() + 1, name);
            image.writeToFile(Path.of(OUTPUT, file));
            SHOTS.add(file);
        } catch (Exception e) {
            throw new IllegalStateException("Screenshot " + name + " failed", e);
        }
    }

    /** Pins the view to the last scripted camera so pointer motion reaching the window cannot turn the shot. */
    private static void lockView(Minecraft mc) {
        var player = mc.player;
        if (player == null || Float.isNaN(lockYaw)) return;
        player.setYRot(lockYaw);
        player.setXRot(lockPitch);
        player.yRotO = lockYaw;
        player.xRotO = lockPitch;
        player.setYHeadRot(lockYaw);
        player.yHeadRotO = lockYaw;
        player.yBodyRot = lockYaw;
        player.yBodyRotO = lockYaw;
    }

    /** Keeps the pointer in a corner so no stray hover state shows; gamescope ignores cursor warps. */
    private static void parkCursor(Minecraft mc) {
        try {
            for (String name : new String[]{"xpos", "ypos"}) {
                var field = net.minecraft.client.MouseHandler.class.getDeclaredField(name);
                field.setAccessible(true);
                field.setDouble(mc.mouseHandler, 1D);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not park the cursor", e);
        }
    }

    private static void select(Minecraft mc, int slot) {
        mc.player.getInventory().selected = slot;
        server(mc, (level, player) -> { player.getInventory().selected = slot; player.inventoryMenu.broadcastChanges(); });
    }

    private static void use(Minecraft mc, BlockPos position) {
        server(mc, (level, player) -> level.getBlockState(position).use(level, player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(position), Direction.UP, position, false)));
        mc.player.swing(InteractionHand.MAIN_HAND);
    }

    private static void server(Minecraft mc, BiConsumer<ServerLevel, ServerPlayer> work) {
        var server = mc.getSingleplayerServer();
        if (server == null) throw new IllegalStateException("Store screenshots need an integrated server");
        pending = CompletableFuture.runAsync(() -> work.accept(server.overworld(), server.getPlayerList().getPlayers().get(0)), server);
    }

    /** Places the eye at (x, baseY + y, z) looking at (tx, baseY + ty, tz); flight keeps elevated cameras still. */
    private static void look(ServerPlayer player, double x, double y, double z, double tx, double ty, double tz) {
        double dx = tx - x, dy = ty - y, dz = tz - z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        player.getAbilities().mayfly = true;
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        lockYaw = yaw;
        lockPitch = pitch;
        player.teleportTo(player.serverLevel(), x, baseY + y - player.getEyeHeight(), z, yaw, pitch);
    }

    private static void cmd(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput().withPermission(4), command);
    }

    private static void fill(MinecraftServer server, int x1, int y1, int z1, int x2, int y2, int z2, String block) {
        cmd(server, "fill " + x1 + " " + (baseY + y1) + " " + z1 + " " + x2 + " " + (baseY + y2) + " " + z2 + " " + block);
    }

    private static void set(MinecraftServer server, int x, int y, int z, String block) {
        cmd(server, "setblock " + x + " " + (baseY + y) + " " + z + " " + block);
    }

    private static void buildSmithy(ServerLevel level, ServerPlayer player) {
        MinecraftServer server = level.getServer();
        level.setDayTime(12650);
        level.setWeatherParameters(60000, 0, false, false);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_ANNOUNCE_ADVANCEMENTS).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_SENDCOMMANDFEEDBACK).set(false, server);
        cmd(server, "kill @e[type=!player]");
        fill(server, -24, 0, -24, 24, 12, 24, "air");
        fill(server, -24, -1, -24, 24, -1, 24, "grass_block");
        RandomSource random = RandomSource.create(20260927L);
        // Yard: a cobbled apron in front of the smithy, meadow elsewhere.
        for (int x = -24; x <= 24; x++) for (int z = -24; z <= 24; z++) {
            boolean floor = x >= -9 && x <= 9 && z >= -3 && z <= 5;
            boolean apron = !floor && Math.abs(x) <= 11 && z >= -3 && z <= 7 && random.nextFloat() < .55F;
            float r = random.nextFloat();
            if (floor) {
                set(server, x, -1, z, r < .5F ? "stone_bricks" : r < .7F ? "polished_andesite" : r < .85F ? "cobblestone" : r < .93F ? "cracked_stone_bricks" : "mossy_stone_bricks");
            } else if (apron) {
                set(server, x, -1, z, r < .5F ? "cobblestone" : r < .8F ? "gravel" : "coarse_dirt");
            } else if (z > -5 || Math.abs(x) > 10) {
                if (r < .18F) set(server, x, 0, z, "grass");
                else if (r < .20F) set(server, x, 0, z, random.nextBoolean() ? "poppy" : "dandelion");
                else if (r < .21F) set(server, x, 0, z, "oxeye_daisy");
            }
        }
        // Back wall with a chimney over the forge.
        fill(server, -9, 0, -4, 9, 0, -4, "stone_bricks");
        fill(server, -9, 1, -4, 9, 3, -4, "spruce_planks");
        for (int x : new int[]{-9, -6, 0, 4, 9}) fill(server, x, 1, -4, x, 3, -4, "stripped_spruce_log");
        fill(server, -9, 4, -4, 9, 4, -4, "spruce_log[axis=x]");
        fill(server, 6, 2, -4, 7, 2, -4, "iron_bars");
        fill(server, 1, 2, -4, 3, 2, -4, "spruce_trapdoor[half=bottom,open=true,facing=south]");
        // Left wall, posts and a half roof so the forge sits in shade.
        fill(server, -9, 0, -3, -9, 0, 1, "stone_bricks");
        fill(server, -9, 1, -3, -9, 3, 1, "spruce_planks");
        fill(server, -9, 1, 1, -9, 4, 1, "stripped_spruce_log");
        fill(server, 9, 0, -1, 9, 4, -1, "stripped_spruce_log");
        fill(server, -9, 5, -4, 9, 5, -2, "spruce_slab[type=bottom]");
        fill(server, -9, 4, -1, 9, 4, -1, "spruce_log[axis=x]");
        fill(server, -9, 5, -1, 9, 5, -1, "spruce_stairs[facing=north,half=bottom]");
        fill(server, -4, 0, -4, -2, 7, -4, "stone_bricks");
        fill(server, -4, 2, -3, -2, 2, -3, "stone_brick_slab[type=top]");
        fill(server, -4, 3, -3, -2, 3, -3, "stone_bricks");
        fill(server, -3, 4, -3, -3, 8, -3, "stone_bricks");
        fill(server, -4, 8, -4, -2, 8, -4, "stone_brick_wall");
        for (int x : new int[]{-7, 1, 6}) set(server, x, 3, -1, "lantern[hanging=true]");
        // Props.
        set(server, -5, 0, -3, "coal_block");
        set(server, -6, 0, -3, "barrel[facing=up]");
        set(server, -7, 0, -3, "chest[facing=south]");
        set(server, -8, 0, -3, "barrel[facing=up]");
        set(server, -8, 1, -3, "barrel[facing=up]");
        set(server, 7, 0, -3, "barrel[facing=up]");
        set(server, 8, 0, -3, "smithing_table");
        set(server, 8, 0, -2, "barrel[facing=up]");
        set(server, -8, 0, 0, "water_cauldron[level=3]");
        cmd(server, "summon armor_stand 7.5 " + baseY + " 0.2 {Rotation:[35f,0f],ShowArms:1b,"
                + "ArmorItems:[{id:\"minecraft:iron_boots\",Count:1b},{id:\"minecraft:iron_leggings\",Count:1b},"
                + "{id:\"minecraft:iron_chestplate\",Count:1b},{id:\"minecraft:iron_helmet\",Count:1b}],"
                + "HandItems:[{id:\"minecraft:iron_sword\",Count:1b},{}],Pose:{RightArm:[-20f,0f,10f]}}");
        String[] wallTools = {"iron_axe", "iron_pickaxe", "iron_shovel", "iron_sword"};
        for (int i = 0; i < wallTools.length; i++)
            cmd(server, "summon item_frame " + (-8 + i + .5) + " " + (baseY + 2.5) + " -2.5 {Facing:3b,Fixed:1b,Item:{id:\"minecraft:" + wallTools[i] + "\",Count:1b}}");
        // Trees and hedges frame the yard.
        int[][] trees = {{-7, -9}, {-1, -11}, {5, -9}, {11, -12}, {-13, -6}, {14, -3}, {-15, 4}, {15, 8}};
        String[] kinds = {"oak", "birch", "fancy_oak", "oak", "birch", "oak", "fancy_oak", "birch"};
        for (int i = 0; i < trees.length; i++)
            cmd(server, "place feature minecraft:" + kinds[i] + " " + trees[i][0] + " " + baseY + " " + trees[i][1]);
        // Stations.
        level.setBlockAndUpdate(forge, ModBlocks.SMITHS_FORGE.get().defaultBlockState().setValue(SmithsForgeBlock.FACING, Direction.SOUTH));
        level.setBlockAndUpdate(anvil, ModBlocks.SMITHS_ANVIL.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        level.setBlockAndUpdate(trough, ModBlocks.SMITHS_TROUGH.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        level.setBlockAndUpdate(grind, ModBlocks.SMITHS_GRINDSTONE.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        var block = (SmithsForgeBlockEntity) level.getBlockEntity(forge);
        block.insertMetal(new ItemStack(Items.IRON_INGOT, 12), 12, false, null);
        block.insertFuel(new ItemStack(Items.COAL, 8), 8, false, null);
        ((SmithsTroughBlockEntity) level.getBlockEntity(trough)).addBucket();
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        player.getInventory().setItem(0, new ItemStack(ModItems.tongs(SmithingTier.IRON)));
        player.getInventory().setItem(1, new ItemStack(ModItems.hammer(SmithingTier.IRON)));
        player.getInventory().setItem(7, new ItemStack(Items.IRON_INGOT, 14));
        player.getInventory().setItem(8, new ItemStack(ModItems.SMITHING_GUIDE.get()));
        player.getInventory().setItem(10, new ItemStack(Items.COAL, 23));
        player.getInventory().setItem(9, new ItemStack(Items.STICK, 16));
        player.getInventory().setItem(11, new ItemStack(Items.LEATHER, 6));
        player.getInventory().setItem(12, new ItemStack(Items.IRON_INGOT, 5));
        player.getInventory().setItem(20, new ItemStack(Items.WATER_BUCKET));
        player.getInventory().selected = 1;
        player.inventoryMenu.broadcastChanges();
        look(player, 2.8, 2.3, 6.4, -1.0, 0.8, -2.0);
    }

    /** JEI's item list is noise in a tooltip shot; its toggle is internal, so reach it reflectively and carry on without it. */
    private static void hideJeiOverlay() {
        try {
            Object state = Class.forName("mezz.jei.common.Internal").getMethod("getClientToggleState").invoke(null);
            if ((boolean) state.getClass().getMethod("isOverlayEnabled").invoke(state))
                state.getClass().getMethod("toggleOverlayEnabled").invoke(state);
            EVIDENCE.put("jei_overlay_hidden", true);
        } catch (Exception | LinkageError e) {
            EVIDENCE.put("jei_overlay_hidden", e.toString());
        }
    }

    private static Object field(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void advance(int next) {
        step = next;
        action = 0;
        entered = Util.getMillis();
    }

    private StoreScreenshots() {}
}
