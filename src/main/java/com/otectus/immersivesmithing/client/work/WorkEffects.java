package com.otectus.immersivesmithing.client.work;

import com.otectus.immersivesmithing.blockentity.SmithsAnvilBlockEntity;
import com.otectus.immersivesmithing.blockentity.SmithsForgeBlockEntity;
import com.otectus.immersivesmithing.blockentity.SmithsGrindstoneBlockEntity;
import com.otectus.immersivesmithing.blockentity.SmithsTroughBlockEntity;
import com.otectus.immersivesmithing.block.SmithsForgeBlock;
import com.otectus.immersivesmithing.config.ClientConfig;
import com.otectus.immersivesmithing.network.packet.WorkEventPacket;
import com.otectus.immersivesmithing.registry.ModParticles;
import com.otectus.immersivesmithing.registry.ModSounds;
import com.otectus.immersivesmithing.work.QuenchRules;
import com.otectus.immersivesmithing.work.ShapingRules;
import com.otectus.immersivesmithing.work.StationLayout;
import com.otectus.immersivesmithing.work.ThermalProfile;
import com.otectus.immersivesmithing.work.ThermalRules;
import com.otectus.immersivesmithing.work.WorkEvents;
import com.otectus.immersivesmithing.workpiece.WorkpieceData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Presentation of accepted station work: each {@link WorkEventPacket} becomes its sound and particles at the contact point,
 * once, on every client tracking the station (the acting client included; it only predicted the tool's motion). Particles
 * follow the client's particle and flash settings within a per-tick budget, sounds the work-sound volume. Continuous sounds
 * (the fire under air, the hiss of a quench, the scrape of the wheel) follow the stations' actual state and stop when it
 * ends, when the station is gone, and when the player leaves.
 */
public final class WorkEffects {
    private static long budgetTick = -1;
    private static int budget;
    private static final Map<BlockPos, Long> SEEN = new HashMap<>();
    private static final Map<BlockPos, Loop> LOOPS = new HashMap<>();
    private static final Map<BlockPos, Boolean> READY = new HashMap<>();
    private record Impact(WorkEventPacket event, long due) {}
    private static final java.util.ArrayDeque<Impact> IMPACTS = new java.util.ArrayDeque<>();

    public static void onEvent(WorkEventPacket e) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || !level.hasChunkAt(e.pos())) return;
        if (mc.player.distanceToSqr(Vec3.atCenterOf(e.pos())) > 40 * 40) return;
        boolean mine = e.actor() == mc.player.getId();
        if (mine && (e.type() == WorkEvents.STRIKE || e.type() == WorkEvents.REFUSED)) {
            SceneTools.resolve(e.sequence());
            long delay = SceneTools.millisUntilImpact(e.sequence());
            if (delay > 0 && FocusController.focusedOn(e.pos())) {
                if (IMPACTS.size() == 16) IMPACTS.removeFirst();
                IMPACTS.addLast(new Impact(e, net.minecraft.Util.getMillis() + delay));
                return;
            }
        }
        Direction facing = WorkTargets.facing(level, e.pos());
        RandomSource random = level.random;
        float strength = Float.isFinite(e.strength()) ? Math.max(0F, Math.min(1F, e.strength())) : 0F;
        boolean reduced = ClientConfig.get(ClientConfig.REDUCED_FLASHES);
        switch (e.type()) {
            case WorkEvents.STRIKE, WorkEvents.REFUSED -> {
                Vec3 at = anvilPoint(level, e.pos(), facing, e.x(), e.y());
                boolean accepted = e.type() == WorkEvents.STRIKE;
                boolean heavyBlow = (e.data() & (1 << 12)) != 0;
                int outcome = e.data() & 15;
                if (accepted) {
                    SoundEvent sound = heavyBlow ? ModSounds.ANVIL_STRIKE_HEAVY.get() : strength >= 0.95F ? ModSounds.ANVIL_STRIKE_PERFECT.get() : ModSounds.ANVIL_STRIKE.get();
                    play(level, at, sound, 1.0F, 0.92F + strength * 0.16F + random.nextFloat() * 0.04F);
                    int sparks = count(reduced ? 2 : 3 + Math.round(strength * 6), level);
                    for (int i = 0; i < sparks; i++) {
                        level.addParticle(ModParticles.SPARK.get(), at.x, at.y + 0.02, at.z, (random.nextDouble() - 0.5) * 0.18,
                                0.06 + random.nextDouble() * 0.12, (random.nextDouble() - 0.5) * 0.18);
                    }
                    int flakes = count(outcome == ShapingRules.Outcome.OVERWORKED.ordinal() ? 3 : 1, level);
                    for (int i = 0; i < flakes; i++) {
                        level.addParticle(ParticleTypes.ASH, at.x + (random.nextDouble() - 0.5) * 0.1, at.y + 0.03, at.z + (random.nextDouble() - 0.5) * 0.1, 0, 0, 0);
                    }
                } else {
                    play(level, at, ModSounds.ANVIL_STRIKE_COLD.get(), 0.7F, 0.8F + random.nextFloat() * 0.1F);
                }
            }
            case WorkEvents.PUMP -> {
                Vec3 at = local(e, facing);
                play(level, at, ModSounds.BELLOWS.get(), 0.8F, 0.9F + random.nextFloat() * 0.15F);
                Vec3 fire = StationLayout.toWorld(e.pos(), facing, 0.5, 0.25, 0.3);
                boolean lit = level.getBlockState(e.pos()).hasProperty(SmithsForgeBlock.LIT) && level.getBlockState(e.pos()).getValue(SmithsForgeBlock.LIT);
                ParticleOptions particle = lit && !reduced ? ParticleTypes.SMALL_FLAME : ParticleTypes.SMOKE;
                for (int i = 0, n = count(lit ? 3 : 1, level); i < n; i++) {
                    level.addParticle(particle, fire.x + (random.nextDouble() - 0.5) * 0.35, fire.y + random.nextDouble() * 0.15,
                            fire.z + (random.nextDouble() - 0.5) * 0.35, 0, 0.03 + random.nextDouble() * 0.03, 0);
                }
            }
            case WorkEvents.TURN -> play(level, local(e, facing), ModSounds.BILLET_TURN.get(), 0.6F, 1.0F + random.nextFloat() * 0.1F);
            case WorkEvents.DRAW, WorkEvents.COMMIT -> {
                Vec3 at = local(e, facing);
                for (int i = 0, n = count(reduced ? 2 : 6, level); i < n; i++) {
                    level.addParticle(ModParticles.SPARK.get(), at.x + (random.nextDouble() - 0.5) * 0.3, at.y + 0.05, at.z + (random.nextDouble() - 0.5) * 0.1,
                            0, 0.05 + random.nextDouble() * 0.06, 0);
                }
            }
            case WorkEvents.FLIP -> play(level, local(e, facing), ModSounds.FLIP.get(), 0.6F, 1.1F);
            case WorkEvents.FIT -> {
                Vec3 at = local(e, facing);
                for (int i = 0, n = count(4, level); i < n; i++) {
                    level.addParticle(ParticleTypes.CRIT, at.x + (random.nextDouble() - 0.5) * 0.3, at.y + 0.08, at.z + (random.nextDouble() - 0.5) * 0.3, 0, 0.02, 0);
                }
            }
            case WorkEvents.COMPLETE -> {
                Vec3 at = local(e, facing);
                // The grade cue: a brief chime that rises with the grade (Faulty is low and dull).
                float pitch = 0.7F + 0.12F * e.data();
                play(level, at, ModSounds.WORK_READY.get(), 0.9F, pitch);
                for (int i = 0, n = count(reduced ? 3 : 10, level); i < n; i++) {
                    level.addParticle(e.data() >= 3 ? ParticleTypes.END_ROD : ParticleTypes.CRIT, at.x + (random.nextDouble() - 0.5) * 0.4,
                            at.y + 0.1 + random.nextDouble() * 0.2, at.z + (random.nextDouble() - 0.5) * 0.4, 0, 0.02, 0);
                }
            }
            case WorkEvents.SHAPED -> play(level, local(e, facing), ModSounds.ANVIL_FINISH.get(), 0.8F, 1.2F);
            case WorkEvents.GRIND -> {
                Vec3 at = local(e, facing);
                Vec3 out = StationLayout.dirToWorld(facing, new Vec3(0, 0, -1));
                for (int i = 0, n = count(reduced ? 2 : 5, level); i < n; i++) {
                    double speed = 0.12 + random.nextDouble() * 0.12;
                    level.addParticle(ModParticles.SPARK.get(), at.x, at.y, at.z, out.x * speed + (random.nextDouble() - 0.5) * 0.04,
                            0.04 + random.nextDouble() * 0.05, out.z * speed + (random.nextDouble() - 0.5) * 0.04);
                }
            }
            case WorkEvents.STAMP -> {
                Vec3 at = local(e, facing);
                play(level, at, ModSounds.STAMP.get(), 0.9F, 1.0F);
                for (int i = 0, n = count(reduced ? 2 : 6, level); i < n; i++) {
                    level.addParticle(ModParticles.SPARK.get(), at.x, at.y + 0.04, at.z, (random.nextDouble() - 0.5) * 0.1, 0.05, (random.nextDouble() - 0.5) * 0.1);
                }
            }
            case WorkEvents.LIFT -> {
                Vec3 at = local(e, facing);
                for (int i = 0, n = count(5, level); i < n; i++) {
                    level.addParticle(ParticleTypes.DRIPPING_WATER, at.x + (random.nextDouble() - 0.5) * 0.4, at.y + 0.4, at.z + (random.nextDouble() - 0.5) * 0.2, 0, 0, 0);
                }
            }
            default -> { }
        }
    }

    private static Vec3 local(WorkEventPacket e, Direction facing) {
        return StationLayout.toWorld(e.pos(), facing, e.x(), e.y(), e.z());
    }

    /** The world point of a canonical (u, v) on the piece now on the anvil (or the anvil centre). */
    private static Vec3 anvilPoint(ClientLevel level, BlockPos pos, Direction facing, float u, float v) {
        if (level.getBlockEntity(pos) instanceof SmithsAnvilBlockEntity anvil && anvil.workpiece() != null) {
            WorkpieceData piece = anvil.workpiece();
            WorkpieceShape shape = WorkpieceShape.of(piece);
            Vec3 p = shape.point(Math.max(0F, Math.min(1F, u)), WorkpieceShape.visibleV(Math.max(-0.5F, Math.min(0.5F, v)), piece.process().flipped));
            return StationLayout.toWorld(pos, facing, WorkTargets.pieceToAnvil(p));
        }
        return StationLayout.toWorld(pos, facing, StationLayout.ANVIL_WORK_CENTER);
    }

    private static void play(ClientLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        float scale = (float) ClientConfig.get(ClientConfig.EFFECT_SOUND_VOLUME);
        if (scale <= 0F) return;
        level.playLocalSound(at.x, at.y, at.z, sound, SoundSource.BLOCKS, volume * scale, pitch, false);
    }

    /** Particles within the client's settings and a per-tick budget shared by all stations. */
    private static int count(int requested, ClientLevel level) {
        if (budgetTick != level.getGameTime()) {
            budgetTick = level.getGameTime();
            budget = 64;
        }
        int n = Math.min(budget, ClientConfig.scaleParticles(requested));
        budget -= n;
        return n;
    }

    // ------------------------------------------------------------------ continuous station sounds and ambient particles

    /** Renderers report the stations they draw; only those nearby are considered for loops. */
    public static void seen(BlockEntity be) {
        SEEN.put(be.getBlockPos().immutable(), be.getLevel() != null ? be.getLevel().getGameTime() : 0L);
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            stopAll();
            return;
        }
        while (!IMPACTS.isEmpty() && IMPACTS.peekFirst().due <= net.minecraft.Util.getMillis()) {
            onEvent(IMPACTS.removeFirst().event);
        }
        long now = level.getGameTime();
        Iterator<Map.Entry<BlockPos, Long>> it = SEEN.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Long> entry = it.next();
            BlockPos pos = entry.getKey();
            if (now - entry.getValue() > 100 || mc.player.distanceToSqr(Vec3.atCenterOf(pos)) > 24 * 24) {
                it.remove();
                continue;
            }
            BlockEntity be = level.getBlockEntity(pos);
            Loop.Kind kind = be instanceof SmithsForgeBlockEntity ? Loop.Kind.FIRE : be instanceof SmithsTroughBlockEntity ? Loop.Kind.HISS
                    : be instanceof SmithsGrindstoneBlockEntity ? Loop.Kind.SCRAPE : null;
            if (kind == null) continue;
            if (Loop.intensity(level, pos, kind) > 0.02F) {
                Loop loop = LOOPS.get(pos);
                if (loop == null || loop.isStopped()) {
                    loop = new Loop(pos, kind);
                    LOOPS.put(pos, loop);
                    mc.getSoundManager().play(loop);
                }
            }
            ambient(level, pos, be);
            cue(pos, be);
        }
        LOOPS.values().removeIf(Loop::isStopped);
        READY.keySet().retainAll(SEEN.keySet());
    }

    /**
     * The readiness cue: once when the billet the smith is working (focused on, or looking at) becomes ready to draw, or the
     * piece on the anvil they are working cools below working heat. Only for that one station, so a busy workshop does not
     * produce a wall of cues.
     */
    private static void cue(BlockPos pos, BlockEntity be) {
        boolean mine = FocusController.focusedOn(pos) || pos.equals(WorldViewInput.lookedAtStation());
        Boolean state = null;
        if (be instanceof SmithsForgeBlockEntity forge && forge.job() != null) state = forge.job().drawable();
        else if (be instanceof SmithsAnvilBlockEntity anvil && anvil.workpiece() != null && anvil.workpiece().stage().shapeable()) {
            state = !anvil.workpiece().process().thermal.workable(ThermalRules.coolest(anvil.currentHeat()));
        }
        Boolean before = state == null ? READY.remove(pos) : READY.put(pos, state);
        if (mine && state != null && state && before != null && !before) com.otectus.immersivesmithing.client.screen.SmithingGui.playCue();
    }

    /** Steam over a quench and glow over a hot billet; restrained and budgeted. */
    private static void ambient(ClientLevel level, BlockPos pos, BlockEntity be) {
        RandomSource random = level.random;
        if (be instanceof SmithsTroughBlockEntity trough && trough.quenchPiece() != null && trough.immersed()) {
            float heat = ThermalProfile.fraction(ThermalRules.mean(trough.quenchPiece().process().heat));
            float surface = (float) StationLayout.troughSurface(trough.clientFillFraction());
            Direction facing = WorkTargets.facing(level, pos);
            if (random.nextFloat() < heat * 0.6F && count(1, level) > 0) {
                Vec3 at = StationLayout.toWorld(pos, facing, 0.5 + (random.nextDouble() - 0.5) * 0.4, surface, 0.5 + (random.nextDouble() - 0.5) * 0.25);
                com.otectus.immersivesmithing.client.ClientEffects.steam(at.x, at.y + 0.02, at.z, 0, 0.015 + heat * 0.02, 0);
            }
            if (random.nextFloat() < 0.5F && count(1, level) > 0) {
                Vec3 at = StationLayout.toWorld(pos, facing, 0.5 + (random.nextDouble() - 0.5) * 0.3, surface - 0.05, 0.5 + (random.nextDouble() - 0.5) * 0.2);
                level.addParticle(ParticleTypes.BUBBLE, at.x, at.y, at.z, 0, 0.04, 0);
            }
        }
    }

    public static void stopAll() {
        for (Loop loop : LOOPS.values()) loop.end();
        LOOPS.clear();
        SEEN.clear();
        READY.clear();
        IMPACTS.clear();
    }

    /** A looping station sound whose volume follows the station's state and which ends itself with it. */
    static final class Loop extends AbstractTickableSoundInstance {
        enum Kind { FIRE, HISS, SCRAPE }

        private final BlockPos pos;
        private final Kind kind;

        Loop(BlockPos pos, Kind kind) {
            super(switch (kind) {
                case FIRE -> ModSounds.FORGE_FIRE_LOOP.get();
                case HISS -> ModSounds.QUENCH_LOOP.get();
                case SCRAPE -> ModSounds.GRIND_SCRAPE.get();
            }, SoundSource.BLOCKS, RandomSource.create());
            this.pos = pos;
            this.kind = kind;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.01F;
            this.x = pos.getX() + 0.5;
            this.y = pos.getY() + 0.5;
            this.z = pos.getZ() + 0.5;
        }

        static float intensity(ClientLevel level, BlockPos pos, Kind kind) {
            BlockEntity be = level.getBlockEntity(pos);
            return switch (kind) {
                case FIRE -> be instanceof SmithsForgeBlockEntity forge && forge.getBlockState().hasProperty(SmithsForgeBlock.LIT)
                        && forge.getBlockState().getValue(SmithsForgeBlock.LIT) ? 0.25F + 0.75F * forge.air() / 1000F : 0F;
                case HISS -> be instanceof SmithsTroughBlockEntity trough && trough.quenchPiece() != null && trough.immersed()
                        ? Math.max(0F, 1F - QuenchRules.progress(trough.quenchPiece().process(), trough.quenchDuration()) / 1000F) : 0F;
                case SCRAPE -> be instanceof SmithsGrindstoneBlockEntity stone && stone.contact() != null ? 1F : 0F;
            };
        }

        void end() {
            stop();
        }

        @Override
        public void tick() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || !mc.level.hasChunkAt(pos)) {
                stop();
                return;
            }
            float intensity = intensity(mc.level, pos, kind);
            if (intensity <= 0.02F) {
                stop();
                return;
            }
            float base = kind == Kind.FIRE ? 0.35F : kind == Kind.HISS ? 0.8F : 0.6F;
            volume = intensity * base * (float) ClientConfig.get(ClientConfig.EFFECT_SOUND_VOLUME);
        }
    }

    private WorkEffects() {}
}
