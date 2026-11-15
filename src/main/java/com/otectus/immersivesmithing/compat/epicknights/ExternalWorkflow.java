package com.otectus.immersivesmithing.compat.epicknights;

import com.otectus.immersivesmithing.compat.Integrations;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.util.LinkedHashSet;
import java.util.Set;

/** Manufacturing bridges own these routes before config, providers and detection. No untested owner override. */
public final class ExternalWorkflow {
    private static volatile Set<String> owned;
    private static String owner = "";

    public static Set<String> namespaces() {
        Set<String> result = owned;
        if (result != null) return result;
        if (ModList.get() == null) return Set.of();
        Set<String> names = new LinkedHashSet<>();
        if (ModList.get().isLoaded("overgeared_epic_knights")) owner = "Overgeared x Epic Knights";
        if (ModList.get().isLoaded("knightsofterrafirma")) owner = ModList.get().isLoaded("gtceu")
                ? "Epic Knights of TerraFirmaGreg" : "Epic Knights of TerraFirma";
        if (!owner.isEmpty()) {
            names.addAll(Integrations.EPIC_KNIGHTS_NAMESPACES);
            names.addAll(Set.of("minecraft", "overgeared", "overgeared_epic_knights", "tfc", "knightsofterrafirma", "gtceu"));
        }
        result = Set.copyOf(names);
        owned = result;
        return result;
    }

    public static boolean owns(String namespace) { return namespaces().contains(namespace); }
    public static boolean owns(ItemStack stack) { return owns(BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace()); }
    public static String reason() { namespaces(); return "EXTERNAL_WORKFLOW: " + owner + " owns manufacturing and equipment quality"; }
    private ExternalWorkflow() {}
}
