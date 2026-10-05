package dev.prismlights.client;

import dev.prismlights.PrismFireworkEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import dev.prismlights.PrismLights;
import org.lwjgl.glfw.GLFW;

public final class PrismClient {
    private static LightField lightField;
    static long pipelineChanges;
    static LightField lightField() { return lightField; }
    private static final dev.prismlights.LightingWorkBudget workBudget=
        new dev.prismlights.LightingWorkBudget(2_000_000L,350_000L,System::nanoTime);
    private static final dev.prismlights.LightingWorkBudget.Stage[] workStages={
        new dev.prismlights.LightingWorkBudget.Stage(()->lightField!=null && lightField.workPending(),deadline->lightField.advanceUntil(deadline)),
        new dev.prismlights.LightingWorkBudget.Stage(MobileLights::workPending,MobileLights::advanceUntil),
        new dev.prismlights.LightingWorkBudget.Stage(()->SkyExposure.workPending(MinecraftClient.getInstance().world,GpuLightVolume.current()),
            deadline->SkyExposure.advance(MinecraftClient.getInstance().world,GpuLightVolume.current(),deadline))
    };
    public static void advanceFrame(MinecraftClient client) {
        if(lightField!=null && !client.isPaused())workBudget.run(workStages);
    }
    public static void beginFrame(){workBudget.beginFrame();}
    public static void blockChanged(BlockPos pos,BlockState before,BlockState after) {
        LightTransitionGate.changed(pos,before,after);
        if(before!=after)WorldOptics.changed(pos);
        if(lightField!=null) lightField.blockChanged(pos,before,after);
        if(before!=after) MobileLights.changed(pos);
    }
    public static void initialize(ClientPlatform platform) {
        platform.registerFireworkRenderer();
        PrismLights.CANDLE_FLAMES.forEach(platform::registerCandleFlame);
        platform.onEntityLoad((entity,world)->{
            if(entity instanceof PrismFireworkEntity shot) MobileLights.load(world,shot);
        });
        platform.onEntityUnload((entity,world)->{
            if(entity instanceof PrismFireworkEntity) MobileLights.unload(world,entity.getId());
        });
        PrismLights.NEW_BLOCKS.forEach((name, block) -> {
            if (name.endsWith("_lantern") || name.endsWith("_torch") || name.endsWith("_candle") || name.endsWith("_candle_cake"))
                platform.cutout(block);
        });
        PrismConfig config = PrismConfig.load();
        LightField field = lightField = new LightField(config);
        KeyBinding mixing = platform.registerKey(new KeyBinding("key.prism_lights.mixing", GLFW.GLFW_KEY_G, "category.prism_lights"));
        KeyBinding toggle = platform.registerKey(new KeyBinding("key.prism_lights.toggle", GLFW.GLFW_KEY_V, "category.prism_lights"));
        platform.onChunkLoad((world,chunk)->{WorldOptics.chunk(world,chunk.getPos());field.load(world,chunk);MobileLights.chunkChanged(chunk.getPos());});
        platform.onChunkUnload((world,chunk)->{WorldOptics.chunk(world,chunk.getPos());field.unload(world,chunk);MobileLights.chunkChanged(chunk.getPos());});
        platform.onClientTick(client -> {
            LauncherRecoil.tick(client);
            if (ShaderPipelineWatcher.changed()) {
                GpuLightVolume.pipelineChanged();
                field.invalidate();
                pipelineChanges++;
            }
            while (mixing.wasPressed()) {
                config.artisticMixing = !config.artisticMixing;
                config.save(); field.invalidate();
                if (client.player != null) client.player.sendMessage(Text.translatable(config.artisticMixing ? "message.prism_lights.artistic" : "message.prism_lights.rgb"), true);
            }
            while (toggle.wasPressed()) {
                config.enabled = !config.enabled; config.save(); field.invalidate();
                if (client.player != null) client.player.sendMessage(Text.translatable(config.enabled ? "message.prism_lights.on" : "message.prism_lights.off"), true);
            }
            PerformanceValidation.beforeTick(client,field,config);
            LightFieldValidation.beforeTick(client,field,config);
            CapacityValidation.beforeTick(client,field,config);
            long capacityTiming=CapacityValidation.ACTIVE?System.nanoTime():0;
            long timing=PerformanceValidation.ACTIVE?System.nanoTime():0;
            long builds=PerformanceValidation.ACTIVE?field.propagationBuilds():0;
            field.tick(client);
            PerformanceValidation.measuredTick(timing,builds);
            LightFieldValidation.afterTick(client,field);
            CapacityValidation.afterTick(client,field,capacityTiming);
            GpuLightVolume.setLightOptions(config.strength,config.artisticMixing);
            MobileLights.tick(client,config);
            ShowcaseSetup.tick(client);
            LightingValidation.tick(client);
            ProductValidation.tick(client);
            OcclusionValidation.tick(client);
            CompatibilityValidation.tick(client);
            NightColorValidation.tick(client);
            ColorResponseValidation.tick(client);
            FireworkValidation.tick(client);
            DesignValidation.tick(client);
            CandleRevisionValidation.tick(client);
        });
    }
}
