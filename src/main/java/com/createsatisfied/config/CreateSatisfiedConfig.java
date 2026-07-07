package com.createsatisfied.config;

import java.util.Locale;

import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.TranslatableEnum;

/**
 * Purely a display preference (which unit throughput/duration numbers render in) - no gameplay
 * effect - so this is a single client-side config rather than mirroring Create's split
 * common/client/server config setup.
 */
public class CreateSatisfiedConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public enum TimeUnit implements TranslatableEnum {
        PER_MINUTE,
        PER_TICK,
        TICKS_PER_ITEM;

        // NeoForge's built-in config screen calls this for the dropdown label instead of the raw
        // enum constant name - without it you'd see "PER_MINUTE" etc. rather than real words.
        @Override
        public Component getTranslatedName() {
            return Component.translatable("createsatisfied.configuration.timeUnit." + name().toLowerCase(Locale.ROOT));
        }
    }

    // .translation(...) sets the lang key NeoForge's config screen uses for this entry's label -
    // without it, the screen falls back to showing the raw key text itself (e.g.
    // "createsatisfied.configuration.timeunit") since there's no generic fallback for option
    // labels the way there is for section titles. The .comment(...) text below IS shown as the
    // tooltip automatically (as a translation fallback) even without a dedicated lang entry.
    public static final ModConfigSpec.EnumValue<TimeUnit> TIME_UNIT = BUILDER
        .translation("createsatisfied.configuration.timeUnit")
        .comment(
            "How throughput rates and durations are displayed:",
            "PER_MINUTE - items/min with items/s in parentheses (e.g. \"32.0/min (0.5/s)\")",
            "PER_TICK - raw items/tick and ticks, matching Create's own internal tick-based timing",
            "TICKS_PER_ITEM - ticks needed per single item, the inverse of PER_TICK - more readable once a rate drops below 1 item/tick"
        )
        .defineEnum("timeUnit", TimeUnit.PER_MINUTE);

    public static final ModConfigSpec SPEC = BUILDER.build();

    /**
     * Advances to the next unit (wrapping around) and persists it, so cycling in-game doesn't get
     * silently discarded on world exit.
     */
    public static void cycleTimeUnit() {
        cycleTimeUnit(1);
    }

    /**
     * Same, but direction-aware - lets scroll-up/scroll-down step forward/backward through the
     * three options instead of always landing on the same next value regardless of scroll
     * direction, which would feel wrong for a scroll gesture (unlike a single-purpose keypress).
     */
    public static void cycleTimeUnit(int direction) {
        TimeUnit[] values = TimeUnit.values();
        TimeUnit next = values[Math.floorMod(TIME_UNIT.get().ordinal() + direction, values.length)];
        TIME_UNIT.set(next);
        TIME_UNIT.save();
    }

    private CreateSatisfiedConfig() {
    }
}
