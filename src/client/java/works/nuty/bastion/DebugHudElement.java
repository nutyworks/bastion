package works.nuty.bastion;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.NonNull;

import java.util.Deque;
import java.util.List;

public class DebugHudElement implements HudElement {
    public static final int LINE_HEIGHT = 11;

    @Override
    public void render(@NonNull GuiGraphics drawContext, @NonNull DeltaTracker tickCounter) {
        if (Bastion.paused) {
            Minecraft client = Minecraft.getInstance();
            final int width = client.getWindow().getGuiScaledWidth();

            int x = 10;
            int y = 10;

            final Deque<Bastion.CallStackEntry> callstack = Bastion.callstack;

            for (Bastion.CallStackEntry entry : callstack.reversed()) {
                final int depth = entry.depth();
                final Bastion.InitialSource initialSource = entry.initialSource();
                final Component command = entry.command();

                final List<FormattedCharSequence> lines = client.font.split(command, width - 20);

                drawContext.fill(x - 5, y - 5, width - 5, y + 24 + LINE_HEIGHT * (lines.size() - 1), 0x80000000);
                //noinspection DataFlowIssue; GOLD.getColor() and RED.getColor() returns non-null
                drawContext.drawString(
                    client.font,
                    Component.translatable("bastion.debugger.breakpoint", initialSource.toComponent().withColor(ChatFormatting.GOLD.getColor())).withColor(ChatFormatting.RED.getColor()),
                    x, y, 0xFFFFFFFF, true
                );
                final Component depthText = Component.translatable("bastion.debugger.depth", depth);
                final int depthTextWidth = client.font.width(depthText);
                drawContext.drawString(client.font, depthText, width - 10 - depthTextWidth, y, 0xFFFFFFFF, true );
                for (int i = 0; i < lines.size(); i++) {
                    FormattedCharSequence line = lines.get(i);
                    drawContext.drawString(client.font, line, x, y + 12 + i * LINE_HEIGHT, 0xFFFFFFFF, true);
                }

                y += 24 + LINE_HEIGHT * (lines.size() - 1) + 7;
            }

            drawContext.drawString(client.font, Component.translatable("bastion.debugger.key_usage", Component.keybind("key.bastion.resume"), Component.keybind("key.bastion.step_over"), Component.keybind("key.bastion.step_into"), Component.keybind("key.bastion.step_into")), x, y - 5, 0xFFAAAAAA, true);
        }
    }
}
