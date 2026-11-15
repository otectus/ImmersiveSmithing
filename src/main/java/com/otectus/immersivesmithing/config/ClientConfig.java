package com.otectus.immersivesmithing.config;

import java.util.List;

import net.minecraftforge.common.ForgeConfigSpec;

/** Presentation settings. None of these change scoring windows. */
public final class ClientConfig {
    public static final ForgeConfigSpec SPEC;

    public enum ParticleAmount { NORMAL, REDUCED, MINIMAL }

    /** How the view behaves while working at a station. All three play by identical rules and reach the same quality. */
    public enum CameraMode { CINEMATIC, SHOULDER, WORLD }

    public enum Side { RIGHT, LEFT }

    public static final ForgeConfigSpec.EnumValue<CameraMode> CAMERA_MODE;
    public static final ForgeConfigSpec.DoubleValue CAMERA_TRANSITION_SPEED;
    public static final ForgeConfigSpec.EnumValue<Side> CAMERA_SIDE;
    public static final ForgeConfigSpec.IntValue CAMERA_FOV;
    public static final ForgeConfigSpec.BooleanValue REDUCED_MOTION;
    public static final ForgeConfigSpec.BooleanValue SHOW_WORK_GUIDES;
    public static final ForgeConfigSpec.BooleanValue EXPANDED_STATUS;
    public static final ForgeConfigSpec.BooleanValue HOLD_TO_ACT;
    public static final ForgeConfigSpec.DoubleValue EFFECT_SOUND_VOLUME;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> FAVORITE_PATTERNS;
    public static final ForgeConfigSpec.ConfigValue<String> LAST_PATTERN;

    public static final ForgeConfigSpec.BooleanValue SHOW_NUMERIC_QUALITY_SCORES;
    public static final ForgeConfigSpec.BooleanValue REDUCED_SCREEN_SHAKE;
    public static final ForgeConfigSpec.BooleanValue REDUCED_FLASHES;
    public static final ForgeConfigSpec.EnumValue<ParticleAmount> PARTICLE_AMOUNT;
    public static final ForgeConfigSpec.BooleanValue HIGH_CONTRAST_MINIGAMES;
    public static final ForgeConfigSpec.BooleanValue LARGE_MINIGAME_TARGETS;
    public static final ForgeConfigSpec.BooleanValue COLORBLIND_SAFE_TARGETS;
    public static final ForgeConfigSpec.BooleanValue TIMING_CUE_SOUNDS;
    public static final ForgeConfigSpec.DoubleValue SOUND_CUE_VOLUME;
    public static final ForgeConfigSpec.IntValue FEEDBACK_LABEL_MS;
    public static final ForgeConfigSpec.BooleanValue STATION_NOTICES;
    public static final ForgeConfigSpec.IntValue NOTICE_SECONDS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("tooltips");
        SHOW_NUMERIC_QUALITY_SCORES = b.comment("Show raw 0-100 scores in the detailed (Shift) quality tooltip.")
                .define("showNumericQualityScores", false);
        b.pop();
        b.comment("Working at a station").push("camera");
        CAMERA_MODE = b.comment("CINEMATIC: a short, collision-safe move to a framed view of the station. SHOULDER: a small offset beside",
                        "your own view. WORLD: no camera movement at all; work with ordinary clicks. Every mode reaches the same quality.")
                .defineEnum("mode", CameraMode.CINEMATIC);
        CAMERA_TRANSITION_SPEED = b.comment("Speed of the camera's move into and out of a work view. 0 cuts directly.")
                .defineInRange("transitionSpeed", 1.0, 0.0, 3.0);
        CAMERA_SIDE = b.comment("The side the work view and tools favour; LEFT mirrors them for left-handed play.")
                .defineEnum("side", Side.RIGHT);
        CAMERA_FOV = b.comment("Field of view of the work view, in degrees. Your saved FOV is never changed.")
                .defineInRange("fov", 55, 30, 90);
        b.pop();
        b.comment("Accessibility. These options only change presentation; they never widen what counts as a valid action.").push("accessibility");
        REDUCED_SCREEN_SHAKE = b.define("reducedScreenShake", false);
        REDUCED_FLASHES = b.define("reducedFlashes", false);
        PARTICLE_AMOUNT = b.defineEnum("particleAmount", ParticleAmount.NORMAL);
        HIGH_CONTRAST_MINIGAMES = b.comment("High-contrast work guides: outlined chalk marks and solid heat bars. (Key kept from the old minigames.)")
                .define("highContrastMinigames", false);
        LARGE_MINIGAME_TARGETS = b.comment("Thicker guide marks and a larger cursor. What counts as a part of the piece is unchanged.")
                .define("largeMinigameTargets", false);
        COLORBLIND_SAFE_TARGETS = b.comment("Use a blue/orange palette for heat and guides instead of green/red. Heat is always also named in words.")
                .define("colorblindSafeTargets", false);
        TIMING_CUE_SOUNDS = b.comment("Readiness cues: a short sound when a billet is ready to draw, a piece too cold to work or a quench done.")
                .define("timingCueSounds", true);
        SOUND_CUE_VOLUME = b.comment("Volume of those cues.").defineInRange("soundCueVolume", 0.8, 0.0, 1.0);
        FEEDBACK_LABEL_MS = b.comment("How long a work result (\"Too cold\", \"Overworked\") stays readable beside the piece, in milliseconds.")
                .defineInRange("feedbackLabelMs", 2500, 500, 10000);
        REDUCED_MOTION = b.comment("No camera travel (direct cuts), no camera shake, no follow shots and no extra tool sway.")
                .define("reducedMotion", false);
        SHOW_WORK_GUIDES = b.comment("Chalk marks, socket cues and the outline of the part under the cursor.")
                .define("showWorkGuides", true);
        EXPANDED_STATUS = b.comment("Show numbers (heat, progress per part, quench time) in the work status.")
                .define("expandedStatus", false);
        HOLD_TO_ACT = b.comment("Holding a button keeps a piece in the water or on the wheel. Off: press once to start, again to stop.")
                .define("holdToAct", true);
        EFFECT_SOUND_VOLUME = b.comment("Volume of station work sounds (impacts, bellows, hiss, scrape).")
                .defineInRange("effectSoundVolume", 1.0, 0.0, 1.0);
        STATION_NOTICES = b.comment("Show station messages (what a station needs, errors) above the hotbar, where they stay for",
                        "noticeSeconds and several can show at once. Off: the vanilla action bar, which the next message replaces.")
                .define("stationNotices", true);
        NOTICE_SECONDS = b.comment("How long a station message stays above the hotbar.")
                .defineInRange("noticeSeconds", 8, 2, 60);
        b.pop();
        b.comment("Pattern book").push("patternBook");
        FAVORITE_PATTERNS = b.comment("Recipe ids starred in the pattern book; shown first.")
                .defineListAllowEmpty("favorites", List.of(), o -> o instanceof String);
        LAST_PATTERN = b.comment("The last pattern chosen, offered as \"Repeat last\". The server checks it again.")
                .define("lastPattern", "");
        b.pop();
        SPEC = b.build();
    }

    public static boolean get(ForgeConfigSpec.BooleanValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    public static double get(ForgeConfigSpec.DoubleValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    public static int get(ForgeConfigSpec.IntValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    public static <T extends Enum<T>> T get(ForgeConfigSpec.EnumValue<T> value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    public static ParticleAmount particleAmount() {
        return SPEC.isLoaded() ? PARTICLE_AMOUNT.get() : PARTICLE_AMOUNT.getDefault();
    }

    /** Scales a particle count by the configured amount, keeping at least one when count > 0 unless minimal. */
    public static int scaleParticles(int count) {
        return switch (particleAmount()) {
            case NORMAL -> count;
            case REDUCED -> Math.max(count > 0 ? 1 : 0, count / 2);
            case MINIMAL -> count > 0 ? Math.min(1, count) : 0;
        };
    }

    private ClientConfig() {}
}
