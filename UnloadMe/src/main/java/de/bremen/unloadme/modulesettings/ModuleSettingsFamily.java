package de.bremen.unloadme.modulesettings;

import de.bremen.unloadme.datalog.DatalogSignatureMapper;

public interface ModuleSettingsFamily {

	ModuleSetting computeModuleSetting(DatalogSignatureMapper mapper);

}
