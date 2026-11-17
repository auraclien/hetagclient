package rocketway.killauracrit;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public class KillAuraCritClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        Keybinds.register();
        HudRenderer.register();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            Keybinds.handleToggle();       // R — вкл/выкл
            CritKillAura.onTick(client);   // логика ауры
        });
    }
}
