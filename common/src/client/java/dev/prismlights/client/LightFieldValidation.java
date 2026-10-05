package dev.prismlights.client;

import dev.prismlights.platform.PlatformServices;
import net.minecraft.client.MinecraftClient;
import org.slf4j.LoggerFactory;

/** Differential cache/geometry QA on a disposable showcase copy. */
final class LightFieldValidation {
    private static final boolean ACTIVE=(PlatformServices.get().isDevelopmentEnvironment() || Boolean.getBoolean("prism.qaRuntime"))
        && Boolean.getBoolean("prism.lightCacheTest");
    private static final String[] CASES={"initial","source-off","source-on","opaque-source",
        "opaque-source-off","source-removed","wall-added","wall-removed","water-added",
        "water-removed","torch-added","far-chunk","near-unloaded","near-reloaded",
        "over-cap","selection-moved","rgb-mixing","artistic-mixing","gpu-state"};
    private static int ticks,phase=-1,step;
    private LightFieldValidation() {}
    static void beforeTick(MinecraftClient client,LightField field,PrismConfig config) {
        if(!ACTIVE || client.world==null || client.player==null || client.getServer()==null || client.isPaused()) return;
        if(!client.runDirectory.getName().startsWith("run-optimization-validation-") && !client.runDirectory.getName().startsWith("run-performance-"))throw new IllegalStateException("Isolated optimization QA copy required");
        client.options.pauseOnLostFocus=false;
        if(++ticks==20) client.getServer().execute(()->{
            var server=client.getServer();var commands=server.getCommandManager();var source=server.getCommandSource();
            for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)server.getOverworld().getChunk(x,z);
            // Fresh backport world: seed the fixtures formerly provided by the
            // showcase copy, including enough local sources to exercise cap72.
            commands.executeWithPrefix(source,"fill -15 -56 20 -12 -56 23 prism_lights:white_prism");
            for(String command:new String[]{"gamemode creative Player","time set midnight","weather clear",
                    "tp Player 0 -49 -36 0 17"}) commands.executeWithPrefix(source,command);
            var player=server.getPlayerManager().getPlayer(client.player.getUuid());
            player.getAbilities().flying=true;player.sendAbilitiesUpdate();
        });
        if(ticks<160) return;
        phase=(ticks-160)/30;step=(ticks-160)%30;
        if(phase>=CASES.length) {log("Differential light field QA COMPLETE");client.scheduleStop();return;}
        if(step!=0) return;
        String command=switch(phase) {
            case 1->"setblock -15 -56 22 prism_lights:white_prism[active=false]";
            case 2->"setblock -15 -56 22 prism_lights:white_prism";
            case 3->"setblock 3 -56 5 prism_lights:blue_prism";
            case 4->"setblock 3 -56 5 prism_lights:blue_prism[active=false]";
            case 5->"setblock 3 -56 5 air";
            case 6->"fill -2 -59 5 -2 -49 15 white_concrete";
            case 7->"fill -2 -59 5 -2 -49 15 air";
            case 8->"setblock 2 -57 8 water";
            case 9->"fill -1 -59 5 5 -55 11 air";
            case 10->"setblock 3 -57 8 prism_lights:blue_torch";
            case 14->"fill -8 -56 9 8 -56 12 prism_lights:blue_prism";
            case 15->"tp Player 8 -49 15 0 17";
            default->null;
        };
        if(command!=null) client.getServer().execute(()->{
            var server=client.getServer();
            if(phase==10) server.getCommandManager().executeWithPrefix(server.getCommandSource(),"setblock 3 -58 8 smooth_quartz");
            server.getCommandManager().executeWithPrefix(server.getCommandSource(),command);
        });
        if(phase==11) {
            int cached=field.cachedSources();
            var chunk=client.world.getChunk(-6,-7);field.unload(client.world,chunk);field.load(client.world,chunk);
            if(field.cachedSources()!=cached) throw new AssertionError("Far chunk invalidated local lights");
        }
        if(phase==12) field.unload(client.world,client.world.getChunk(0,1));
        if(phase==13) field.load(client.world,client.world.getChunk(0,1));
        if(phase==16 || phase==17) {config.artisticMixing=phase==17;field.invalidate();}
        if(phase==18) {GpuUploadValidation.run();SkyExposure.verifyReloadSnapshot(client.world,GpuLightVolume.current());}
    }
    static void afterTick(MinecraftClient client,LightField field) {
        if(!ACTIVE || phase<0 || phase>=CASES.length || step!=20) return;
        // Compare the event itself above. Subsequent ticks may legitimately rebuild
        // nearby masks as water flows in this QA copy.
        if(phase==10 && !field.hasEmitter(new net.minecraft.util.math.BlockPos(3,-57,8)))
            throw new AssertionError("Placed torch was not registered as an emitter");
        if(phase==14 && (field.candidateCount()<=dev.prismlights.LightSourceLimit.MAX || field.selectedCount()!=dev.prismlights.LightSourceLimit.MAX))
            throw new AssertionError("QA did not exercise the current source selection cap");
        field.verifyFreshSnapshot(client);
        SkyExposure.verifyFreshSnapshot(client.world,GpuLightVolume.current());
        log("Fresh field equality PASS: "+CASES[phase]);
    }
    private static void log(String text){LoggerFactory.getLogger("Prism Lights").info(text);}
}
