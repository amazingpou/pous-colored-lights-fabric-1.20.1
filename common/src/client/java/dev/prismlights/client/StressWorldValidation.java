package dev.prismlights.client;

import dev.prismlights.PrismLights;
import dev.prismlights.platform.PlatformServices;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import java.util.List;
import org.slf4j.LoggerFactory;

/** Dimension transitions, modded blocks/entities and shader smoke tests in the QA copy. */
final class StressWorldValidation {
    private static final boolean ACTIVE=(PlatformServices.get().isDevelopmentEnvironment() || Boolean.getBoolean("prism.qaRuntime")) && Boolean.getBoolean("prism.stressTest");
    private static List<net.minecraft.server.world.ServerWorld> worlds;
    private static int dimension,wait;
    private static volatile boolean ready;
    private static String expected;
    private StressWorldValidation() {}

    static boolean tick(MinecraftClient client) {
        if(!ACTIVE) return true;
        if(worlds==null) {
            worlds=java.util.stream.StreamSupport.stream(client.getServer().getWorlds().spliterator(),false).toList();
            if(PlatformServices.get().isModLoaded("iris")) NightColorValidation.reload("BSL_v10.1.8.zip");
        }
        if(dimension>=worlds.size()) {log("Stress world QA COMPLETE: modded blocks/entities, dimension changes, active material pipelines");return true;}
        if(wait++==0) {
            ready=false;var world=worlds.get(dimension);expected=world.getRegistryKey().getValue().toString();
            client.getServer().execute(()->{
                int y=world.getTopY()-12;
                for(int x=-1;x<=1;x++) for(int z=-1;z<=1;z++) world.getChunk(x,z);
                for(int x=-5;x<=19;x++) for(int z=-12;z<=18;z++) {
                    world.setBlockState(new BlockPos(x,y-1,z),Blocks.WHITE_CONCRETE.getDefaultState());
                    for(int h=0;h<=7;h++) world.setBlockState(new BlockPos(x,y+h,z),Blocks.AIR.getDefaultState());
                }
                for(int i=0;i<16;i++) world.setBlockState(new BlockPos(i%8*2,y,4+i/8*6),PrismLights.BLOCKS.get("blue").getDefaultState());
                for(String namespace:new String[]{"cobblemon","betterend","betternether","spectrum","deeperdarker","adventurez","chipped","farmersdelight"}) {
                    var pos=new BlockPos(2,y,2);
                    var block=Registries.BLOCK.getIds().stream().filter(id->id.getNamespace().equals(namespace))
                        .map(Registries.BLOCK::get).filter(b->b.getDefaultState().isOpaqueFullCube(world,pos)).findFirst();
                    if(block.isPresent()) {
                        int index=java.util.Arrays.asList("cobblemon","betterend","betternether","spectrum","deeperdarker","adventurez","chipped","farmersdelight").indexOf(namespace);
                        world.setBlockState(new BlockPos(index*2,y,1),block.get().getDefaultState());
                        log("Placed content block "+Registries.BLOCK.getId(block.get()));
                    }
                }
                // Real mod entity constructors/renderers; disable their combat AI in this disposable fixture.
                for(String id:new String[]{"adventurez:orc","deeperdarker:sculk_centipede","betternether:firefly"}) {
                    var key=new net.minecraft.util.Identifier(id);
                    if(!Registries.ENTITY_TYPE.containsId(key)) continue;
                    var entity=Registries.ENTITY_TYPE.get(key).create(world);
                    if(entity!=null) {
                        entity.setPosition(8,y+1,3);entity.setNoGravity(true);
                        if(entity instanceof MobEntity mob) mob.setAiDisabled(true);
                        world.spawnEntity(entity);log("Spawned content entity "+id);
                    }
                }
                var server=client.getServer();
                server.getCommandManager().executeWithPrefix(server.getCommandSource(),"execute in "+expected+" run tp Player 8 "+(y+5)+" -6 0 20");
                var player=server.getPlayerManager().getPlayer(client.player.getUuid());
                player.getAbilities().flying=true;player.sendAbilitiesUpdate();
                if(PlatformServices.get().isModLoaded("worldedit")) {
                    var source=player.getCommandSource().withLevel(4);
                    for(String command:new String[]{"//pos1 0,"+y+",12","//pos2 6,"+y+",12","//set prism_lights:blue_prism"})
                        server.getCommandManager().executeWithPrefix(source,command);
                    if(!world.getBlockState(new BlockPos(0,y,12)).isOf(PrismLights.BLOCKS.get("blue")))
                        throw new AssertionError("WorldEdit did not place registered light blocks");
                    log("WorldEdit registry/edit PASS "+expected);
                }
                if(PlatformServices.get().isModLoaded("cobblemon")) {
                    var type=Registries.ENTITY_TYPE.get(new net.minecraft.util.Identifier("cobblemon:pokemon"));
                    int before=world.getEntitiesByType(type,e->true).size();
                    server.getCommandManager().executeWithPrefix(player.getCommandSource().withLevel(4),"pokespawn pikachu");
                    if(world.getEntitiesByType(type,e->true).size()<=before) throw new AssertionError("Cobblemon spawn command failed");
                    log("Cobblemon spawn command PASS "+expected);
                }
                ready=true;
            });
        }
        if(wait<100 || !ready) return false;
        if(!client.world.getRegistryKey().getValue().toString().equals(expected) || PrismClient.lightField().selectedCount()<16) {
            if(wait>600) throw new AssertionError("Dimension fixture timeout "+expected);return false;
        }
        client.player.getAbilities().flying=true;
        if(PlatformServices.get().isModLoaded("iris")) NightColorValidation.verifyPack("BSL_v10.1.8.zip");
        // Dimension loading can deliver many client ticks before a rendered
        // frame. Cooperative rebuilding must be allowed to publish naturally;
        // never force its completion from the correctness fixture.
        if(GpuLightVolume.current()==null) {
            if(wait>600)throw new AssertionError("RGB rebuild timeout in "+expected);
            return false;
        }
        ScreenshotRecorder.saveScreenshot(client.runDirectory,"stress-"+expected.replace(':','-')+".png",client.getFramebuffer(),message->log(message.getString()));
        log("Dimension RGB/pipeline PASS "+expected+" sources="+PrismClient.lightField().selectedCount());
        dimension++;wait=0;return false;
    }
    private static void log(String value){LoggerFactory.getLogger("Prism Lights").info("Stress QA: {}",value);}
}
