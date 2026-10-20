package io.github.mgthorn.garitonemineman.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import fi.dy.masa.malilib.event.InitializationHandler;
import io.github.mgthorn.garitonemineman.client.compat.OpenContainerTracker;
import io.github.mgthorn.garitonemineman.client.compat.baritone.BaritoneController;
import io.github.mgthorn.garitonemineman.client.feature.ChestQuickDepositHandler;
import io.github.mgthorn.garitonemineman.client.feature.BlockEspHandler;
import io.github.mgthorn.garitonemineman.client.feature.BlockEspRenderHandler;
import io.github.mgthorn.garitonemineman.client.feature.ChestRuleOverlayRenderer;
import io.github.mgthorn.garitonemineman.client.feature.EasyEatHandler;
import io.github.mgthorn.garitonemineman.client.feature.EntityEspRenderHandler;
import io.github.mgthorn.garitonemineman.client.feature.HungryMinemanHandler;
import io.github.mgthorn.garitonemineman.client.feature.ItemEspRenderHandler;
import io.github.mgthorn.garitonemineman.client.feature.MissingToolCancelHandler;
import io.github.mgthorn.garitonemineman.client.feature.PauseIndicatorRenderHandler;
import io.github.mgthorn.garitonemineman.client.feature.SelectionToolHandler;
import io.github.mgthorn.garitonemineman.client.feature.SmartMinemanHandler;
import io.github.mgthorn.garitonemineman.client.feature.StorageContentSnapshotter;
import io.github.mgthorn.garitonemineman.client.feature.StoragePointRenderHandler;
import io.github.mgthorn.garitonemineman.client.feature.ToolSwitchBackHandler;
import io.github.mgthorn.garitonemineman.client.storage.StorageContentIndex;

public class GaritoneMinemanClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		InitializationHandler.getInstance().registerInitializationHandler(new InitHandler());
		ClientTickEvents.START_CLIENT_TICK.register(mc -> EasyEatHandler.onClientTick());
		ClientTickEvents.START_CLIENT_TICK.register(mc -> OpenContainerTracker.tick());
		ClientTickEvents.START_CLIENT_TICK.register(mc -> BaritoneController.tick());
		ClientTickEvents.START_CLIENT_TICK.register(mc -> StorageContentIndex.tick());
		ClientTickEvents.START_CLIENT_TICK.register(mc -> HungryMinemanHandler.onClientTick());
		ClientTickEvents.START_CLIENT_TICK.register(mc -> SmartMinemanHandler.onClientTick());
		ClientTickEvents.START_CLIENT_TICK.register(mc -> ChestQuickDepositHandler.onClientTick());
		ClientTickEvents.START_CLIENT_TICK.register(mc -> MissingToolCancelHandler.onClientTick());
		ClientTickEvents.START_CLIENT_TICK.register(mc -> BlockEspHandler.onClientTick());
		ClientTickEvents.START_CLIENT_TICK.register(mc -> ToolSwitchBackHandler.onClientTick());
		SelectionToolHandler.register();
		StoragePointRenderHandler.register();
		PauseIndicatorRenderHandler.register();
		ChestRuleOverlayRenderer.register();
		StorageContentSnapshotter.register();
		ItemEspRenderHandler.register();
		BlockEspRenderHandler.register();
		EntityEspRenderHandler.register();
	}
}
