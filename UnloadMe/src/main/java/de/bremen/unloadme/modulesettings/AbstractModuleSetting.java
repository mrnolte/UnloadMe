package de.bremen.unloadme.modulesettings;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

import org.semanticweb.rulewerk.core.model.api.Fact;
import org.semanticweb.rulewerk.core.model.api.Predicate;
import org.semanticweb.rulewerk.core.model.api.Term;
import org.semanticweb.rulewerk.core.model.implementation.Expressions;

import de.bremen.unloadme.datalog.DatalogSignatureMapper;

public abstract class AbstractModuleSetting implements ModuleSetting {
	
	private final DatalogSignatureMapper signatureMapper;
	
	public AbstractModuleSetting(final DatalogSignatureMapper signatureMapper) {
		this.signatureMapper = signatureMapper;
	}
	
	protected Fact bottom() {
		return Expressions.makeFact(getSignatureMapper().bottomPredicate(), signatureMapper.bottomConstant());
	}
	
	protected Set<Fact> criticalDataset(final Set<Predicate> signature) {
		return signature.stream()
				.map(next -> Expressions.makeFact(next,
						Collections.nCopies(next.getArity(), signatureMapper.criticalConstant()).toArray(Term[]::new)))
				.collect(Collectors.toSet());
	}
	
	public DatalogSignatureMapper getSignatureMapper() {
		return signatureMapper;
	}
	
}
