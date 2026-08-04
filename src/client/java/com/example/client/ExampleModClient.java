package com.example.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import fi.dy.masa.malilib.event.InitializationHandler;
import com.example.client.compat.OpenContainerTracker;
import com.example.client.compat.baritone.BaritoneController;
import com.example.client.feature.ChestQuickDepositHandler;
import com.example.client.feature.ChestRuleOverlayRenderer;
import com.example.client.feature.EasyEatHandler;
import com.example.client.feature.HungryMinemanHandler;
import com.example.client.feature.MissingToolCancelHandler;
import com.example.client.feature.PauseIndicatorRenderHandler;
import com.example.client.feature.SelectionToolHandler;
import com.example.client.feature.SmartMinemanHandler;
import com.example.client.feature.StorageContentSnapshotter;
import com.example.client.feature.StoragePointRenderHandler;
import com.example.client.storage.StorageContentIndex;

public class ExampleModClient implements ClientModInitializer {
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
		SelectionToolHandler.register();
		StoragePointRenderHandler.register();
		PauseIndicatorRenderHandler.register();
		ChestRuleOverlayRenderer.register();
		StorageContentSnapshotter.register();
	}
}
