package dev.prismlights.client;

import dev.prismlights.*;
import dev.prismlights.platform.PlatformServices;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.Arrays;

/** Opt-in QA in a separate copy: assets, loaded recipes, light identity and real hand rendering. */
final class DesignValidation {
    private static final boolean ACTIVE=(PlatformServices.get().isDevelopmentEnvironment() || Boolean.getBoolean("prism.qaRuntime")) && Boolean.getBoolean("prism.designTest");
    private static final String[] COLORS={"white","orange","magenta","light_blue","yellow","lime","pink","gray","light_gray","cyan","purple","blue","brown","green","red","black"};
    private static final String[] PACKS=ColorResponseValidation.renderers();
    private static int ticks;
    private static volatile boolean verified;
    private static float[] conventional;
    private static int ox,oy,oz,sx,sy,sz;
    private static long recoilBefore, mainBefore, offBefore;
    private static boolean serverShotsVerified;
    private static int startupTicks;
    private static boolean complete;
    private DesignValidation() {}

    static void tick(MinecraftClient client) {
        if(!ACTIVE || complete) return;
        if(!java.util.Set.of("run-design-validation","run-production-validation").contains(client.runDirectory.getName()) && !client.runDirectory.getName().startsWith("run-optimization-validation-")) throw new IllegalStateException("Design QA must run in its isolated copy");
        if(client.player==null || client.getServer()==null) {
            if(++startupTicks==40) {
                LoggerFactory.getLogger("Prism Lights").info("Design QA startup: screen={} directory={}",client.currentScreen==null?"null":client.currentScreen.getClass().getName(),client.runDirectory);
                ScreenshotRecorder.saveScreenshot(client.runDirectory,"design-startup.png",client.getFramebuffer(),message->LoggerFactory.getLogger("Prism Lights").info("Design QA startup capture: {}",message.getString()));
            }
            return;
        }
        if(client.isPaused()) return;
        client.options.pauseOnLostFocus=false;
        for(var key:new net.minecraft.client.option.KeyBinding[]{client.options.forwardKey,client.options.backKey,client.options.leftKey,client.options.rightKey,client.options.jumpKey,client.options.sneakKey,client.options.sprintKey})key.setPressed(false);

        client.options.setPerspective(Perspective.FIRST_PERSON);
        int current=++ticks;
        if(current==10) {
            ItemGroups.updateDisplayContext(client.world.getEnabledFeatures(),true,client.world.getRegistryManager());
            var expected=new ArrayList<String>();
            for(String family:new String[]{"prism","smooth_light","redstone_prism","redstone_smooth_light","lantern","torch","candle"})
                for(String color:COLORS) expected.add("prism_lights:"+color+"_"+family);
            expected.add("prism_lights:firework_launcher");
            var actual=Registries.ITEM_GROUP.get(PrismLights.id("lights")).getDisplayStacks().stream()
                .map(s->Registries.ITEM.getId(s.getItem()).toString()).toList();
            require(actual.equals(expected),"113 creative entries in family/color order");
            client.getServer().execute(()->{
                var server=client.getServer();var world=server.getOverworld();
                // Load the entire remote fixture before commands such as fill/setblock.
                // Otherwise Minecraft rejects their coordinates while tp still succeeds.
                for(int x=1980>>4;x<=2020>>4;x++) for(int z=-12>>4;z<=18>>4;z++) world.getChunk(x,z);
                for(String id:expected) require(server.getRecipeManager().get(new net.minecraft.util.Identifier(id)).isPresent(),"Loaded recipe "+id);
                for(int i=0;i<COLORS.length;i++) {
                    var block=PrismLights.NEW_BLOCKS.get(COLORS[i]+"_smooth_light");
                    require(block instanceof PrismBlock && ((PrismBlock)block).color==PrismLights.BLOCKS.get(COLORS[i]).color,"Smooth palette "+COLORS[i]);
                    var pos=new BlockPos(1988+i,120,4);
                    world.setBlockState(pos,block.getDefaultState());
                    require(world.getBlockEntity(pos) instanceof PrismBlockEntity,"Smooth emitter block entity "+COLORS[i]);
                    require(world.getBlockState(pos).getLuminance()==15,"Smooth native light "+COLORS[i]);
                    world.setBlockState(pos,block.getDefaultState().with(PrismBlock.ACTIVE,false));
                    require(world.getBlockState(pos).getLuminance()==0,"Smooth off state "+COLORS[i]);
                    var powered=PrismLights.NEW_BLOCKS.get(COLORS[i]+"_redstone_smooth_light");
                    world.setBlockState(pos,powered.getDefaultState());
                    require(!world.getBlockState(pos).get(PrismBlock.ACTIVE),"Redstone smooth starts off");
                    world.setBlockState(pos.down(),Blocks.REDSTONE_BLOCK.getDefaultState());
                    require(world.getBlockState(pos).get(PrismBlock.ACTIVE) && world.getBlockState(pos).getLuminance()==15,"Redstone smooth powers on "+COLORS[i]);
                    require(world.getBlockEntity(pos) instanceof PrismBlockEntity,"Redstone smooth has emitter "+COLORS[i]);
                    world.setBlockState(pos.down(),Blocks.AIR.getDefaultState());
                    require(!world.getBlockState(pos).get(PrismBlock.ACTIVE) && world.getBlockState(pos).getLuminance()==0,"Redstone smooth powers off "+COLORS[i]);
                    world.setBlockState(pos,Blocks.AIR.getDefaultState());
                }
                command(client,"gamemode creative Player","gamerule doMobSpawning false","gamerule doDaylightCycle false",
                    "weather clear","time set midnight","fill 1980 99 -12 2020 115 18 air",
                    "fill 1980 99 -12 2020 99 18 white_concrete","fill 1980 100 14 2020 106 14 white_concrete",
                    "setblock 2000 103 -6 barrier","tp Player 2000 104 -6 0 20");
                place(client,"prism");
                var player=server.getPlayerManager().getPlayer(client.player.getUuid());
                player.setVelocity(net.minecraft.util.math.Vec3d.ZERO);
                player.getAbilities().flying=true;player.sendAbilitiesUpdate();
                validateServerShots(client);
                verified=true;log("16 smooth/redstone smooth on-off emitters and 97 loaded recipes PASS");
            });
        }
        if(current<140 || !verified) return;
        client.player.getAbilities().flying=true;
        int phase=(current-140)/160,step=(current-140)%160;
        if(phase>=PACKS.length) {
            require(serverShotsVerified,"Server launcher tests completed");
            if(StressWorldValidation.tick(client)) {
                complete=true;
                log("Design QA COMPLETE");
                // Match the pause menu: stop the integrated server before waiting for disconnect.
                client.world.disconnect();
                client.disconnect();
                client.scheduleStop();
            }
            return;
        }
        if(step>=140 && step<149) client.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
        if(step==0) {
            if(PlatformServices.get().isModLoaded("iris")) NightColorValidation.reload(PACKS[phase]);
            client.getServer().execute(()->place(client,"prism"));
        }
        if(step==35) {
            var volume=GpuLightVolume.current();
            require(volume!=null,"Published conventional volume");
            require(client.player.getPos().squaredDistanceTo(2000.5,104,-5.5)<0.1,"Camera remains at fixture");
            require(PrismClient.lightField().selectedCount()==16,"All 16 isolated sources selected");
            require(client.world.getBlockState(new BlockPos(2000,99,0)).isOf(Blocks.WHITE_CONCRETE),"Fixture floor loaded");
            conventional=Arrays.copyOf(volume.rgba,volume.rgba.length);
            ox=volume.x;oy=volume.y;oz=volume.z;sx=volume.width;sy=volume.height;sz=volume.depth;
            client.getServer().execute(()->place(client,"smooth_light"));
        }
        if(step==55) {
            if(PlatformServices.get().isModLoaded("iris")) NightColorValidation.verifyPack(PACKS[phase]);
            var volume=GpuLightVolume.current();
            require(volume.x==ox && volume.y==oy && volume.z==oz && volume.width==sx && volume.height==sy && volume.depth==sz
                && Arrays.equals(volume.rgba,conventional),"Smooth/conventional complete light field identity");
            client.options.hudHidden=true;
            capture(client,phase,"smooth-gallery");
            log(PACKS[phase]+" smooth/prism full-volume identity PASS");
            client.getServer().execute(()->place(client,"redstone_smooth_light"));
            client.getServer().execute(()->{
                var player=client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
                player.setStackInHand(Hand.MAIN_HAND,new ItemStack(PrismLights.FIREWORK_LAUNCHER));
                player.setStackInHand(Hand.OFF_HAND,new ItemStack(PrismLights.FIREWORK_LAUNCHER));
            });
        }
        if(step==65) {
            var volume=GpuLightVolume.current();
            require(volume.x==ox && volume.y==oy && volume.z==oz && volume.width==sx && volume.height==sy && volume.depth==sz
                && Arrays.equals(volume.rgba,conventional),"Powered smooth/conventional complete light field identity");
            capture(client,phase,"redstone-smooth-gallery");
            log(PACKS[phase]+" redstone smooth/prism full-volume identity PASS");
        }
        if(step==145) {validateRemotePose(client);capture(client,phase,"third-person-aim");}
        if(step==150) {
            RecipeViewerValidation.verify();
            client.setScreen(new net.minecraft.client.gui.screen.ingame.InventoryScreen(client.player));
        }
        if(step==154) capture(client,phase,"inventory-recipes");
        if(step==155) client.setScreen(null);
        client.player.setYaw(0);client.player.prevYaw=0;client.player.setPitch(20);client.player.prevPitch=20;
        if(step==75 || step==100 || step==125) {
            client.options.hudHidden=false;
            client.options.getMainArm().setValue(step==125?Arm.LEFT:Arm.RIGHT);
            client.player.setMainArm(step==125?Arm.LEFT:Arm.RIGHT);
            Hand hand=step==100?Hand.OFF_HAND:Hand.MAIN_HAND;
            recoilBefore=LauncherRecoil.renderedFrames();
            mainBefore=LauncherRecoil.renderedFrames(Hand.MAIN_HAND);offBefore=LauncherRecoil.renderedFrames(Hand.OFF_HAND);
            require(client.interactionManager.interactItem(client.player,hand).isAccepted(),"Launcher accepted trigger "+hand);
            require(!client.player.handSwinging,"Launcher does not start Vanilla swing");
            require(!client.interactionManager.interactItem(client.player,hand).isAccepted(),"Cooldown rejects repeated trigger");
        }
        if(step==78 || step==103 || step==128) {
            require(LauncherRecoil.renderedFrames()>recoilBefore,"Native first-person recoil rendered");
            require(LauncherRecoil.renderedFrames(Hand.MAIN_HAND)>mainBefore && LauncherRecoil.renderedFrames(Hand.OFF_HAND)>offBefore,"Both firing hands animate");
            capture(client,phase,step==78?"recoil-right":step==103?"recoil-offhand":"recoil-left");
            log(PACKS[phase]+" recoil native hand/cooldown PASS step "+step);
        }
        if(step==86 || step==111 || step==136) {
            long frames=LauncherRecoil.renderedFrames();
            require(LauncherRecoilCurve.sample(8)==0,"Recoil settled before cooldown");
            capture(client,phase,step==86?"rest-right":step==111?"rest-offhand":"rest-left");
            recoilBefore=frames;
        }
        if(step==88 || step==113 || step==138)
            require(LauncherRecoil.renderedFrames()==recoilBefore,"Recoil ended without residual transform");
    }

    private static void place(MinecraftClient client,String family) {
        var world=client.getServer().getOverworld();
        for(int i=0;i<COLORS.length;i++) {
            var block=family.equals("prism")?PrismLights.BLOCKS.get(COLORS[i]):PrismLights.NEW_BLOCKS.get(COLORS[i]+"_"+family);
            var pos=new BlockPos(1989+i%8*3,100,4+i/8*6);
            world.setBlockState(pos.down(),Blocks.WHITE_CONCRETE.getDefaultState());
            world.setBlockState(pos,block.getDefaultState());
            if(family.equals("redstone_smooth_light")) world.setBlockState(pos.down(),Blocks.REDSTONE_BLOCK.getDefaultState());
        }
    }
    private static void validateServerShots(MinecraftClient client) {
        var world=client.getServer().getOverworld();
        var player=client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
        boolean differentColors=false;
        for(int mode=0;mode<6;mode++) {
            boolean dual=mode>=2;
            Hand trigger=mode%2==0?Hand.MAIN_HAND:Hand.OFF_HAND;
            player.setMainArm(mode>=4?Arm.LEFT:Arm.RIGHT);
            player.setStackInHand(Hand.MAIN_HAND,dual || trigger==Hand.MAIN_HAND?new ItemStack(PrismLights.FIREWORK_LAUNCHER):ItemStack.EMPTY);
            player.setStackInHand(Hand.OFF_HAND,dual || trigger==Hand.OFF_HAND?new ItemStack(PrismLights.FIREWORK_LAUNCHER):ItemStack.EMPTY);
            player.getItemCooldownManager().set(PrismLights.FIREWORK_LAUNCHER,0);
            var before=world.getEntitiesByType(PrismLights.FIREWORK_ENTITY,e->true).stream().map(e->e.getId()).toList();
            require(PrismLights.FIREWORK_LAUNCHER.use(world,player,trigger).getResult().isAccepted(),"Server trigger accepted mode "+mode);
            var created=world.getEntitiesByType(PrismLights.FIREWORK_ENTITY,e->!before.contains(e.getId()));
            require(created.size()==(dual?2:1),"Exactly one/two server shots mode "+mode);
            if(dual) {
                var a=created.get(0);var b=created.get(1);
                require(Math.abs(a.getPos().distanceTo(b.getPos())-0.56)<1e-5,"Distinct muzzle positions");
                require(a.getVelocity().subtract(b.getVelocity()).length()>0.08,"Distinct shot angles");
                for(var shot:created) {
                    double yawRadians=Math.toRadians(player.getYaw());
                    var right=new net.minecraft.util.math.Vec3d(-Math.cos(yawRadians),0,-Math.sin(yawRadians));
                    double side=shot.getPos().subtract(player.getEyePos()).dotProduct(right)>0?1:-1;
                    var velocity=shot.getVelocity();
                    double heading=Math.toDegrees(Math.atan2(-velocity.x,velocity.z));
                    double deviation=net.minecraft.util.math.MathHelper.wrapDegrees(heading-player.getYaw());
                    // Minecraft's projectile sin/cos table quantizes yaw by about 0.006 degrees.
                    require(Math.abs(deviation-side*5)<0.01,
                        "Physical left/right trajectory targets 5 degrees: mode="+mode+" side="+side+" actual="+deviation);
                }
                differentColors |= a.color()!=b.color();
            }
            require(!PrismLights.FIREWORK_LAUNCHER.use(world,player,trigger).getResult().isAccepted(),"Shared cooldown rejects duplicate use");
            require(world.getEntitiesByType(PrismLights.FIREWORK_ENTITY,e->!before.contains(e.getId())).size()==created.size(),"Cooldown does not create extra shots");
            created.forEach(e->e.discard());
        }
        require(differentColors,"Independent color draws across dual volleys");
        player.setMainArm(Arm.RIGHT);player.getItemCooldownManager().set(PrismLights.FIREWORK_LAUNCHER,0);
        serverShotsVerified=true;log("Server main/off/dual/right-left: count, separate origins, outward 5-degree trajectories, independent colors and shared cooldown PASS");
    }
    private static void validateRemotePose(MinecraftClient client) {
        var remote=new net.minecraft.client.network.OtherClientPlayerEntity(client.world,new com.mojang.authlib.GameProfile(java.util.UUID.fromString("19892692-8b84-4f94-9087-81ae4c18f832"),"PoseQA"));
        var model=new net.minecraft.client.render.entity.model.PlayerEntityModel<net.minecraft.entity.LivingEntity>(client.getEntityModelLoader().getModelPart(net.minecraft.client.render.entity.model.EntityModelLayers.PLAYER),false);
        for(Arm main:new Arm[]{Arm.RIGHT,Arm.LEFT}) for(int mode=0;mode<3;mode++) {
            remote.setMainArm(main);
            remote.setStackInHand(Hand.MAIN_HAND,mode!=1?new ItemStack(PrismLights.FIREWORK_LAUNCHER):ItemStack.EMPTY);
            remote.setStackInHand(Hand.OFF_HAND,mode!=0?new ItemStack(PrismLights.FIREWORK_LAUNCHER):ItemStack.EMPTY);
            model.setAngles(remote,0,0,0,15,10);
            for(Hand hand:Hand.values()) if(remote.getStackInHand(hand).isOf(PrismLights.FIREWORK_LAUNCHER)) {
                var arm=(hand==Hand.MAIN_HAND?main:main.getOpposite())==Arm.RIGHT?model.rightArm:model.leftArm;
                require(Math.abs(arm.pitch-(-(float)Math.PI/2+model.head.pitch))<1e-5,"Remote arm points forward at head pitch");
            }
        }
        log("Remote player model main/off/dual/right-left forward aiming PASS");
    }
    private static void command(MinecraftClient client,String... commands) {
        var server=client.getServer();
        for(String command:commands) server.getCommandManager().executeWithPrefix(server.getCommandSource(),command);
    }
    private static void capture(MinecraftClient client,int phase,String name) {
        ScreenshotRecorder.saveScreenshot(client.runDirectory,"design-"+PACKS[phase].replace('.','-')+"-"+name+".png",client.getFramebuffer(),message->log(message.getString()));
    }
    private static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
    private static void log(String text) { LoggerFactory.getLogger("Prism Lights").info("Design QA: {}",text); }
}
