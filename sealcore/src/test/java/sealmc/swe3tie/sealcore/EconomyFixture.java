package sealmc.swe3tie.sealcore;

import java.util.logging.Logger;
import sealmc.swe3tie.sealcore.config.SealCoreConfig;
import sealmc.swe3tie.sealcore.economy.EconomyService;
import sealmc.swe3tie.sealcore.economy.provider.ExcellentEconomyBridge;
import su.nightexpress.excellenteconomy.api.ExcellentEconomyAPI;

/**
 * Builds a real {@link EconomyService} on top of the ExcellentEconomy stand-in, so
 * tests that need currency behaviour exercise the same provider stack a live server
 * would.
 */
public final class EconomyFixture {

    public static final Logger LOGGER = Logger.getLogger("SealCoreTest");

    private EconomyFixture() {
    }

    public static EconomyService service() {
        return service(new ExcellentEconomyAPI.Fake(), new SealCoreConfig.Economy());
    }

    public static EconomyService service(ExcellentEconomyAPI.Fake api) {
        return service(api, new SealCoreConfig.Economy());
    }

    public static EconomyService service(ExcellentEconomyAPI.Fake api, SealCoreConfig.Economy config) {
        EconomyService service = new EconomyService(config, LOGGER);
        ExcellentEconomyBridge bridge = ExcellentEconomyBridge.create(LOGGER, api);
        if (bridge == null) {
            throw new AssertionError("the fixture could not build a bridge for the fake API");
        }
        if (!service.install(bridge)) {
            throw new AssertionError("the service refused a bridge it was just given");
        }
        return service;
    }
}
