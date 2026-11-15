package com.otectus.immersivesmithing.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.otectus.immersivesmithing.client.work.BladeWorkpieceModel;
import com.otectus.immersivesmithing.client.work.HeatColors;
import com.otectus.immersivesmithing.client.work.WorkpieceRenderer;
import com.otectus.immersivesmithing.client.work.WorkpieceShape;
import com.otectus.immersivesmithing.client.work.WorkpieceVisuals;
import com.otectus.immersivesmithing.item.SmithingTier;
import com.otectus.immersivesmithing.registry.ModItems;
import com.otectus.immersivesmithing.work.ProcessState;
import com.otectus.immersivesmithing.workpiece.WorkpieceCodec;
import com.otectus.immersivesmithing.workpiece.WorkpieceData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Draws a loaded tongs stack as the ordinary tier model plus the real in-progress workpiece. The jaw anchor is derived
 * from the shared 16x16 tongs silhouette: the two metal tips surround pixel (12, 4), well beyond the wooden grips that
 * end around (8, 8). Because the carried geometry comes from the process visual, an axe head remains an axe head and a
 * blade remains a blade; completed-item handles are never mistaken for hot stock.
 */
public final class TongsItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static final float JAW_X = 12F / 16F;
    private static final float JAW_Y = 1F - 4F / 16F;
    private static final float JAW_Z = 0.5F;
    private static final float MAX_PIECE_SPAN = 0.42F;
    private static final int SHAPE_CACHE_LIMIT = 64;

    /** Replaced atomically after every model bake, so resource-pack model changes cannot leave stale delegates. */
    private static volatile Map<Item, BakedModel> baseModels = Map.of();
    private static final LinkedHashMap<UUID, CachedShape> shapes = new LinkedHashMap<>(16, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, CachedShape> eldest) {
            return size() > SHAPE_CACHE_LIMIT;
        }
    };

    public TongsItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    /** Runtime pack-test hook: verifies model routing and that the saved delegate cannot recurse into this BEWLR. */
    public static boolean modelContract(ItemStack stack) {
        BakedModel routed = Minecraft.getInstance().getItemRenderer().getModel(stack, Minecraft.getInstance().level, null, 0);
        BakedModel delegate = baseModels.get(stack.getItem());
        return routed instanceof CustomTongsModel && routed.isCustomRenderer() && routed.getOverrides() == ItemOverrides.EMPTY
                && delegate instanceof RenderDelegate && !delegate.isCustomRenderer()
                && delegate.getOverrides() == ItemOverrides.EMPTY;
    }

    /** Installs a custom-render flag without replacing any resource-pack transforms or baked tier quads. */
    public static void modifyModels(ModelEvent.ModifyBakingResult event) {
        Map<Item, BakedModel> next = new java.util.IdentityHashMap<>();
        for (SmithingTier tier : SmithingTier.values()) {
            Item item = ModItems.tongs(tier);
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
            if (id == null) continue;
            ModelResourceLocation modelId = new ModelResourceLocation(id, "inventory");
            BakedModel model = event.getModels().get(modelId);
            if (model == null) continue;
            next.put(item, new RenderDelegate(model));
            event.getModels().put(modelId, new CustomTongsModel(model));
        }
        baseModels = Map.copyOf(next);
        synchronized (shapes) {
            shapes.clear();
        }
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers,
                             int light, int overlay) {
        BakedModel base = baseModels.get(stack.getItem());
        if (base == null) return;

        WorkpieceData piece = WorkpieceCodec.getHeld(stack).orElse(null);
        if (piece != null) renderPiece(piece, pose, buffers, light);

        // ItemRenderer has already translated the custom-render pose by -0.5. Compensate before asking it to draw the
        // saved baked model, whose own NONE transform then lands its 0..1 geometry in exactly the same item frame.
        pose.pushPose();
        pose.translate(0.5F, 0.5F, 0.5F);
        Minecraft mc = Minecraft.getInstance();
        mc.getItemRenderer().render(stack, ItemDisplayContext.NONE, false, pose, buffers, light, overlay, base);
        pose.popPose();
    }

    private static void renderPiece(WorkpieceData piece, PoseStack pose, MultiBufferSource buffers, int light) {
        ProcessState process = piece.process();
        WorkpieceShape shape = shape(piece);
        BladeWorkpieceModel blade = BladeWorkpieceModel.get(piece.target(), shape.visual);
        float widest = 0F;
        for (float width : shape.width) widest = Math.max(widest, width);
        float scale = MAX_PIECE_SPAN / Math.max(0.72F, Math.max(shape.length, widest));

        pose.pushPose();
        pose.translate(JAW_X, JAW_Y, JAW_Z);
        // The item's jaws run lower-left to upper-right. Rotate the process frame into the sprite plane, with its broad
        // face visible and its thickness passing between the front and back jaw pixels.
        pose.mulPose(Axis.ZP.rotationDegrees(45F));
        pose.mulPose(Axis.XP.rotationDegrees(90F));
        pose.scale(scale, scale, scale);
        // Grip the stock near its tang end (canonical u ~= 0.18), not through its centre. Most of the recognizable
        // silhouette then projects beyond the tips, while the contact point remains hot metal rather than a fitted grip.
        // Centre its thickness through the fork as well, so neither face hides behind the front jaw pixel.
        pose.translate(shape.length * 0.32F, -shape.maxThickness() * 0.5F, 0F);
        int coldColor = HeatColors.coldColor(piece.family().getPath());
        if (blade != null && (process.allComplete() || process.stage.quenched())) {
            blade.render(pose, buffers, shape.visual, process, process.heat, coldColor, light);
        } else {
            WorkpieceRenderer.render(pose, buffers, shape, process.heat, coldColor, light, process, -1, false);
        }
        pose.popPose();
    }

    /** Reuses the small deterministic mesh description until shaping state or resource-pack visuals change. */
    private static WorkpieceShape shape(WorkpieceData piece) {
        ProcessState process = piece.process();
        int workHash = Arrays.hashCode(process.work);
        int generation = WorkpieceVisuals.generation();
        ResourceLocation visualId = process.profile.visual();
        Minecraft mc = Minecraft.getInstance();
        BakedModel targetModel = mc.getItemRenderer().getModel(piece.target(), mc.level, null, 0);
        synchronized (shapes) {
            CachedShape cached = shapes.get(process.id);
            if (cached != null && cached.workHash == workHash && cached.flipped == process.flipped
                    && cached.generation == generation && cached.visualId.equals(visualId)
                    && cached.targetModel == targetModel) {
                return cached.shape;
            }
            WorkpieceShape shape = WorkpieceShape.of(piece);
            shapes.put(process.id, new CachedShape(workHash, process.flipped, generation, visualId, targetModel, shape));
            return shape;
        }
    }

    private record CachedShape(int workHash, boolean flipped, int generation, ResourceLocation visualId,
                               BakedModel targetModel, WorkpieceShape shape) {}

    /** The item wrapper suppresses the old loaded-overlay override and routes both empty and loaded stacks here. */
    private static final class CustomTongsModel extends BakedModelWrapper<BakedModel> {
        private CustomTongsModel(BakedModel original) {
            super(original);
        }

        @Override
        public boolean isCustomRenderer() {
            return true;
        }

        @Override
        public ItemOverrides getOverrides() {
            return ItemOverrides.EMPTY;
        }

        @Override
        public BakedModel applyTransform(ItemDisplayContext context, PoseStack pose, boolean leftHand) {
            originalModel.applyTransform(context, pose, leftHand);
            return this;
        }
    }

    /** Prevents a resource-pack custom model from recursively entering this renderer when its quads are drawn. */
    private static final class RenderDelegate extends BakedModelWrapper<BakedModel> {
        private RenderDelegate(BakedModel original) {
            super(original);
        }

        @Override
        public boolean isCustomRenderer() {
            return false;
        }

        @Override
        public ItemOverrides getOverrides() {
            return ItemOverrides.EMPTY;
        }

        @Override
        public BakedModel applyTransform(ItemDisplayContext context, PoseStack pose, boolean leftHand) {
            originalModel.applyTransform(context, pose, leftHand);
            return this;
        }
    }
}
