package dev.prismlights.client.mixin;

import dev.prismlights.client.PrismClient;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Rebuild colored irradiance after the client accepts a prism or nearby
 * occluder update. The rebuild itself runs once at the end of the same tick. */
@Mixin(ClientWorld.class)
public abstract class ClientWorldMixin {
    @Inject(method="updateListeners",at=@At("TAIL"))
    private void prism$blockChanged(BlockPos pos,BlockState before,BlockState after,int flags,CallbackInfo ci) {
        PrismClient.blockChanged(pos,before,after);
    }
}
