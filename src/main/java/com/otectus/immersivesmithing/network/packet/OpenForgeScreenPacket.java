package com.otectus.immersivesmithing.network.packet;

import com.otectus.immersivesmithing.client.ClientPacketHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

/**
 * Opens the forge's pattern book: every recipe of the forge's metal, each flagged with what keeps it from being made now
 * (shown dimmed with that reason), complete ingredient alternatives, the plan already pinned, and cast outputs. Molten units
 * are the free ones (a committed job's reservation excluded). Protocol 6.
 */
public record OpenForgeScreenPacket(int sessionId, BlockPos pos, Component familyName, int moltenUnits,
                                    @org.jetbrains.annotations.Nullable ResourceLocation plan, List<Entry> entries, List<CastOption> castOptions) {

    public static final int AFFORDABLE = 1;
    public static final int HAS_COMPONENTS = 2;
    public static final int ALLOWED = 4;

    /** One offered recipe. Choosable when every flag is set; otherwise {@code reason} says why not. */
    public record Entry(ResourceLocation recipeId, ItemStack result, int metalUnits, List<ItemStack> auxiliary, int flags,
                        int shortfall, Component reason, List<List<ItemStack>> alternatives, boolean upgrade) {
        public Entry(ResourceLocation recipeId, ItemStack result, int metalUnits, List<ItemStack> auxiliary, int flags,
                     int shortfall, Component reason) {
            this(recipeId, result, metalUnits, auxiliary, flags, shortfall, reason, auxiliary.stream().map(List::of).toList(), false);
        }

        public ItemStack displayAuxiliary(int index, long second) {
            List<ItemStack> choices = index < alternatives.size() ? alternatives.get(index) : List.of();
            return choices.isEmpty() ? auxiliary.get(index) : choices.get(Math.floorMod(second, choices.size()));
        }

        public Component componentNames(int index) {
            var name = Component.empty();
            List<ItemStack> choices = index < alternatives.size() ? alternatives.get(index) : List.of(auxiliary.get(index));
            for (ItemStack choice : choices) {
                if (!name.getString().isEmpty()) name.append(" / ");
                name.append(choice.getHoverName());
            }
            return name;
        }
        public boolean selectable() {
            return flags == (AFFORDABLE | HAS_COMPONENTS | ALLOWED);
        }

        public boolean affordable() {
            return (flags & AFFORDABLE) != 0;
        }
    }

    /** A cast output: the item, the units one costs, and how many the smith can cast right now (metal and inventory room). */
    public record CastOption(ItemStack item, int unitsEach, int max) {}

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(sessionId);
        buf.writeBlockPos(pos);
        buf.writeComponent(familyName);
        buf.writeVarInt(moltenUnits);
        buf.writeBoolean(plan != null);
        if (plan != null) buf.writeResourceLocation(plan);
        writeEntries(buf, entries);
        writeCastOptions(buf, castOptions);
    }

    public static OpenForgeScreenPacket decode(FriendlyByteBuf buf) {
        int id = buf.readVarInt();
        BlockPos pos = buf.readBlockPos();
        Component name = buf.readComponent();
        int molten = buf.readVarInt();
        ResourceLocation plan = buf.readBoolean() ? buf.readResourceLocation() : null;
        List<Entry> entries = readEntries(buf);
        List<CastOption> casts = readCastOptions(buf);
        return new OpenForgeScreenPacket(id, pos, name, molten, plan, entries, casts);
    }

    static void writeEntries(FriendlyByteBuf buf, List<Entry> entries) {
        buf.writeCollection(entries, (b, e) -> {
            b.writeResourceLocation(e.recipeId());
            b.writeItem(e.result());
            b.writeVarInt(e.metalUnits());
            b.writeCollection(e.auxiliary(), FriendlyByteBuf::writeItem);
            b.writeByte(e.flags());
            b.writeVarInt(e.shortfall());
            b.writeComponent(e.reason());
            b.writeCollection(e.alternatives(), (out, choices) -> out.writeCollection(choices, FriendlyByteBuf::writeItem));
            b.writeBoolean(e.upgrade());
        });
    }

    static List<Entry> readEntries(FriendlyByteBuf buf) {
        return buf.readList(b -> new Entry(b.readResourceLocation(), b.readItem(), b.readVarInt(), b.readList(FriendlyByteBuf::readItem),
                b.readByte(), b.readVarInt(), b.readComponent(), b.readList(in -> in.readList(FriendlyByteBuf::readItem)), b.readBoolean()));
    }

    static void writeCastOptions(FriendlyByteBuf buf, List<CastOption> options) {
        buf.writeCollection(options, (b, o) -> {
            b.writeItem(o.item());
            b.writeVarInt(o.unitsEach());
            b.writeVarInt(o.max());
        });
    }

    static List<CastOption> readCastOptions(FriendlyByteBuf buf) {
        return buf.readList(b -> new CastOption(b.readItem(), b.readVarInt(), b.readVarInt()));
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.openForge(this));
    }
}
