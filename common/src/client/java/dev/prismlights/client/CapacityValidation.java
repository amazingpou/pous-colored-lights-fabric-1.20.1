package dev.prismlights.client;

import dev.prismlights.*;
import dev.prismlights.platform.PlatformServices;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.util.math.BlockPos;
import org.slf4j.LoggerFactory;
import java.util.Arrays;

/** Opt-in native capacity/caching checks in a disposable world copy. */
final class CapacityValidation {
    static final boolean ACTIVE=(PlatformServices.get().isDevelopmentEnvironment() || Boolean.getBoolean("prism.qaRuntime")) && Boolean.getBoolean("prism.capacityTest");
    private static final String[] PACKS=ColorResponseValidation.renderers();
    private static int ticks,phase,step,count;
    private static volatile boolean ready;
    private static final long[] samples=new long[80];
    private static long builds;
    private CapacityValidation() {}

    static void beforeTick(MinecraftClient client,LightField field,PrismConfig config) {
        if(!ACTIVE || client.player==null || client.getServer()==null || client.isPaused()) return;
        if(!java.util.Set.of("run-capacity-validation","run-limit-validation-72-8").contains(client.runDirectory.getName()) && !client.runDirectory.getName().startsWith("run-optimization-validation-")) throw new IllegalStateException("Capacity QA needs an isolated world");
        client.options.pauseOnLostFocus=false;
        if(++ticks==10) {
            require(config.maxLights==LightSourceLimit.MAX && config.configVersion==LightSourceLimit.CONFIG_VERSION,"Loaded current default/migrated configuration");
            client.getServer().execute(()->{
                var server=client.getServer();var world=server.getOverworld();
                for(int x=1980>>4;x<=2020>>4;x++) for(int z=-12>>4;z<=25>>4;z++) world.getChunk(x,z);
                for(String command:new String[]{"gamemode creative Player","gamerule doMobSpawning false","time set midnight",
                        "fill 1980 99 -12 2020 112 25 air","fill 1980 113 -12 2020 125 25 air",
                        "fill 1980 99 -12 2020 99 25 white_concrete",
                        "tp Player 2000 105 -5 0 28"}) server.getCommandManager().executeWithPrefix(server.getCommandSource(),command);
                for(int i=0;i<97;i++) world.setBlockState(new BlockPos(1989+i%12*2,100,2+i/12*2),PrismLights.BLOCKS.get("blue").getDefaultState());
                var player=server.getPlayerManager().getPlayer(client.player.getUuid());
                player.getAbilities().flying=true;player.sendAbilitiesUpdate();ready=true;
            });
        }
        if(ticks<160 || !ready) return;
        client.player.getAbilities().flying=true;
        phase=(ticks-160)/140;step=(ticks-160)%140;
        if(phase>=PACKS.length) {log("Capacity QA COMPLETE");client.scheduleStop();return;}
        if(step==0) {
            count=0;
            if(PlatformServices.get().isModLoaded("iris")) NightColorValidation.reload(PACKS[phase]);
        }
        if(step==40) builds=field.propagationBuilds();
        if(step==70)client.getServer().execute(()->{
            var world=client.getServer().getOverworld();
            for(int i=0;i<MobileLights.LIMIT+1;i++) {
                var shot=new PrismFireworkEntity(PrismLights.FIREWORK_ENTITY,world);
                shot.setPosition(1999+(i%3)*.5,104,1+(i/3)*.5);shot.setColor(0xFF1510);
                shot.setVelocity(0,0,0);require(world.spawnEntity(shot),"mobile capacity fixture spawned");
            }
        });
        if(step==90) {
            require(MobileLights.count()==MobileLights.LIMIT,"nine projectiles capped at eight mobile lights");
            MobileLights.verifyAtlas();log("9 projectiles / 8 lights; exact GPU atlas readback PASS");
        }
    }

    static void afterTick(MinecraftClient client,LightField field,long started) {
        if(!ACTIVE || ticks<160 || !ready || phase>=PACKS.length || client.isPaused()) return;
        long elapsed=System.nanoTime()-started;
        if(step>=40 && step<120) samples[count++]=elapsed;
        if(step!=120) return;
        log(PACKS[phase]+" candidates="+field.candidateCount()+" selected="+field.selectedCount()+" propagated="+field.cachedSources());
        for(int i=0;i<97;i++) require(field.hasEmitter(new BlockPos(1989+i%12*2,100,2+i/12*2)),"Fixture emitter registered "+i);
        require(field.candidateCount()>=97 && field.selectedCount()==LightSourceLimit.MAX && field.cachedSources()==LightSourceLimit.MAX,"97 fixture candidates capped at current fully propagated source limit");
        require(field.propagationBuilds()==builds,"Steady scene does not rebuild propagation");
        if(PlatformServices.get().isModLoaded("iris")) NightColorValidation.verifyPack(PACKS[phase]);
        field.verifyFreshSnapshot(client);
        log(PACKS[phase]+" full field equality PASS");
        var fullVolume=GpuLightVolume.current();
        for(int cap:new int[]{64,LightSourceLimit.MAX}) {
            var comparisonConfig=new PrismConfig();comparisonConfig.maxLights=cap;
            var comparison=new LightField(comparisonConfig);
            for(int x=1980>>4;x<=2020>>4;x++) for(int z=-12>>4;z<=25>>4;z++) comparison.load(client.world,client.world.getChunk(x,z));
            long comparisonStart=System.nanoTime();comparison.tick(client);comparison.finishPending();
            long comparisonElapsed=System.nanoTime()-comparisonStart;
            require(comparison.selectedCount()==cap && comparison.cachedSources()==cap,"Fully propagated comparison scene at "+cap);
            log(PACKS[phase]+" fresh "+cap+"-source rebuild ms="+comparisonElapsed/1e6);
        }
        GpuLightVolume.publish(fullVolume);
        Arrays.sort(samples,0,count);
        log(PACKS[phase]+" 97 candidates / "+LightSourceLimit.MAX+" selected PASS; cached tick median ms="+samples[count/2]/1e6+" p95 ms="+samples[(count*95)/100]/1e6+" samples="+count);
        ScreenshotRecorder.saveScreenshot(client.runDirectory,"capacity-"+LightSourceLimit.MAX+"-"+PACKS[phase].replace('.','-')+".png",client.getFramebuffer(),message->log(message.getString()));
    }
    private static void require(boolean value,String message) {if(!value) throw new AssertionError(message);}
    private static void log(String value) {LoggerFactory.getLogger("Prism Lights").info("Capacity QA: {}",value);}
}
