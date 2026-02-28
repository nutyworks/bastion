package works.nuty.bastion.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.brigadier.context.ContextChain;
import com.mojang.brigadier.context.StringRange;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.commands.execution.ChainModifiers;
import net.minecraft.commands.execution.ExecutionContext;
import net.minecraft.commands.execution.Frame;
import net.minecraft.commands.execution.tasks.BuildContexts;
import net.minecraft.network.chat.Component;
import org.apache.commons.lang3.tuple.Triple;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import works.nuty.bastion.Bastion;

import java.util.List;

@Mixin(BuildContexts.class)
public class BuildContextsMixin<T extends ExecutionCommandSource<T>> {
    @Shadow
    @Final
    public String commandInput;

    @Inject(
        method = "execute",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/brigadier/context/ContextChain;getTopContext()Lcom/mojang/brigadier/context/CommandContext;"
        ),
        slice = @Slice(
            to = @At(
                value = "INVOKE",
                target = "Ljava/util/List;isEmpty()Z"
            )
        )
    )
    void bastion$executeBeforeApplyModifier(
        T originalSource, List<T> initialSources, ExecutionContext<T> context, Frame frame, ChainModifiers initialModifiers, CallbackInfo ci,
        @Local(name = "currentSources") List<T> currentSources,
        @Local(name = "currentStage") ContextChain<T> currentStage
    ) {
        pauseIfNeeded(frame, currentStage, currentSources);
    }

    @Inject(
        method = "execute",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/List;isEmpty()Z"
        )
    )
    void bastion$executeAfterModifiers(
        T originalSource, List<T> initialSources, ExecutionContext<T> context, Frame frame, ChainModifiers initialModifiers, CallbackInfo ci,
        @Local(name = "currentStage") ContextChain<T> currentStage,
        @Local(name = "currentSources") List<T> currentSources
    ) {
        pauseIfNeeded(frame, currentStage, currentSources);
    }

    @Unique
    private void pauseIfNeeded(final Frame frame, final ContextChain<T> currentStage, final List<T> currentSources) {
        if (Bastion.funcBreakpoints.isEmpty() && Bastion.blockBreakpoints.isEmpty()) return;

        final StringRange range = currentStage.getTopContext().getRange();
        final Component cmd = Component.literal(this.commandInput.substring(0, range.getStart())).withColor(0xFFAAAAAA)
            .append(Component.literal(range.get(this.commandInput)).withColor(0xFFFFFFFF))
            .append(Component.literal(this.commandInput.substring(range.getEnd())));

        while (!Bastion.callstack.isEmpty() && frame.depth() <= Bastion.callstack.peek().depth()) {
            Bastion.callstack.pop();
        }

        final Bastion.InitialSource initialSource = Bastion.getInitialSource((BuildContexts<?>) (Object) this);
        Bastion.callstack.push(Bastion.CallStackEntry.of(frame.depth(), initialSource, cmd));

        if (Bastion.shouldPause(initialSource, frame)) {
            Bastion.pause(initialSource, cmd, frame.depth(), (List<CommandSourceStack>) currentSources);
        }
    }
}
