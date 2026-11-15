package com.otectus.immersivesmithing.quality;

import com.otectus.immersivesmithing.advancement.ModAdvancements;
import com.otectus.immersivesmithing.config.ServerConfig;
import com.otectus.immersivesmithing.network.ModNetwork;
import com.otectus.immersivesmithing.network.packet.OpenMakersMarkPacket;
import com.otectus.immersivesmithing.registry.ModSounds;
import com.otectus.immersivesmithing.util.Feedback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Server side of the Maker's Mark: which pieces may be signed, and the only path that writes a signature. */
public final class SigningService {

    public static boolean enabled() {
        return ServerConfig.get(ServerConfig.ENABLE_SIGNING);
    }

    /** Whether the player may open the signing screen for this piece right now. */
    public static boolean canSign(ItemStack stack, ServerPlayer player) {
        return enabled() && MakersMark.canSign(stack, player, ServerConfig.get(ServerConfig.ALLOW_RESIGNING));
    }

    /** Where each smith opened the engraving screen, for the stamp's effect. */
    private static final java.util.Map<java.util.UUID, net.minecraft.core.GlobalPos> STAMPS = new java.util.HashMap<>();

    /** Opens the screen for the piece in the given inventory slot; silently does nothing when it may not be signed. */
    public static void open(ServerPlayer player, int slot) {
        open(player, slot, null);
    }

    /** As {@link #open(ServerPlayer, int)}, engraving at the given anvil (the stamp is struck there). */
    public static void open(ServerPlayer player, int slot, @org.jetbrains.annotations.Nullable net.minecraft.core.BlockPos anvil) {
        ItemStack stack = slotStack(player.getInventory(), slot);
        if (!canSign(stack, player)) return;
        if (anvil != null) STAMPS.put(player.getUUID(), net.minecraft.core.GlobalPos.of(player.level().dimension(), anvil.immutable()));
        else STAMPS.remove(player.getUUID());
        if (player instanceof net.minecraftforge.common.util.FakePlayer || player.connection == null) return;
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new OpenMakersMarkPacket(slot, stack.copy()));
    }

    /** Signs exactly the piece the screen was opened for; anything else now in that slot is refused. */
    public static void sign(ServerPlayer player, int slot, ItemStack expected, String rawTitle, List<String> rawInscription) {
        ItemStack current = slotStack(player.getInventory(), slot);
        if (!ItemStack.matches(current, expected)) {
            Feedback.fail(player, "sign_piece_changed");
            return;
        }
        sign(player, slot, rawTitle, rawInscription);
    }

    public static void sign(ServerPlayer player, int slot, String rawTitle, List<String> rawInscription) {
        if (!enabled()) return;
        ItemStack stack = slotStack(player.getInventory(), slot);
        Optional<MakersMark> mark = MakersMark.get(stack);
        if (stack.isEmpty() || mark.isEmpty() || !mark.get().smithId().equals(player.getUUID())) {
            Feedback.fail(player, "sign_not_yours");
            return;
        }
        Optional<QualityData> quality = QualityData.get(stack);
        if (quality.isEmpty() || quality.get().faulty()) {
            Feedback.fail(player, "sign_faulty");
            return;
        }
        if (mark.get().signed() && !ServerConfig.get(ServerConfig.ALLOW_RESIGNING)) {
            Feedback.fail(player, "sign_already");
            return;
        }
        String title = MakersMark.sanitise(rawTitle, ServerConfig.get(ServerConfig.MAX_TITLE_LENGTH));
        List<String> inscription = new ArrayList<>();
        int maxLines = ServerConfig.get(ServerConfig.MAX_INSCRIPTION_LINES);
        int maxLength = ServerConfig.get(ServerConfig.MAX_INSCRIPTION_LINE_LENGTH);
        for (String line : rawInscription) {
            if (inscription.size() >= maxLines) break;
            String clean = MakersMark.sanitise(line, maxLength);
            if (!clean.isEmpty()) inscription.add(clean);
        }
        MakersMark.sign(stack, title, inscription);
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        net.minecraft.core.GlobalPos stamp = STAMPS.remove(player.getUUID());
        if (stamp != null && stamp.dimension().equals(player.level().dimension())
                && player.distanceToSqr(stamp.pos().getCenter()) < 64 && player.level() instanceof net.minecraft.server.level.ServerLevel level) {
            // A visible stamp at the anvil the piece was engraved on.
            com.otectus.immersivesmithing.work.WorkEvents.send(level, stamp.pos(), com.otectus.immersivesmithing.work.WorkEvents.STAMP, player, 0,
                    com.otectus.immersivesmithing.work.StationLayout.ANVIL_WORK_CENTER, 1F, 0);
        } else {
            player.level().playSound(null, player.blockPosition(), ModSounds.STAMP.get(), SoundSource.PLAYERS, 0.6F, 1.2F);
        }
        Feedback.info(player, "signed", stack.getHoverName());
        ModAdvancements.award(player, ModAdvancements.MAKERS_MARK);
    }

    private static ItemStack slotStack(Inventory inventory, int slot) {
        return slot >= 0 && slot < inventory.getContainerSize() ? inventory.getItem(slot) : ItemStack.EMPTY;
    }

    private SigningService() {}
}
