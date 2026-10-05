package dev.frameprobe.mixin;
import net.minecraft.client.gui.screen.GameMenuScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(GameMenuScreen.class)
public interface PauseMenuAccess {
    @Invoker("disconnect") void frameprobe$saveAndQuit();
}
