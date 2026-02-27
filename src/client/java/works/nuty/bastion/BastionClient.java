package works.nuty.bastion;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.resources.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BastionClient implements ClientModInitializer {
	public static final String MOD_ID = "bastion";
	public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

	private final InputManager inputManager = new InputManager();
	private final DebugLevelRenderer debugLevelRenderer = new DebugLevelRenderer();

	@Override
	public void onInitializeClient() {
		inputManager.registerKeyMappings();

		ClientTickEvents.END_CLIENT_TICK.register(this.inputManager);
		LevelRenderEvents.END_MAIN.register(this.debugLevelRenderer);
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("bastion", "debug_overlay"), new DebugHudElement());
	}
}