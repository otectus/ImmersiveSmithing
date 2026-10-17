package com.otectus.immersivesmithing.compat;

import com.google.gson.JsonObject;
import com.otectus.immersivesmithing.ImmersiveSmithing;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.common.crafting.conditions.IConditionSerializer;
import net.minecraftforge.fml.ModList;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;

/**
 * {@code {"type": "immersive_smithing:mod_version", "modid": "medieval_boomsticks", "versions": "[1.2,)"}}: true when the
 * mod is loaded in a version inside the Maven range. Built-in data that only matches some versions of a mod (the same
 * recipe id with different ingredients) uses it, so an untested version keeps its own recipes instead of getting ours.
 */
public record ModVersionCondition(String modid, String versions) implements ICondition {
    private static final ResourceLocation NAME = ImmersiveSmithing.id("mod_version");

    @Override
    public ResourceLocation getID() {
        return NAME;
    }

    @Override
    public boolean test(IContext context) {
        return ModList.get().getModContainerById(modid).map(c -> {
            try {
                return VersionRange.createFromVersionSpec(versions).containsVersion(c.getModInfo().getVersion());
            } catch (InvalidVersionSpecificationException e) {
                ImmersiveSmithing.LOGGER.error("Invalid version range {} for {}", versions, modid);
                return false;
            }
        }).orElse(false);
    }

    @Override
    public String toString() {
        return "mod_version(\"" + modid + "\" " + versions + ")";
    }

    public static final class Serializer implements IConditionSerializer<ModVersionCondition> {
        public static final Serializer INSTANCE = new Serializer();

        @Override
        public void write(JsonObject json, ModVersionCondition value) {
            json.addProperty("modid", value.modid());
            json.addProperty("versions", value.versions());
        }

        @Override
        public ModVersionCondition read(JsonObject json) {
            return new ModVersionCondition(GsonHelper.getAsString(json, "modid"), GsonHelper.getAsString(json, "versions"));
        }

        @Override
        public ResourceLocation getID() {
            return NAME;
        }
    }
}
