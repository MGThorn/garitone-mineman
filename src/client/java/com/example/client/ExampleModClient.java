package com.example.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import fi.dy.masa.malilib.event.InitializationHandler;
import com.example.client.feature.EasyEatHandler;

public class ExampleModClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		InitializationHandler.getInstance().registerInitializationHandler(new InitHandler());
		ClientTickEvents.START_CLIENT_TICK.register(mc -> EasyEatHandler.onClientTick());
	}
}
