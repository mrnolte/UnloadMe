package de.bremen.unloadme.datalog;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.semanticweb.rulewerk.core.model.api.AbstractConstant;
import org.semanticweb.rulewerk.core.model.api.Fact;
import org.semanticweb.rulewerk.core.model.api.Literal;
import org.semanticweb.rulewerk.core.model.api.PositiveLiteral;
import org.semanticweb.rulewerk.core.model.api.Rule;
import org.semanticweb.rulewerk.core.model.api.Statement;
import org.semanticweb.rulewerk.core.model.implementation.Expressions;
import org.semanticweb.rulewerk.core.reasoner.KnowledgeBase;
import org.semanticweb.rulewerk.core.reasoner.Reasoner;
import org.semanticweb.rulewerk.reasoner.vlog.VLogReasoner;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;

import de.bremen.unloadme.Util;
import de.bremen.unloadme.modulesettings.Facts;
import de.bremen.unloadme.modulesettings.ModuleSetting;

public class SupportComputer {

	private final DatalogSignatureMapper mapper;

	private final SetMultimap<Statement, Rule> reductionMap = HashMultimap.create();
	private final Map<Statement, AbstractConstant> ruleConstants = new HashMap<>();
	private ModuleSetting moduleSetting;

	public SupportComputer(final DatalogSignatureMapper mapper) {
		this.mapper = mapper;
	}

	private Set<Fact> computeRelevantFactsInMaterialsiation(final KnowledgeBase kB, final Set<Fact> relevantFacts)
			throws IOException {
		try (final Reasoner reasoner = new VLogReasoner(kB)) {
			if (!reasoner.reason()) {
				throw new RuntimeException();
			}
			final var allPredicates = Stream
					.concat(Util.predicates(kB.getRules().stream()), kB.getFacts().stream().map(Fact::getPredicate))
					.collect(Collectors.toSet());
			return relevantFacts.stream().filter(next -> allPredicates.contains(next.getPredicate())).filter(next -> {
				try (var result = reasoner.answerQuery(
						Expressions.makePositiveLiteral(next.getPredicate(), next.getArguments()), false)) {
					return result.hasNext();
				}
			}).collect(Collectors.toSet());
		}
	}

	public Set<Statement> computeSupport(final KnowledgeBase kB, final Facts facts) throws IOException {
		// calculate entailed relevant facts
		kB.addStatements(facts.getInitialFacts());
		kB.addStatements(enrichment(kB, facts.getInitialFacts()));
		final Set<Fact> relevantFactsInMaterialsiation = computeRelevantFactsInMaterialsiation(kB,
				facts.getRelevantFacts());
		
		// construct Δ(𝒟,𝐹)
		// construct Δ(𝒫)
		final KnowledgeBase reduction = new KnowledgeBase();
		reduction.addStatements(kB.getStatements());
		kB.getStatements().stream().flatMap(next -> reductionMap.get(next).stream()).forEach(reduction::addStatement);
		reduction.addStatements(relevantFactsInMaterialsiation.stream()
				.map(next -> Expressions.makeFact(mapper.suppPredicate(next.getPredicate()), next.getArguments()))
				.collect(Collectors.toSet()));

		try (final Reasoner reasoner = new VLogReasoner(reduction)) {
			if (!reasoner.reason()) {
				throw new RuntimeException();
			}
			return kB.getStatements().stream().filter(ruleConstants::containsKey).filter(next -> {
				try (var result = reasoner.answerQuery(
						Expressions.makePositiveLiteral(mapper.suppRel(), ruleConstants.get(next)), false)) {
					return result.hasNext();
				}
			}).collect(Collectors.toSet());
		}
	}

	private Set<Statement> enrichment(final KnowledgeBase kb, final Set<Fact> initialFacts) {
		final Set<Statement> enrichment = new HashSet<>();
		final var predicates = Util.predicates(kb.getStatements().stream()).collect(Collectors.toSet());
		if (predicates.contains(mapper.sameAs())) {
			// EQ1
			Util.allConstants(initialFacts.stream())
					.forEach(next -> enrichment.add(Expressions.makeFact(mapper.sameAs(), next, next)));
		}
		return enrichment;
	}

	public void setModuleSetting(final ModuleSetting moduleSetting, final KnowledgeBase complete) {
		Objects.requireNonNull(moduleSetting);
		if (this.moduleSetting != null && this.moduleSetting.getClass() == moduleSetting.getClass()) {
			return;
		}
		this.moduleSetting = moduleSetting;
		reductionMap.clear();
		ruleConstants.clear();
		complete.getFacts().forEach(next -> {
			final Fact fact = next;
			final var ruleConstant = mapper.suppRuleConstant(next);
			ruleConstants.put(fact, ruleConstant);
			final PositiveLiteral bodyReductionLiteral = Expressions
					.makePositiveLiteral(mapper.suppPredicate(fact.getPredicate()), fact.getArguments());
			reductionMap.put(next, Expressions.makeRule(Expressions.makePositiveLiteral(mapper.suppRel(), ruleConstant),
					bodyReductionLiteral));
		});
		complete.getRules().forEach(next -> {
			final Rule rule = next;
			final var headLiteral = rule.getHead().getLiterals().get(0);
			final PositiveLiteral bodyReductionLiteral = Expressions
					.makePositiveLiteral(mapper.suppPredicate(headLiteral.getPredicate()), headLiteral.getArguments());
			final Literal[] body = new Literal[rule.getBody().getLiterals().size() + 1];
			body[0] = bodyReductionLiteral;
			for (int i = 0; i < rule.getBody().getLiterals().size(); i++) {
				body[i + 1] = rule.getBody().getLiterals().get(i);
			}

			final var ruleConstant = mapper.suppRuleConstant(next);
			ruleConstants.put(rule, ruleConstant);
			final Rule first = Expressions.makeRule(Expressions.makePositiveLiteral(mapper.suppRel(), ruleConstant),
					body);

			final Stream<Rule> others = rule.getBody().getLiterals().stream().map(lit -> Expressions.makeRule(
					Expressions.makePositiveLiteral(mapper.suppPredicate(lit.getPredicate()), lit.getArguments()),
					body));
			reductionMap.put(next, first);
			others.forEach(n -> reductionMap.put(next, n));
		});
	}

}
