package com.otectus.immersivesmithing.compat.jade;

import com.otectus.immersivesmithing.ImmersiveSmithing;
import com.otectus.immersivesmithing.block.SmithsAnvilBlock;
import com.otectus.immersivesmithing.block.SmithsForgeBlock;
import com.otectus.immersivesmithing.block.SmithsGrindstoneBlock;
import com.otectus.immersivesmithing.block.SmithsTroughBlock;
import com.otectus.immersivesmithing.blockentity.SmithsAnvilBlockEntity;
import com.otectus.immersivesmithing.blockentity.SmithsForgeBlockEntity;
import com.otectus.immersivesmithing.blockentity.SmithsTroughBlockEntity;
import com.otectus.immersivesmithing.material.MaterialUnits;
import com.otectus.immersivesmithing.config.ServerConfig;
import com.otectus.immersivesmithing.quality.QualityData;
import com.otectus.immersivesmithing.quality.QualityEligibility;
import com.otectus.immersivesmithing.workpiece.WorkpieceData;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade overlays for the four stations. Discovered by Jade through the annotation, so nothing here loads without
 * it. The server providers write what the block entity knows into the tooltip's server data; the client providers
 * only format it, so the overlay is right even where a block entity does not sync a field.
 */
@WailaPlugin
public class SmithingJadePlugin implements IWailaPlugin {
    static final ResourceLocation FORGE = ImmersiveSmithing.id("forge");
    static final ResourceLocation ANVIL = ImmersiveSmithing.id("anvil");
    static final ResourceLocation TROUGH = ImmersiveSmithing.id("trough");
    static final ResourceLocation GRINDSTONE = ImmersiveSmithing.id("grindstone");

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(ForgeProvider.INSTANCE, SmithsForgeBlockEntity.class);
        registration.registerBlockDataProvider(AnvilProvider.INSTANCE, SmithsAnvilBlockEntity.class);
        registration.registerBlockDataProvider(TroughProvider.INSTANCE, SmithsTroughBlockEntity.class);
        registration.registerBlockDataProvider(GrindstoneProvider.INSTANCE, com.otectus.immersivesmithing.blockentity.SmithsGrindstoneBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(ForgeProvider.INSTANCE, SmithsForgeBlock.class);
        registration.registerBlockComponent(AnvilProvider.INSTANCE, SmithsAnvilBlock.class);
        registration.registerBlockComponent(TroughProvider.INSTANCE, SmithsTroughBlock.class);
        registration.registerBlockComponent(GrindstoneProvider.INSTANCE, SmithsGrindstoneBlock.class);
    }

    private static MutableComponent line(String key, Object... args) {
        return Component.translatable("jade.immersive_smithing." + key, args);
    }

    private static Component heat(String band) {
        return Component.translatable("heat.immersive_smithing." + band);
    }

    enum ForgeProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
        INSTANCE;

        @Override
        public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            if (!(accessor.getBlockEntity() instanceof SmithsForgeBlockEntity forge)) return;
            data.putInt("Molten", forge.moltenUnits());
            data.putInt("Reserved", forge.reservedUnits());
            data.putInt("Deposits", forge.depositUnits());
            data.putInt("Capacity", forge.capacity());
            data.putBoolean("Ready", forge.isReady());
            data.putBoolean("Burning", forge.isBurning());
            data.putBoolean("Lava", forge.hasLava());
            forge.familyData().ifPresent(f -> data.putString("Family", Component.Serializer.toJson(f.displayName())));
            forge.familyData().filter(f -> forge.isReady() && ServerConfig.get(ServerConfig.CASTING_ENABLED)).ifPresent(f -> {
                if (!f.casting().ingots().isEmpty()) data.putBoolean("CastIngots", true);
                if (!f.casting().nuggets().isEmpty() && ServerConfig.get(ServerConfig.CASTING_ALLOW_NUGGETS)) data.putBoolean("CastNuggets", true);
            });
            ItemStack fuel = forge.fuel();
            if (!fuel.isEmpty()) data.put("Fuel", fuel.save(new CompoundTag()));
            if (forge.plan() != null && accessor.getLevel() != null) {
                accessor.getLevel().getRecipeManager().byKey(forge.plan()).ifPresent(r ->
                        data.put("Plan", r.getResultItem(accessor.getLevel().registryAccess()).save(new CompoundTag())));
            }
            var job = forge.job();
            if (job != null) {
                data.put("Job", job.target.save(new CompoundTag()));
                data.putString("JobHeat", job.thermal.band(com.otectus.immersivesmithing.work.ThermalRules.coolest(job.heat)).key());
            }
            var rest = forge.rest();
            if (rest != null) {
                data.put("Rest", rest.target().save(new CompoundTag()));
                data.putString("RestHeat", rest.process().thermal.band(com.otectus.immersivesmithing.work.ThermalRules.coolest(rest.process().heat)).key());
            }
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getServerData();
            if (!data.contains("Capacity")) return;
            int molten = data.getInt("Molten");
            int reserved = data.getInt("Reserved");
            int deposits = data.getInt("Deposits");
            if (molten + deposits == 0) {
                tooltip.add(line("forge.empty").withStyle(ChatFormatting.GRAY));
            } else {
                Component family = data.contains("Family") ? Component.Serializer.fromJson(data.getString("Family")) : Component.empty();
                if (molten > 0) tooltip.add(line("forge.molten", family, MaterialUnits.describe(molten)).withStyle(ChatFormatting.GOLD));
                if (reserved > 0) tooltip.add(line("forge.reserved", MaterialUnits.describe(reserved)).withStyle(ChatFormatting.GRAY));
                if (deposits > 0) tooltip.add(line("forge.deposits", family, MaterialUnits.describe(deposits)).withStyle(ChatFormatting.GRAY));
            }
            if (data.contains("Job")) {
                tooltip.add(line("forge.job", ItemStack.of(data.getCompound("Job")).getHoverName(), heat(data.getString("JobHeat"))).withStyle(ChatFormatting.YELLOW));
            } else if (data.contains("Plan")) {
                tooltip.add(line("forge.plan", ItemStack.of(data.getCompound("Plan")).getHoverName()).withStyle(ChatFormatting.GREEN));
            }
            if (data.contains("Rest")) {
                tooltip.add(line("forge.rest", ItemStack.of(data.getCompound("Rest")).getHoverName(), heat(data.getString("RestHeat"))).withStyle(ChatFormatting.GOLD));
            }
            Component castable = castable(data, molten - reserved);
            if (castable != null) tooltip.add(line("forge.castable", castable).withStyle(ChatFormatting.GOLD));
            else if (data.getBoolean("Burning")) tooltip.add(line("forge.heating").withStyle(ChatFormatting.YELLOW));
            if (data.getBoolean("Lava")) tooltip.add(line("forge.lava").withStyle(ChatFormatting.RED));
            else if (data.contains("Fuel")) {
                ItemStack fuel = ItemStack.of(data.getCompound("Fuel"));
                tooltip.add(line("forge.fuel", fuel.getCount(), fuel.getHoverName()).withStyle(ChatFormatting.GRAY));
            } else {
                tooltip.add(line("forge.no_fuel").withStyle(ChatFormatting.DARK_GRAY));
            }
        }

        /** What the Cast panel can make of the free metal: whole ingots, with the remainder as nuggets where the metal has them. */
        @org.jetbrains.annotations.Nullable
        private static Component castable(CompoundTag data, int molten) {
            if (molten <= 0) return null;
            boolean ingots = data.getBoolean("CastIngots");
            boolean nuggets = data.getBoolean("CastNuggets");
            if (ingots && nuggets) return MaterialUnits.describe(molten);
            if (ingots) return molten >= MaterialUnits.INGOT ? MaterialUnits.describe(molten - molten % MaterialUnits.INGOT) : null;
            if (nuggets) return Component.translatable(molten == 1 ? "units.immersive_smithing.nugget" : "units.immersive_smithing.nuggets", molten);
            return null;
        }

        @Override
        public ResourceLocation getUid() {
            return FORGE;
        }
    }

    enum AnvilProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
        INSTANCE;

        @Override
        public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            if (!(accessor.getBlockEntity() instanceof SmithsAnvilBlockEntity anvil)) return;
            data.putBoolean("Locked", anvil.isLocked());
            if (anvil.hasOutput()) data.put("Output", anvil.output().save(new CompoundTag()));
            WorkpieceData workpiece = anvil.workpiece();
            if (workpiece == null) return;
            var p = workpiece.process();
            data.put("Target", workpiece.target().save(new CompoundTag()));
            data.putString("Stage", p.stage.key());
            data.putInt("Progress", p.progress() / 10);
            data.putString("Heat", p.thermal.band(com.otectus.immersivesmithing.work.ThermalRules.coolest(anvil.currentHeat())).key());
            if (p.needsFitting()) data.putInt("Sockets", p.socketsLeft());
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getServerData();
            if (!data.contains("Locked")) return;
            if (data.contains("Output")) {
                tooltip.add(line("anvil.output", ItemStack.of(data.getCompound("Output")).getHoverName()).withStyle(ChatFormatting.GREEN));
            } else if (!data.contains("Target")) {
                tooltip.add(line("anvil.empty").withStyle(ChatFormatting.GRAY));
            } else {
                ItemStack target = ItemStack.of(data.getCompound("Target"));
                tooltip.add(line("anvil.piece", target.getHoverName(), Component.translatable("stage.immersive_smithing." + data.getString("Stage")))
                        .withStyle(ChatFormatting.GOLD));
                String stage = data.getString("Stage");
                if (stage.equals("billet") || stage.equals("shaping")) {
                    tooltip.add(line("anvil.progress", data.getInt("Progress"), heat(data.getString("Heat"))).withStyle(ChatFormatting.GRAY));
                }
                if (data.contains("Sockets")) tooltip.add(line("anvil.components", data.getInt("Sockets")).withStyle(ChatFormatting.GRAY));
            }
            if (data.getBoolean("Locked")) tooltip.add(line("anvil.in_use").withStyle(ChatFormatting.RED));
        }

        @Override
        public ResourceLocation getUid() {
            return ANVIL;
        }
    }

    enum TroughProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
        INSTANCE;

        @Override
        public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            if (!(accessor.getBlockEntity() instanceof SmithsTroughBlockEntity trough)) return;
            data.putInt("Quenches", trough.quenchesLeft());
            data.putInt("Water", trough.water());
            data.putInt("WaterCapacity", trough.capacity());
            WorkpieceData piece = trough.quenchPiece();
            if (piece != null) {
                data.put("Quenching", piece.target().save(new CompoundTag()));
                data.putInt("QuenchProgress", Math.min(100, com.otectus.immersivesmithing.work.QuenchRules.progress(piece.process(), trough.quenchDuration()) / 10));
            }
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getServerData();
            if (!data.contains("Quenches")) return;
            tooltip.add(line("trough.quenches", data.getInt("Quenches"), data.getInt("Water"), data.getInt("WaterCapacity"))
                    .withStyle(data.getInt("Quenches") > 0 ? ChatFormatting.AQUA : ChatFormatting.GRAY));
            if (data.contains("Quenching")) {
                tooltip.add(line("trough.quenching", ItemStack.of(data.getCompound("Quenching")).getHoverName(), data.getInt("QuenchProgress"))
                        .withStyle(ChatFormatting.GOLD));
            }
        }

        @Override
        public ResourceLocation getUid() {
            return TROUGH;
        }
    }

    /** The piece resting at the wheel, its aspect and the exact price of a pass for the looking player (their cost modifiers included). */
    enum GrindstoneProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
        INSTANCE;

        @Override
        public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            if (!(accessor.getBlockEntity() instanceof com.otectus.immersivesmithing.blockentity.SmithsGrindstoneBlockEntity stone)) return;
            data.putBoolean("Enabled", ServerConfig.get(ServerConfig.ENABLE_SMITH_GRINDSTONE));
            ItemStack piece = stone.piece();
            if (piece.isEmpty()) return;
            data.put("Piece", piece.save(new CompoundTag()));
            data.putBoolean("Efficacy", stone.efficacy());
            QualityData quality = QualityData.get(piece).orElse(null);
            boolean ungraded = quality == null;
            if (quality == null && QualityEligibility.canCarryQuality(piece)) quality = QualityEligibility.neutral();
            if (quality == null) return;
            data.putBoolean("Ungraded", ungraded);
            data.putBoolean("Faulty", quality.faulty());
            int score = stone.efficacy() ? quality.anvilScore() : quality.forgeScore();
            data.putInt("Score", score);
            if (accessor.getPlayer() instanceof net.minecraft.server.level.ServerPlayer player) {
                data.putInt("Cost", SmithsGrindstoneBlock.levelCost(player, piece, score));
            }
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getServerData();
            if (!data.contains("Enabled")) return;
            if (!data.getBoolean("Enabled")) {
                tooltip.add(Component.translatable("message.immersive_smithing.grindstone_disabled").withStyle(ChatFormatting.GRAY));
                return;
            }
            if (!data.contains("Piece")) {
                tooltip.add(line("grindstone.empty").withStyle(ChatFormatting.GRAY));
                return;
            }
            tooltip.add(line("grindstone.piece", ItemStack.of(data.getCompound("Piece")).getHoverName()).withStyle(ChatFormatting.GOLD));
            if (data.getBoolean("Faulty")) {
                tooltip.add(line("grindstone.faulty").withStyle(ChatFormatting.RED));
                return;
            }
            if (data.getBoolean("Ungraded")) tooltip.add(line("grindstone.ungraded").withStyle(ChatFormatting.GRAY));
            if (data.contains("Cost")) {
                tooltip.add(line("grindstone.quote", com.otectus.immersivesmithing.work.GrindstoneActions.aspectName(data.getBoolean("Efficacy")),
                        data.getInt("Score"), data.getInt("Cost")).withStyle(ChatFormatting.GRAY));
            }
        }

        @Override
        public ResourceLocation getUid() {
            return GRINDSTONE;
        }
    }
}
