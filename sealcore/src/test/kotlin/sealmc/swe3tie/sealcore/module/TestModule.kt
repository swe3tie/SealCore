package sealmc.swe3tie.sealcore.module

/**
 * A module whose spec is just a few values, used to exercise the registry.
 *
 * Deliberately configurable through [SpecBuilder] so one class can stand in for
 * a well behaved module, a throwing module, an invalid one and so on.
 */
class TestModule(
    override val id: String,
    override val schemaVersion: Int = 1,
    override val dependsOn: Set<String> = emptySet(),
    override val restartKeys: Set<String> = emptySet(),
    private val builder: SpecBuilder = SpecBuilder(),
) : SealModule<TestModule.Spec> {

    data class Spec(
        val enabled: Boolean,
        val payout: Double,
        val label: String,
        val tags: List<String>,
        val prices: Map<String, Double>,
    ) : ModuleSpec() {
        override fun describe(): String = "payout=$payout label=$label"
    }

    class SpecBuilder {
        var onParse: (ModuleSection) -> Unit = {}
        var onValidate: (Spec, ModuleSection) -> List<String> = { _, _ -> emptyList() }
        var onEnable: (Spec) -> Unit = {}
        var onDisable: () -> Unit = {}
    }

    val applied = mutableListOf<Spec>()
    var disableCount = 0
        private set

    override fun parse(section: ModuleSection): Spec {
        builder.onParse(section)
        return Spec(
            enabled = section.bool("enabled", true),
            payout = section.double("payout", 10.0, min = 0.0),
            label = section.string("label", "default"),
            tags = section.stringList("tags"),
            prices = section.doubleMap("prices", min = 0.0),
        )
    }

    override fun validate(spec: Spec, section: ModuleSection): List<String> = builder.onValidate(spec, section)

    override fun enable(spec: Spec) {
        applied += spec
        builder.onEnable(spec)
    }

    override fun disable() {
        disableCount++
        builder.onDisable()
    }
}
