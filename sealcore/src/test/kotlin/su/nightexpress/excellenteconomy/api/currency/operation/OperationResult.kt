package su.nightexpress.excellenteconomy.api.currency.operation

/**
 * Test stand-in for ExcellentEconomy's `OperationResult`.
 *
 * Declared at the upstream package and type name on purpose: the reflective
 * bridge resolves it by name, so this fake pins the exact reflective contract
 * the plugin depends on without vendoring the third party jar.
 */
enum class OperationResult {
    SUCCESS,
    FAILURE,
}
