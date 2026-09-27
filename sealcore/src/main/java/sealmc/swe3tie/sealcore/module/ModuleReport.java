package sealmc.swe3tie.sealcore.module;

import java.util.List;

/**
 * What happened to one module, for the log and {@code /sealcore debug modules}.
 *
 * <p>{@code problems} names the file and key, so an operator can fix it without
 * reading a stack trace. {@code restartRequired} lists changed keys that only
 * apply on a restart, so a reload never quietly appears to do nothing.
 */
public record ModuleReport(
    String id,
    String file,
    ModuleState state,
    int schemaVersion,
    List<String> problems,
    List<String> restartRequired
) {

    public ModuleReport(String id, String file, ModuleState state) {
        this(id, file, state, 0, List.of(), List.of());
    }

    public boolean isActive() {
        return state == ModuleState.ACTIVE;
    }

    /** Same report with a new state, keeping everything else. */
    public ModuleReport withState(ModuleState state) {
        return new ModuleReport(id, file, state, schemaVersion, problems, restartRequired);
    }

    public ModuleReport withProblems(List<String> problems) {
        return new ModuleReport(id, file, state, schemaVersion, problems, restartRequired);
    }

    public ModuleReport withSchemaVersion(int schemaVersion) {
        return new ModuleReport(id, file, state, schemaVersion, problems, restartRequired);
    }

    public ModuleReport withRestartRequired(List<String> restartRequired) {
        return new ModuleReport(id, file, state, schemaVersion, problems, restartRequired);
    }
}
