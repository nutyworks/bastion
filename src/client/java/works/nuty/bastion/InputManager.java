package works.nuty.bastion;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;

public class InputManager implements ClientTickEvents.EndTick {
    public static Identifier identifier = Identifier.fromNamespaceAndPath("bastion", "debugger");
    public KeyMapping breakpointKey;
    public KeyMapping resumeKey;
    public KeyMapping stepIntoKey;
    public KeyMapping stepOverKey;

    @Override
    public void onEndTick(Minecraft client) {
        if (client.player == null) return; if (Bastion.paused) {
            while (this.resumeKey.consumeClick()) {
                client.player.connection.sendCommand("bastion resume");
            }
            while (this.stepOverKey.consumeClick()) {
                client.player.connection.sendCommand("bastion stepover");
            }
            while (this.stepIntoKey.consumeClick()) {
                if (client.hasShiftDown()) {
                    client.player.connection.sendCommand("bastion stepout");
                } else {
                    client.player.connection.sendCommand("bastion stepinto");
                }
            }
        } else {
            this.resumeKey.consumeClick();
            this.stepIntoKey.consumeClick();
            this.stepOverKey.consumeClick();
        }
        while (breakpointKey.consumeClick()) {
            HitResult block = client.player.pick(20.0, 0.0F, false);
            if (block.getType() == HitResult.Type.BLOCK) {
                BlockPos pos = ((BlockHitResult)block).getBlockPos();
                client.player.connection.sendCommand("bastion breakpoint block %d %d %d".formatted(pos.getX(), pos.getY(), pos.getZ()));
            }
        }
    }

    public void registerKeyMappings() {
        final KeyMapping.Category category = new KeyMapping.Category(identifier);
        this.breakpointKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.bastion.breakpoint",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F10,
            category
        ));
        this.resumeKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.bastion.resume",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F7,
            category
        ));
        this.stepOverKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.bastion.step_over",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F8,
            category
        ));
        this.stepIntoKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.bastion.step_into",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F9,
            category
        ));
    }
}
