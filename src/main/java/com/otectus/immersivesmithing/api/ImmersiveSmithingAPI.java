package com.otectus.immersivesmithing.api;

import com.otectus.immersivesmithing.ImmersiveSmithing;
import com.otectus.immersivesmithing.config.ServerConfig;
import com.otectus.immersivesmithing.recipe.SmithingRecipe;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.InterModComms;
import net.minecraftforge.fml.event.lifecycle.InterModProcessEvent;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Public Java hooks for integrations whose items cannot be described by datapacks alone. Register during
 * {@code FMLCommonSetupEvent}, or send an IMC message to {@code immersive_smithing} whose method is one of the
 * {@code IMC_*} names and whose payload is a {@link Supplier} of the matching interface.
 */
public final class ImmersiveSmithingAPI {
    public static final String IMC_MATERIAL_PROVIDER = "material_provider";
    public static final String IMC_RECIPE_PROVIDER = "recipe_provider";
    public static final String IMC_EFFICACY_HANDLER = "efficacy_handler";
    public static final String IMC_SHIELD_HANDLER = "shield_handler";
    public static final String IMC_CLASSIFIER = "equipment_classifier";
    public static final String IMC_RECYCLING_PROVIDER = "recycling_provider";
    public static final String IMC_EXCLUSION = "exclusion";
    public static final String IMC_MELT_VETO = "melt_veto";
    public static final String IMC_RECIPE_GATE = "recipe_gate";
    public static final String IMC_COST_MODIFIER = "cost_modifier";
    public static final String IMC_ASSIST_SOURCE = "assist_source";

    private static final List<ISmithingMaterialProvider> MATERIAL_PROVIDERS = new CopyOnWriteArrayList<>();
    private static final List<ISmithingRecipeProvider> RECIPE_PROVIDERS = new CopyOnWriteArrayList<>();
    private static final List<ISmithingEfficacyHandler> EFFICACY_HANDLERS = new CopyOnWriteArrayList<>();
    private static final List<IShieldSmithingHandler> SHIELD_HANDLERS = new CopyOnWriteArrayList<>();
    private static final List<IEquipmentClassifier> CLASSIFIERS = new CopyOnWriteArrayList<>();
    private static final List<IRecyclingValueProvider> RECYCLING_PROVIDERS = new CopyOnWriteArrayList<>();
    private static final List<Predicate<ItemStack>> EXCLUSIONS = new CopyOnWriteArrayList<>();
    private static final List<IMeltVeto> MELT_VETOES = new CopyOnWriteArrayList<>();
    private static final List<ISmithingRecipeGate> RECIPE_GATES = new CopyOnWriteArrayList<>();
    private static final List<ISmithingCostModifier> COST_MODIFIERS = new CopyOnWriteArrayList<>();
    private static final List<ISmithingAssistSource> ASSIST_SOURCES = new CopyOnWriteArrayList<>();

    public static void registerMaterialProvider(ISmithingMaterialProvider provider) { MATERIAL_PROVIDERS.add(provider); }
    public static void registerRecipeProvider(ISmithingRecipeProvider provider) { RECIPE_PROVIDERS.add(provider); }
    public static void registerEfficacyHandler(ISmithingEfficacyHandler handler) { EFFICACY_HANDLERS.add(handler); }
    public static void registerShieldHandler(IShieldSmithingHandler handler) { SHIELD_HANDLERS.add(handler); }
    public static void registerClassifier(IEquipmentClassifier classifier) { CLASSIFIERS.add(classifier); }
    public static void registerRecyclingProvider(IRecyclingValueProvider provider) { RECYCLING_PROVIDERS.add(provider); }
    /** Items matching the predicate are never auto-detected, disabled, recycled or given quality. */
    public static void registerExclusion(Predicate<ItemStack> exclusion) { EXCLUSIONS.add(exclusion); }

    public static List<ISmithingMaterialProvider> materialProviders() { return MATERIAL_PROVIDERS; }
    public static List<ISmithingRecipeProvider> recipeProviders() { return RECIPE_PROVIDERS; }
    public static List<ISmithingEfficacyHandler> efficacyHandlers() { return EFFICACY_HANDLERS; }
    public static List<IShieldSmithingHandler> shieldHandlers() { return SHIELD_HANDLERS; }
    public static List<IEquipmentClassifier> classifiers() { return CLASSIFIERS; }
    public static List<IRecyclingValueProvider> recyclingProviders() { return RECYCLING_PROVIDERS; }

    public static void registerMeltVeto(IMeltVeto veto) { MELT_VETOES.add(veto); }

    // ---- 1.1.0: recipe gates, cost modifiers and assistance (progression hooks) --------------------------------

    public static void registerRecipeGate(ISmithingRecipeGate gate) { if (gate != null) RECIPE_GATES.add(gate); }
    public static void unregisterRecipeGate(ISmithingRecipeGate gate) { RECIPE_GATES.remove(gate); }
    public static void registerCostModifier(ISmithingCostModifier modifier) { if (modifier != null) COST_MODIFIERS.add(modifier); }
    public static void unregisterCostModifier(ISmithingCostModifier modifier) { COST_MODIFIERS.remove(modifier); }
    public static void registerAssistSource(ISmithingAssistSource source) { if (source != null) ASSIST_SOURCES.add(source); }
    public static void unregisterAssistSource(ISmithingAssistSource source) { ASSIST_SOURCES.remove(source); }
    public static List<ISmithingRecipeGate> recipeGates() { return RECIPE_GATES; }
    public static List<ISmithingCostModifier> costModifiers() { return COST_MODIFIERS; }
    public static List<ISmithingAssistSource> assistSources() { return ASSIST_SOURCES; }

    /** The first registered reason to refuse {@code recipe} to {@code smith}, if any. A gate that throws is skipped. */
    public static Optional<Component> recipeDenial(ServerPlayer smith, SmithingRecipe recipe) {
        for (ISmithingRecipeGate gate : RECIPE_GATES) {
            try {
                Optional<Component> reason = gate.deny(smith, recipe);
                if (reason != null && reason.isPresent()) return reason;
            } catch (RuntimeException e) {
                ImmersiveSmithing.LOGGER.error("Recipe gate {} failed for {}", gate.getClass().getName(), recipe.getId(), e);
            }
        }
        return Optional.empty();
    }

    /** Metal units the forge charges {@code smith} for {@code recipe}: the recipe's own through every modifier, never below one. */
    public static int metalUnits(ServerPlayer smith, SmithingRecipe recipe) {
        int units = recipe.metalUnits();
        for (ISmithingCostModifier modifier : COST_MODIFIERS) {
            try {
                units = modifier.metalUnits(smith, recipe, units);
            } catch (RuntimeException e) {
                ImmersiveSmithing.LOGGER.error("Cost modifier {} failed for {}", modifier.getClass().getName(), recipe.getId(), e);
            }
        }
        return Math.max(1, Math.min(recipe.metalUnits(), units));
    }

    /** Experience levels one refinement of {@code piece} costs {@code smith}: {@code base} through every modifier, never below zero. */
    public static int refinementLevelCost(ServerPlayer smith, ItemStack piece, int base) {
        int levels = base;
        for (ISmithingCostModifier modifier : COST_MODIFIERS) {
            try {
                levels = modifier.refinementLevelCost(smith, piece, levels);
            } catch (RuntimeException e) {
                ImmersiveSmithing.LOGGER.error("Cost modifier {} failed for a refinement", modifier.getClass().getName(), e);
            }
        }
        return Math.max(0, levels);
    }

    /**
     * {@code baseMs} times every assistance source's answer for {@code smith} at {@code station}, the product clamped to
     * {@code 1.0..maxAssistTimeMultiplier}. Exactly {@code baseMs} with no sources. The anvil divides its blow recovery by
     * this factor.
     */
    public static int assistedTimeMs(ServerPlayer smith, SmithingStation station, int baseMs) {
        double product = 1.0;
        for (ISmithingAssistSource source : ASSIST_SOURCES) {
            try {
                double value = source.timeMultiplier(smith, station);
                if (Double.isFinite(value) && value > 0) product *= value;
            } catch (RuntimeException e) {
                ImmersiveSmithing.LOGGER.error("Assist source {} failed for {}", source.getClass().getName(), smith.getGameProfile().getName(), e);
            }
        }
        double ceiling = Math.max(1.0, ServerConfig.get(ServerConfig.MAX_ASSIST_TIME_MULTIPLIER));
        double clamped = Math.max(1.0, Math.min(ceiling, product));
        return (int) Math.round(baseMs * clamped);
    }

    /** The first registered reason to keep {@code stack} out of the forge, if any. A veto that throws is skipped. */
    public static Optional<Component> meltVeto(ItemStack stack) {
        for (IMeltVeto veto : MELT_VETOES) {
            try {
                Optional<Component> reason = veto.vetoMelting(stack);
                if (reason.isPresent()) return reason;
            } catch (RuntimeException e) {
                ImmersiveSmithing.LOGGER.error("Melt veto {} failed for {}", veto.getClass().getName(), stack, e);
            }
        }
        return Optional.empty();
    }

    public static boolean isExcluded(ItemStack stack) {
        for (Predicate<ItemStack> p : EXCLUSIONS) {
            if (p.test(stack)) return true;
        }
        return false;
    }

    /** Drains IMC messages. Called from the mod-bus {@link InterModProcessEvent}. */
    @SuppressWarnings("unchecked")
    public static void processIMC(InterModProcessEvent event) {
        InterModComms.getMessages(ImmersiveSmithing.MOD_ID).forEach(msg -> {
            try {
                Object payload = ((Supplier<Object>) msg.messageSupplier()).get();
                switch (msg.method()) {
                    case IMC_MATERIAL_PROVIDER -> registerMaterialProvider((ISmithingMaterialProvider) payload);
                    case IMC_RECIPE_PROVIDER -> registerRecipeProvider((ISmithingRecipeProvider) payload);
                    case IMC_EFFICACY_HANDLER -> registerEfficacyHandler((ISmithingEfficacyHandler) payload);
                    case IMC_SHIELD_HANDLER -> registerShieldHandler((IShieldSmithingHandler) payload);
                    case IMC_CLASSIFIER -> registerClassifier((IEquipmentClassifier) payload);
                    case IMC_RECYCLING_PROVIDER -> registerRecyclingProvider((IRecyclingValueProvider) payload);
                    case IMC_EXCLUSION -> registerExclusion((Predicate<ItemStack>) payload);
                    case IMC_MELT_VETO -> registerMeltVeto((IMeltVeto) payload);
                    case IMC_RECIPE_GATE -> registerRecipeGate((ISmithingRecipeGate) payload);
                    case IMC_COST_MODIFIER -> registerCostModifier((ISmithingCostModifier) payload);
                    case IMC_ASSIST_SOURCE -> registerAssistSource((ISmithingAssistSource) payload);
                    default -> ImmersiveSmithing.LOGGER.warn("Unknown IMC method '{}' from {}", msg.method(), msg.senderModId());
                }
            } catch (ClassCastException e) {
                ImmersiveSmithing.LOGGER.error("IMC message '{}' from {} has the wrong payload type", msg.method(), msg.senderModId());
            }
        });
    }

    private ImmersiveSmithingAPI() {}
}
