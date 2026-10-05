package dev.prismlights.client;

import dev.prismlights.*;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.fluid.Fluids;
import org.joml.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import java.nio.*;
import java.nio.file.*;
import org.slf4j.LoggerFactory;

/** Real native interaction and framebuffer QA, restricted to disposable world copies. */
public final class CandleRevisionValidation {
    private static final boolean ACTIVE=Boolean.getBoolean("prism.candleTest") && Boolean.getBoolean("prism.qaRuntime");
    private static final String[] COLORS="white orange magenta light_blue yellow lime pink gray light_gray cyan purple blue brown green red black".split(" ");
    private static final String[] PACKS=ColorResponseValidation.renderers();
    private static final String[] MODES={"day-on","day-off","day-fallback","night-on","night-off","night-fallback","day-uncovered-on","day-uncovered-off"};
    private static final double[][] POINTS={{1996.5,146.01,2.5},{2004.5,146.01,2.5},{1993.5,146.01,5.5},{2007.5,146.01,5.5}};
    private static final ByteBuffer pixels=BufferUtils.createByteBuffer(7*7*4);
    private static int ticks,lastCapture=-1,lightingWait;
    private static volatile boolean ready;
    private static boolean flameView;
    private static float flamePitch=12;
    private static volatile Throwable failure;
    private static long buildsBefore,uploadsBefore;
    public static void prepareCamera(MinecraftClient c) {
        if(!ACTIVE || !ready || c.player==null)return;
        float pitch=flameView?flamePitch:35;
        c.player.setYaw(0);c.player.prevYaw=0;c.player.setPitch(pitch);c.player.prevPitch=pitch;
    }
    public static void tick(MinecraftClient c) {
        if(!ACTIVE)return;
        require(c.runDirectory.getName().startsWith("run-candle-validation-"),"isolated QA directory");
        if(failure!=null)throw new AssertionError("Candle server verification failed",failure);
        if(c.player==null || c.getServer()==null || c.isPaused())return;
        c.options.pauseOnLostFocus=false;c.options.hudHidden=true;
        if(ticks==159 && (c.world.getLightLevel(net.minecraft.world.LightType.SKY,new BlockPos(1996,146,2))==15
            || !c.world.getBlockState(new BlockPos(1996,154,2)).isOf(Blocks.OAK_LEAVES))) {
            if(++lightingWait%20==1)log("Waiting for fresh-world skylight: ground="+c.world.getLightLevel(net.minecraft.world.LightType.SKY,new BlockPos(1996,146,2))+" roof="+c.world.getBlockState(new BlockPos(1996,154,2)));
            if(lightingWait>300)throw new AssertionError("Fresh-world skylight never reached shaded fixture");
            return;
        }
        int current=++ticks;
        if(current==10)c.getServer().execute(()->{
            try {
                var s=c.getServer();var w=s.getOverworld();var p=s.getPlayerManager().getPlayer(c.player.getUuid());
                for(int x=1980>>4;x<=2020>>4;x++)for(int z=-12>>4;z<=18>>4;z++)w.getChunk(x,z);
                command(c,"gamemode creative Player","gamerule doMobSpawning false","gamerule doDaylightCycle false","weather clear",
                    "fill 1980 145 -12 2020 158 18 air","fill 1980 145 -12 2020 145 18 white_concrete",
                    "fill 1980 145 -12 2020 145 3 grass_block");
                for(int color=0;color<16;color++) {
                    var b=(ColoredCandleBlock)PrismLights.NEW_BLOCKS.get(COLORS[color]+"_candle");
                    var pos=new BlockPos(1982+color,146,-10);var hit=new BlockHitResult(Vec3d.ofCenter(pos),Direction.UP,pos,false);
                    require(s.getRecipeManager().get(PrismLights.id(COLORS[color]+"_candle")).isPresent(),"recipe "+COLORS[color]);
                    w.setBlockState(pos,b.getDefaultState());
                    require(w.getBlockEntity(pos) instanceof PrismBlockEntity,"candle emitter entity");
                    require(w.getBlockState(pos).getLuminance()==0 && !b.prismActive(w.getBlockState(pos)),"unlit emission");
                    for(int count=1;count<=4;count++) {
                        if(count>1) {
                            p.setStackInHand(Hand.MAIN_HAND,new ItemStack(b));
                            var ctx=new ItemUsageContext(p,Hand.MAIN_HAND,hit);
                            require(w.getBlockState(pos).canReplace(new ItemPlacementContext(ctx)),"stackable candle "+count);
                            b.asItem().useOnBlock(ctx);
                        }
                        var state=w.getBlockState(pos);require(state.get(CandleBlock.CANDLES)==count,"native placement count");
                        p.setStackInHand(Hand.MAIN_HAND,new ItemStack(Items.FLINT_AND_STEEL));
                        Items.FLINT_AND_STEEL.useOnBlock(new ItemUsageContext(p,Hand.MAIN_HAND,hit));
                        state=w.getBlockState(pos);
                        require(state.get(CandleBlock.LIT) && state.getLuminance()==3*count && b.prismEmission(state)==3*count,"native ignition and brightness");
                        var drops=Block.getDroppedStacks(state,w,pos,w.getBlockEntity(pos));
                        require(drops.size()==1 && drops.get(0).isOf(b.asItem()) && drops.get(0).getCount()==count,"own count loot "+COLORS[color]);
                        p.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);
                        state.onUse(w,p,Hand.MAIN_HAND,hit);
                        require(!w.getBlockState(pos).get(CandleBlock.LIT),"empty-hand extinguish");
                    }
                    p.setStackInHand(Hand.MAIN_HAND,new ItemStack(b));
                    require(!w.getBlockState(pos).canReplace(new ItemPlacementContext(p,Hand.MAIN_HAND,p.getMainHandStack(),hit)),"maximum four candles");
                    w.setBlockState(pos,w.getBlockState(pos).with(CandleBlock.LIT,true));
                    require(b.tryFillWithFluid(w,pos,w.getBlockState(pos),Fluids.WATER.getStill(false)),"waterlogging");
                    require(w.getBlockState(pos).get(CandleBlock.WATERLOGGED) && !w.getBlockState(pos).get(CandleBlock.LIT) && !b.prismActive(w.getBlockState(pos)),"water extinguishes RGB");
                    w.setBlockState(pos,Blocks.CAKE.getDefaultState());
                    p.setStackInHand(Hand.MAIN_HAND,new ItemStack(b));
                    w.getBlockState(pos).onUse(w,p,Hand.MAIN_HAND,hit);
                    var cake=PrismLights.NEW_BLOCKS.get(COLORS[color]+"_candle_cake");
                    require(w.getBlockState(pos).isOf(cake) && w.getBlockEntity(pos) instanceof PrismBlockEntity,"native candle cake placement");
                    p.setStackInHand(Hand.MAIN_HAND,new ItemStack(Items.FLINT_AND_STEEL));
                    Items.FLINT_AND_STEEL.useOnBlock(new ItemUsageContext(p,Hand.MAIN_HAND,hit));
                    require(w.getBlockState(pos).getLuminance()==3,"cake flame brightness");
                    p.getHungerManager().setFoodLevel(10);
                    // 1.20.1's single onUse callback reads the actual held item;
                    // eating with the lighter held intentionally returns PASS.
                    p.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);
                    w.getBlockState(pos).onUse(w,p,Hand.MAIN_HAND,hit);
                    require(w.getBlockState(pos).isOf(Blocks.CAKE) && w.getBlockState(pos).get(CakeBlock.BITES)==1,"native candle cake eating");
                    w.setBlockState(pos,b.getDefaultState().with(CandleBlock.CANDLES,1+color%4).with(CandleBlock.LIT,true));
                }
                command(c,"fill 1988 154 -2 1999 156 12 oak_leaves[persistent=true]",
                    "setblock 1997 146 3 prism_lights:red_prism","setblock 2003 146 3 prism_lights:red_prism",
                    "setblock 2000 149 -7 barrier","tp Player 2000 150 -7 0 35","time set noon");
                p.getAbilities().flying=true;p.sendAbilitiesUpdate();p.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);
                Files.writeString(c.runDirectory.toPath().resolve("candle-pixels.csv"),"renderer,mode,point,r,g,b,sky\n");
                ready=true;log("16 colors: real placement 1-4, ignition 3/6/9/12, own count drops, extinguish, waterlogging, cake placement/eating PASS");
            }catch(Throwable e){failure=e;}
        });
        if(!ready || current<160)return;
        if(current==160)RecipeViewerValidation.verify();
        int phase=(current-160)/100,step=(current-160)%100;
        if(phase>=PACKS.length*MODES.length) {
            int end=current-160-PACKS.length*MODES.length*100;
            if(end==0) {
                flameView=true;GpuLightVolume.setEnabled(true);SkyExposure.qaFallback=false;
                c.getServer().execute(()->command(c,"time set midnight","tp Player 1989.5 148 -23 0 12"));
            }
            if(end==60) {
                ScreenshotRecorder.saveScreenshot(c.runDirectory,"candles-sixteen-flames.png",c.getFramebuffer(),message->log(message.getString()));
                flamePitch=30;c.getServer().execute(()->command(c,"tp Player 1993.5 146.7 -13 0 30"));
            }
            if(end==110) {
                ScreenshotRecorder.saveScreenshot(c.runDirectory,"candles-blue-close.png",c.getFramebuffer(),message->log(message.getString()));
                log("Candle revision QA COMPLETE");c.scheduleStop();
            }
            return;
        }
        int mode=phase%MODES.length;
        if(step==0) {
            if(mode==0 && !PACKS[phase/MODES.length].equals("Vanilla"))NightColorValidation.reload(PACKS[phase/MODES.length]);
            c.getServer().execute(()->{
                command(c,"time set "+(mode<3 || mode>=6?"noon":"midnight"));
                if(mode==0)command(c,"fill 1988 154 -2 1999 156 12 oak_leaves[persistent=true]");
                if(mode==6)command(c,"fill 1988 154 -2 1999 156 12 air");
            });
        }
        SkyExposure.qaFallback=mode==2 || mode==5;
        GpuLightVolume.setEnabled(mode!=1 && mode!=4 && mode!=7);
        if(step==50){buildsBefore=SkyExposure.builds();uploadsBefore=SkyExposure.uploads();}
        if(step==95){log(PACKS[phase/MODES.length]+" "+MODES[mode]+" steady sky builds="+(SkyExposure.builds()-buildsBefore)+" uploads="+(SkyExposure.uploads()-uploadsBefore)+" lastBuildMs="+SkyExposure.lastBuildNanos()/1e6);}
    }
    public static void frame(MinecraftClient c,Matrix4f view,Matrix4f projection) {
        if(!ACTIVE || !ready || flameView || ticks<160 || ticks==lastCapture || (ticks-160)%100!=85)return;
        lastCapture=ticks;int phase=(ticks-160)/100;String pack=PACKS[phase/MODES.length],mode=MODES[phase%MODES.length];
        if(!pack.equals("Vanilla"))NightColorValidation.verifyPack(pack);
        var fb=c.getFramebuffer();var camera=c.gameRenderer.getCamera().getPos();int old=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,fb.fbo);
        try(LightingValidation.PixelPackState ignored=new LightingValidation.PixelPackState()) {
            var rows=new StringBuilder();
            for(int i=0;i<POINTS.length;i++) {
                var p=POINTS[i];var clip=new Vector4f((float)(p[0]-camera.x),(float)(p[1]-camera.y),(float)(p[2]-camera.z),1);view.transform(clip);projection.transform(clip);
                int x=(int)((clip.x/clip.w*.5+.5)*fb.textureWidth),y=(int)((clip.y/clip.w*.5+.5)*fb.textureHeight);
                require(clip.w>0 && x>=3 && y>=3 && x<fb.textureWidth-3 && y<fb.textureHeight-3,"visible probe point");
                pixels.clear();GL11.glReadPixels(x-3,y-3,7,7,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
                double[] rgb=new double[3];for(int n=0;n<49;n++)for(int ch=0;ch<3;ch++)rgb[ch]+=pixels.get(n*4+ch)&255;
                int sky=c.world.getLightLevel(net.minecraft.world.LightType.SKY,BlockPos.ofFloored(p[0],p[1],p[2]));
                require(i%2==0 && phase%MODES.length<6?sky<15:sky==15,"shaded/open probe skylight "+sky);
                var target=new Vec3d(p[0],p[1],p[2]);
                var hit=c.world.raycast(new net.minecraft.world.RaycastContext(camera,target.add(target.subtract(camera).normalize().multiply(.1)),net.minecraft.world.RaycastContext.ShapeType.OUTLINE,net.minecraft.world.RaycastContext.FluidHandling.NONE,c.player));
                require(hit.getBlockPos().equals(BlockPos.ofFloored(p[0],145,p[2])),"ground probe must hit the intended floor, got "+hit.getBlockPos());
                rows.append(pack).append(',').append(mode).append(',').append(i).append(',').append(rgb[0]/49).append(',').append(rgb[1]/49).append(',').append(rgb[2]/49).append(',').append(sky).append('\n');
            }
            Files.writeString(c.runDirectory.toPath().resolve("candle-pixels.csv"),rows,StandardOpenOption.APPEND);
        }catch(Exception e){throw new IllegalStateException(e);}finally{GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,old);}
        ScreenshotRecorder.saveScreenshot(c.runDirectory,pack.replace(".zip","")+"-"+mode+".png",fb,message->log(message.getString()));
    }
    private static void command(MinecraftClient c,String... commands){var s=c.getServer();for(var command:commands)s.getCommandManager().executeWithPrefix(s.getCommandSource().withLevel(4),command);}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void log(String text){LoggerFactory.getLogger("Prism Lights").info("Candle QA: {}",text);}
}
