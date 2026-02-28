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
import net.minecraft.util.ArrayListDeque;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import works.nuty.bastion.action.MacroLineAction;
import works.nuty.bastion.action.PlainLineAction;
import works.nuty.bastion.network.BastionResumePayload;

import java.util.*;

public class Bastion implements ModInitializer {
	public static final String MOD_ID = "bastion";
	public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

	public static ArrayListDeque<CallStackEntry> callstack = new ArrayListDeque<>();
	public static MutableComponent debugLocation = Component.empty();
	public static Component debugCommand = Component.empty();

	public static final Set<BlockPos> blockBreakpoints = new HashSet<>();
	public static final Set<FunctionBreakpoint> funcBreakpoints = new HashSet<>();

	public static volatile boolean paused = false;
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

	public static InitialSource getInitialSource(final BuildContexts<?> contexts) {
		if (contexts instanceof BuildContexts.TopLevel<?> topLevelInstance) {
			return getInitialSourceTopLevel(topLevelInstance);
		} else if (contexts instanceof BuildContexts.Continuation<?> continuationInstance) {
			return getInitialSourceContinuation(continuationInstance);
		} else if (contexts instanceof PlainLineAction<?> fnInstance) {
			return getInitialSourcePlainFunction(fnInstance);
		} else if (contexts instanceof MacroLineAction<?> fnInstance) {
			return getInitialSourceMacroFunction(fnInstance);
		}

		Bastion.LOGGER.error("context got {}", contexts);
		throw new IllegalStateException("Could not match contexts to execution source");
	}

	private static InitialSource getInitialSourceTopLevel(final BuildContexts.TopLevel<?> contexts) {
		final CommandSourceStack source = ((BuildContexts.TopLevel<CommandSourceStack>) contexts).source;
		return getPauseTypeSource(source);
	}

	private static InitialSource getInitialSourceContinuation(final BuildContexts.Continuation<?> contexts) {
		final CommandSourceStack source = ((BuildContexts.Continuation<CommandSourceStack>) contexts).originalSource;
		return getPauseTypeSource(source);
	}

	private static InitialSource getPauseTypeSource(CommandSourceStack source) {
		if (source.isPlayer()) {
			return InitialSource.player(source.getPlayer());
		} else {
			return InitialSource.block(BlockPos.containing(source.getPosition()));
		}
	}

	private static InitialSource getInitialSourcePlainFunction(final PlainLineAction<?> contexts) {
		final Identifier id = contexts.functionId;
		final int line = contexts.lineNumber;

		return InitialSource.function(id, line);
	}

	private static InitialSource getInitialSourceMacroFunction(final MacroLineAction<?> contexts) {
		final Identifier id = contexts.functionId;
		final int line = contexts.lineNumber;

		return InitialSource.function(id, line);
	}

	public static boolean shouldPause(final InitialSource initialSource, final Frame frame) {
		if (currentStepMode == StepMode.INTO || (currentStepMode == StepMode.OVER || currentStepMode == StepMode.OUT) && frame.depth() <= targetDepth) {
			return true;
		}

		switch (initialSource) {
            case InitialSource.Block block -> {
				return blockBreakpoints.contains(block.blockPos);
            }
            case InitialSource.Function function -> {
				return funcBreakpoints.contains(new FunctionBreakpoint(function.id, function.line));
            }
            case InitialSource.Player player -> {
				return false;
            }
        }
	}

	public static void pause(final InitialSource initialSource, final Component command, final int depth, final List<CommandSourceStack> sources) {
		paused = true;
		currentPauseSources = sources;
		currentStepMode = Bastion.StepMode.NONE;
		debugLocation = initialSource.toComponent();
		debugCommand = command;
		lastPausedDepth = depth;

		Bastion.LOGGER.info("PauseType: " + initialSource);
		server.managedBlock(() -> {
            server.getConnection().tick();
            return !paused || !server.isRunning();
		});
	}

	public static void resume() {
		paused = false;
		currentPauseSources = List.of();
	}

	public record FunctionBreakpoint(Identifier id, int line) {}

	public enum StepMode { NONE, INTO, OUT, OVER }

	public sealed interface InitialSource {
		record Player(ServerPlayer player) implements InitialSource {
			@Override
			public MutableComponent toComponent() {
				return Component.translatable("bastion.debugger.paused.player", this.player.getName());
			}
		}
		record Block(BlockPos blockPos) implements InitialSource {
			@Override
			public MutableComponent toComponent() {
				return Component.translatable("bastion.debugger.paused.block", this.blockPos.getX(), this.blockPos.getY(), this.blockPos.getZ());
			}
		}
		record Function(Identifier id, int line) implements InitialSource {
			@Override
			public MutableComponent toComponent() {
				return Component.translatable("bastion.debugger.paused.function", Component.translationArg(id), line);
			}
		}

		MutableComponent toComponent();

		static Player player(final ServerPlayer player) {
			return new Player(player);
		}

		static Block block(final BlockPos blockPos) {
			return new Block(blockPos);
		}

		static Function function(final Identifier id, final int line) {
			return new Function(id, line);
		}
	}

	public record CallStackEntry(int depth, InitialSource initialSource, Component command) {
		public static CallStackEntry of(int depth, InitialSource initialSource, Component command) {
			return  new CallStackEntry(depth, initialSource, command);
		}
	}
}