package edn.stratodonut.drivebywire.mixin.compat;

import com.simibubi.create.content.redstone.link.controller.LecternControllerBlockEntity;
import com.simibubi.create.content.redstone.link.controller.LinkedControllerInputPacket;
import edn.stratodonut.drivebywire.compat.LinkedControllerWireServerHandler;
import edn.stratodonut.drivebywire.util.HubItem;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;

@Mixin(LinkedControllerInputPacket.class)
public abstract class MixinLinkedControllerInputPacket {
    @Shadow private Collection<Integer> activatedButtons;

    @Shadow private boolean press;

    @Inject(
            method = "handleLectern",
            at = @At("RETURN"),
            remap = false
    )
    private void mixinHandleLectern(ServerPlayer player, LecternControllerBlockEntity lectern, CallbackInfo ci) {
        LinkedControllerWireServerHandler.receivePressed(player.level(), lectern.getBlockPos(), activatedButtons, press);
    }

    @Inject(
            method = "handleItem",
            at = @At("RETURN"),
            remap = false
    )
    private void mixinHandleItem(ServerPlayer player, ItemStack heldItem, CallbackInfo ci) {
        HubItem.ifHubPresent(heldItem, pos -> LinkedControllerWireServerHandler.receivePressed(player.level(), pos, activatedButtons, press));
    }
}
