package rocketway.killauracrit;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public final class Keybinds {

    public static KeyBinding TOGGLE;

    private Keybinds() {}

    public static void register() {
        TOGGLE = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.killauracrit.toggle",          // translation key
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_R,                    // ← клавиша R
                "category.killauracrit"             // категория в настройках управления
        ));
    }

    /** Вызывать каждый клиентский тик — обрабатывает нажатие. */
    public static void handleToggle() {
        while (TOGGLE.wasPressed()) {
            CritKillAura.enabled = !CritKillAura.enabled;
        }
    }
}
