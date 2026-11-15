package com.otectus.immersivesmithing.mixin;

import net.minecraftforge.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Optional bytecode adapters are only enabled for the inspected release ABI. */
public final class EpicMixinPlugin implements IMixinConfigPlugin {
    private static final Map<String, String[]> TARGETS = Map.of(
            "EpicHeraldryNameMixin", new String[]{"magistuarmory", "10.15"},
            "EpicDecorationNameMixin", new String[]{"magistuarmory", "10.15"},
            "QuickSwapPacketMixin", new String[]{"quick_swap", "0.1.0"},
            "ThirtyYearsWarGunMixin", new String[]{"thirtyyearswar", "1.3"},
            "FletcheryShotMixin", new String[]{"fletchery", "0.7"},
            "ArtilleryShotMixin", new String[]{"artillery_addon", "1.16.2"},
            "BoomsticksShotMixin", new String[]{"medieval_boomsticks", "1.01", "1.2"});

    @Override public boolean shouldApplyMixin(String target, String mixin) {
        String name = mixin.substring(mixin.lastIndexOf('.') + 1);
        String[] version = name.startsWith("Boomsticks") ? new String[]{"medieval_boomsticks", "1.01", "1.2"}
                : name.startsWith("Artillery") ? new String[]{"artillery_addon", "1.16.2"} : TARGETS.get(name);
        if (version == null) return true;
        return LoadingModList.get().getMods().stream().anyMatch(mod -> mod.getModId().equals(version[0])
                && java.util.Arrays.asList(version).subList(1, version.length).contains(mod.getVersion().toString()));
    }
    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
