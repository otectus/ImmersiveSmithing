package com.otectus.immersivesmithing.client.compat.jei;

import com.otectus.immersivesmithing.ImmersiveSmithing;
import com.otectus.immersivesmithing.client.screen.VanillaGui;
import com.otectus.immersivesmithing.material.MaterialUnits;
import com.otectus.immersivesmithing.recipe.AuxiliaryIngredient;
import com.otectus.immersivesmithing.recipe.SmithingRecipe;
import com.otectus.immersivesmithing.registry.ModItems;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The whole process in JEI's own vanilla style: metal and components in standard slots, Forge, Anvil and Trough
 * joined by JEI's recipe arrows, the finished piece in the output slot, and dark grey notes on material, pattern and
 * quality. Everything is JEI's standard drawables on its own background; nothing here is themed.
 */
public class SmithingRecipeCategory implements IRecipeCategory<SmithingRecipe> {
    public static final RecipeType<SmithingRecipe> TYPE = RecipeType.create(ImmersiveSmithing.MOD_ID, "smithing", SmithingRecipe.class);
    private static final int WIDTH = 186;
    private static final int HEIGHT = 104;
    private static final int PROCESS_X = 60;
    private static final int PROCESS_Y = 20;
    private static final int OUTPUT_X = 160;

    private final IDrawable background;
    private final IDrawable icon;
    private final IDrawable slot;
    private final IDrawable outputSlot;
    private final IDrawable arrow;
    private final IDrawable forge;
    private final IDrawable anvil;
    private final IDrawable trough;

    public SmithingRecipeCategory(IGuiHelper helper) {
        this.background = helper.createBlankDrawable(WIDTH, HEIGHT);
        this.icon = helper.createDrawableItemStack(new ItemStack(ModItems.SMITHS_ANVIL.get()));
        this.slot = helper.getSlotDrawable();
        this.outputSlot = helper.getOutputSlot();
        this.arrow = helper.getRecipeArrow();
        this.forge = helper.createDrawableItemStack(new ItemStack(ModItems.SMITHS_FORGE.get()));
        this.anvil = helper.createDrawableItemStack(new ItemStack(ModItems.SMITHS_ANVIL.get()));
        this.trough = helper.createDrawableItemStack(new ItemStack(ModItems.SMITHS_TROUGH.get()));
    }

    @Override
    public RecipeType<SmithingRecipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.immersive_smithing.category");
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public IDrawable getBackground() {
        return background;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, SmithingRecipe recipe, IFocusGroup focuses) {
        List<List<ItemStack>> metal = recipe.metalDisplay();
        for (int i = 0; i < Math.min(2, metal.size()); i++) {
            builder.addSlot(RecipeIngredientRole.INPUT, 1 + i * 18, 1)
                    .addItemStacks(metal.get(i))
                    .setBackground(slot, -1, -1)
                    .addTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable("jei.immersive_smithing.metal",
                            recipe.familyName(), MaterialUnits.describe(recipe.metalUnits())).withStyle(ChatFormatting.GOLD)));
        }
        List<AuxiliaryIngredient> aux = recipe.auxiliary();
        for (int i = 0; i < Math.min(6, aux.size()); i++) {
            AuxiliaryIngredient a = aux.get(i);
            List<ItemStack> stacks = Arrays.stream(a.ingredient().getItems()).map(s -> s.copyWithCount(a.count())).toList();
            builder.addSlot(RecipeIngredientRole.INPUT, 1 + (i % 3) * 18, 21 + (i / 3) * 18)
                    .addItemStacks(stacks)
                    .setBackground(slot, -1, -1)
                    .addTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable(a.consumesEquipment()
                            ? "jei.immersive_smithing.equipment_consumed" : "jei.immersive_smithing.component_consumed")
                            .withStyle(ChatFormatting.GRAY)));
        }
        builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_X + 5, PROCESS_Y - 4)
                .addItemStack(recipe.result())
                .setBackground(outputSlot, -5, -5)
                .addTooltipCallback((view, tooltip) -> {
                    tooltip.add(Component.translatable("jei.immersive_smithing.quality_note").withStyle(ChatFormatting.GRAY));
                    tooltip.add(Component.translatable(recipe.auxiliary().stream().anyMatch(AuxiliaryIngredient::consumesEquipment)
                            ? "jei.immersive_smithing.structural_upgrade" : "jei.immersive_smithing.new_equipment").withStyle(ChatFormatting.GOLD));
                });
        // Overflow still participates in ingredient lookup/focus; its full alternatives also appear below.
        for (int i = 6; i < aux.size(); i++) {
            AuxiliaryIngredient a = aux.get(i);
            builder.addInvisibleIngredients(RecipeIngredientRole.INPUT)
                    .addItemStacks(Arrays.stream(a.ingredient().getItems()).map(s -> s.copyWithCount(a.count())).toList());
        }
    }

    @Override
    public void draw(SmithingRecipe recipe, IRecipeSlotsView slots, GuiGraphics g, double mouseX, double mouseY) {
        forge.draw(g, PROCESS_X, PROCESS_Y - 4);
        arrow.draw(g, PROCESS_X + 17, PROCESS_Y - 4);
        anvil.draw(g, PROCESS_X + 42, PROCESS_Y - 4);
        arrow.draw(g, PROCESS_X + 59, PROCESS_Y - 4);
        trough.draw(g, PROCESS_X + 84, PROCESS_Y - 4);
        Font font = Minecraft.getInstance().font;
        Component metal = Component.translatable("jei.immersive_smithing.material", recipe.familyName(), MaterialUnits.describe(recipe.metalUnits()));
        g.drawString(font, VanillaGui.clipped(font, metal, WIDTH), 0, 60, VanillaGui.LABEL, false);
        Component pattern = Component.translatable("jei.immersive_smithing.pattern", patternName(recipe));
        if (recipe.isAuto()) pattern = pattern.copy().append(Component.translatable("jei.immersive_smithing.auto"));
        g.drawString(font, VanillaGui.clipped(font, pattern, WIDTH), 0, 71, VanillaGui.LABEL_DIM, false);
        g.drawString(font, VanillaGui.clipped(font, Component.translatable("jei.immersive_smithing.quality_forge"), WIDTH), 0, 82, VanillaGui.LABEL, false);
        g.drawString(font, VanillaGui.clipped(font, Component.translatable("jei.immersive_smithing.quality_anvil"), WIDTH), 0, 93, VanillaGui.LABEL, false);
        if (recipe.auxiliary().size() > 6) {
            g.drawString(font, Component.translatable("jei.immersive_smithing.more_components", recipe.auxiliary().size() - 6), 58, 46, VanillaGui.LABEL, false);
        }
    }

    private static Component patternName(SmithingRecipe recipe) {
        return Component.translatable("pattern." + recipe.anvilPattern().getNamespace() + "." + recipe.anvilPattern().getPath());
    }

    @Override
    public List<Component> getTooltipStrings(SmithingRecipe recipe, IRecipeSlotsView slots, double mouseX, double mouseY) {
        List<Component> tips = new ArrayList<>();
        if (mouseY >= PROCESS_Y - 4 && mouseY < PROCESS_Y + 12) {
            if (mouseX >= PROCESS_X && mouseX < PROCESS_X + 16) {
                tips.add(Component.translatable("block.immersive_smithing.smiths_forge"));
                tips.add(Component.translatable("jei.immersive_smithing.step.plan").withStyle(ChatFormatting.GRAY));
            } else if (mouseX >= PROCESS_X + 42 && mouseX < PROCESS_X + 58) {
                tips.add(Component.translatable("block.immersive_smithing.smiths_anvil"));
                tips.add(Component.translatable("jei.immersive_smithing.step.shape").withStyle(ChatFormatting.GRAY));
            } else if (mouseX >= PROCESS_X + 84 && mouseX < PROCESS_X + 100) {
                tips.add(Component.translatable("block.immersive_smithing.smiths_trough"));
                tips.add(Component.translatable("jei.immersive_smithing.step.quench").withStyle(ChatFormatting.GRAY));
            }
        } else if (mouseY >= 59 && mouseY < 70) {
            tips.add(Component.translatable("jei.immersive_smithing.material", recipe.familyName(), MaterialUnits.describe(recipe.metalUnits())));
            tips.add(Component.translatable("jei.immersive_smithing.metal_cost_explainer").withStyle(ChatFormatting.GRAY));
            if (!recipe.auxiliary().isEmpty()) {
                tips.add(Component.translatable("jei.immersive_smithing.components").withStyle(ChatFormatting.GOLD));
                for (AuxiliaryIngredient auxiliary : recipe.auxiliary()) {
                    ItemStack[] options = auxiliary.ingredient().getItems();
                    var name = Component.empty();
                    if (options.length == 0) name.append(Component.translatable("jei.immersive_smithing.unknown_component"));
                    for (ItemStack option : options) {
                        if (!name.getString().isEmpty()) name.append(" / ");
                        name.append(option.getHoverName());
                    }
                    tips.add(Component.translatable("jei.immersive_smithing.component_line", auxiliary.count(), name).withStyle(ChatFormatting.GRAY));
                }
            }
        } else if (mouseY >= 70 && mouseY < 81) {
            tips.add(Component.translatable("jei.immersive_smithing.pattern", patternName(recipe)));
        } else if (mouseY >= 81) {
            tips.add(Component.translatable("jei.immersive_smithing.quality_note").withStyle(ChatFormatting.GRAY));
        }
        return tips;
    }

    @Override
    public ResourceLocation getRegistryName(SmithingRecipe recipe) {
        return recipe.getId();
    }
}
