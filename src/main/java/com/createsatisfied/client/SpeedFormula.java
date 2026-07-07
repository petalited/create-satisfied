package com.createsatisfied.client;

import com.simibubi.create.content.processing.recipe.ProcessingRecipe;

import net.minecraft.util.Mth;

public interface SpeedFormula {

    float opsPerMinute(float rpm, ProcessingRecipe<?, ?> recipe);

    static SpeedFormula continuous(float divisor, float minSpeed, float maxSpeed) {
        return (rpm, recipe) -> {
            float speed = Mth.clamp(Math.abs(rpm) / divisor, minSpeed, maxSpeed);
            float ticksPerOperation = recipe.getProcessingDuration() / speed;
            return ThroughputFormat.TICKS_PER_MINUTE / ticksPerOperation;
        };
    }

    static SpeedFormula press() {
        return (rpm, recipe) -> {
            float speed = Math.abs(rpm);
            if (speed == 0) {
                return 0;
            }
            float runningTickSpeed = Mth.lerp(Mth.clamp(speed / 512f, 0f, 1f), 1f, 60f);
            return ThroughputFormat.TICKS_PER_MINUTE / (240f / runningTickSpeed);
        };
    }

    static SpeedFormula mixer() {
        return (rpm, recipe) -> {
            float speed = Math.abs(rpm);
            if (speed == 0) {
                return 0;
            }
            int duration = recipe.getProcessingDuration();
            float recipeSpeedFactor = duration != 0 ? duration / 100f : 1f;
            float processingTicks = Math.max(Mth.log2((int) (512f / speed)) * Mth.ceil(recipeSpeedFactor * 15f) + 1, 1);
            return ThroughputFormat.TICKS_PER_MINUTE / (40f + processingTicks);
        };
    }

    /**
     * Deployer's animation length (expand+retract+wait) is fixed regardless of recipe,
     * so this ignores the recipe entirely - every recipe gets the same number at a given rpm.
     */
    static SpeedFormula deployer() {
        return (rpm, recipe) -> {
            float speed = Math.abs(rpm) * 2f;
            if (speed == 0) {
                return 0;
            }
            float timerSpeed = Mth.clamp(speed, 8f, 512f);
            return ThroughputFormat.TICKS_PER_MINUTE / (2500f / timerSpeed);
        };
    }
}
