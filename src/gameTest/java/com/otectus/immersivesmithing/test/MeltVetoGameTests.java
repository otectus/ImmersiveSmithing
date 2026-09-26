package com.otectus.immersivesmithing.test;

import com.otectus.immersivesmithing.ImmersiveSmithing;
import com.otectus.immersivesmithing.api.ImmersiveSmithingAPI;
import com.otectus.immersivesmithing.blockentity.SmithsForgeBlockEntity;
import com.otectus.immersivesmithing.config.ServerConfig;
import com.otectus.immersivesmithing.registry.ModBlocks;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;

import java.util.Optional;

import static com.otectus.immersivesmithing.test.TestSupport.STATION;

/** Another mod's melt veto keeps an item out of the forge, by hand and by automation alike. */
@GameTestHolder(ImmersiveSmithing.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MeltVetoGameTests {
    private static final String NO_MELT = "is_test_no_melt";
    private static boolean registered;

    /** Vetoes only stacks carrying the test tag, so other tests are unaffected. */
    private static ItemStack vetoed(ItemStack stack) {
        if (!registered) {
            registered = true;
            ImmersiveSmithingAPI.registerMeltVeto(s -> s.getTag() != null && s.getTag().getBoolean(NO_MELT)
                    ? Optional.of(Component.literal("test veto")) : Optional.empty());
        }
        stack.getOrCreateTag().putBoolean(NO_MELT, true);
        return stack;
    }

    @GameTest(template = "empty")
    public static void vetoedItemsStayOutOfTheForge(GameTestHelper h) {
        ItemStack sword = vetoed(new ItemStack(Items.IRON_SWORD));
        h.setBlock(STATION, ModBlocks.SMITHS_FORGE.get().defaultBlockState());
        SmithsForgeBlockEntity forge = (SmithsForgeBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(STATION));
        SmithsForgeBlockEntity.Result[] err = new SmithsForgeBlockEntity.Result[1];

        h.assertTrue(ImmersiveSmithingAPI.meltVeto(sword).map(Component::getString).orElse("").equals("test veto"), "the veto gives its reason");
        h.assertTrue(forge.insertMetal(sword, 1, false, err) == 0 && err[0] == SmithsForgeBlockEntity.Result.VETOED,
                "a vetoed sword is refused by hand, got " + err[0]);
        h.assertTrue(forge.deposits().isEmpty(), "nothing was deposited");

        if (ServerConfig.get(ServerConfig.ENABLE_AUTOMATION)) {
            for (Direction side : Direction.values()) {
                IItemHandler input = forge.getCapability(ForgeCapabilities.ITEM_HANDLER, side).orElse(null);
                if (input == null) continue;
                h.assertTrue(!input.isItemValid(0, sword), "automation on " + side + " does not accept a vetoed sword");
                h.assertTrue(input.insertItem(0, sword, false).getCount() == 1, "and cannot insert it from " + side);
            }
            h.assertTrue(forge.deposits().isEmpty(), "automation deposited nothing");
        }

        ItemStack plain = new ItemStack(Items.IRON_SWORD);
        h.assertTrue(forge.insertMetal(plain, 1, false, err) == 1 && forge.depositUnits() == 18, "an ordinary sword still melts");
        h.succeed();
    }
}
