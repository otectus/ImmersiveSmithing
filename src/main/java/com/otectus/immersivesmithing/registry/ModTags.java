package com.otectus.immersivesmithing.registry;

import com.otectus.immersivesmithing.ImmersiveSmithing;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;

public final class ModTags {
    public static final TagKey<Item> SMITHABLE_EQUIPMENT = tag("smithable_equipment");
    public static final TagKey<Item> NON_SMITHABLE_EQUIPMENT = tag("non_smithable_equipment");
    public static final TagKey<Item> SMITHABLE_SHIELDS = tag("smithable_shields");
    public static final TagKey<Item> NON_SMITHABLE_SHIELDS = tag("non_smithable_shields");
    public static final TagKey<Item> NON_RECYCLABLE = tag("non_recyclable");
    public static final TagKey<Item> FORGE_FUELS = tag("forge_fuels");
    public static final TagKey<Item> FORGE_IGNITERS = tag("forge_igniters");
    public static final TagKey<Item> NETHERITE_FUELS = tag("netherite_fuels");
    public static final TagKey<Item> HAMMERS = tag("hammers");
    public static final TagKey<Item> TONGS = tag("tongs");
    /** Equipment that may carry smithing quality without a forge recipe: loot weapons, runic and unique pieces. */
    public static final TagKey<Item> QUALITY_ELIGIBLE = tag("quality_eligible");
    /** Projectiles whose damage is read from the thrown weapon's (already quality-adjusted) attributes at launch. */
    public static final TagKey<EntityType<?>> WEAPON_SCALED_PROJECTILES =
            TagKey.create(Registries.ENTITY_TYPE, ImmersiveSmithing.id("weapon_scaled_projectiles"));

    private static TagKey<Item> tag(String name) {
        return ItemTags.create(ImmersiveSmithing.id(name));
    }

    private ModTags() {}
}
