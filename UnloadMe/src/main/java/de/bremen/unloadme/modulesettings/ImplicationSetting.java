package de.bremen.unloadme.modulesettings;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.semanticweb.rulewerk.core.model.api.AbstractConstant;
import org.semanticweb.rulewerk.core.model.api.ExistentialVariable;
import org.semanticweb.rulewerk.core.model.api.Fact;
import org.semanticweb.rulewerk.core.model.api.Predicate;
import org.semanticweb.rulewerk.core.model.api.Term;
import org.semanticweb.rulewerk.core.model.implementation.Expressions;

import de.bremen.unloadme.datalog.DatalogSignatureMapper;

public class ImplicationSetting extends AbstractModuleSetting {

	public ImplicationSetting(final DatalogSignatureMapper signatureMapper) {
		super(signatureMapper);

	}

	@Override
	public Facts getFacts(final Set<Predicate> signature) {
		final Map<Predicate, List<Term>> newPredicateArity1Vectors = new HashMap<>();
		final Map<Predicate, List<Term>> newPredicateArity2Vectors = new HashMap<>();
		signature.forEach(next -> {
			final List<Term> vector = getSignatureMapper().newConstantVector(next).stream().map(n -> (Term) n)
					.collect(Collectors.toList());
			if (next.getArity() == 1) {
				newPredicateArity1Vectors.put(next, vector);
			} else if (next.getArity() == 2) {
				newPredicateArity2Vectors.put(next, vector);
			}
		});
		final var relevantFacts = Stream
				.concat(Stream.concat(Stream.of(bottom()), relevantFacts(newPredicateArity1Vectors)),
						relevantFacts(newPredicateArity2Vectors))
				.collect(Collectors.toSet());

		final var initialFacts = Stream.concat(
				newPredicateArity1Vectors.entrySet().stream()
						.map(next -> Expressions.makeFact(next.getKey(), next.getValue())),
				newPredicateArity2Vectors.entrySet().stream()
						.map(next -> Expressions.makeFact(next.getKey(), next.getValue())))
				.collect(Collectors.toSet());
		if (signature.contains(getSignatureMapper().bottomPredicate())) {
			initialFacts.add(bottom());
		}
		return new Facts(relevantFacts, initialFacts);
	}
	
	private Stream<Fact> relevantFacts(final Map<Predicate, List<Term>> map) {
		return map.keySet().stream().flatMap(p1 -> map.keySet().stream().filter(p2 -> !p2.equals(p1))
				.map(p2 -> Expressions.makeFact(p1, map.get(p2))));
	}
	
	@Override
	public AbstractConstant substitute(final AbstractConstant toMap) {
		return toMap;
	}

	@Override
	public AbstractConstant substitute(final ExistentialVariable toMap) {
		return getSignatureMapper().newConstant(toMap);
	}
}
