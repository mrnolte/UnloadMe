package de.bremen.unloadme.modulesettings;

import de.bremen.unloadme.datalog.DatalogSignatureMapper;

public enum InseparabilityRelation implements ModuleSettingsFamily {

	MODEL {

		@Override
		public ModuleSetting computeModuleSetting(final DatalogSignatureMapper mapper) {
			return new ModelSetting(mapper);
		}
		
		@Override
		public boolean isRobustUnderVocabularyExtension() {
			return true;
		}
	},

	BOOLEAN_PEQ {

		@Override
		public ModuleSetting computeModuleSetting(final DatalogSignatureMapper mapper) {
			return new BooleanPeqSetting(mapper);
		}

		@Override
		public boolean isRobustUnderVocabularyExtension() {
			return true;
		}
	},

	IMPLICATION {

		@Override
		public ModuleSetting computeModuleSetting(final DatalogSignatureMapper mapper) {
			return new ImplicationSetting(mapper);
		}

		@Override
		public boolean isRobustUnderVocabularyExtension() {
			// Unknown at the moment
			return false;
		}
	},

	FACT {

		@Override
		public ModuleSetting computeModuleSetting(final DatalogSignatureMapper mapper) {
			return new FactSetting(mapper);
		}

		@Override
		public boolean isRobustUnderVocabularyExtension() {
			// Unknown at the moment
			return false;
		}

	};

	public abstract boolean isRobustUnderVocabularyExtension();

}
