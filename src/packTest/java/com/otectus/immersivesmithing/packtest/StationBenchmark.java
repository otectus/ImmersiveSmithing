package com.otectus.immersivesmithing.packtest;

import com.google.gson.GsonBuilder;
import com.otectus.immersivesmithing.ImmersiveSmithing;
import com.otectus.immersivesmithing.blockentity.SmithsAnvilBlockEntity;
import com.otectus.immersivesmithing.blockentity.SmithsForgeBlockEntity;
import com.otectus.immersivesmithing.blockentity.SmithsTroughBlockEntity;
import com.otectus.immersivesmithing.registry.ModBlocks;
import com.otectus.immersivesmithing.workpiece.WorkpieceData;
import com.otectus.immersivesmithing.workpiece.WorkpieceState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.Screenshot;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Opt-in station benchmark ({@code -Dimmersive_smithing.packtest.benchmark=<label>}, the {@code -PstationBenchmark}
 * client run). After the agent's flat world settles it builds rows of all four stations in every facing and measures
 * real render frames in fixed scenes: one station, a four-station workshop, 32 and 128 stations idle and active, the
 * 128 active with the camera turning, at night and without particles, and the selection outline of each station.
 * Window 1280 x 720, vsync off, frame limit 260, render distance 8, creative flight, clear weather, daylight cycle
 * stopped, HUD hidden except in the outline scenes (the outline needs it). Writes
 * {@code station-benchmark-<label>.json} (frame intervals: median, p95, p99; render ticks; memory) to the agent's output
 * directory and saves a screenshot per scene, then quits. Before the scenes it records each station's baked quads, outline
 * boxes and edges, and the CPU time to tessellate it into a chunk buffer. Run it on the same machine before and after a
 * change, alternating the two several times: frame times move with everything else the machine is doing.
 */
@Mod.EventBusSubscriber(modid = PackTestAgent.MOD_ID, value = Dist.CLIENT)
public final class StationBenchmark {
    private static final String LABEL = System.getProperty("immersive_smithing.packtest.benchmark");
    private static final String OUTPUT = System.getProperty(PackTestAgent.OUTPUT_PROPERTY, ".");
    private static final int WARMUP_FRAMES = 120;
    private static final int SAMPLE_FRAMES = 600;
    private static final long SAMPLE_NANOS = 5_000_000_000L;

    private record Scene(String name, int stations, boolean active, boolean rotate, boolean night, boolean particles, String target) {}

    // The outline scenes reuse the idle workshop, before the large grids, so no teardown is still settling while they run.
    private static final List<Scene> SCENES = List.of(
            new Scene("single_idle", 1, false, false, false, true, null),
            new Scene("workshop_idle", 4, false, false, false, true, null),
            new Scene("outline_forge", 4, false, false, false, true, "forge"),
            new Scene("outline_anvil", 4, false, false, false, true, "anvil"),
            new Scene("outline_trough", 4, false, false, false, true, "trough"),
            new Scene("outline_grindstone", 4, false, false, false, true, "grindstone"),
            new Scene("workshop_active", 4, true, false, false, true, null),
            new Scene("grid32_idle", 32, false, false, false, true, null),
            new Scene("grid32_active", 32, true, false, false, true, null),
            new Scene("grid128_idle", 128, false, false, false, true, null),
            new Scene("grid128_active", 128, true, false, false, true, null),
            new Scene("grid128_active_rotating", 128, true, true, false, true, null),
            new Scene("grid128_active_night", 128, true, false, true, true, null),
            new Scene("grid128_active_no_particles", 128, true, false, false, false, null));

    private static final Map<String, Object> RESULTS = new LinkedHashMap<>();
    private static int sceneIndex = -1;
    private static int wait;
    private static BlockPos base;
    private static Measurement measurement;
    private static boolean finished;
    private static float rotateYaw;

    static boolean enabled() {
        return LABEL != null && !LABEL.isBlank();
    }

    /** Called by the agent every client tick once the world has settled. */
    static void tick(Minecraft mc) {
        if (finished) return;
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null) return;
        if (sceneIndex < 0) {
            setup(mc, server);
            sceneIndex = 0;
            wait = 60;
            return;
        }
        if (wait > 0) {
            if (--wait == 0) measurement = new Measurement();
            return;
        }
        if (measurement == null || !measurement.done()) return;
        Scene scene = SCENES.get(sceneIndex);
        Map<String, Object> result = measurement.result();
        result.put("stations", scene.stations());
        result.put("active", scene.active());
        // What the crosshair was on when the sample ended: the outline scenes must name their station.
        result.put("crosshair", mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                ? mc.level.getBlockState(hit.getBlockPos()).getBlock().getDescriptionId() : "none");
        RESULTS.put(scene.name(), result);
        measurement = null;
        Screenshot.grab(mc.gameDirectory, "benchmark_" + LABEL + "_" + scene.name() + ".png", mc.getMainRenderTarget(), m -> {});
        sceneIndex++;
        if (sceneIndex >= SCENES.size()) {
            finish(mc);
            return;
        }
        stage(mc, server, SCENES.get(sceneIndex));
        wait = 100;
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (!enabled() || finished) return;
        Minecraft mc = Minecraft.getInstance();
        if (event.phase == TickEvent.Phase.START && sceneIndex >= 0 && sceneIndex < SCENES.size() && SCENES.get(sceneIndex).rotate()
                && mc.player != null) {
            // A steady sweep of 70 degrees either side of the grid (yaw 0 looks along +z), like a player looking over a workshop.
            rotateYaw += 0.6F;
            float yaw = 70F * (float) Math.sin(Math.toRadians(rotateYaw));
            mc.player.yRotO = yaw;
            mc.player.setYRot(yaw);
            mc.player.setYHeadRot(yaw);
        }
        if (measurement == null) return;
        if (event.phase == TickEvent.Phase.START) measurement.begin(System.nanoTime());
        else measurement.end(System.nanoTime());
    }

    private static void setup(Minecraft mc, IntegratedServer server) {
        mc.options.enableVsync().set(false);
        mc.options.framerateLimit().set(260);
        mc.options.renderDistance().set(8);
        ClientPackTest.sizeWindow(mc, 1280, 720);
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("window", "1280x720");
        settings.put("vsync", false);
        settings.put("frame_limit", 260);
        settings.put("render_distance", 8);
        settings.put("gl_renderer", org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER));
        settings.put("gl_version", org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_VERSION));
        settings.put("java", System.getProperty("java.version"));
        settings.put("warmup_frames", WARMUP_FRAMES);
        settings.put("sample_frames_min", SAMPLE_FRAMES);
        settings.put("sample_seconds_min", SAMPLE_NANOS / 1_000_000_000L);
        RESULTS.put("settings", settings);
        RESULTS.put("geometry", geometry(mc));
        base = mc.player.blockPosition().offset(0, 0, 24);
        RESULTS.put("structure", structure(mc));
        RESULTS.put("meshing", meshing(mc, base));
        stage(mc, server, SCENES.get(0));
    }

    /** Decomposed outline boxes per station and facing, as the client resolves them. */
    private static Map<String, Object> geometry(Minecraft mc) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Block block : stationBlocks()) {
            List<Integer> boxes = new ArrayList<>();
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                boxes.add(block.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing)
                        .getShape(mc.level, BlockPos.ZERO, CollisionContext.empty()).toAabbs().size());
            }
            out.put(block.getDescriptionId(), boxes);
        }
        return out;
    }

    /** Baked quads, outline boxes and outline edges per station in its default (north, idle) state. */
    private static Map<String, Object> structure(Minecraft mc) {
        Map<String, Object> out = new LinkedHashMap<>();
        RandomSource random = RandomSource.create(42);
        for (Block block : stationBlocks()) {
            BlockState state = block.defaultBlockState();
            BakedModel model = mc.getBlockRenderer().getBlockModel(state);
            int quads = model.getQuads(state, null, random, ModelData.EMPTY, null).size();
            for (Direction side : Direction.values()) quads += model.getQuads(state, side, random, ModelData.EMPTY, null).size();
            VoxelShape shape = state.getShape(mc.level, BlockPos.ZERO, CollisionContext.empty());
            int[] edges = {0};
            shape.forAllEdges((x0, y0, z0, x1, y1, z1) -> edges[0]++);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("baked_quads", quads);
            entry.put("outline_boxes", shape.toAabbs().size());
            entry.put("outline_edges", edges[0]);
            out.put(block.getDescriptionId(), entry);
        }
        return out;
    }

    /**
     * CPU time to tessellate one station into a chunk buffer, the work a chunk rebuild does per station (smooth lighting
     * as configured, no face culling): 2,000 warm-up calls, then the median of five batches of 2,000.
     */
    private static Map<String, Object> meshing(Minecraft mc, BlockPos pos) {
        Map<String, Object> out = new LinkedHashMap<>();
        RandomSource random = RandomSource.create(42);
        PoseStack pose = new PoseStack();
        CountingConsumer consumer = new CountingConsumer();
        for (Block block : stationBlocks()) {
            BlockState state = block.defaultBlockState();
            Runnable tessellate = () -> mc.getBlockRenderer().renderBatched(state, pos, mc.level, pose, consumer, false, random, ModelData.EMPTY, null);
            for (int i = 0; i < 2000; i++) tessellate.run();
            List<Double> batches = new ArrayList<>();
            for (int batch = 0; batch < 5; batch++) {
                consumer.vertices = 0;
                long start = System.nanoTime();
                for (int i = 0; i < 2000; i++) tessellate.run();
                batches.add((System.nanoTime() - start) / 2000.0 / 1000.0);
            }
            batches.sort(null);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("vertices", consumer.vertices / 2000);
            entry.put("median_us", Measurement.round(batches.get(2)));
            entry.put("batches_us", batches.stream().map(Measurement::round).toList());
            out.put(block.getDescriptionId(), entry);
        }
        out.put("smooth_lighting", mc.options.ambientOcclusion().get());
        return out;
    }

    /** Counts vertices and discards them. */
    private static final class CountingConsumer implements VertexConsumer {
        private long vertices;

        @Override public VertexConsumer vertex(double x, double y, double z) { return this; }
        @Override public VertexConsumer color(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer uv(float u, float v) { return this; }
        @Override public VertexConsumer overlayCoords(int u, int v) { return this; }
        @Override public VertexConsumer uv2(int u, int v) { return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { return this; }
        @Override public void endVertex() { vertices++; }
        @Override public void defaultColor(int r, int g, int b, int a) {}
        @Override public void unsetDefaultColor() {}
    }

    private static List<Block> stationBlocks() {
        return List.of(ModBlocks.SMITHS_FORGE.get(), ModBlocks.SMITHS_ANVIL.get(), ModBlocks.SMITHS_TROUGH.get(), ModBlocks.SMITHS_GRINDSTONE.get());
    }

    private static void stage(Minecraft mc, IntegratedServer server, Scene scene) {
        mc.options.particles().set(scene.particles() ? ParticleStatus.ALL : ParticleStatus.MINIMAL);
        // The selection outline is only drawn with the HUD shown, so the outline scenes keep it; the others hide it.
        mc.options.hideGui = scene.target() == null;
        server.execute(() -> {
            ServerLevel level = server.overworld();
            level.setDayTime(scene.night() ? 18000 : 6000);
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
            level.setWeatherParameters(1_000_000, 0, false, false);
            List<Entity> others = new ArrayList<>();
            level.getAllEntities().forEach(e -> { if (!(e instanceof Player)) others.add(e); });
            others.forEach(Entity::discard);
            // Clear the whole area, then build: rows of forge, anvil, trough and grindstone, facings turning per column.
            for (int x = -18; x <= 18; x++) for (int z = -2; z <= 18; z++) {
                level.setBlockAndUpdate(base.offset(x, 0, z), Blocks.AIR.defaultBlockState());
            }
            int columns = scene.stations() <= 4 ? scene.stations() : scene.stations() <= 32 ? 8 : 16;
            int rows = Math.max(1, scene.stations() / columns);
            List<Block> blocks = stationBlocks();
            for (int row = 0; row < rows; row++) for (int column = 0; column < columns; column++) {
                int index = row * columns + column;
                Block block = blocks.get(scene.stations() <= 4 ? index : (row + column) % 4);
                Direction facing = scene.stations() <= 4 ? Direction.SOUTH : Direction.from2DDataValue(column % 4);
                BlockPos pos = base.offset((column - columns / 2) * 2, 0, row * 2);
                level.setBlockAndUpdate(pos, block.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing));
                if (scene.active()) activate(level, pos);
            }
            ServerPlayer player = server.getPlayerList().getPlayers().get(0);
            // Creative and flying: spectators get no selection outline, and the camera must not fall.
            player.setGameMode(GameType.CREATIVE);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            double distance = scene.stations() <= 1 ? 3 : scene.stations() <= 4 ? 5 : scene.stations() <= 32 ? 11 : 16;
            double targetX = base.getX() + 0.5;
            double targetZ = base.getZ() + 0.5 + (rows - 1);
            if (scene.target() != null) {
                // Close to one station, aimed at its body so the selection outline is drawn every frame.
                int which = List.of("forge", "anvil", "trough", "grindstone").indexOf(scene.target());
                targetX = base.getX() + 0.5 + (which - 2) * 2;
                targetZ = base.getZ() + 0.5;
                look(player, level, targetX, base.getY() + 1.6, targetZ - 2.2, targetX, base.getY() + 0.45, targetZ);
            } else {
                look(player, level, targetX, base.getY() + distance * 0.55, targetZ - distance - 2, targetX, base.getY() + 0.5, targetZ);
            }
        });
    }

    private static void activate(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof SmithsForgeBlockEntity forge) {
            forge.insertMetal(new ItemStack(Items.IRON_INGOT, 16), 16, false, null);
            forge.insertFuel(new ItemStack(Items.COAL, 16), 16, false, null);
            forge.ignite();
        } else if (level.getBlockEntity(pos) instanceof SmithsAnvilBlockEntity anvil) {
            anvil.setWorkpiece(new WorkpieceData(new ItemStack(Items.IRON_SWORD), ImmersiveSmithing.id("smithing/iron_sword"),
                    new ResourceLocation("minecraft", "iron"), 18, 80, -1, false, WorkpieceState.FORGED, ImmersiveSmithing.id("sword")));
        } else if (level.getBlockEntity(pos) instanceof SmithsTroughBlockEntity trough) {
            trough.addBucket();
            trough.addBucket();
        }
    }

    /** Puts the player's eye at (x, y, z), looking at (tx, ty, tz). */
    private static void look(ServerPlayer player, ServerLevel level, double x, double y, double z, double tx, double ty, double tz) {
        double dx = tx - x, dy = ty - y, dz = tz - z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        player.teleportTo(level, x, y - player.getEyeHeight(), z, yaw, pitch);
    }

    private static void finish(Minecraft mc) {
        finished = true;
        mc.options.hideGui = false;
        try {
            Path dir = Path.of(OUTPUT);
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("station-benchmark-" + LABEL + ".json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(RESULTS) + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            ImmersiveSmithing.LOGGER.error("Station benchmark could not write its result", e);
        }
        ImmersiveSmithing.LOGGER.info("PACKTEST station benchmark {} finished: {}", LABEL, RESULTS.keySet());
        mc.stop();
    }

    /** Real render frames: warm-up, then at least SAMPLE_FRAMES frames over at least SAMPLE_NANOS. */
    private static final class Measurement {
        private final List<Double> intervals = new ArrayList<>();
        private final List<Double> ticks = new ArrayList<>();
        private long started, previous, sampleStart, sampleEnd;
        private int seen;
        private final long usedBefore = used();

        void begin(long now) {
            started = now;
        }

        void end(long now) {
            if (started == 0) return;
            if (seen >= WARMUP_FRAMES) {
                if (sampleStart == 0) sampleStart = started;
                ticks.add((now - started) / 1.0E6);
                if (previous != 0) intervals.add((started - previous) / 1.0E6);
                sampleEnd = now;
            }
            previous = started;
            started = 0;
            seen++;
        }

        boolean done() {
            return ticks.size() >= SAMPLE_FRAMES && sampleEnd - sampleStart >= SAMPLE_NANOS;
        }

        Map<String, Object> result() {
            List<Double> sorted = intervals.stream().sorted().toList();
            List<Double> sortedTicks = ticks.stream().sorted().toList();
            double average = intervals.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("frames", ticks.size());
            out.put("seconds", round((sampleEnd - sampleStart) / 1.0E9));
            out.put("average_fps", round(average == 0 ? 0 : 1000 / average));
            out.put("p50_frame_ms", round(percentile(sorted, .50)));
            out.put("p95_frame_ms", round(percentile(sorted, .95)));
            out.put("p99_frame_ms", round(percentile(sorted, .99)));
            out.put("p50_render_tick_ms", round(percentile(sortedTicks, .50)));
            out.put("p95_render_tick_ms", round(percentile(sortedTicks, .95)));
            out.put("heap_used_mb_before", usedBefore);
            out.put("heap_used_mb_after", used());
            return out;
        }

        private static long used() {
            Runtime r = Runtime.getRuntime();
            return (r.totalMemory() - r.freeMemory()) / (1024 * 1024);
        }

        private static double percentile(List<Double> values, double q) {
            return values.isEmpty() ? 0 : values.get(Math.min(values.size() - 1, (int) Math.ceil(values.size() * q) - 1));
        }

        static double round(double v) {
            return Math.round(v * 100.0) / 100.0;
        }
    }

    private StationBenchmark() {}
}
