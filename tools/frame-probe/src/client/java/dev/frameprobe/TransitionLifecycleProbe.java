package dev.frameprobe;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;
import net.minecraft.block.CandleBlock;
import org.slf4j.LoggerFactory;
import java.lang.reflect.*;
import java.nio.file.Files;
import java.util.Map;

/** Real off-screen updates and disable/re-enable; never forces RGB completion. */
final class TransitionLifecycleProbe {
    private static int ticks;
    private static Object config;
    private static Field enabled,sources;
    private static final BlockPos LAMP=new BlockPos(1000,-59,988);
    private static final StringBuilder result=new StringBuilder();
    static void register(){ClientTickEvents.END_CLIENT_TICK.register(TransitionLifecycleProbe::tick);}
    private static void command(MinecraftClient c,String text){c.getServer().execute(()->c.getServer().getCommandManager().executeWithPrefix(c.getServer().getCommandSource(),text));}
    private static void tick(MinecraftClient c){
        if(c.world==null || c.player==null || c.getServer()==null || c.isPaused())return;
        if(!c.runDirectory.getName().startsWith("run-frame-validation-transition-"))throw new AssertionError("Disposable transition world required");
        c.options.pauseOnLostFocus=false;
        try {
            int t=++ticks;
            if(t==1){
                var api=Class.forName("dev.prismlights.client.PrismClient").getDeclaredMethod("lightField");api.setAccessible(true);
                Object field=api.invoke(null);var cf=field.getClass().getDeclaredField("config");cf.setAccessible(true);config=cf.get(field);
                enabled=config.getClass().getField("enabled");enabled.setBoolean(config,true);
                sources=Class.forName("dev.prismlights.client.LightTransitionGate").getDeclaredField("sources");sources.setAccessible(true);
                command(c,"gamemode creative Player");command(c,"gamerule doDaylightCycle false");command(c,"time set midnight");
                command(c,"tp Player 1000 -55 1000 0 5");
            }
            if(t==100)c.getServer().execute(()->{
                var w=c.getServer().getOverworld();
                for(int x=980>>4;x<=1020>>4;x++)for(int z=980>>4;z<=1020>>4;z++)w.getChunk(x,z);
                var source=c.getServer().getCommandSource();
                for(String text:new String[]{"fill 980 -60 980 1020 -49 1020 air","fill 980 -60 980 1020 -60 1020 white_concrete","setblock 1004 -59 1008 prism_lights:green_prism","tp Player 1000 -55 1000 0 5"})
                    c.getServer().getCommandManager().executeWithPrefix(source,text);
                var player=c.getServer().getPlayerManager().getPlayer(c.player.getUuid());player.getAbilities().flying=true;player.sendAbilitiesUpdate();
            });
            if(t>=200 && t<=440){float yaw=t>=310 && t<370?180:0;c.player.setYaw(yaw);c.player.prevYaw=yaw;c.player.setPitch(5);c.player.prevPitch=5;}
            if(t==200 || t==320)command(c,"setblock 1000 -59 988 prism_lights:blue_prism");
            if(t==260 || t==380)command(c,"setblock 1000 -59 988 prism_lights:blue_prism[active=false]");
            if(t==240 || t==300 || t==360 || t==420){
                if(!((Map<?,?>)sources.get(null)).isEmpty()){debug(c);throw new AssertionError("Off-screen transition held after 40 ticks at "+t);}
                int actual=c.world.getLightingProvider().get(LightType.BLOCK).getLightLevel(LAMP);
                boolean on=t==240 || t==360;
                if(actual!=(on?15:0))throw new AssertionError("Native off-screen luminance "+actual+" at "+t);
                pass("off-screen "+(on?"on":"off")+" "+t);
            }
            if(t==440)command(c,"setblock 1000 -59 988 prism_lights:blue_prism");
            if(t==442)enabled.setBoolean(config,false);
            if(t==460){if(!((Map<?,?>)sources.get(null)).isEmpty())throw new AssertionError("Disable retained barrier");pass("disabled releases pending light");enabled.setBoolean(config,true);}
            if(t==480)command(c,"setblock 1000 -59 988 air");
            if(t==520){if(!((Map<?,?>)sources.get(null)).isEmpty())throw new AssertionError("Removed light retained barrier");pass("removal after re-enable");}
            if(t==550)command(c,"setblock 1006 -59 1008 prism_lights:blue_candle[candles=1,lit=true]");
            if(t==600){
                var pos=new BlockPos(1006,-59,1008);var state=c.world.getBlockState(pos);
                if(!state.contains(CandleBlock.CANDLES) || state.get(CandleBlock.CANDLES)!=1)throw new AssertionError("Candle fixture missing");
                for(int count=2;count<=4;count++)c.world.setBlockState(pos,state.with(CandleBlock.CANDLES,count),3);
                var api=sources.getDeclaringClass().getMethod("luminance",net.minecraft.client.world.ClientWorld.class,long.class,int.class);
                int held=(Integer)api.invoke(null,c.world,pos.asLong(),12);
                if(held!=3)throw new AssertionError("Rapid candle growth advanced held emission before RGB: "+held);
                pass("rapid candle 3/6/9/12 holds emission 3 until publication");
                command(c,"setblock 1006 -59 1008 prism_lights:blue_candle[candles=4,lit=true]");
            }
            if(t==660){if(!((Map<?,?>)sources.get(null)).isEmpty())throw new AssertionError("Candle growth retained barrier");pass("candle growth publishes and releases");}
            if(t==670){Files.writeString(c.runDirectory.toPath().resolve("transition-lifecycle.txt"),result);pass("TRANSITION LIFECYCLE COMPLETE");c.scheduleStop();}
        }catch(ReflectiveOperationException | java.io.IOException e){throw new IllegalStateException(e);}
    }
    private static void pass(String text){result.append(text).append('\n');LoggerFactory.getLogger("Transition Probe").info(text);}
    private static void debug(MinecraftClient c)throws ReflectiveOperationException {
        var gate=sources.getDeclaringClass();
        for(String name:new String[]{"sources","removals","packets","nativeEpoch","renderedEpoch"}){var f=gate.getDeclaredField(name);f.setAccessible(true);pass("gate "+name+"="+f.get(null));}
        pass("native source="+c.world.getLightingProvider().get(LightType.BLOCK).getLightLevel(LAMP)+" updates="+c.world.getLightingProvider().get(LightType.BLOCK).hasUpdates());
        var renderer=Class.forName("me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer").getMethod("instanceNullable").invoke(null);
        var mf=renderer.getClass().getDeclaredField("renderSectionManager");mf.setAccessible(true);var manager=mf.get(renderer);
        var sf=manager.getClass().getDeclaredField("sectionByPosition");sf.setAccessible(true);var loaded=(Map<?,?>)sf.get(manager);
        var af=gate.getDeclaredField("sections");af.setAccessible(true);
        for(Object key:(Iterable<?>)af.get(null)){var section=loaded.get(key);if(section==null)continue;
            var built=section.getClass().getMethod("isBuilt").invoke(section);var pending=section.getClass().getMethod("getPendingUpdate").invoke(section);var task=section.getClass().getMethod("getBuildCancellationToken").invoke(section);
            if(pending!=null || task!=null)pass("section "+section+" built="+built+" pending="+pending+" task="+task);
        }
    }
}
