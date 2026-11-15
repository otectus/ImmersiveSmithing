package com.otectus.immersivesmithing.work;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The gameplay side of how one kind of piece is made: its shaping operations (each on an area of the workpiece, in
 * canonical coordinates {@code u} along the length from the tang/socket end 0 to the tip 1, and {@code v} across from -0.5
 * to 0.5), optional heat bands, the quench and the fitting sockets. Loaded from
 * {@code data/<namespace>/immersive_smithing/process_profiles/<id>.json}; a snapshot travels with every workpiece, so a
 * piece in progress finishes by the rules it started with even if a datapack changes.
 *
 * <p>{@code work} is abstract simulation work: one ordinary blow at good heat does 1.00 before the piece's size scale.
 * Presentation (how the piece looks as it changes) is a separate client resource named by {@link #visual}.</p>
 */
public record ProcessProfile(ResourceLocation id, ResourceLocation visual, @Nullable ThermalProfile heat, List<Operation> operations,
                             int quenchTicks, int immersionMin, List<String> sockets, List<ResourceLocation> patterns) {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_OPERATIONS = 12;
    public static final int MAX_SOCKETS = 8;

    public enum Kind {
        DRAW, SPREAD, PUNCH, SHAPE, CURVE, DISH, FINISH;

        /** Rough work that a heavy blow speeds up without spoiling. */
        public boolean rough() {
            return this == DRAW || this == SPREAD || this == PUNCH;
        }

        public static Kind byName(String name) {
            for (Kind k : values()) if (k.name().equalsIgnoreCase(name)) return k;
            throw new JsonParseException("unknown operation kind '" + name + "' (draw, spread, punch, shape, curve, dish, finish)");
        }
    }

    /** Which face of the piece an operation is struck on; {@code BACK} needs the piece turned over first. */
    public enum Face {
        FRONT, BACK, ANY;

        public boolean matches(boolean flipped) {
            return this == ANY || (this == BACK) == flipped;
        }

        public static Face byName(String name) {
            return switch (name.toLowerCase(Locale.ROOT)) {
                case "front", "top" -> FRONT;
                case "back", "bottom" -> BACK;
                case "any" -> ANY;
                default -> throw new JsonParseException("unknown face '" + name + "' (front, back, any)");
            };
        }
    }

    /**
     * One shaping operation. {@code work} is in hundredths (an ordinary blow does 100 before scaling), {@code weight} its
     * share of the Anvil score, {@code after} the operations that must be complete before it is accepted.
     */
    public record Operation(String id, String region, Kind kind, int work, int weight, Face face,
                            float u0, float u1, float v0, float v1, List<String> after) {
        public boolean contains(float u, float v) {
            return u >= u0 && u <= u1 && v >= v0 && v <= v1;
        }

        public float centerU() {
            return (u0 + u1) / 2F;
        }

        public float centerV() {
            return (v0 + v1) / 2F;
        }

        public int zone() {
            return ThermalRules.zoneAt(centerU());
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Id", id);
            tag.putString("Region", region);
            tag.putString("Kind", kind.name());
            tag.putInt("Work", work);
            tag.putInt("Weight", weight);
            tag.putString("Face", face.name());
            tag.putFloat("U0", u0);
            tag.putFloat("U1", u1);
            tag.putFloat("V0", v0);
            tag.putFloat("V1", v1);
            ListTag after = new ListTag();
            for (String a : this.after) after.add(StringTag.valueOf(a));
            tag.put("After", after);
            return tag;
        }

        static Operation load(CompoundTag tag) {
            List<String> after = new ArrayList<>();
            ListTag list = tag.getList("After", Tag.TAG_STRING);
            for (int i = 0; i < Math.min(list.size(), MAX_OPERATIONS); i++) after.add(list.getString(i));
            Kind kind;
            Face face;
            try {
                kind = Kind.valueOf(tag.getString("Kind"));
                face = Face.valueOf(tag.getString("Face"));
            } catch (IllegalArgumentException e) {
                kind = Kind.SHAPE;
                face = Face.ANY;
            }
            return new Operation(tag.getString("Id"), tag.getString("Region"), kind, clampWork(tag.getInt("Work")),
                    Math.max(1, Math.min(100, tag.getInt("Weight"))), face,
                    clampU(tag.getFloat("U0")), clampU(tag.getFloat("U1")), clampV(tag.getFloat("V0")), clampV(tag.getFloat("V1")),
                    List.copyOf(after));
        }
    }

    public int operationIndex(String id) {
        for (int i = 0; i < operations.size(); i++) if (operations.get(i).id().equals(id)) return i;
        return -1;
    }

    /** Duration of a correct quench in ticks and the immersion depth (per mille) the trough must reach. */
    public static ProcessProfile fromJson(ResourceLocation id, JsonObject json) {
        int schema = GsonHelper.getAsInt(json, "schema_version", SCHEMA_VERSION);
        if (schema != SCHEMA_VERSION) throw new JsonParseException("schema_version " + schema + " is not supported (expected " + SCHEMA_VERSION + ")");
        ResourceLocation visual = json.has("workpiece_profile") ? new ResourceLocation(GsonHelper.getAsString(json, "workpiece_profile")) : id;
        ThermalProfile heat = json.has("heat") ? ThermalProfile.fromJson(GsonHelper.getAsJsonObject(json, "heat"), ThermalProfile.DEFAULT) : null;

        JsonArray ops = GsonHelper.getAsJsonArray(json, "operations");
        if (ops.isEmpty()) throw new JsonParseException("operations: at least one operation is required");
        if (ops.size() > MAX_OPERATIONS) throw new JsonParseException("operations: at most " + MAX_OPERATIONS + " are allowed");
        List<Operation> operations = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < ops.size(); i++) {
            String where = "operations[" + i + "]";
            JsonObject o = GsonHelper.convertToJsonObject(ops.get(i), where);
            String opId = GsonHelper.getAsString(o, "id");
            if (!opId.matches("[a-z0-9_]{1,32}")) throw new JsonParseException(where + ".id must be 1-32 characters of a-z, 0-9 or _");
            if (!ids.add(opId)) throw new JsonParseException(where + ".id '" + opId + "' is used twice");
            float work = GsonHelper.getAsFloat(o, "work");
            if (!Float.isFinite(work) || work <= 0F || work > 40F) throw new JsonParseException(where + ".work must be greater than 0 and at most 40");
            int weight = GsonHelper.getAsInt(o, "weight", 1);
            if (weight < 1 || weight > 100) throw new JsonParseException(where + ".weight must be from 1 to 100");
            JsonArray area = GsonHelper.getAsJsonArray(o, "area");
            if (area.size() != 4) throw new JsonParseException(where + ".area must be [u0, u1, v0, v1]");
            float u0 = area.get(0).getAsFloat(), u1 = area.get(1).getAsFloat(), v0 = area.get(2).getAsFloat(), v1 = area.get(3).getAsFloat();
            if (!(Float.isFinite(u0) && Float.isFinite(u1) && Float.isFinite(v0) && Float.isFinite(v1))
                    || u0 < 0F || u1 > 1F || u0 >= u1 || v0 < -0.5F || v1 > 0.5F || v0 >= v1) {
                throw new JsonParseException(where + ".area must satisfy 0 <= u0 < u1 <= 1 and -0.5 <= v0 < v1 <= 0.5");
            }
            List<String> after = new ArrayList<>();
            if (o.has("after")) for (JsonElement e : GsonHelper.getAsJsonArray(o, "after")) after.add(e.getAsString());
            operations.add(new Operation(opId, GsonHelper.getAsString(o, "region", opId), Kind.byName(GsonHelper.getAsString(o, "kind")),
                    Math.round(work * 100F), weight, Face.byName(GsonHelper.getAsString(o, "face", "any")), u0, u1, v0, v1, List.copyOf(after)));
        }
        for (int i = 0; i < operations.size(); i++) {
            for (String dep : operations.get(i).after()) {
                int index = -1;
                for (int j = 0; j < operations.size(); j++) if (operations.get(j).id().equals(dep)) index = j;
                if (index < 0) throw new JsonParseException("operations[" + i + "].after names unknown operation '" + dep + "'");
                if (index >= i) throw new JsonParseException("operations[" + i + "].after must name an earlier operation ('" + dep + "')");
            }
        }

        int quenchTicks = 60;
        int immersion = 650;
        if (json.has("quench")) {
            JsonObject q = GsonHelper.getAsJsonObject(json, "quench");
            quenchTicks = GsonHelper.getAsInt(q, "duration_ticks", quenchTicks);
            if (quenchTicks < 20 || quenchTicks > 200) throw new JsonParseException("quench.duration_ticks must be from 20 to 200");
            float min = GsonHelper.getAsFloat(q, "immersion_min", 0.65F);
            if (!Float.isFinite(min) || min < 0.1F || min > 1F) throw new JsonParseException("quench.immersion_min must be from 0.1 to 1");
            immersion = Math.round(min * 1000F);
        }

        List<String> sockets = new ArrayList<>();
        if (json.has("assembly")) {
            JsonArray assembly = GsonHelper.getAsJsonArray(json, "assembly");
            if (assembly.size() > MAX_SOCKETS) throw new JsonParseException("assembly: at most " + MAX_SOCKETS + " sockets are allowed");
            for (int i = 0; i < assembly.size(); i++) {
                JsonObject s = GsonHelper.convertToJsonObject(assembly.get(i), "assembly[" + i + "]");
                int index = GsonHelper.getAsInt(s, "auxiliary_index", i);
                if (index != i) throw new JsonParseException("assembly[" + i + "].auxiliary_index must be " + i + " (sockets follow the recipe's auxiliary order)");
                String socket = GsonHelper.getAsString(s, "socket");
                if (!socket.matches("[a-z0-9_]{1,32}")) throw new JsonParseException("assembly[" + i + "].socket must be 1-32 characters of a-z, 0-9 or _");
                sockets.add(socket);
            }
        }

        List<ResourceLocation> patterns = new ArrayList<>();
        if (json.has("patterns")) for (JsonElement e : GsonHelper.getAsJsonArray(json, "patterns")) patterns.add(new ResourceLocation(e.getAsString()));
        return new ProcessProfile(id, visual, heat, List.copyOf(operations), quenchTicks, immersion, List.copyOf(sockets), List.copyOf(patterns));
    }

    /** The frozen snapshot carried by a workpiece. Patterns are not needed after resolution and are not saved. */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", id.toString());
        tag.putString("Visual", visual.toString());
        if (heat != null) tag.put("Heat", heat.save());
        ListTag ops = new ListTag();
        for (Operation o : operations) ops.add(o.save());
        tag.put("Operations", ops);
        tag.putInt("QuenchTicks", quenchTicks);
        tag.putInt("Immersion", immersionMin);
        ListTag sockets = new ListTag();
        for (String s : this.sockets) sockets.add(StringTag.valueOf(s));
        tag.put("Sockets", sockets);
        return tag;
    }

    /** Reads a snapshot, bounding every list; null when it is unusable (no operations). */
    @Nullable
    public static ProcessProfile load(CompoundTag tag) {
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("Id"));
        ResourceLocation visual = ResourceLocation.tryParse(tag.getString("Visual"));
        if (id == null) return null;
        ListTag list = tag.getList("Operations", Tag.TAG_COMPOUND);
        if (list.isEmpty()) return null;
        List<Operation> ops = new ArrayList<>();
        for (int i = 0; i < Math.min(list.size(), MAX_OPERATIONS); i++) ops.add(Operation.load(list.getCompound(i)));
        List<String> sockets = new ArrayList<>();
        ListTag socketList = tag.getList("Sockets", Tag.TAG_STRING);
        for (int i = 0; i < Math.min(socketList.size(), MAX_SOCKETS); i++) sockets.add(socketList.getString(i));
        ThermalProfile heat = tag.contains("Heat", Tag.TAG_COMPOUND) ? ThermalProfile.load(tag.getCompound("Heat")) : null;
        int quench = Math.max(20, Math.min(200, tag.getInt("QuenchTicks")));
        int immersion = Math.max(100, Math.min(1000, tag.getInt("Immersion")));
        return new ProcessProfile(id, visual != null ? visual : id, heat, List.copyOf(ops), quench, immersion, List.copyOf(sockets), List.of());
    }

    private static int clampWork(int work) {
        return Math.max(1, Math.min(4000, work));
    }

    private static float clampU(float u) {
        return Float.isFinite(u) ? Math.max(0F, Math.min(1F, u)) : 0F;
    }

    private static float clampV(float v) {
        return Float.isFinite(v) ? Math.max(-0.5F, Math.min(0.5F, v)) : 0F;
    }
}
