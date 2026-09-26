package sealmc.swe3tie.sealcore

import sealmc.swe3tie.sealcore.config.SealCoreConfig
import sealmc.swe3tie.sealcore.economy.EconomyService
import sealmc.swe3tie.sealcore.economy.provider.ExcellentEconomyBridge
import su.nightexpress.excellenteconomy.api.ExcellentEconomyAPI
import java.util.logging.Logger

/**
 * Builds a real [EconomyService] on top of the ExcellentEconomy stand-in, so
 * tests that need currency behaviour exercise the same provider stack a live
 * server would.
 */
object EconomyFixture {

    val logger: Logger = Logger.getLogger("SealCoreTest")

    fun service(
        api: ExcellentEconomyAPI.Fake = ExcellentEconomyAPI.Fake(),
        config: SealCoreConfig.Economy = SealCoreConfig.Economy(),
    ): EconomyService {
        val service = EconomyService(config, logger)
        val bridge = requireNotNull(ExcellentEconomyBridge.create(logger, api))
        check(service.install(bridge)) { "the service refused a bridge it was just given" }
        return service
    }
}
