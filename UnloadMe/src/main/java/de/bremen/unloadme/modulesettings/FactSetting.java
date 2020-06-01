package de.bremen.unloadme.modulesettings;

import java.util.HashSet;
import java.util.Set;

import org.semanticweb.rulewerk.core.model.api.AbstractConstant;
import org.semanticweb.rulewerk.core.model.api.ExistentialVariable;
import org.semanticweb.rulewerk.core.model.api.Predicate;

import de.bremen.unloadme.datalog.DatalogSignatureMapper;

public class FactSetting extends AbstractModuleSetting {
	
	public FactSetting(final DatalogSignatureMapper signatureMapper) {
		super(signatureMapper);
	}
	
	@Override
	public Facts getFacts(final Set<Predicate> signature) {
		final var criticalDataset = criticalDataset(signature);
		
		final var relevantFacts = new HashSet<>(criticalDataset);
		relevantFacts.add(bottom());
		
		return new Facts(relevantFacts, criticalDataset);
		
	}
	
	@Override
	public AbstractConstant substitute(final AbstractConstant toMap) {
		return getSignatureMapper().criticalConstant();
	}
	
	@Override
	public AbstractConstant substitute(final ExistentialVariable toMap) {
		return getSignatureMapper().newConstant(toMap);
	}
}
