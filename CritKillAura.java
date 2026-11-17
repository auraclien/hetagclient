package rocketway.killauracrit;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * KillAura, адаптированная под античит SpookyTime (Grim-подобный).
 * Только криты. Включение — клавиша R (см. Keybinds).
 *
 * ОБХОДЫ, ВСТРОЕННЫЕ ЗДЕСЬ:
 *  - рандомный CPS 6-8 + джиттер ±4 мс
 *  - задержка реакции 90-200 мс на новую цель
 *  - плавный доворот по 8° за тик (без снапа)
 *  - FOV 100° (не 360°)
 *  - reach 2.85 (безопасно для Grim)
 *  - обязательный canSee (без throughWalls)
 *  - обязательный крит + cooldown > 0.92
 *  - 4% рандомных "промахов" (бьём мимо) — ломает статистику
 *  - пауза 1.5-3 с каждые 12-20 ударов (имитация "устал")
 */
public final class CritKillAura {

    public static volatile boolean enabled = false;

    // === ЛЕГИТИМНЫЕ НАСТРОЙКИ ===
    private static final double RANGE = 2.85D;
    private static final double RANGE_SQ = RANGE * RANGE;
    private static final float FOV = 100.0F;
    private static final int CPS_MIN = 6;
    private static final int CPS_MAX = 8;
    private static final long REACTION_MIN_MS = 90L;
    private static final long REACTION_MAX_MS = 200L;
    private static final double ROT_STEP = 8.0D;
    private static final double AIM_TOLERANCE = 2.5D;
    private static final double MISS_CHANCE = 0.04D;     // 4% промахов
    private static final int HITS_BEFORE_BREAK = 14;    // среднее
    private static final long BREAK_MIN_MS = 1500L;
    private static final long BREAK_MAX_MS = 3000L;

    private static long lastAttack = 0L;
    private static long nextDelay = 0L;
    private static long targetAcquiredAt = 0L;
    private static LivingEntity lastTarget = null;
    private static int hitStreak = 0;
    private static long breakUntil = 0L;
    private static int breakTarget = HITS_BEFORE_BREAK;

    private CritKillAura() {}

    public static void onTick(MinecraftClient mc) {
        if (!enabled) { reset(); return; }
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        long now = System.currentTimeMillis();

        // "перерыв" — имитация усталости
        if (now < breakUntil) return;

        // 1) Крит обязателен
        if (!canCrit(mc)) { resetTarget(); return; }

        // 2) Поиск цели
        LivingEntity target = findTarget(mc);
        if (target == null) { resetTarget(); return; }

        // 3) Реакция на новую цель
        if (target != lastTarget) {
            lastTarget = target;
            targetAcquiredAt = now;
            return;
        }
        long reaction = ThreadLocalRandom.current().nextLong(REACTION_MIN_MS, REACTION_MAX_MS + 1);
        if (now - targetAcquiredAt < reaction) return;

        // 4) Плавный доворот
        if (!lookAtSmooth(mc, target)) return;

        // 5) Задержка CPS + джиттер
        if (nextDelay == 0L) {
            int cps = ThreadLocalRandom.current().nextInt(CPS_MIN, CPS_MAX + 1);
            nextDelay = 1000L / cps + ThreadLocalRandom.current().nextLong(-4L, 5L);
        }
        if (now - lastAttack < nextDelay) return;

        // 6) Удар (с шансом промаха)
        boolean miss = ThreadLocalRandom.current().nextDouble() < MISS_CHANCE;
        if (miss) {
            // бьём "мимо" — просто мах рукой, без attackEntity
            mc.player.swingHand(Hand.MAIN_HAND);
        } else {
            mc.interactionManager.attackEntity(mc.player, target);
            mc.player.swingHand(Hand.MAIN_HAND);
        }

        lastAttack = now;
        nextDelay = 0L;

        // 7) Считаем серию → принудительный перерыв
        if (!miss && ++hitStreak >= breakTarget) {
            breakUntil = now + ThreadLocalRandom.current().nextLong(BREAK_MIN_MS, BREAK_MAX_MS + 1);
            hitStreak = 0;
            breakTarget = HITS_BEFORE_BREAK + ThreadLocalRandom.current().nextInt(-4, 5);
        }
    }

    private static void reset() {
        lastTarget = null;
        targetAcquiredAt = 0L;
        nextDelay = 0L;
        hitStreak = 0;
        breakUntil = 0L;
    }

    private static void resetTarget() {
        lastTarget = null;
        targetAcquiredAt = 0L;
        nextDelay = 0L;
    }

    private static boolean canCrit(MinecraftClient mc) {
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

    private static LivingEntity findTarget(MinecraftClient mc) {
        List<LivingEntity> candidates = mc.world.getEntitiesByClass(
                        LivingEntity.class,
                        mc.player.getBoundingBox().expand(RANGE),
                        e -> isValid(mc, e))
                .stream()
                .filter(e -> mc.player.squaredDistanceTo(e) <= RANGE_SQ)
                .filter(e -> isInFov(mc, e))
                .filter(mc.player::canSee)
                .sorted(Comparator.comparingDouble(mc.player::squaredDistanceTo))
                .toList();
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private static boolean isValid(MinecraftClient mc, LivingEntity e) {
        if (e == mc.player || e.isDead() || e.getHealth() <= 0) return false;
        if (e instanceof PlayerEntity p) {
            return !p.isCreative() && !p.isSpectator();
        }
        return false; // только игроки — меньше шума для Grim
    }

    private static boolean isInFov(MinecraftClient mc, Entity e) {
        double dx = e.getX() - mc.player.getX();
        double dz = e.getZ() - mc.player.getZ();
        double angle = Math.toDegrees(Math.atan2(dz, dx))
                - Math.toDegrees(Math.atan2(mc.player.getVelocity().z, mc.player.getVelocity().x));
        angle = Math.abs((angle + 180) % 360 - 180);
        return angle <= FOV / 2.0F;
    }

    private static boolean lookAtSmooth(MinecraftClient mc, Entity e) {
        double dx = e.getX() - mc.player.getX();
        double dy = (e.getY() + e.getEyeHeight()) - (mc.player.getY() + mc.player.getEyeHeight());
        double dz = e.getZ() - mc.player.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);

        float targetYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0F);
        float targetPitch = (float) -Math.toDegrees(Math.atan2(dy, dist));

        float curYaw = mc.player.getYaw();
        float curPitch = mc.player.getPitch();
        float dYaw = MathHelper.wrapDegrees(targetYaw - curYaw);
        float dPitch = MathHelper.wrapDegrees(targetPitch - curPitch);

        mc.player.setYaw(curYaw + (float) MathHelper.clamp(dYaw, -ROT_STEP, ROT_STEP));
        mc.player.setPitch(curPitch + (float) MathHelper.clamp(dPitch, -ROT_STEP, ROT_STEP));

        return Math.abs(dYaw) < AIM_TOLERANCE && Math.abs(dPitch) < AIM_TOLERANCE;
    }
                                                     }
