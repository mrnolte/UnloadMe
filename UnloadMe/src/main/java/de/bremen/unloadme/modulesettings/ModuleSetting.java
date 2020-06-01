package de.bremen.unloadme.modulesettings;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.semanticweb.rulewerk.core.model.api.AbstractConstant;
import org.semanticweb.rulewerk.core.model.api.ExistentialVariable;
import org.semanticweb.rulewerk.core.model.api.Fact;
import org.semanticweb.rulewerk.core.model.api.PositiveLiteral;
import org.semanticweb.rulewerk.core.model.api.Predicate;
import org.semanticweb.rulewerk.core.model.api.Rule;
import org.semanticweb.rulewerk.core.model.api.Statement;
import org.semanticweb.rulewerk.core.model.api.Term;
import org.semanticweb.rulewerk.core.model.implementation.Expressions;

import de.bremen.unloadme.datalog.DatalogSignatureMapper;

public interface ModuleSetting {

	static Statement apply(final ModuleSetting setting, final Statement statement, final DatalogSignatureMapper dsm) {
		if (statement instanceof Fact) {
			final Fact fact = (Fact) statement;
			return Expressions.makeFact(fact.getPredicate(), substitute(setting, fact.getArguments().stream(), dsm));
		}
		final Rule rule = (Rule) statement;
		return Expressions.makeRule(
				Expressions.makePositiveConjunction(rule.getHead().getLiterals().stream()
						.map(next -> Expressions.makePositiveLiteral(next.getPredicate(),
								substitute(setting, next.getArguments().stream(), dsm)))
						.collect(Collectors.toList())),
				Expressions.makeConjunction(rule.getBody().getLiterals().stream().map(next -> {
					final List<Term> terms = substitute(setting, next.getArguments().stream(), dsm);
					if (next instanceof PositiveLiteral) {
						return Expressions.makePositiveLiteral(next.getPredicate(), terms);
					}
					return Expressions.makeNegativeLiteral(next.getPredicate(), terms);
				}).collect(Collectors.toList())));
	}

	private static List<Term> substitute(final ModuleSetting setting, final Stream<Term> toSubstitute,
			final DatalogSignatureMapper dsm) {
		return toSubstitute.map(term -> {
			if (term instanceof ExistentialVariable) {
				return setting.substitute((ExistentialVariable) term);
			}
			if (term instanceof AbstractConstant) {
				if (term.equals(dsm.bottomConstant())) {
					return term;
				}
				return setting.substitute((AbstractConstant) term);
			}
			return term;
		}).collect(Collectors.toList());
	}

	/**
	 * Guaranteed to be called after any call of substitute
	 */
	Facts getFacts(Set<Predicate> signature);

	AbstractConstant substitute(AbstractConstant toMap);

	AbstractConstant substitute(ExistentialVariable toMap);
}
