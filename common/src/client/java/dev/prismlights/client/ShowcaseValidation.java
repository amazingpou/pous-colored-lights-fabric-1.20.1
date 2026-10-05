package dev.prismlights.client;

import dev.prismlights.PrismLights;
import dev.prismlights.platform.PlatformServices;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.ButtonBlock;
import net.minecraft.block.LeverBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.slf4j.LoggerFactory;
import java.nio.file.Files;
import java.util.ArrayList;

/** Opt-in checks and camera captures of the isolated filming map. */
public final class ShowcaseValidation {
    private static final boolean ACTIVE=PlatformServices.get().isDevelopmentEnvironment()
            && Boolean.getBoolean("prism.showcaseQa");
    private static final String[] COLORS="white orange magenta light_blue yellow lime pink gray light_gray cyan purple blue brown green red black".split(" ");
    private static final String[] NAMES={"01-pavilhao","02-jardim","03-prismas","04-laboratorio","05-lanternas","06-redstone","07-alvo","08-tochas"};
    private static final double[][] VIEWS={{0,-49,-36,0,17},{-29,-56,-28,-38,8},{0,-55,7,0,7},
        {36,-56,7,-34,8},{28,-56,-26,-55,12},{-2,-55,33,0,9},{-43,-55,26,0,8},{-31,-56,-26,38,10}};
    private static int ticks;
    private static volatile boolean verified;
    private static boolean started;

    private ShowcaseValidation() {}

    public static void tick(MinecraftClient client) {
        if(!ACTIVE || !ShowcaseSetup.ready() || client.player==null || client.getServer()==null) return;
        client.options.pauseOnLostFocus=false;
        if(!started) {
            started=true;client.options.hudHidden=true;
            ItemGroups.updateDisplayContext(client.world.getEnabledFeatures(),true,client.world.getRegistryManager());
            var actual=Registries.ITEM_GROUP.get(PrismLights.id("lights")).getDisplayStacks().stream()
                .map(stack->Registries.ITEM.getId(stack.getItem()).toString()).toList();
            var expected=new ArrayList<String>();
            for(String family:new String[]{"prism","smooth_light","redstone_prism","lantern","torch"})
                for(String color:COLORS) expected.add("prism_lights:"+color+"_"+family);
            expected.add("prism_lights:firework_launcher");
            if(!actual.equals(expected)) throw new AssertionError("Creative tab order differs from requested families/palette: "+actual);
            log("Creative order PASS: 81 entries");
            client.getServer().execute(()->{
                var server=client.getServer();var world=server.getOverworld();
                for(String id:expected) if(server.getRecipeManager().get(new Identifier(id)).isEmpty())
                    throw new AssertionError("Missing loaded recipe: "+id);
                // Verify normal gallery, hanging lanterns, supported torches and functional redstone.
                for(int i=0;i<COLORS.length;i++) {
                    expect(world.getBlockState(new BlockPos(-15+i*2,-56,22)).getBlock(),COLORS[i]+"_prism");
                    expect(world.getBlockState(new BlockPos(32+i%8*3,-55,i<8?-23:-14)).getBlock(),COLORS[i]+"_lantern");
                    expect(world.getBlockState(new BlockPos(i<8?-42:-37,-57,-23+i%8*5)).getBlock(),COLORS[i]+"_torch");
                    var prism=new BlockPos(-25+i*3,-56,40);var lever=new BlockPos(-25+i*3,-57,39);
                    expect(world.getBlockState(prism).getBlock(),COLORS[i]+"_redstone_prism");
                    if(!world.getBlockState(lever).isOf(Blocks.LEVER)) throw new AssertionError("Redstone lever missing: "+COLORS[i]);
                    // Notify neighbors through the native lever interaction, then restore off.
                    ((LeverBlock)Blocks.LEVER).togglePower(world.getBlockState(lever),world,lever);
                    if(!world.getBlockState(prism).get(dev.prismlights.PrismBlock.ACTIVE))
                        throw new AssertionError("Exhibit lever did not power prism: "+COLORS[i]);
                    ((LeverBlock)Blocks.LEVER).togglePower(world.getBlockState(lever),world,lever);
                    if(world.getBlockState(prism).get(dev.prismlights.PrismBlock.ACTIVE))
                        throw new AssertionError("Exhibit lever did not turn prism off: "+COLORS[i]);
                }
                var player=server.getPlayerManager().getPlayer(client.player.getUuid());
                player.getAbilities().flying=true;player.sendAbilitiesUpdate();
                log("Loaded recipes PASS: 81; gallery blocks/torch support/lanterns/16 redstone switches PASS");
                verified=true;
            });
        }
        if(!verified || client.isPaused()) return;
        client.player.getAbilities().flying=true;
        int current=++ticks;
        if(current==10) press(client,-6);
        if(current==20) {checkControl(client,"day");press(client,-2);}
        if(current==30) {checkControl(client,"night");press(client,6);}
        if(current==40) {checkControl(client,"rain");press(client,2);}
        if(current==50) checkControl(client,"clear");
        if(current<=120) return;
        int phase=(current-121)/100,step=(current-121)%100;
        if(phase>=VIEWS.length) {
            if(step==0) client.getServer().execute(()->{
                var server=client.getServer();
                server.getCommandManager().executeWithPrefix(server.getCommandSource(),"tp Player 0 -59 -35 0 2");
                var player=server.getPlayerManager().getPlayer(client.player.getUuid());
                player.getAbilities().flying=false;player.sendAbilitiesUpdate();
                server.save(false,true,true);
            });
            if(step==35) {
                client.options.hudHidden=false;client.options.write();
                try {Files.writeString(client.runDirectory.toPath().resolve("showcase-qa-complete.txt"),"81 creative entries; 81 loaded recipes; original showcase families placed; smooth lights verified separately by DesignValidation; 16 redstone levers; 4 entrance buttons; "+NAMES.length+" real screenshots.\n");}
                catch(Exception error){throw new IllegalStateException(error);}
                log("Showcase QA COMPLETE");client.scheduleStop();
            }
            return;
        }
        var view=VIEWS[phase];
        if(step==0) client.getServer().execute(()->{
            var server=client.getServer();
            server.getCommandManager().executeWithPrefix(server.getCommandSource(),"tp Player "+view[0]+" "+view[1]+" "+view[2]+" "+view[3]+" "+view[4]);
        });
        client.player.setYaw((float)view[3]);client.player.prevYaw=(float)view[3];
        client.player.setPitch((float)view[4]);client.player.prevPitch=(float)view[4];
        if(step==80) {
            if(client.player.getPos().squaredDistanceTo(view[0],view[1],view[2])>0.2)
                throw new AssertionError("Showcase camera moved unexpectedly: "+NAMES[phase]);
            NightColorValidation.verifyPack("ComplementaryUnbound_r5.9.3.zip");
            ScreenshotRecorder.saveScreenshot(client.runDirectory,"showcase-"+NAMES[phase]+".png",client.getFramebuffer(),message->log(message.getString()));
            log("Captured "+NAMES[phase]);
        }
    }
    private static void expect(Block actual,String name) {
        if(actual!=Registries.BLOCK.get(PrismLights.id(name))) throw new AssertionError("Showcase block missing: "+name);
    }
    private static void press(MinecraftClient client,int x) {
        client.getServer().execute(()->{
            var server=client.getServer();var world=server.getOverworld();var pos=new BlockPos(x,-59,-39);
            if(!world.getBlockState(pos).isOf(Blocks.STONE_BUTTON)) throw new AssertionError("Entrance button missing: "+x);
            ((ButtonBlock)Blocks.STONE_BUTTON).powerOn(world.getBlockState(pos),world,pos);
        });
    }
    private static void checkControl(MinecraftClient client,String mode) {
        client.getServer().execute(()->{
            var world=client.getServer().getOverworld();
            boolean valid=switch(mode) {
                case "day" -> world.getTimeOfDay()%24000==6000;
                case "night" -> world.getTimeOfDay()%24000==18000;
                case "rain" -> world.getLevelProperties().isRaining();
                case "clear" -> !world.getLevelProperties().isRaining();
                default -> false;
            };
            if(!valid) throw new AssertionError("Entrance control failed: "+mode);
            log("Entrance control PASS: "+mode);
        });
    }
    private static void log(String value) {LoggerFactory.getLogger("Prism Lights").info(value);}
}
