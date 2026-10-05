package dev.frameprobe;

import dev.frameprobe.mixin.PauseMenuAccess;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import java.lang.reflect.*;
import java.nio.file.Files;
import java.util.*;
import org.slf4j.LoggerFactory;

/** Causal flight/burst RGB on/off study, with native particles unchanged. */
public final class FireworkProbe {
    static final boolean ACTIVE=Boolean.getBoolean("frameprobe.fireworkStudy");
    private static final boolean SMALL=Boolean.getBoolean("frameprobe.fireworkSmall");
    private static final boolean COMPARE=Boolean.getBoolean("frameprobe.sparkCompare");
    private static final String[] PHASES=COMPARE?new String[]{"idle","warm-cache8","warm-native8","cache8-a","native8-a","native8-b","cache8-b","cache16-a","native16-a","native16-b","cache16-b"}
        :SMALL?new String[]{"idle","flight8-on","flight8-off","burst1-on","burst1-off","burst2-on","burst2-off"}
        :new String[]{"idle","flight8-on","flight8-off","burst8-on","burst8-off","burst16-on","burst16-off"};
    private static Object config;
    private static Field enabled;
    private static boolean setup,closing,finished;
    private static volatile boolean ready;
    private static int phase=-1,ticks,maxParticles,worldFrames;
    private static long start,phaseStart,previousFrame,tickStarted,renderStarted,gcMillisBefore;
    private static final StringBuilder frames=new StringBuilder("phase,kind,milliseconds\n");
    private static final StringBuilder summary=new StringBuilder("phase,maxParticles,frames,gcMillis\n");
    static String label(){return phase<0?"firework-warmup":PHASES[phase];}
    private static long gcMillis(){return java.lang.management.ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(b->b.getCollectionTime()).sum();}
    static void register(){ClientTickEvents.END_CLIENT_TICK.register(FireworkProbe::tick);}
    private static void command(MinecraftClient client,String text){var server=client.getServer();server.getCommandManager().executeWithPrefix(server.getCommandSource(),text);}
    private static void tick(MinecraftClient client){
        if(finished || client.world==null || client.player==null || client.getServer()==null)return;
        if(!client.runDirectory.getName().startsWith("run-frame-validation-firework-study-"))throw new IllegalStateException("Disposable firework study required");
        if(closing){ProfileProbe.finish();finished=true;((PauseMenuAccess)client.currentScreen).frameprobe$saveAndQuit();LoggerFactory.getLogger("Firework Probe").info("FIREWORK STUDY COMPLETE");client.scheduleStop();return;}
        client.options.pauseOnLostFocus=false;client.options.hudHidden=true;
        if(!setup){
            setup=true;start=System.nanoTime();
            try{var api=Class.forName("dev.prismlights.client.PrismClient").getDeclaredMethod("lightField");api.setAccessible(true);var engine=api.invoke(null);
                var f=engine.getClass().getDeclaredField("config");f.setAccessible(true);config=f.get(engine);enabled=config.getClass().getField("enabled");
                ProfileProbe.start(client.runDirectory.toPath());
            }catch(Exception e){throw new IllegalStateException(e);}
            client.getServer().execute(()->{
                for(String c:new String[]{"gamemode creative Player","time set midnight","weather clear","gamerule doDaylightCycle false","gamerule doMobSpawning false","tp Player 1000 -54 999 0 15"})command(client,c);
                var player=client.getServer().getPlayerManager().getPlayer(client.player.getUuid());player.getAbilities().flying=true;player.sendAbilitiesUpdate();ready=true;
            });return;
        }
        if(!ready || System.nanoTime()-start<25_000_000_000L)return;
        if(phase<0)begin(0,client);
        int at=++ticks,currentPhase=phase;
        boolean flight=!COMPARE && (phase==1 || phase==2),burst=COMPARE?phase>=1:phase>=3;
        if((flight && at%40==1) || (burst && at%20==1))client.getServer().execute(()->{
            if(flight)command(client,"kill @e[type=prism_lights:prism_firework]");
            int number=COMPARE?(PHASES[currentPhase].contains("16")?16:8):flight?8:SMALL?(currentPhase>=5?2:1):(currentPhase>=5?16:8);
            for(int i=0;i<number;i++){
                double x=992+(i%8)*2,z=1010+(i/8)*3,y=-57;
                String nbt="{PrismColor:"+(i%2==0?255:16711935)+",Motion:[0.0d,0.0d,0.2d]";
                if(burst)nbt+=",PrismBurst:1b,PrismBurstX:"+x+"d,PrismBurstY:"+y+"d,PrismBurstZ:"+z+"d";
                command(client,"summon prism_lights:prism_firework "+x+" "+y+" "+z+" "+nbt+"}");
            }
        });
        try{maxParticles=Math.max(maxParticles,Integer.parseInt(client.particleManager.getDebugString()));}catch(NumberFormatException e){throw new IllegalStateException("Unexpected particle count format",e);}
        if(System.nanoTime()-phaseStart>10_000_000_000L){
            summary.append(label()).append(',').append(maxParticles).append(',').append(worldFrames).append(',').append(gcMillis()-gcMillisBefore).append('\n');
            LoggerFactory.getLogger("Firework Probe").info("FIREWORK STUDY phase={} particles={} frames={}",label(),maxParticles,worldFrames);
            try{
                var cache=Class.forName("dev.prismlights.client.FireworkCollisionCache");
                for(String key:new String[]{"hits","misses","fallbacks","verified","verifiedContacts"}){
                    var field=cache.getDeclaredField(key);field.setAccessible(true);
                    LoggerFactory.getLogger("Firework Probe").info("SPARK CACHE {}={}",key,field.getLong(null));
                }
            }catch(ClassNotFoundException absent){/* Stable control has no cache. */}
            catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
            if(worldFrames<100 || (phase>0 && maxParticles<10))throw new AssertionError("Firework phase not exercised");
            if(phase+1<PHASES.length)begin(phase+1,client);
            else{try{Files.writeString(client.runDirectory.toPath().resolve("firework-study-frames.csv"),frames);Files.writeString(client.runDirectory.toPath().resolve("firework-study-summary.csv"),summary);}catch(Exception e){throw new IllegalStateException(e);}
                closing=true;phase=-1;client.setScreen(new GameMenuScreen(true));}
        }
    }
    private static void begin(int next,MinecraftClient client){
        phase=next;ticks=0;phaseStart=System.nanoTime();previousFrame=0;maxParticles=worldFrames=0;gcMillisBefore=gcMillis();
        try{
            enabled.setBoolean(config,COMPARE || next==0 || (next&1)==1);
            if(COMPARE)Class.forName("dev.prismlights.client.FireworkCollisionCache").getMethod("compareEnabled",boolean.class)
                .invoke(null,!PHASES[next].contains("native"));
        }catch(Exception e){throw new IllegalStateException(e);}
        client.particleManager.setWorld(client.world);
        client.getServer().execute(()->command(client,"kill @e[type=prism_lights:prism_firework]"));
        if(next==0 && Boolean.getBoolean("prism.collisionVerify"))client.getServer().execute(()->{
            if(!client.getServer().getOverworld().isChunkLoaded(new net.minecraft.util.math.BlockPos(1000,-59,1010)))
                throw new AssertionError("Collision fixture chunk not loaded");
            String[] surfaces={"stone_slab[type=bottom]","oak_stairs[facing=north]","oak_fence","snow[layers=5]","stone_slab[type=top]","cobblestone_wall","water","piston[facing=up]"};
            for(int i=0;i<surfaces.length;i++)command(client,"fill "+(990+i*2)+" -59 1008 "+(991+i*2)+" -59 1016 minecraft:"+surfaces[i]);
            command(client,"fill 1008 -58 1010 1008 -55 1016 minecraft:glass");
        });
    }
    private static boolean measuring(){return ACTIVE && phase>=0 && !finished && !closing && System.nanoTime()-phaseStart>2_000_000_000L;}
    static void rendered(){
        if(!measuring())return;long now=System.nanoTime();if(previousFrame!=0)frames.append(label()).append(",interval,").append((now-previousFrame)/1e6).append('\n');previousFrame=now;worldFrames++;
    }
    public static void tickBegin(){if(ACTIVE){tickStarted=System.nanoTime();ProfileProbe.begin("native-particles-tick");}}
    public static void tickEnd(){if(ACTIVE){ProfileProbe.end();if(measuring())frames.append(label()).append(",particles-tick,").append((System.nanoTime()-tickStarted)/1e6).append('\n');}}
    public static void renderBegin(){if(ACTIVE){renderStarted=System.nanoTime();ProfileProbe.begin("native-particles-render");}}
    public static void renderEnd(){if(ACTIVE){ProfileProbe.end();if(measuring())frames.append(label()).append(",particles-render,").append((System.nanoTime()-renderStarted)/1e6).append('\n');}}
}
