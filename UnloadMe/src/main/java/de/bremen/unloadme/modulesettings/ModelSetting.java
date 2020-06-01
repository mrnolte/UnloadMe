package de.bremen.unloadme.modulesettings;

import java.util.HashSet;
import java.util.Set;

import org.semanticweb.rulewerk.core.model.api.AbstractConstant;
import org.semanticweb.rulewerk.core.model.api.ExistentialVariable;
import org.semanticweb.rulewerk.core.model.api.Predicate;

import de.bremen.unloadme.datalog.DatalogSignatureMapper;

public class ModelSetting extends AbstractModuleSetting {
	
	public ModelSetting(final DatalogSignatureMapper signatureMapper) {
		super(signatureMapper);
	}

	@Override
	public Facts getFacts(final Set<Predicate> signature) {
		final var initialFacts = criticalDataset(signature);
		final var relevantFacts = new HashSet<>(initialFacts);
		relevantFacts.add(bottom());
		return new Facts(relevantFacts, initialFacts);
	}
	
	@Override
	public AbstractConstant substitute(final AbstractConstant toMap) {
		return getSignatureMapper().criticalConstant();
	}

	@Override
	public AbstractConstant substitute(final ExistentialVariable toMap) {
		return getSignatureMapper().criticalConstant();
	}

}
