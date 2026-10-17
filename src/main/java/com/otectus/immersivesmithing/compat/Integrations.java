package com.otectus.immersivesmithing.compat;

import com.otectus.immersivesmithing.ImmersiveSmithing;
import com.otectus.immersivesmithing.config.ServerConfig;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The built-in integrations and their server-config switches.
 *
 * <ul>
 *   <li>Spartan Weaponry's switch only turns its generated recipes off; automatic detection then takes over.</li>
 *   <li>Every other switch turns the whole integration off: its built-in recipes, automatic detection and suppression of
 *   the mod's recipes, and quality on its loot pieces, so the mod is left exactly as it ships. Already graded stacks
 *   keep their quality either way.</li>
 *   <li>A <em>curated</em> integration's equipment comes only from Immersive Smithing's bundled recipes: automatic
 *   detection never guesses for its namespaces (a gilding upgrade would otherwise become a cheap gold forging), and an
 *   item the bundled data does not cover keeps its native recipe and is reported by the coverage catalog.</li>
 *   <li>TerraFirmaCraft and Epic Knights of TerraFirma keep their own metalworking: they are left alone unless the
 *   server opts in.</li>
 * </ul>
 */
public final class Integrations {
    public static final String SPARTAN_WEAPONRY = "spartanweaponry";
    public static final String SPARTAN_FIRE = "spartanfire";
    public static final String EPIC_FIGHT = "epicfight";
    public static final String SIMPLY_SWORDS = "simplyswords";
    public static final String EPIC_KNIGHTS = "epicknights";
    public static final String TERRAFIRMACRAFT = "terrafirmacraft";

    /** A switchable integration: the item namespaces it covers and whether only its bundled recipes forge them. */
    public record Integration(String id, String displayName, List<String> namespaces, boolean curated) {}

    /** The Epic Knights ecosystem: the base mod and the content add-ons whose equipment the bundled data covers. */
    public static final List<String> EPIC_KNIGHTS_NAMESPACES = List.of("magistuarmory", "magistuarmoryaddon", "slavicarmory",
            "antiquelegacy", "epic_knights_ice_and_fire", "epic_knights_ores_and_alloys", "darkagesarmory", "epicknights_open_helms",
            "artillery_addon", "medieval_boomsticks", "ekotsk");

    public static final List<Integration> ALL = List.of(
            new Integration(EPIC_FIGHT, "Epic Fight", List.of(EPIC_FIGHT), true),
            new Integration(SIMPLY_SWORDS, "Simply Swords", List.of(SIMPLY_SWORDS), false),
            new Integration(EPIC_KNIGHTS, "Epic Knights", EPIC_KNIGHTS_NAMESPACES, true),
            // Not curated: Immersive Smithing ships no TerraFirmaCraft data, so opting in means automatic detection.
            new Integration(TERRAFIRMACRAFT, "TerraFirmaCraft", List.of("tfc", "knightsofterrafirma"), false));

    /** The integration covering an item namespace, if any. */
    public static Optional<Integration> forNamespace(String namespace) {
        for (Integration integration : ALL) {
            if (integration.namespaces().contains(namespace)) return Optional.of(integration);
        }
        return Optional.empty();
    }

    /** Whether Immersive Smithing may handle the mod with this namespace. True for every namespace without a switch. */
    public static boolean enabled(String namespace) {
        return switch (forNamespace(namespace).map(Integration::id).orElse("")) {
            case EPIC_FIGHT -> ServerConfig.get(ServerConfig.EPIC_FIGHT_INTEGRATION);
            case SIMPLY_SWORDS -> ServerConfig.get(ServerConfig.SIMPLY_SWORDS_INTEGRATION);
            case EPIC_KNIGHTS -> ServerConfig.get(ServerConfig.EPIC_KNIGHTS_INTEGRATION)
                    && !ServerConfig.list(ServerConfig.EPIC_KNIGHTS_DISABLED_ADDONS).contains(namespace);
            case TERRAFIRMACRAFT -> ServerConfig.get(ServerConfig.TERRAFIRMACRAFT_INTEGRATION);
            default -> true;
        };
    }

    /** Item namespaces that automatic detection, suppression and loot quality skip because their integration is off. */
    public static Set<String> disabledNamespaces() {
        Set<String> out = new LinkedHashSet<>();
        for (Integration integration : ALL) {
            for (String namespace : integration.namespaces()) {
                if (!enabled(namespace)) out.add(namespace);
            }
        }
        return out;
    }

    /** Namespaces of switched-on curated integrations: forged only from bundled recipes, never auto-detected. */
    public static Set<String> curatedNamespaces() {
        Set<String> out = new LinkedHashSet<>();
        for (Integration integration : ALL) {
            if (!integration.curated()) continue;
            for (String namespace : integration.namespaces()) {
                if (enabled(namespace)) out.add(namespace);
            }
        }
        return out;
    }

    /** The server-config option that switches a namespace's integration off, as the report names selectors. */
    public static String configKey(String namespace) {
        if (namespace.equals(SPARTAN_WEAPONRY) || namespace.equals(SPARTAN_FIRE)) return "integrations.spartanWeaponry = false";
        return switch (forNamespace(namespace).map(Integration::id).orElse("")) {
            case EPIC_FIGHT -> "integrations.epicFight = false";
            case SIMPLY_SWORDS -> "integrations.simplySwords = false";
            case EPIC_KNIGHTS -> ServerConfig.get(ServerConfig.EPIC_KNIGHTS_INTEGRATION)
                    ? "integrations.epicKnightsDisabledAddons = " + namespace : "integrations.epicKnights = false";
            case TERRAFIRMACRAFT -> "integrations.terraFirmaCraft = false";
            default -> "integrations (" + namespace + ")";
        };
    }

    /** The mod a built-in recipe under {@code immersive_smithing:compat/<modid>/} belongs to, or null. */
    @Nullable
    public static String compatModOf(ResourceLocation recipeId) {
        if (!recipeId.getNamespace().equals(ImmersiveSmithing.MOD_ID) || !recipeId.getPath().startsWith("compat/")) return null;
        String rest = recipeId.getPath().substring("compat/".length());
        int slash = rest.indexOf('/');
        return slash > 0 ? rest.substring(0, slash) : null;
    }

    /** Null when a built-in compat recipe (smithing or crafting) may be active, otherwise why it is switched off. */
    @Nullable
    public static String compatRecipeDisabled(ResourceLocation recipeId) {
        String modId = compatModOf(recipeId);
        if (modId == null) return null;
        if ((modId.equals(SPARTAN_WEAPONRY) || modId.equals(SPARTAN_FIRE)) && !ServerConfig.get(ServerConfig.SPARTAN_WEAPONRY_INTEGRATION)) {
            return "Spartan Weaponry integration is disabled in the config";
        }
        if (enabled(modId)) return null;
        return forNamespace(modId).map(i -> i.displayName() + " integration is disabled in the config (" + modId + ")")
                .orElse(modId + " integration is disabled in the config");
    }

    private Integrations() {}
}
