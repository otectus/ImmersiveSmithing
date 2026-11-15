package com.otectus.immersivesmithing.work;

import com.otectus.immersivesmithing.ImmersiveSmithing;
import com.otectus.immersivesmithing.recipe.AuxiliaryIngredient;
import com.otectus.immersivesmithing.recipe.SmithingData;
import com.otectus.immersivesmithing.recipe.SmithingRecipe;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The recipe-to-process coverage of the running server: for every active smithing recipe, its output, metal and cost, the
 * process profile it resolves to and how (explicit, pattern, classifier, fallback), the visual profile and whether this jar
 * ships it, and what each auxiliary becomes (a fitting socket, or an assimilated base). Rejected profile files are listed
 * first. Recipes the forge does not make (kept native, suppressed) are in the compatibility report, not here.
 */
public final class ProcessReport {

    public record Row(ResourceLocation recipe, ResourceLocation output, ResourceLocation family, int units, ResourceLocation profile,
                      ProcessResolver.Source source, ResourceLocation visual, boolean visualShipped, List<String> auxiliary) {}

    public static List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        for (SmithingRecipe recipe : SmithingData.current().recipes()) {
            ProcessResolver.Resolution resolution = ProcessResolver.explain(recipe);
            ProcessProfile profile = resolution.profile();
            List<String> aux = new ArrayList<>();
            for (int i = 0; i < recipe.auxiliary().size(); i++) {
                AuxiliaryIngredient a = recipe.auxiliary().get(i);
                ItemStack shown = a.displayStack();
                String role = a.consumesEquipment() ? "assimilated base"
                        : "socket " + (i < profile.sockets().size() ? profile.sockets().get(i) : ProcessResolver.socketFor(shown));
                aux.add(a.count() + "x " + (shown.isEmpty() ? "?" : BuiltInRegistries.ITEM.getKey(shown.getItem())) + " -> " + role);
            }
            rows.add(new Row(recipe.getId(), BuiltInRegistries.ITEM.getKey(recipe.resultItem()), recipe.family(), recipe.metalUnits(),
                    profile.id(), resolution.source(), profile.visual(), shipped(profile.visual()), List.copyOf(aux)));
        }
        rows.sort(Comparator.comparing(r -> r.recipe().toString()));
        return rows;
    }

    /** Whether this jar contains the visual profile (resource packs may still add or replace it on clients). */
    static boolean shipped(ResourceLocation visual) {
        String path = "/assets/" + visual.getNamespace() + "/immersive_smithing/workpiece_profiles/" + visual.getPath() + ".json";
        return ImmersiveSmithing.class.getResource(path) != null;
    }

    public static Path write(Path dir) throws IOException {
        List<Row> rows = rows();
        List<String> lines = new ArrayList<>();
        lines.add("# Immersive Smithing process coverage");
        lines.add("recipes: " + rows.size());
        Map<String, Integer> bySource = new TreeMap<>();
        Map<String, Integer> byProfile = new TreeMap<>();
        for (Row r : rows) {
            bySource.merge(r.source().name(), 1, Integer::sum);
            byProfile.merge(r.profile().toString(), 1, Integer::sum);
        }
        lines.add("by resolution: " + bySource);
        lines.add("by profile: " + byProfile);
        lines.add("rejected profile files: " + (ProcessProfiles.problems().isEmpty() ? "none" : ""));
        ProcessProfiles.problems().forEach(p -> lines.add("  " + p));
        lines.add("");
        lines.add("recipe\toutput\tmaterial\tunits\tprocess\tresolved by\tvisual\tvisual shipped\tauxiliary");
        for (Row r : rows) {
            lines.add(r.recipe() + "\t" + r.output() + "\t" + r.family() + "\t" + r.units() + "\t" + r.profile() + "\t"
                    + r.source().name().toLowerCase(java.util.Locale.ROOT) + "\t" + r.visual() + "\t" + (r.visualShipped() ? "yes" : "fallback")
                    + "\t" + String.join("; ", r.auxiliary()));
        }
        Files.createDirectories(dir);
        Path file = dir.resolve("immersive_smithing_process_report.txt");
        Files.write(file, lines, StandardCharsets.UTF_8);
        return file;
    }

    private ProcessReport() {}
}
