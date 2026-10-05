package dev.prismlights;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Arm;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import java.util.List;

/** One accepted trigger fires both equipped launchers, using one shared cooldown. */
public final class FireworkLauncherItem extends Item {
    public static final int COOLDOWN_TICKS=8;
    public FireworkLauncherItem(Settings settings) { super(settings); }
    @Override public TypedActionResult<ItemStack> use(World world,PlayerEntity player,Hand hand) {
        ItemStack stack=player.getStackInHand(hand);
        if(player.getItemCooldownManager().isCoolingDown(this)) return TypedActionResult.fail(stack);
        boolean dual=player.getMainHandStack().isOf(this) && player.getOffHandStack().isOf(this);
        if(!world.isClient) {
            boolean fired=false;
            for(Hand firingHand:dual ? Hand.values() : new Hand[]{hand}) {
                var layout=LauncherShotLayout.forHand(player.getYaw(),player.getMainArm()==Arm.RIGHT,firingHand==Hand.OFF_HAND,dual);
                PrismFireworkEntity shot=new PrismFireworkEntity(PrismLights.FIREWORK_ENTITY,world);
                shot.setOwner(player);
                shot.setColor(MathHelper.hsvToRgb(world.random.nextFloat(),0.95f,1.0f));
                shot.setPosition(player.getEyePos().add(player.getRotationVec(1.0f).multiply(0.35))
                    .add(layout.offsetX(),-0.18,layout.offsetZ()));
                shot.setVelocity(player,player.getPitch(),layout.yaw(),0,1.2f,0);
                fired |= world.spawnEntity(shot);
            }
            if(!fired) return TypedActionResult.fail(stack);
            world.playSound(null,player.getX(),player.getY(),player.getZ(),
                SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH,SoundCategory.PLAYERS,0.7f,1.0f);
        }
        player.getItemCooldownManager().set(this,COOLDOWN_TICKS);
        // Accepted use without Minecraft's generic melee-style hand swing.
        // The client renderer applies a short recoil only after an accepted trigger.
        return TypedActionResult.consume(stack);
    }
    @Override public void appendTooltip(ItemStack stack,World world,List<Text> tooltip,TooltipContext context) {
        tooltip.add(Text.translatable("item.prism_lights.firework_launcher.tooltip"));
        tooltip.add(Text.translatable("item.prism_lights.firework_launcher.tooltip.dual"));
    }
}
