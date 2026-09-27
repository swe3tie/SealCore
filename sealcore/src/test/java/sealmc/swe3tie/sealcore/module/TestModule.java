package sealmc.swe3tie.sealcore.module;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A module whose spec is just a few values, used to exercise the registry.
 *
 * <p>Deliberately configurable through {@link SpecBuilder} so one class can stand in for
 * a well behaved module, a throwing module, an invalid one and so on.
 */
public final class TestModule implements SealModule<TestModule.Spec> {

    private final String id;
    private final int schemaVersion;
    private final Set<String> dependsOn;
    private final Set<String> restartKeys;
    private final SpecBuilder builder;

    public final List<Spec> applied = new ArrayList<>();
    private int disableCount;

    public TestModule(String id) {
        this(id, 1, Set.of(), Set.of(), new SpecBuilder());
    }

    public TestModule(String id, SpecBuilder builder) {
        this(id, 1, Set.of(), Set.of(), builder);
    }

    public TestModule(String id, Set<String> dependsOn, SpecBuilder builder) {
        this(id, 1, dependsOn, Set.of(), builder);
    }

    public TestModule(String id, Set<String> dependsOn, Set<String> restartKeys, SpecBuilder builder) {
        this(id, 1, dependsOn, restartKeys, builder);
    }

    public TestModule(String id, int schemaVersion, Set<String> dependsOn, Set<String> restartKeys, SpecBuilder builder) {
        this.id = id;
        this.schemaVersion = schemaVersion;
        this.dependsOn = dependsOn;
        this.restartKeys = restartKeys;
        this.builder = builder == null ? new SpecBuilder() : builder;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public int schemaVersion() {
        return schemaVersion;
    }

    @Override
    public Set<String> dependsOn() {
        return dependsOn;
    }

    @Override
    public Set<String> restartKeys() {
        return restartKeys;
    }

    @Override
    public Spec parse(ModuleSection section) {
        builder.onParse.accept(section);
        return new Spec(
            section.bool("enabled", true),
            section.decimal("payout", 10.0, 0.0, Double.MAX_VALUE),
            section.string("label", "default"),
            section.stringList("tags"),
            section.doubleMap("prices", 0.0, Double.MAX_VALUE));
    }

    @Override
    public List<String> validate(Spec spec, ModuleSection section) {
        return builder.onValidate.apply(spec, section);
    }

    @Override
    public void enable(Spec spec) {
        applied.add(spec);
        builder.onEnable.accept(spec);
    }

    @Override
    public void disable() {
        disableCount++;
        builder.onDisable.run();
    }

    public int disableCount() {
        return disableCount;
    }

    public record Spec(
        boolean enabled,
        double payout,
        String label,
        List<String> tags,
        Map<String, Double> prices
    ) implements ModuleSpec {

        @Override
        public String describe() {
            return "payout=" + payout + " label=" + label;
        }
    }

    /** Hooks that decide how a test module behaves. */
    public static final class SpecBuilder {

        public java.util.function.Consumer<ModuleSection> onParse = section -> { };

        public java.util.function.BiFunction<Spec, ModuleSection, List<String>> onValidate = (spec, section) -> List.of();

        public java.util.function.Consumer<Spec> onEnable = spec -> { };

        public Runnable onDisable = () -> { };

        public SpecBuilder onParse(java.util.function.Consumer<ModuleSection> handler) {
            this.onParse = handler;
            return this;
        }

        public SpecBuilder onValidate(java.util.function.BiFunction<Spec, ModuleSection, List<String>> handler) {
            this.onValidate = handler;
            return this;
        }

        public SpecBuilder onEnable(java.util.function.Consumer<Spec> handler) {
            this.onEnable = handler;
            return this;
        }

        public SpecBuilder onDisable(Runnable handler) {
            this.onDisable = handler;
            return this;
        }
    }
}
