package dev.prismlights.client.mixin;

import dev.prismlights.PrismLights;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Equipment is already synchronized by Minecraft, so remote players share this pose. */
@Mixin(BipedEntityModel.class)
public abstract class LauncherArmPoseMixin {
    @Inject(method="setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V",at=@At("TAIL"))
    private void prism$aiming(LivingEntity entity,float limbAngle,float limbDistance,float age,
                             float headYaw,float headPitch,CallbackInfo ci) {
        if(!(entity instanceof PlayerEntity player)) return;
        var model=(BipedEntityModel<?>)(Object)this;
        for(int index=0;index<2;index++) {
            Hand hand=index==0?Hand.MAIN_HAND:Hand.OFF_HAND;
            if(!player.getStackInHand(hand).isOf(PrismLights.FIREWORK_LAUNCHER)) continue;
            Arm physical=hand==Hand.MAIN_HAND?player.getMainArm():player.getMainArm().getOpposite();
            var arm=physical==Arm.RIGHT?model.rightArm:model.leftArm;
            arm.pitch=-(float)Math.PI/2+model.head.pitch;
            arm.yaw=model.head.yaw+(physical==Arm.RIGHT?-0.10f:0.10f);
            arm.roll=0;
        }
    }
}
