package de.bremen.unloadme.modulesettings;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.semanticweb.rulewerk.core.model.api.AbstractConstant;
import org.semanticweb.rulewerk.core.model.api.ExistentialVariable;
import org.semanticweb.rulewerk.core.model.api.Fact;
import org.semanticweb.rulewerk.core.model.api.Predicate;
import org.semanticweb.rulewerk.core.model.implementation.Expressions;

import de.bremen.unloadme.datalog.DatalogSignatureMapper;

public class BooleanPeqSetting extends AbstractModuleSetting {
	
	private final Set<AbstractConstant> newExistentalConstants = new HashSet<>();
	
	public BooleanPeqSetting(final DatalogSignatureMapper signatureMapper) {
		super(signatureMapper);
	}
	
	@Override
	public Facts getFacts(final Set<Predicate> signature) {
		final var initialFacts = criticalDataset(signature);

		final var relevantFacts = Stream.concat(Stream.of(bottom()), signature.stream().flatMap(next -> {
			if (next.getArity() == 1) {
				return Stream.concat(Stream.of(Expressions.makeFact(next, getSignatureMapper().criticalConstant())),
						newExistentalConstants.stream().map(c -> Expressions.makeFact(next, c)));
			} else if (next.getArity() == 2) {
				final Set<Fact> allFacts = new HashSet<>();
				final Set<AbstractConstant> allConstants = new HashSet<>(newExistentalConstants);
				allConstants.add(getSignatureMapper().criticalConstant());
				
				for (final AbstractConstant first : allConstants) {
					for (final AbstractConstant second : allConstants) {
						allFacts.add(Expressions.makeFact(next, first, second));
					}
				}
				return allFacts.stream();
			}
			return Stream.empty();
		})).collect(Collectors.toSet());
		return new Facts(relevantFacts, initialFacts);
	}

	@Override
	public AbstractConstant substitute(final AbstractConstant toMap) {
		return getSignatureMapper().criticalConstant();
	}

	@Override
	public AbstractConstant substitute(final ExistentialVariable toMap) {
		final var nEC = getSignatureMapper().newConstant(toMap);
		newExistentalConstants.add(nEC);
		return nEC;
	}
	
}
