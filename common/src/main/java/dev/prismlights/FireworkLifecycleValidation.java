package dev.prismlights;

import dev.prismlights.platform.PlatformServices;
import dev.prismlights.platform.ContentPlatform;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.slf4j.LoggerFactory;
import java.util.LinkedHashMap;
import java.util.Map;

/** Opt-in real server entity tests. Never edits normal worlds. */
public final class FireworkLifecycleValidation {
    private static final boolean DEDICATED=Boolean.getBoolean("prism.fireworkServerTest");
    private static final boolean ACTIVE=(PlatformServices.get().isDevelopmentEnvironment() || Boolean.getBoolean("prism.qaRuntime"))
        && (Boolean.getBoolean("prism.fireworkTest") || DEDICATED);
    private static final Map<String,PrismFireworkEntity> shots=new LinkedHashMap<>();
    private static final java.util.List<net.minecraft.util.math.ChunkPos> forced=new java.util.ArrayList<>();
    private static ArmorStandEntity target;
    private static int ticks;
    private static boolean started,complete;
    public static boolean complete() {return complete;}
    public static void register(ContentPlatform platform) {
        if(ACTIVE) platform.onServerTick(FireworkLifecycleValidation::tick);
    }
    private static void tick(MinecraftServer server) {
        int current=++ticks;
        if(!started && (DEDICATED?current>=30:current>=100 && !server.getPlayerManager().getPlayerList().isEmpty())) {
            started=true;var world=server.getOverworld();
            if(DEDICATED) {
                require(java.nio.file.Path.of("").toAbsolutePath().getFileName().toString().equals("run-firework-server-validation"),
                    "Dedicated QA must use its isolated world");
                // getChunk loads terrain, but does not tick entities without nearby
                // players. Keep only this disposable server fixture ticking.
                for(int x=1;x<=3;x++) for(int z=-1;z<=4;z++) {
                    var chunk=new net.minecraft.util.math.ChunkPos(x,z);
                    if(!world.getForcedChunks().contains(chunk.toLong())) {
                        world.setChunkForced(x,z,true);forced.add(chunk);
                    }
                    world.getChunk(x,z);
                }
            }
            for(int z=0;z<=48;z+=16) world.getChunk(2,z>>4);
            world.setBlockState(new BlockPos(30,270,0),Blocks.WHITE_CONCRETE.getDefaultState());
            target=new ArmorStandEntity(EntityType.ARMOR_STAND,world);
            target.setPosition(34.5,272,4.5);target.setNoGravity(true);world.spawnEntity(target);
            shot(server,"block",new Vec3d(30.5,273,0.5),new Vec3d(0,-1,0));
            shot(server,"entity",new Vec3d(34.5,273,0.5),new Vec3d(0,0,1));
            shot(server,"distance",new Vec3d(38.5,306,0.5),new Vec3d(0,0,2));
            shot(server,"time",new Vec3d(42.5,306,0.5),Vec3d.ZERO);
            var original=shots.get("distance");NbtCompound data=new NbtCompound();original.writeNbt(data);
            var restored=new PrismFireworkEntity(PrismLights.FIREWORK_ENTITY,world);restored.readNbt(data);
            require(restored.color()==original.color() && restored.hasNoGravity(),"NBT round trip");
            log("Firework lifecycle tests START");
        }
        if(!started || complete) return;
        boolean done=true;
        for(var entry:shots.entrySet()) {
            var shot=entry.getValue();
            if(!shot.isRemoved()) {done=false;continue;}
            require(entry.getKey().equals(shot.burstReason()),"Wrong burst cause: "+entry.getKey()+" -> "+shot.burstReason());
            require(shot.age<=PrismFireworkEntity.MAX_FLIGHT_TICKS+PrismFireworkEntity.BURST_TICKS+2,"Projectile leaked");
            if(entry.getKey().equals("distance")) require(shot.flownDistance()>=40 && shot.flownDistance()<=42,"Range limit");
        }
        if(done) {
            require(server.getOverworld().getBlockState(new BlockPos(30,270,0)).isOf(Blocks.WHITE_CONCRETE),"Explosion destroyed terrain");
            require(target.getHealth()==target.getMaxHealth(),"Visual explosion damaged target");
            target.discard();complete=true;
            for(var chunk:forced) server.getOverworld().setChunkForced(chunk.x,chunk.z,false);
            forced.clear();
            log("Firework lifecycle tests PASS: block/entity/distance/time, cleanup, NBT, terrain, no damage");
            if(DEDICATED) server.stop(false);
        }
        require(current<500,"Firework lifecycle timeout");
    }
    private static void shot(MinecraftServer server,String name,Vec3d position,Vec3d velocity) {
        var entity=new PrismFireworkEntity(PrismLights.FIREWORK_ENTITY,server.getOverworld());
        entity.setColor(0x054DFF);entity.setPosition(position);entity.setVelocity(velocity);
        require(server.getOverworld().spawnEntity(entity),"Could not spawn "+name);shots.put(name,entity);
    }
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static void log(String text){LoggerFactory.getLogger("Prism Lights").info(text);}
}
