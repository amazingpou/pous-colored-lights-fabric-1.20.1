package dev.prismlights.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.prismlights.client.GpuLightVolume;
import dev.prismlights.client.LightingValidation;
import dev.prismlights.client.ProductValidation;
import dev.prismlights.client.OcclusionValidation;
import dev.prismlights.client.NightColorValidation;
import dev.prismlights.client.ColorResponseValidation;
import dev.prismlights.client.MobileLights;
import dev.prismlights.client.FireworkValidation;
import dev.prismlights.client.PerformanceValidation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Set world-space shader inputs before rendering terrain and entities. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Unique private final java.util.ArrayList<Matrix4f[]> prism$matrices=new java.util.ArrayList<>();
    @Unique private int prism$renderDepth;
    @Inject(method="renderWorld",at=@At("TAIL"))
    private void prism$fireworkQaPreview(float tickDelta,long limitTime,MatrixStack matrices,CallbackInfo ci) {
        dev.prismlights.client.LightTransitionGate.rendered();
        FireworkValidation.afterFrame(MinecraftClient.getInstance());
        PerformanceValidation.frame();
    }
    @Inject(method="renderWorld",at=@At("HEAD"))
    private void prism$validationCamera(float tickDelta,long limitTime,MatrixStack matrices,CallbackInfo ci) {
        dev.prismlights.client.PrismClient.advanceFrame(MinecraftClient.getInstance());
        LightingValidation.prepareCamera(MinecraftClient.getInstance());
        ProductValidation.prepareCamera(MinecraftClient.getInstance());
        OcclusionValidation.prepareCamera(MinecraftClient.getInstance());
        NightColorValidation.prepareCamera(MinecraftClient.getInstance());
        ColorResponseValidation.prepareCamera(MinecraftClient.getInstance());
        FireworkValidation.prepareCamera(MinecraftClient.getInstance());
        PerformanceValidation.prepareCamera(MinecraftClient.getInstance());
        dev.prismlights.client.CandleRevisionValidation.prepareCamera(MinecraftClient.getInstance());
    }
    @WrapOperation(method = "renderWorld", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/render/WorldRenderer;render(Lnet/minecraft/client/util/math/MatrixStack;FJZLnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/GameRenderer;Lnet/minecraft/client/render/LightmapTextureManager;Lorg/joml/Matrix4f;)V"))
    private void prism$afterWorld(WorldRenderer renderer, MatrixStack matrices,float tickDelta,long limitTime,boolean outline,
                                  Camera camera, GameRenderer gameRenderer, LightmapTextureManager lightmap,
                                  Matrix4f projection, Operation<Void> original) {
        // Preserve the original matrices even if a renderer mutates its arguments.
        if(prism$renderDepth==prism$matrices.size())prism$matrices.add(new Matrix4f[]{new Matrix4f(),new Matrix4f()});
        Matrix4f[] slot=prism$matrices.get(prism$renderDepth++);
        Matrix4f savedView = slot[0].set(matrices.peek().getPositionMatrix());
        Matrix4f savedProjection = slot[1].set(projection);
        MobileLights.frame(camera.getPos(),tickDelta);
        GpuLightVolume.beginWorld(camera.getPos(),savedView,savedProjection);
        try {
            original.call(renderer, matrices,tickDelta,limitTime,outline,camera,gameRenderer,lightmap,projection);
            LightingValidation.frame(MinecraftClient.getInstance());
            ProductValidation.frame(MinecraftClient.getInstance());
            OcclusionValidation.frame(MinecraftClient.getInstance());
            NightColorValidation.frame(MinecraftClient.getInstance(),savedView,savedProjection);
            ColorResponseValidation.frame(MinecraftClient.getInstance(),savedView,savedProjection);
            FireworkValidation.frame(MinecraftClient.getInstance(),savedView,savedProjection);
            dev.prismlights.client.CandleRevisionValidation.frame(MinecraftClient.getInstance(),savedView,savedProjection);
        }
        finally { GpuLightVolume.endWorld();prism$renderDepth--; }
    }
}
