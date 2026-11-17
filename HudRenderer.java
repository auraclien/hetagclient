package rocketway.killauracrit;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public final class HudRenderer {

    private HudRenderer() {}

    public static void register() {
        HudRenderCallback.EVENT.register(HudRenderer::render);
    }

    private static void render(DrawContext ctx, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        String status = CritKillAura.enabled ? "§aKillAura ON" : "§cKillAura OFF";
        String crit = isCritReady(mc) ? "§eCRIT READY" : "§7not ready";

        int x = 6;
        int y = 6;
        ctx.drawText(mc.textRenderer, status, x, y, 0xFFFFFF, true);
        ctx.drawText(mc.textRenderer, crit, x, y + 12, 0xFFFFFF, true);
        ctx.drawText(mc.textRenderer, "§8[R] toggle", x, y + 24, 0xAAAAAA, true);
    }

    private static boolean isCritReady(MinecraftClient mc) {
        var p = mc.player;
        return p.fallDistance > 0.0F
                && !p.isOnGround()
                && !p.isClimbing()
                && !p.isTouchingWater()
                && !p.isInLava()
                && !p.hasVehicle()
                && !p.getAbilities().flying
                && !p.isGliding()
                && p.getAttackCooldownProgress(0.5F) > 0.92F;
    }
}
