package com.otectus.immersivesmithing.packtest;

import com.mojang.blaze3d.platform.InputConstants;
import com.otectus.immersivesmithing.blockentity.SmithsAnvilBlockEntity;
import com.otectus.immersivesmithing.blockentity.SmithsForgeBlockEntity;
import com.otectus.immersivesmithing.blockentity.SmithsGrindstoneBlockEntity;
import com.otectus.immersivesmithing.blockentity.SmithsTroughBlockEntity;
import com.otectus.immersivesmithing.client.screen.PatternBookScreen;
import com.otectus.immersivesmithing.client.work.FocusController;
import com.otectus.immersivesmithing.client.work.WorkFocusScreen;
import com.otectus.immersivesmithing.client.work.WorkKeys;
import com.otectus.immersivesmithing.client.work.WorkTargets;
import com.otectus.immersivesmithing.config.ClientConfig;
import com.otectus.immersivesmithing.item.SmithingTier;
import com.otectus.immersivesmithing.material.MaterialFamily;
import com.otectus.immersivesmithing.quality.QualityData;
import com.otectus.immersivesmithing.recipe.AuxiliaryIngredient;
import com.otectus.immersivesmithing.recipe.SmithingRecipe;
import com.otectus.immersivesmithing.registry.ModBlocks;
import com.otectus.immersivesmithing.registry.ModItems;
import com.otectus.immersivesmithing.work.ForgeJob;
import com.otectus.immersivesmithing.work.ProcessProfile;
import com.otectus.immersivesmithing.work.ProcessState;
import com.otectus.immersivesmithing.work.ShapingRules;
import com.otectus.immersivesmithing.work.ThermalRules;
import com.otectus.immersivesmithing.workpiece.WorkpieceCodec;
import com.otectus.immersivesmithing.workpiece.WorkpieceData;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Plays one complete piece from the client like a careful smith, through the inputs a player uses: the configured action
 * and turn-over keys pressed on the focused work view, the pattern book's search and Enter, hotbar selection and ordinary
 * block use. Every action reaches the integrated server as the packet that input sends and is validated and scored there;
 * the driver only reads the synchronized station state to decide what to do next, and fails with a reason instead of
 * waiting forever.
 *
 * <p>Flow: open the pattern book at the forge and choose the recipe, commit it, heat the billet evenly with the bellows and
 * by turning it, draw it, set it on the anvil and work every operation (turning the piece over and reheating it on the
 * forge's rest whenever the next zone is too cold), quench it for the full hiss, fit its components, collect it and,
 * optionally, take one grindstone pass. Each station has a standing spot (possibly the same one); the smith is moved there
 * before its view is asked for. Hotbar slot 0 holds empty tongs, slot 1 the hammer ({@link #stage}).</p>
 */
final class SmithDriver {
    private enum Step { FOCUS_FORGE, BOOK, CHOOSE, COMMIT, HEAT, DRAW, PLACE, SHAPE, TAKE, REST, REHEAT, LOWER, QUENCH, LIFT,
        FIT, COLLECT, GRIND_PLACE, GRIND, GRIND_STOP, GRIND_TAKE, DONE }

    /** What the piece in the tongs is on its way to. */
    private enum Goal { SHAPE, QUENCH, FIT }

    private static final int TONGS_SLOT = 0, HAMMER_SLOT = 1;
    /** Above the slowest hammer's recovery, so every blow lands (the server silently ignores early ones). */
    private static final int STRIKE_INTERVAL = 14;
    private static final Field SELECTED;

    static {
        try {
            SELECTED = WorkFocusScreen.class.getDeclaredField("selected");
            SELECTED.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final BlockPos forge, anvil, trough, grindstone;
    private final Map<BlockPos, Vec3> stands;
    @Nullable
    private final ResourceLocation recipe;
    private final String search;
    private final Item result;
    private final boolean refine;
    private final Consumer<String> moments;
    final Map<String, Object> evidence = new LinkedHashMap<>();
    private final Set<String> said = new HashSet<>();

    private Step step = Step.FOCUS_FORGE;
    private Goal goal = Goal.SHAPE;
    private int total, age;
    private int requestedAt = -1000, lastPress = -1000, lastPump = -1000, lastTurn = -1000, lastStrike = -1000, lastFlip = -1000;
    private int selectedAt = -1000, movedAt = -1000, occupiedAt = -1000, releasedAt = -1000;
    @Nullable
    private BlockPos requestedFor;
    @Nullable
    private Vec3 movingTo;
    private boolean keyDown;
    private int pumps, turns, blows, flips, reheats, focusOpens;
    private int refinedFrom = -1;
    /** The aspect the grindstone pass refines: the one still below 100 (durability first). */
    private boolean refineEfficacy;
    private boolean nothingToRefine;

    SmithDriver(BlockPos forge, BlockPos anvil, BlockPos trough, BlockPos grindstone, Map<BlockPos, Vec3> stands,
                @Nullable ResourceLocation recipe, String search, Item result, boolean refine, Consumer<String> moments) {
        this.forge = forge.immutable();
        this.anvil = anvil.immutable();
        this.trough = trough.immutable();
        this.grindstone = grindstone.immutable();
        this.stands = Map.copyOf(stands);
        this.recipe = recipe;
        this.search = search;
        this.result = result;
        this.refine = refine;
        this.moments = moments;
    }

    /** One grindstone pass only, on the first graded {@code item} in the hotbar, worked from {@code stand}. */
    static SmithDriver refineOnly(BlockPos grindstone, Vec3 stand, Item item, Consumer<String> moments) {
        SmithDriver driver = new SmithDriver(grindstone, grindstone, grindstone, grindstone, Map.of(grindstone, stand), null, "",
                item, true, moments);
        driver.step = Step.GRIND_PLACE;
        return driver;
    }

    /**
     * Server thread: the four stations facing {@code facing}, molten metal for two pieces and a fire in the forge, a filled
     * trough, and the smith at the anvil's standing spot with empty tongs (slot 0), a hammer (slot 1), the recipe's components
     * and levels for the grindstone.
     */
    static void stage(ServerLevel level, ServerPlayer player, SmithingRecipe recipe, BlockPos forge, BlockPos anvil, BlockPos trough,
                      BlockPos grindstone, Direction facing, Vec3 stand, GameType mode, SmithingTier tools) {
        place(level, forge, ModBlocks.SMITHS_FORGE.get(), facing);
        place(level, anvil, ModBlocks.SMITHS_ANVIL.get(), facing);
        place(level, trough, ModBlocks.SMITHS_TROUGH.get(), facing);
        place(level, grindstone, ModBlocks.SMITHS_GRINDSTONE.get(), facing);
        SmithsForgeBlockEntity f = (SmithsForgeBlockEntity) level.getBlockEntity(forge);
        f.refundCast(recipe.metalUnits() * 2, recipe.family());
        light(f);
        SmithsTroughBlockEntity t = (SmithsTroughBlockEntity) level.getBlockEntity(trough);
        while (t.canAcceptBucket()) t.addBucket();
        player.setGameMode(mode);
        player.getInventory().clearContent();
        player.getInventory().setItem(TONGS_SLOT, new ItemStack(ModItems.tongs(tools)));
        player.getInventory().setItem(HAMMER_SLOT, new ItemStack(ModItems.hammer(tools)));
        int slot = 9;
        for (AuxiliaryIngredient aux : recipe.auxiliary()) player.getInventory().setItem(slot++, aux.displayStack());
        player.getInventory().selected = TONGS_SLOT;
        if (player.experienceLevel < 30) player.giveExperienceLevels(30 - player.experienceLevel);
        player.inventoryMenu.broadcastChanges();
        lookFrom(player, level, stand, Vec3.atCenterOf(anvil));
    }

    private static void place(ServerLevel level, BlockPos pos, Block block, Direction facing) {
        level.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(pos, block.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing));
    }

    /** Lava where the metal accepts it, otherwise the first fuel its rule names, lit. */
    static void light(SmithsForgeBlockEntity forge) {
        forge.addLavaBucket(false);
        TagKey<Item> required = forge.familyData().map(MaterialFamily::requiredFuelTag).orElse(null);
        if (forge.heatSourceAllows(required)) return;
        forge.takeLavaBucket();
        Item fuel = required == null ? net.minecraft.world.item.Items.COAL : BuiltInRegistries.ITEM.getTag(required)
                .flatMap(set -> set.stream().findFirst()).map(holder -> holder.value()).orElse(net.minecraft.world.item.Items.COAL);
        forge.insertFuel(new ItemStack(fuel, 32), 32, false, null);
        forge.ignite();
    }

    static void lookFrom(ServerPlayer player, ServerLevel level, Vec3 eyeFoot, Vec3 target) {
        double dx = target.x - eyeFoot.x, dy = target.y - (eyeFoot.y + player.getEyeHeight()), dz = target.z - eyeFoot.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        player.teleportTo(level, eyeFoot.x, eyeFoot.y, eyeFoot.z, yaw, pitch);
    }

    boolean done() {
        return step == Step.DONE;
    }

    /** One client tick. True once the piece is finished (and refined, if asked). */
    boolean tick(Minecraft mc) {
        if (step == Step.DONE) return true;
        if (mc.player == null || mc.level == null) throw fail("the client left the world");
        if (FocusController.mode() == ClientConfig.CameraMode.WORLD) throw fail("the camera mode is World view; the driver works in a focused view");
        total++;
        age++;
        if (total > 12_000) throw fail("the run took more than ten minutes");
        switch (step) {
            case FOCUS_FORGE -> {
                hotbar(mc, TONGS_SLOT);
                if (focused(mc, forge)) {
                    moment("forge_focus");
                    next(Step.BOOK);
                } else if (age > 300) {
                    throw fail("the forge's work view did not open");
                }
            }
            case BOOK -> {
                SmithsForgeBlockEntity f = forgeBe(mc);
                if (f.job() != null) next(Step.HEAT);
                else if (recipe.equals(f.plan())) next(Step.COMMIT);
                else if (mc.screen instanceof PatternBookScreen) next(Step.CHOOSE);
                else if (focused(mc, forge) && since(lastPress) >= 60 && age >= 10) press(mc, WorkTargets.Kind.BOOK, -1, true);
                if (age > 400) throw fail("the pattern book did not open");
            }
            case CHOOSE -> {
                if (mc.screen instanceof PatternBookScreen book) {
                    if (age == 15) moment("pattern_book_live");
                    if (age == 18) {
                        EditBox box = book.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst()
                                .orElseThrow(() -> fail("the pattern book has no search field"));
                        box.setValue(search);
                    }
                    if (age == 30) moment("pattern_book_search");
                    if (age == 33) book.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
                } else if (recipe.equals(forgeBe(mc).plan())) {
                    evidence.put("plan_pinned", recipe.toString());
                    next(Step.COMMIT);
                }
                if (age > 300) throw fail("choosing " + recipe + " in the pattern book did not pin it");
            }
            case COMMIT -> {
                SmithsForgeBlockEntity f = forgeBe(mc);
                if (f.job() != null) {
                    evidence.put("job_units", f.job().units);
                    moment("job_committed");
                    next(Step.HEAT);
                } else if (focused(mc, forge) && since(lastPress) >= 40) {
                    press(mc, WorkTargets.Kind.COMMIT, -1, true);
                }
                if (age > 300) throw fail("committing the plan cast no billet");
            }
            case HEAT -> heat(mc);
            case DRAW -> {
                hotbar(mc, TONGS_SLOT);
                ItemStack held = mc.player.getMainHandItem();
                if (WorkpieceCodec.isHolding(held)) {
                    WorkpieceCodec.getHeld(held).ifPresent(piece -> evidence.put("heat_preparation", piece.process().heatPrep));
                    moment("billet_drawn");
                    goal = Goal.SHAPE;
                    next(Step.PLACE);
                } else if (focused(mc, forge) && handReady() && since(lastPress) >= 40) {
                    press(mc, WorkTargets.Kind.DRAW, -1, true);
                }
                if (age > 300) throw fail("drawing the billet failed");
            }
            case PLACE -> {
                hotbar(mc, TONGS_SLOT);
                if (anvilBe(mc).workpiece() != null) next(goal == Goal.FIT ? Step.FIT : Step.SHAPE);
                else if (focused(mc, anvil) && handReady() && since(lastPress) >= 40) press(mc, WorkTargets.Kind.PLACE, -1, true);
                if (age > 300) throw fail("setting the piece on the anvil failed");
            }
            case SHAPE -> shape(mc);
            case TAKE -> {
                hotbar(mc, TONGS_SLOT);
                ItemStack held = mc.player.getMainHandItem();
                if (WorkpieceCodec.isHolding(held) && anvilBe(mc).workpiece() == null) {
                    WorkpieceData piece = WorkpieceCodec.getHeld(held).orElseThrow(() -> fail("the lifted piece is unreadable"));
                    boolean hotEnough = piece.process().thermal.workable(ThermalRules.mean(piece.process().heat) - 300);
                    next(goal == Goal.QUENCH && hotEnough ? Step.LOWER : Step.REST);
                } else if (focused(mc, anvil) && handReady() && since(lastPress) >= 40) {
                    press(mc, WorkTargets.Kind.PIECE, -1, true);
                }
                if (age > 300) throw fail("lifting the piece off the anvil failed");
            }
            case REST -> {
                hotbar(mc, TONGS_SLOT);
                if (forgeBe(mc).restOccupied()) {
                    reheats++;
                    moment("reheat_on_rest");
                    next(Step.REHEAT);
                } else if (focused(mc, forge) && handReady() && since(lastPress) >= 40) {
                    press(mc, WorkTargets.Kind.REST, -1, true);
                }
                if (reheats > 12) throw fail("more than twelve reheats");
                if (age > 300) throw fail("putting the piece on the reheat rest failed");
            }
            case REHEAT -> reheat(mc);
            case LOWER -> {
                hotbar(mc, TONGS_SLOT);
                if (troughBe(mc).occupied()) {
                    occupiedAt = total;
                    moment("quench_lowered");
                    next(Step.QUENCH);
                } else if (focused(mc, trough) && handReady() && since(lastPress) >= 40) {
                    releaseKey(mc);
                    press(mc, WorkTargets.Kind.WATER, -1, false); // held: the piece stays down while the key is
                    keyDown = true;
                }
                if (age > 300) throw fail("lowering the piece into the trough failed");
            }
            case QUENCH -> {
                SmithsTroughBlockEntity t = troughBe(mc);
                if (!t.occupied()) {
                    releaseKey(mc);
                    next(Step.LIFT);
                    break;
                }
                WorkpieceData piece = t.quenchPiece();
                int duration = t.quenchDuration();
                int immersed = piece != null ? piece.process().quenchTicks : Math.max(0, since(occupiedAt) - 10);
                if (immersed >= duration / 2) moment("quench_hiss");
                if (immersed >= duration + 10) {
                    // Let go: in hold mode releasing lifts; in toggle mode a second press does.
                    if (keyDown) {
                        releaseKey(mc);
                        releasedAt = total;
                    } else if (since(releasedAt) > 8 && since(lastPress) >= 20 && focused(mc, trough)) {
                        press(mc, WorkTargets.Kind.WATER, -1, true);
                    }
                }
                if (age > 1500) throw fail("the quench never finished");
            }
            case LIFT -> {
                ItemStack held = mc.player.getMainHandItem();
                if (WorkpieceCodec.isHolding(held)) {
                    WorkpieceData piece = WorkpieceCodec.getHeld(held).orElseThrow(() -> fail("the quenched piece is unreadable"));
                    if (piece.stage().quenched()) {
                        evidence.put("quench_control", piece.process().quenchControl);
                        goal = Goal.FIT;
                        next(Step.PLACE);
                    } else if (age > 10) {
                        next(Step.LOWER); // lifted early: lower it again
                    }
                } else if (finishedSlot(mc) >= 0) {
                    finished(mc);
                }
                if (age > 300) throw fail("the trough did not hand the piece back");
            }
            case FIT -> {
                SmithsAnvilBlockEntity a = anvilBe(mc);
                if (a.hasOutput()) {
                    moment("finished_on_anvil");
                    next(Step.COLLECT);
                } else if (focused(mc, anvil) && a.workpiece() != null) {
                    moment("fitting");
                    if (since(lastPress) >= 20) press(mc, WorkTargets.Kind.SOCKET, -1, true);
                }
                if (age > 600) throw fail("fitting the components failed");
            }
            case COLLECT -> {
                if (finishedSlot(mc) >= 0) finished(mc);
                else if (focused(mc, anvil) && since(lastPress) >= 30) press(mc, WorkTargets.Kind.OUTPUT, -1, true);
                if (age > 300) throw fail("collecting the finished piece failed");
            }
            case GRIND_PLACE -> {
                if (!grindBe(mc).piece().isEmpty()) {
                    next(Step.GRIND);
                    break;
                }
                int slot = finishedSlot(mc);
                if (slot < 0 || slot > 8) throw fail("the finished piece is not in the hotbar");
                if (FocusController.active()) FocusController.exit(null, true);
                hotbar(mc, slot);
                if (!atStand(mc, grindstone) || !handReady() || mc.screen != null) break;
                if (since(lastPress) >= 40) {
                    QualityData quality = QualityData.get(mc.player.getMainHandItem()).orElseThrow(() -> fail("the finished piece has no quality"));
                    refineEfficacy = quality.forgeScore() >= 100 && quality.anvilScore() < 100;
                    nothingToRefine = quality.forgeScore() >= 100 && quality.anvilScore() >= 100;
                    refinedFrom = refineEfficacy ? quality.anvilScore() : quality.forgeScore();
                    mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                            new BlockHitResult(Vec3.atCenterOf(grindstone).add(0, 0.45, 0), Direction.UP, grindstone, false));
                    mc.player.swing(InteractionHand.MAIN_HAND);
                    lastPress = total;
                }
                if (age > 300) throw fail("resting the piece at the grindstone failed");
            }
            case GRIND -> {
                SmithsGrindstoneBlockEntity g = grindBe(mc);
                if (nothingToRefine) {
                    // A perfect piece: both aspects are at 100, so the stone only quotes; take it back.
                    evidence.put("refinement", "both aspects already 100; no pass possible");
                    next(Step.GRIND_TAKE);
                    break;
                }
                int score = QualityData.get(g.piece()).map(q -> refineEfficacy ? q.anvilScore() : q.forgeScore()).orElse(-1);
                if (refinedFrom >= 0 && score > refinedFrom) {
                    evidence.put("refined_" + (refineEfficacy ? "anvil" : "forge") + "_score", refinedFrom + " -> " + score);
                    moment("refined");
                    next(Step.GRIND_STOP);
                    break;
                }
                if (!focused(mc, grindstone)) break;
                if (g.efficacy() != refineEfficacy) {
                    // The secondary action switches the aspect being refined.
                    if (!keyDown && since(lastPress) >= 20) pressUse(mc);
                    break;
                }
                if (g.contact() != null && g.contactTicks() > 8) moment("grind_contact");
                if (!keyDown && since(lastPress) >= 20) {
                    press(mc, WorkTargets.Kind.WHEEL, -1, false); // held to the wheel while the key is down
                    keyDown = true;
                }
                if (age > 900) throw fail("the grindstone pass did not complete");
            }
            case GRIND_STOP -> {
                if (keyDown) releaseKey(mc);
                else if (!ClientConfig.get(ClientConfig.HOLD_TO_ACT) && age == 2) press(mc, WorkTargets.Kind.WHEEL, -1, true);
                if (age > 6) next(Step.GRIND_TAKE);
            }
            case GRIND_TAKE -> {
                if (grindBe(mc).piece().isEmpty() && finishedSlot(mc) >= 0) {
                    evidence.put("refined_quality", QualityData.get(mc.player.getInventory().getItem(finishedSlot(mc))).map(Object::toString).orElse("none"));
                    complete();
                } else if (focused(mc, grindstone) && since(lastPress) >= 30) {
                    press(mc, WorkTargets.Kind.TAKE, -1, true);
                }
                if (age > 300) throw fail("taking the refined piece back failed");
            }
            case DONE -> { }
        }
        return step == Step.DONE;
    }

    /** Brings the billet evenly into the ideal band: the coolest end over the hottest coals, the bellows only while everything is low. */
    private void heat(Minecraft mc) {
        if (!focused(mc, forge)) return;
        ForgeJob job = forgeBe(mc).job();
        if (job == null) throw fail("the billet vanished from the tray");
        int[] heat = job.heat;
        int lo = 0, hi = 0;
        for (int z = 1; z < heat.length; z++) {
            if (heat[z] < heat[lo]) lo = z;
            if (heat[z] > heat[hi]) hi = z;
        }
        if (ThermalRules.mean(heat) >= job.thermal.workableMin()) moment("forge_heating");
        boolean ready = inBand(job) && heat[hi] - heat[lo] <= 550;
        if (ready || (age > 2400 && job.drawable())) {
            evidence.put("heating_ticks", age);
            evidence.put("bellows_pumps", pumps);
            evidence.put("billet_turns", turns);
            evidence.put("billet_heat", Arrays.toString(heat));
            moment("billet_ready");
            next(Step.DRAW);
            return;
        }
        if (age > 2400) throw fail("the billet never reached a workable heat: " + Arrays.toString(heat));
        if (since(lastPress) < 3) return;
        int want = lo == 0 ? 0 : lo == 2 ? 2 : 1;
        int ideal = (job.thermal.optimalMin() + job.thermal.optimalMax()) / 2;
        if (heat[hi] - heat[lo] > 300 && want != job.position && since(lastTurn) >= 12) {
            press(mc, WorkTargets.Kind.BILLET, -1, true); // each turn moves it one place along the tray
            lastTurn = total;
            turns++;
        } else if (heat[lo] < ideal - 200 && heat[hi] < job.thermal.optimalMax() - 200 && since(lastPump) > ThermalRules.PUMP_COOLDOWN_TICKS) {
            press(mc, WorkTargets.Kind.BELLOWS, -1, true);
            lastPump = total;
            pumps++;
        }
    }

    private static boolean inBand(ForgeJob job) {
        for (int heat : job.heat) if (heat < job.thermal.optimalMin() + 150 || heat > job.thermal.optimalMax() - 150) return false;
        return true;
    }

    /** Ideal blows in order on the right face; off to the forge's rest whenever the next zone has cooled too far. */
    private void shape(Minecraft mc) {
        hotbar(mc, HAMMER_SLOT);
        if (!focused(mc, anvil) || !handReady()) return;
        WorkpieceData piece = anvilBe(mc).workpiece();
        if (piece == null) throw fail("the piece left the anvil");
        ProcessState p = piece.process();
        int op = ShapingRules.nextOperation(p);
        if (p.allComplete() || op < 0) {
            evidence.put("hammer_blows", blows);
            evidence.put("turned_over", flips);
            evidence.put("reheats", reheats);
            moment("shaped");
            goal = Goal.QUENCH;
            next(Step.TAKE);
            return;
        }
        if (age > 8000) throw fail("shaping did not finish: progress " + p.progress() + "/1000");
        ProcessProfile.Operation o = p.profile.operations().get(op);
        if (!o.face().matches(p.flipped)) {
            if (since(lastFlip) >= 20 && since(lastStrike) >= 6) {
                pressKey(mc, WorkKeys.FLIP, GLFW.GLFW_KEY_R);
                lastFlip = total;
                flips++;
            }
            return;
        }
        int[] heat = anvilBe(mc).currentHeat();
        if (heat.length > o.zone() && heat[o.zone()] < p.thermal.optimalMin() + 200) {
            goal = Goal.SHAPE;
            next(Step.TAKE);
            return;
        }
        if (since(lastStrike) >= STRIKE_INTERVAL && press(mc, WorkTargets.Kind.OPERATION, op, true)) {
            lastStrike = total;
            blows++;
            if (blows == 2) moment("anvil_first_blows");
            if (p.progress() >= 500) moment("anvil_half_shaped");
        }
    }

    /** Pumps until the piece on the rest sits at the top of the ideal band, then takes it back. */
    private void reheat(Minecraft mc) {
        hotbar(mc, TONGS_SLOT);
        SmithsForgeBlockEntity f = forgeBe(mc);
        if (WorkpieceCodec.isHolding(mc.player.getMainHandItem()) && !f.restOccupied()) {
            next(goal == Goal.QUENCH ? Step.LOWER : Step.PLACE);
            return;
        }
        if (!focused(mc, forge) || !handReady()) return;
        WorkpieceData rest = f.rest();
        if (rest == null) {
            if (age > 200) throw fail("the piece on the rest is unreadable");
            return;
        }
        ProcessState p = rest.process();
        int lo = ThermalRules.coolest(p.heat);
        int hi = Math.max(p.heat[0], Math.max(p.heat[1], p.heat[2]));
        boolean hot = lo >= p.thermal.optimalMax() - 400 && hi <= p.thermal.optimalMax() - 50;
        if (hot || (age > 1600 && p.thermal.workable(lo))) {
            if (since(lastPress) >= 20) press(mc, WorkTargets.Kind.REST, -1, true); // empty tongs: take it back
        } else if (hi < p.thermal.optimalMax() - 300 && since(lastPump) > ThermalRules.PUMP_COOLDOWN_TICKS && since(lastPress) >= 3) {
            press(mc, WorkTargets.Kind.BELLOWS, -1, true);
            lastPump = total;
            pumps++;
        }
        if (age > 2400) throw fail("the piece on the rest never reheated: " + Arrays.toString(p.heat));
    }

    private void finished(Minecraft mc) {
        int slot = finishedSlot(mc);
        ItemStack stack = mc.player.getInventory().getItem(slot);
        QualityData quality = QualityData.get(stack).orElse(null);
        evidence.put("finished_item", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        evidence.put("finished_quality", quality != null ? quality.toString() : "none");
        if (quality != null) {
            evidence.put("forge_score", quality.forgeScore());
            evidence.put("anvil_score", quality.anvilScore());
            evidence.put("faulty", quality.faulty());
        }
        moment("finished");
        if (refine) next(Step.GRIND_PLACE);
        else complete();
    }

    private void complete() {
        if (FocusController.active()) FocusController.exit(null, true);
        evidence.put("focus_views_opened", focusOpens);
        evidence.put("client_ticks", total);
        step = Step.DONE;
    }

    // ------------------------------------------------------------------ inputs

    /** True once the focused view of {@code pos} is open; moves the smith to its standing spot and asks for it otherwise. */
    private boolean focused(Minecraft mc, BlockPos pos) {
        if (FocusController.focusedOn(pos)) return WorkFocusScreen.showing() != null;
        if (FocusController.active()) FocusController.exit(null, true);
        if (mc.screen != null || !atStand(mc, pos)) return false;
        if (!pos.equals(requestedFor) || since(requestedAt) >= 40) {
            FocusController.request(pos);
            if (!pos.equals(requestedFor)) focusOpens++;
            requestedFor = pos;
            requestedAt = total;
        }
        return false;
    }

    /** Whether the smith stands at the spot for this station; asks the server to move them there otherwise. */
    private boolean atStand(Minecraft mc, BlockPos station) {
        Vec3 stand = stands.get(station);
        if (stand == null || mc.player.position().distanceToSqr(stand) < 0.04) {
            movingTo = null;
            return since(movedAt) >= 4;
        }
        if (!stand.equals(movingTo) || since(movedAt) >= 60) {
            var server = mc.getSingleplayerServer();
            if (server == null) throw fail("moving between stations needs the integrated server");
            Vec3 target = Vec3.atCenterOf(station);
            server.execute(() -> {
                ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                lookFrom(player, player.serverLevel(), stand, target);
            });
            movingTo = stand;
            movedAt = total;
        }
        return false;
    }

    /** Selects a part on the focused view and presses the action key on it, as the keyboard does. */
    private boolean press(Minecraft mc, WorkTargets.Kind kind, int index, boolean release) {
        WorkFocusScreen screen = WorkFocusScreen.showing();
        if (screen == null) return false;
        List<WorkTargets.Target> targets = WorkTargets.list(mc.level, screen.focus().pos, screen.focus().kind);
        for (int i = 0; i < targets.size(); i++) {
            WorkTargets.Target t = targets.get(i);
            if (t.kind() != kind || (index >= 0 && t.index() != index)) continue;
            try {
                SELECTED.setInt(screen, i);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
            int key = key(WorkKeys.ACT, GLFW.GLFW_KEY_ENTER);
            screen.keyPressed(key, 0, 0);
            if (release) screen.keyReleased(key, 0, 0);
            lastPress = total;
            return true;
        }
        return false;
    }

    private void pressKey(Minecraft mc, KeyMapping mapping, int fallback) {
        WorkFocusScreen screen = WorkFocusScreen.showing();
        if (screen == null) return;
        int key = key(mapping, fallback);
        screen.keyPressed(key, 0, 0);
        screen.keyReleased(key, 0, 0);
        lastPress = total;
    }

    /** The player's use binding on the focused view (the secondary action), mouse or key. */
    private void pressUse(Minecraft mc) {
        WorkFocusScreen screen = WorkFocusScreen.showing();
        if (screen == null) return;
        InputConstants.Key use = mc.options.keyUse.getKey();
        if (use.getType() == InputConstants.Type.MOUSE) {
            screen.mouseClicked(screen.width / 2.0, screen.height / 2.0, use.getValue());
            screen.mouseReleased(screen.width / 2.0, screen.height / 2.0, use.getValue());
        } else {
            screen.keyPressed(use.getValue(), 0, 0);
            screen.keyReleased(use.getValue(), 0, 0);
        }
        lastPress = total;
    }

    private void releaseKey(Minecraft mc) {
        if (!keyDown) return;
        keyDown = false;
        WorkFocusScreen screen = WorkFocusScreen.showing();
        if (screen != null) screen.keyReleased(key(WorkKeys.ACT, GLFW.GLFW_KEY_ENTER), 0, 0);
    }

    private static int key(KeyMapping mapping, int fallback) {
        InputConstants.Key key = mapping.getKey();
        return key.getType() == InputConstants.Type.KEYSYM && key.getValue() > 0 ? key.getValue() : fallback;
    }

    /** Hotbar selection as the number keys make it; the slot reaches the server on the next tick. */
    private void hotbar(Minecraft mc, int slot) {
        if (mc.player.getInventory().selected == slot) return;
        mc.player.getInventory().selected = slot;
        selectedAt = total;
    }

    private boolean handReady() {
        return since(selectedAt) >= 3;
    }

    private int finishedSlot(Minecraft mc) {
        for (int i = 0; i < mc.player.getInventory().items.size(); i++) {
            ItemStack stack = mc.player.getInventory().items.get(i);
            if (stack.is(result) && QualityData.get(stack).isPresent()) return i;
        }
        return -1;
    }

    // ------------------------------------------------------------------ state

    private SmithsForgeBlockEntity forgeBe(Minecraft mc) {
        if (mc.level.getBlockEntity(forge) instanceof SmithsForgeBlockEntity be) return be;
        throw fail("no forge at " + forge.toShortString());
    }

    private SmithsAnvilBlockEntity anvilBe(Minecraft mc) {
        if (mc.level.getBlockEntity(anvil) instanceof SmithsAnvilBlockEntity be) return be;
        throw fail("no anvil at " + anvil.toShortString());
    }

    private SmithsTroughBlockEntity troughBe(Minecraft mc) {
        if (mc.level.getBlockEntity(trough) instanceof SmithsTroughBlockEntity be) return be;
        throw fail("no trough at " + trough.toShortString());
    }

    private SmithsGrindstoneBlockEntity grindBe(Minecraft mc) {
        if (mc.level.getBlockEntity(grindstone) instanceof SmithsGrindstoneBlockEntity be) return be;
        throw fail("no grindstone at " + grindstone.toShortString());
    }

    private int since(int tick) {
        return total - tick;
    }

    private void next(Step next) {
        step = next;
        age = 0;
    }

    private void moment(String name) {
        if (said.add(name)) moments.accept(name);
    }

    private IllegalStateException fail(String reason) {
        return new IllegalStateException("Hands-on run failed at " + step + " (tick " + total + "): " + reason);
    }

}
