package sealmc.swe3tie.sealcore.modules.economy;

import org.jetbrains.annotations.NotNull;
import sealmc.swe3tie.sealcore.modules.economy.util.MoneyFormat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Pure progression math for one player's sell category. */
public final class SellProgress {

    public static final double START_MULTIPLIER = 1.0D;
    public static final double MAX_MULTIPLIER = 3.0D;
    public static final double STEP = 0.05D;
    public static final int BAR_LENGTH = 16;

    private static final double[] NICE_MANTISSAS = {100D, 125D, 150D, 200D, 250D, 300D, 350D, 500D, 750D, 850D, 1000D};
    private static final List<Threshold> THRESHOLDS = buildThresholds();

    private SellProgress() {
    }

    @NotNull
    public static List<Threshold> thresholds() {
        return THRESHOLDS;
    }

    public static double multiplier(double paid) {
        double current = Math.max(0D, paid);
        double multiplier = START_MULTIPLIER;
        for (Threshold threshold : THRESHOLDS) {
            if (current < threshold.required()) break;
            multiplier = threshold.multiplier();
        }
        return multiplier;
    }

    /** Returns next threshold, or null after final multiplier unlocks. */
    public static Threshold next(double paid) {
        double current = Math.max(0D, paid);
        for (Threshold threshold : THRESHOLDS) {
            if (current < threshold.required()) return threshold;
        }
        return null;
    }

    public static double percentage(double paid, Threshold target) {
        if (target == null) return 100D;
        double current = Math.max(0D, paid);
        if (target.required() <= 0D) return current > 0D ? 100D : 0D;
        return clamp(current / target.required() * 100D, 0D, 100D);
    }

    @NotNull
    public static String percentageText(double paid, Threshold target) {
        return String.format(Locale.ROOT, "%.1f", percentage(paid, target));
    }

    @NotNull
    public static String bar(double paid, Threshold target, @NotNull String complete, @NotNull String incomplete) {
        return bar(paid, target, complete, incomplete, BAR_LENGTH);
    }

    @NotNull
    public static String bar(double paid, Threshold target, @NotNull String complete, @NotNull String incomplete, int length) {
        int safeLength = Math.max(1, length);
        int filled = target == null ? safeLength : (int) Math.round(percentage(paid, target) / 100D * safeLength);
        filled = Math.max(0, Math.min(safeLength, filled));
        return complete.repeat(filled) + incomplete.repeat(safeLength - filled);
    }

    @NotNull
    public static String bar(double paid, Threshold target, @NotNull BarConfig config) {
        return bar(paid, target, config.complete(), config.incomplete(), config.length());
    }

    /** Returns cumulative target for next multiplier, or final target when maxed. */
    public static double nextTarget(double paid) {
        Threshold next = next(paid);
        return next == null ? THRESHOLDS.getLast().required() : next.required();
    }

    /** Returns amount still needed for next multiplier, or zero when maxed. */
    public static double remaining(double paid) {
        return thresholdRemaining(paid, next(paid));
    }

    public static double thresholdRemaining(double paid, Threshold target) {
        return target == null ? 0D : Math.max(0D, target.required() - Math.max(0D, paid));
    }

    @NotNull
    public static String categoryLabel(@NotNull SellCategory category) {
        return switch (category) {
            case CROPS -> "Cây trồng";
            case ORES -> "Khoáng sản";
            case MOBS -> "Sinh vật";
            case NATURAL -> "Tự nhiên";
            case TOOLS -> "Giáp và công cụ";
            case FISH -> "Cá";
            case BOOKS -> "Sách và sách phù phép";
            case POTIONS -> "Thuốc";
            case BLOCKS -> "Khối";
        };
    }

    /** Category item name with MiniMessage tags removed, suitable for a {SECTION} value. */
    @NotNull
    public static String configuredCategoryLabel(@NotNull SellCategory category, @NotNull String configuredName) {
        String plain = configuredName.replaceAll("<[^>]+>", "").trim();
        return plain.isBlank() || plain.contains("{") || plain.contains("}") ? categoryLabel(category) : plain;
    }

    /** Complete placeholder map for category buttons and summary items. */
    @NotNull
    public static Map<String, String> placeholders(@NotNull SellCategory category, double paid) {
        return placeholders(category, paid, categoryLabel(category));
    }

    /** Complete placeholder map using the configured category item name and custom progress bar. */
    @NotNull
    public static Map<String, String> placeholders(@NotNull SellCategory category, double paid,
                                                   @NotNull String configuredName,
                                                   @NotNull BarConfig barConfig) {
        Threshold target = next(paid);
        double required = target == null ? THRESHOLDS.getLast().required() : target.required();
        double current = Math.max(0D, paid);
        double remaining = target == null ? 0D : thresholdRemaining(paid, target);
        return Map.ofEntries(
            Map.entry("SECTION", configuredCategoryLabel(category, configuredName)),
            Map.entry("MULTIPLIER", formatMultiplier(multiplier(paid))),
            Map.entry("NEXT_MULTIPLIER", formatMultiplier(target == null ? MAX_MULTIPLIER : target.multiplier())),
            Map.entry("CURRENT", MoneyFormat.formatStandard(current)),
            Map.entry("CURRENT_FORMATTED", MoneyFormat.formatCompact(current)),
            Map.entry("TARGET", MoneyFormat.formatStandard(required)),
            Map.entry("TARGET_FORMATTED", MoneyFormat.formatCompact(required)),
            Map.entry("NEED", MoneyFormat.formatStandard(required)),
            Map.entry("NEED_FORMATTED", MoneyFormat.formatCompact(required)),
            Map.entry("NEXT_TARGET", MoneyFormat.formatStandard(required)),
            Map.entry("NEXT_TARGET_FORMATTED", MoneyFormat.formatCompact(required)),
            Map.entry("REMAINING", MoneyFormat.formatStandard(remaining)),
            Map.entry("REMAINING_FORMATTED", MoneyFormat.formatCompact(remaining)),
            Map.entry("PERCENTAGE", percentageText(paid, target)),
            Map.entry("PROGRESS_BAR", bar(paid, target, barConfig))
        );
    }

    /** Complete placeholder map using the configured category item name for {SECTION}. */
    @NotNull
    public static Map<String, String> placeholders(@NotNull SellCategory category, double paid,
                                                   @NotNull String configuredName) {
        return placeholders(category, paid, configuredName, BarConfig.DEFAULT);
    }

    /** Milestone placeholder map using configured category name and shared progress bar config. */
    @NotNull
    public static Map<String, String> thresholdPlaceholders(@NotNull SellCategory category, double paid,
                                                            Threshold target, @NotNull String configuredName,
                                                            @NotNull BarConfig barConfig) {
        double current = Math.max(0D, paid);
        double required = target == null ? THRESHOLDS.getLast().required() : target.required();
        double progress = progressAmount(paid, target);
        Map<String, String> values = new java.util.HashMap<>(placeholders(category, paid, configuredName, barConfig));
        values.put("MULTIPLIER", formatMultiplier(target == null ? MAX_MULTIPLIER : target.multiplier()));
        values.put("CURRENT", MoneyFormat.formatStandard(current));
        values.put("CURRENT_FORMATTED", MoneyFormat.formatCompact(current));
        values.put("NEED", MoneyFormat.formatStandard(required));
        values.put("NEED_FORMATTED", MoneyFormat.formatCompact(required));
        values.put("TARGET", MoneyFormat.formatStandard(required));
        values.put("TARGET_FORMATTED", MoneyFormat.formatCompact(required));
        values.put("NEXT_TARGET", MoneyFormat.formatStandard(required));
        values.put("NEXT_TARGET_FORMATTED", MoneyFormat.formatCompact(required));
        values.put("REMAINING", MoneyFormat.formatStandard(thresholdRemaining(paid, target)));
        values.put("REMAINING_FORMATTED", MoneyFormat.formatCompact(thresholdRemaining(paid, target)));
        values.put("PERCENTAGE", percentageText(progress, target));
        values.put("PROGRESS_BAR", bar(progress, target, barConfig));
        return Map.copyOf(values);
    }

    /** Placeholder map for one milestone pane; NEED/TARGET identify that milestone. */
    @NotNull
    public static Map<String, String> thresholdPlaceholders(@NotNull SellCategory category, double paid,
                                                            Threshold target, @NotNull String complete,
                                                            @NotNull String incomplete) {
        return thresholdPlaceholders(category, paid, target, complete, incomplete, categoryLabel(category));
    }

    /** Milestone placeholder map using configured category item name for {SECTION}. */
    @NotNull
    public static Map<String, String> thresholdPlaceholders(@NotNull SellCategory category, double paid,
                                                            Threshold target, @NotNull String complete,
                                                            @NotNull String incomplete, @NotNull String configuredName) {
        return thresholdPlaceholders(category, paid, target, configuredName, new BarConfig(BAR_LENGTH, complete, incomplete));
    }

    /**
     * Amount counted toward one milestone's bar: the full target when completed, the paid total while it is the
     * next milestone, and zero for milestones not yet reached.
     */
    private static double progressAmount(double paid, Threshold target) {
        if (target == null) return Math.max(0D, paid);
        double current = Math.max(0D, paid);
        if (current >= target.required()) return target.required();
        return next(current) == target ? current : 0D;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static List<Threshold> buildThresholds() {
        List<Threshold> result = new ArrayList<>();
        double[] initial = {25_000D, 50_000D, 150_000D, 250_000D, 750_000D, 1_000_000D};
        for (int i = 0; i < 40; i++) {
            double required = i < initial.length ? initial[i]
                : roundUpToNiceNumber(result.get(i - 1).required() * 1.3D);
            result.add(new Threshold(roundMultiplier(START_MULTIPLIER + STEP * (i + 1)), required));
        }
        return List.copyOf(result);
    }

    private static double roundUpToNiceNumber(double value) {
        if (!(value > 0D) || !Double.isFinite(value)) return value;
        double magnitude = Math.pow(10D, Math.floor(Math.log10(value)) - 2D);
        double mantissa = value / magnitude;
        for (double nice : NICE_MANTISSAS) {
            if (nice >= mantissa - 1e-9) {
                return nice * magnitude;
            }
        }
        return 1000D * magnitude;
    }

    private static double roundMultiplier(double value) {
        return Math.round(value * 100D) / 100D;
    }

    private static String formatMultiplier(double multiplier) {
        return String.format(Locale.ROOT, "%.2f", multiplier);
    }

    public record Threshold(double multiplier, double required) {
    }

    public record BarConfig(int length, @NotNull String complete, @NotNull String incomplete) {
        public static final BarConfig DEFAULT = new BarConfig(BAR_LENGTH, "<#9CC0D9>■", "<dark_gray>■");

        public BarConfig {
            if (length < 1) length = BAR_LENGTH;
        }
    }
}
