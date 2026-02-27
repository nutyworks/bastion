package works.nuty.bastion;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.item.FunctionArgument;
import net.minecraft.commands.execution.Frame;
import net.minecraft.commands.execution.tasks.BuildContexts;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.commands.FunctionCommand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import works.nuty.bastion.action.MacroLineAction;
import works.nuty.bastion.action.PlainLineAction;
import works.nuty.bastion.network.BastionResumePayload;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Bastion implements ModInitializer {
	public static final String MOD_ID = "bastion";
	public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

	public static MutableComponent debugLocation = Component.empty();
	public static Component debugCommand = Component.empty();

	public static final Set<BlockPos> blockBreakpoints = new HashSet<>();
	public static final Set<FunctionBreakpoint> funcBreakpoints = new HashSet<>();

	public static volatile boolean paused = false;
	public static volatile PauseType currentPauseType = PauseType.NONE;
	public static volatile List<CommandSourceStack> currentPauseSources = List.of();
	public static volatile StepMode currentStepMode = StepMode.NONE;
	public static volatile int targetDepth = -1;
	public static volatile int lastPausedDepth = 0;
	public static MinecraftServer server;

    public static boolean toggleBlockBreakpoint(BlockPos blockPos) {
		if (blockBreakpoints.contains(blockPos)) {
			blockBreakpoints.remove(blockPos);
			return false;
		} else {
			blockBreakpoints.add(blockPos);
			return true;
		}
    }

	public static boolean toggleFunctionBreakpoint(Identifier id, int line) {
		FunctionBreakpoint bp = new FunctionBreakpoint(id, line);
		if (funcBreakpoints.contains(bp)) {
			funcBreakpoints.remove(bp);
			return false;
		} else {
			funcBreakpoints.add(bp);
			return true;
		}
	}

	@Override
	public void onInitialize() {
		funcBreakpoints.add(new FunctionBreakpoint(Identifier.fromNamespaceAndPath("tteokguk", "tick"), 3));

		ServerLifecycleEvents.SERVER_STARTED.register(s -> server = s);
		ServerLifecycleEvents.SERVER_STOPPED.register(_ -> server = null);

		PayloadTypeRegistry.serverboundPlay().register(BastionResumePayload.TYPE, BastionResumePayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(BastionResumePayload.TYPE, (payload, context) -> {
			LOGGER.info("Received BastionResumePayload packet");
			Bastion.resume();
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(Commands.literal("bastion")
				.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_OWNER))
				.then(Commands.literal("breakpoint")
					.then(Commands.literal("function")
						.then(Commands.argument("function", FunctionArgument.functions()).suggests(FunctionCommand.SUGGEST_FUNCTION)
							.then(Commands.argument("line", IntegerArgumentType.integer(1))
								.executes(context -> {
									Identifier funcId = FunctionArgument.getFunctionOrTag(context, "function").getFirst();

									int line = IntegerArgumentType.getInteger(context, "line");

									boolean isAdded = Bastion.toggleFunctionBreakpoint(funcId, line);

									if (isAdded) {
										context.getSource().sendSuccess(() ->
											Component.translatable("command.bastion.breakpoint.function.success.set", Component.translationArg(funcId), line), false);
									} else {
										context.getSource().sendSuccess(() ->
											Component.translatable("command.bastion.breakpoint.function.success.removed", Component.translationArg(funcId), line), false);
									}
									return 1;
								})
							)
						)
					)
					.then(Commands.literal("block")
						.then(Commands.argument("pos", BlockPosArgument.blockPos())
							.executes(context -> {
								BlockPos blockPos = BlockPosArgument.getBlockPos(context, "pos");

								boolean isAdded = Bastion.toggleBlockBreakpoint(blockPos);

								if (isAdded) {
									context.getSource().sendSuccess(() ->
										Component.translatable("command.bastion.breakpoint.block.success.set", blockPos.getX(), blockPos.getY(), blockPos.getZ()), false);
								} else {
									context.getSource().sendSuccess(() ->
										Component.translatable("command.bastion.breakpoint.block.success.removed", blockPos.getX(), blockPos.getY(), blockPos.getZ()), false);
								}

								return 1;
							})
						)
					)
				)
				.then(Commands.literal("resume")
					.executes(context -> {
						if (!Bastion.paused) {
							return 0;
						}
						Bastion.resume();
						return 1;
					}))
				.then(Commands.literal("stepinto")
					.executes(context -> {
						if (Bastion.paused) {
							Bastion.currentStepMode = StepMode.INTO;
							Bastion.resume();
						}
						return 1;
					}))
				.then(Commands.literal("stepout")
					.executes(context -> {
						if (Bastion.paused) {
							Bastion.currentStepMode = StepMode.OUT;
							Bastion.targetDepth = Bastion.lastPausedDepth - 1;
							Bastion.resume();
						}
						return 1;
					}))
				.then(Commands.literal("stepover")
					.executes(context -> {
						if (Bastion.paused) {
							Bastion.currentStepMode = StepMode.OVER;
							Bastion.targetDepth = Bastion.lastPausedDepth;
							Bastion.resume();
						}
						return 1;
					}))
			);
		});
	}

	public static PauseType getPauseType(final BuildContexts<?> contexts, final Frame frame, final boolean forced) {
		if (!forced && (currentStepMode == StepMode.INTO || (currentStepMode == StepMode.OVER || currentStepMode == StepMode.OUT) && frame.depth() <= targetDepth)) {
			return getPauseType(contexts, frame, true);
		}
		if (contexts instanceof BuildContexts.TopLevel<?> topLevelInstance) {
			return getPauseTypeTopLevel(topLevelInstance, forced);
		}
		if (contexts instanceof BuildContexts.Continuation<?> continuationInstance) {
			return getPauseTypeContinuation(continuationInstance, forced);
		}
		if (contexts instanceof PlainLineAction<?> fnInstance) {
			return getPauseTypePlainFunction(fnInstance, forced);
		}
		if (contexts instanceof MacroLineAction<?> fnInstance) {
			return getPauseTypeMacroFunction(fnInstance, forced);
		}

		return PauseType.NONE;
	}

	private static PauseType getPauseTypeTopLevel(final BuildContexts.TopLevel<?> contexts, final boolean forced) {
		final CommandSourceStack source = ((BuildContexts.TopLevel<CommandSourceStack>) contexts).source;
		return getPauseTypeSource(forced, source);
	}

	private static PauseType getPauseTypeContinuation(final BuildContexts.Continuation<?> contexts, final boolean forced) {
		final CommandSourceStack source = ((BuildContexts.Continuation<CommandSourceStack>) contexts).originalSource;
		return getPauseTypeSource(forced, source);
	}

	private static Bastion.PauseType getPauseTypeSource(boolean forced, CommandSourceStack source) {
		if (forced && source.isPlayer()) {
			return new PauseType.Player(source.getPlayer());
		} else {
			BlockPos pos = BlockPos.containing(source.getPosition());

			if (forced || blockBreakpoints.contains(pos)) {
				return new PauseType.Block(pos);
			} else {
				return PauseType.NONE;
			}
		}
	}

	private static PauseType getPauseTypePlainFunction(final PlainLineAction<?> contexts, final boolean forced) {
		final Identifier id = contexts.functionId;
		final int line = contexts.lineNumber;

		if (forced || funcBreakpoints.contains(new FunctionBreakpoint(id, line))) {
			return new PauseType.Function(id, line);
		} else {
			return PauseType.NONE;
		}
	}

	private static PauseType getPauseTypeMacroFunction(final MacroLineAction<?> contexts, final boolean forced) {
		final Identifier id = contexts.functionId;
		final int line = contexts.lineNumber;

		if (forced || funcBreakpoints.contains(new FunctionBreakpoint(id, line))) {
			return new PauseType.Function(id, line);
		} else {
			return PauseType.NONE;
		}
	}

	public static void pause(final PauseType pauseType, final Component command, final int depth, final List<CommandSourceStack> sources) {
		paused = true;
		currentPauseType = pauseType;
		currentPauseSources = sources;
		currentStepMode = Bastion.StepMode.NONE;
		debugLocation = pauseType.toComponent();
		debugCommand = command;
		lastPausedDepth = depth;

		Bastion.LOGGER.info("PauseType: " + pauseType);
		server.managedBlock(() -> {
            server.getConnection().tick();
            return !paused || !server.isRunning();
		});
	}

	public static void resume() {
		paused = false;
		currentPauseType = PauseType.NONE;
		currentPauseSources = List.of();
	}

	public record FunctionBreakpoint(Identifier id, int line) {}

	public enum StepMode { NONE, INTO, OUT, OVER }

	public sealed interface PauseType {
		record Player(ServerPlayer player) implements PauseType {
			@Override
			public MutableComponent toComponent() {
				return Component.translatable("bastion.debugger.paused.player", this.player.getName());
			}
		}
		record Block(BlockPos blockPos) implements PauseType {
			@Override
			public MutableComponent toComponent() {
				return Component.translatable("bastion.debugger.paused.block", this.blockPos.getX(), this.blockPos.getY(), this.blockPos.getZ());
			}
		}
		record Function(Identifier id, int line) implements PauseType {
			@Override
			public MutableComponent toComponent() {
				return Component.translatable("bastion.debugger.paused.function", Component.translationArg(id), line);
			}
		}
		final class None implements PauseType {
			@Override
			public MutableComponent toComponent() {
				return Component.empty();
			}
		}

		PauseType.None NONE = new PauseType.None();

		MutableComponent toComponent();
	}
}