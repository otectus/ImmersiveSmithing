package com.otectus.immersivesmithing.api.event;

import com.otectus.immersivesmithing.quality.QualityData;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;

/**
 * Posted on the Forge bus once per finished piece, when it is completed: at the trough as it is lifted from a correct
 * quench (pieces without components, and pieces forged before 1.3.0), or at the anvil when its last component is fitted.
 * Quality and the maker's mark are already written and the piece has not reached its output or inventory yet:
 * {@link #getItem()} is the live stack, and changes made by listeners stay on the item. Not cancellable; server side only.
 * Progression mods and KubeJS scripts can listen to it to reward smithing like any other craft. Refinement at the
 * grindstone and signing never post it again.
 *
 * <p>Timing changed in 1.3.0: for pieces with components it now fires after fitting instead of at the quench.
 * {@link #getTroughPos()} is still the trough where the piece was quenched; {@link #getCompletionPos()} and
 * {@link #getCompletedAt()} say where it was finished.</p>
 */
public class ItemSmithedEvent extends PlayerEvent {
    /** Where a piece was completed. */
    public enum Station { TROUGH, ANVIL }

    private final ItemStack result;
    private final QualityData quality;
    private final ResourceLocation recipeId;
    private final ResourceLocation family;
    private final int metalUnits;
    private final BlockPos troughPos;
    private final BlockPos completionPos;
    private final Station completedAt;

    public ItemSmithedEvent(ServerPlayer smith, ItemStack result, QualityData quality, ResourceLocation recipeId,
                            ResourceLocation family, int metalUnits, BlockPos troughPos) {
        this(smith, result, quality, recipeId, family, metalUnits, troughPos, troughPos, Station.TROUGH);
    }

    public ItemSmithedEvent(ServerPlayer smith, ItemStack result, QualityData quality, ResourceLocation recipeId,
                            ResourceLocation family, int metalUnits, BlockPos troughPos, BlockPos completionPos, Station completedAt) {
        super(smith);
        this.result = result;
        this.quality = quality;
        this.recipeId = recipeId;
        this.family = family;
        this.metalUnits = metalUnits;
        this.troughPos = troughPos;
        this.completionPos = completionPos;
        this.completedAt = completedAt;
    }

    @Override
    public ServerPlayer getEntity() {
        return (ServerPlayer) super.getEntity();
    }

    /** The finished item, mutable. */
    public ItemStack getItem() { return result; }
    public QualityData getQuality() { return quality; }
    /** The smithing recipe that was forged, explicit or automatic. */
    public ResourceLocation getRecipeId() { return recipeId; }
    /** The material family the metal came from. */
    public ResourceLocation getFamily() { return family; }
    /** Material units consumed (nugget 1, ingot 9, block 81). */
    public int getMetalUnits() { return metalUnits; }
    /** The trough the piece was quenched in. */
    public BlockPos getTroughPos() { return troughPos; }
    /** Where the piece was completed: the trough, or the anvil it was fitted on. */
    public BlockPos getCompletionPos() { return completionPos; }
    public Station getCompletedAt() { return completedAt; }
}
