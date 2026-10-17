package com.otectus.immersivesmithing.compat;

import java.util.Locale;

/**
 * How a supported mod's item is obtained with Immersive Smithing installed, for the coverage catalog
 * ({@code /immersivesmithing catalog}) and the checked-in inventories under {@code docs/compatibility/inventory}.
 * Everything except {@link #MISSING_SUPPORT} is a deliberate, explained route.
 */
public enum CoverageDisposition {
    /** Forged from a built-in or datapack smithing recipe. */
    FORGE_DEFAULT,
    /** Forged from a recipe automatic detection derived from the mod's own crafting recipe. */
    FORGE_AUTO,
    /** Not metal work (wood, stone, diamond, bone, cloth): its own crafting recipe is kept as the mod ships it. */
    NATIVE_NONMETAL_RECIPE,
    /** Made from another piece by the mod's own transformation (gilding, darkening, a visor state, a decoration). */
    NATIVE_TRANSFORMATION,
    /** Assembled from mechanisms the forge does not make (firearms with locks and triggers); kept as the mod ships it. */
    NATIVE_ASSEMBLY,
    /** Unique, world-limited or loot-only equipment: never manufactured. */
    PROTECTED_LOOT,
    /** Not equipment the forge works (cosmetic decorations, horse armour, ammunition, tools of another trade). */
    NON_EQUIPMENT,
    /** The mod itself ships no working route for it (a disabled or broken native recipe). */
    DISABLED_BY_UPSTREAM,
    /** Its built-in forge recipe needs a metal no installed mod provides (tin, silver or bronze ingots). */
    MATERIAL_UNAVAILABLE,
    /** Its integration is switched off in the server config; the mod is left exactly as it ships. */
    INTEGRATION_DISABLED,
    /** Eligible metal equipment without any route: a gap in Immersive Smithing's support. */
    MISSING_SUPPORT;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static CoverageDisposition byId(String id) {
        return valueOf(id.trim().toUpperCase(Locale.ROOT));
    }
}
