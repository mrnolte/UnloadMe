package de.bremen.unloadme.datalog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.Pair;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.rulewerk.core.model.api.AbstractConstant;
import org.semanticweb.rulewerk.core.model.api.Statement;
import org.semanticweb.rulewerk.core.model.api.Term;
import org.semanticweb.rulewerk.core.model.implementation.Expressions;
import org.semanticweb.rulewerk.core.reasoner.KnowledgeBase;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;

import de.bremen.unloadme.Util;
import de.bremen.unloadme.modulesettings.ModuleSetting;

public class ProgramManager {

	private final DatalogSignatureMapper signatureMapper;
	private final DatalogRewriter datalogRewriter;

	private final SetMultimap<OWLAxiom, Statement> datalogMapping;
	private final Map<Statement, Statement> substitutionMapping = new HashMap<>();

	private final SetMultimap<org.semanticweb.rulewerk.core.model.api.Predicate, Statement> topEnrichment = HashMultimap
			.create();
	private final Map<AbstractConstant, Statement> eq1Map = new HashMap<>();
	private final SetMultimap<org.semanticweb.rulewerk.core.model.api.Predicate, Statement> eq2_5Map = HashMultimap
			.create();
	private final Set<Statement> eq3_4Set = new HashSet<>();

	public ProgramManager(final Stream<OWLAxiom> axiomBaseInNormalForm, final DatalogSignatureMapper signatureMapper) {
		datalogRewriter = new DatalogRewriter(signatureMapper);
		this.signatureMapper = signatureMapper;

		datalogMapping = axiomBaseInNormalForm.parallel().collect(Multimaps.flatteningToMultimap(next -> next,
				next -> datalogRewriter.rewrite(next), HashMultimap::create));

		// EQ3
		final var eq3Var1 = signatureMapper.nextUniversalVariable();
		final var eq3Var2 = signatureMapper.nextUniversalVariable();
		eq3_4Set.add(Expressions.makeRule(Expressions.makePositiveLiteral(signatureMapper.sameAs(), eq3Var1, eq3Var2),
				Expressions.makePositiveLiteral(signatureMapper.sameAs(), eq3Var2, eq3Var1)));

		// EQ4
		final var eq4Var1 = signatureMapper.nextUniversalVariable();
		final var eq4Var2 = signatureMapper.nextUniversalVariable();
		final var eq4Var3 = signatureMapper.nextUniversalVariable();
		eq3_4Set.add(Expressions.makeRule(Expressions.makePositiveLiteral(signatureMapper.sameAs(), eq4Var1, eq4Var3),
				Expressions.makePositiveLiteral(signatureMapper.sameAs(), eq4Var1, eq4Var2),
				Expressions.makePositiveLiteral(signatureMapper.sameAs(), eq4Var2, eq4Var3)));
	}

	public void enrich(final KnowledgeBase knowledgeBase) {
		final var predicates = Util.predicates(knowledgeBase.getStatements().stream()).collect(Collectors.toSet());
		if (predicates.contains(signatureMapper.topClassPredicate())) {
			predicates.forEach(next -> knowledgeBase.addStatements(topEnrichment.get(next)));
		}
		if (predicates.contains(signatureMapper.sameAs())) {
			// eq1
			Util.allConstants(knowledgeBase.getStatements().stream()).collect(Collectors.toSet()).stream()
					.forEach(next -> knowledgeBase.addStatement(eq1Map.get(next)));
			// eq3,4
			knowledgeBase.addStatements(eq3_4Set);
			// eq 2,5
			predicates.forEach(next -> knowledgeBase.addStatements(eq2_5Map.get(next)));
		}
	}

	public KnowledgeBase getCompleteDatalogKnowledgeBase() {
		final KnowledgeBase complete = new KnowledgeBase();
		complete.addStatements(substitutionMapping.values());
		return complete;
	}

	private void precalcEnrichment() {
		eq1Map.clear();
		eq2_5Map.clear();
		eq3_4Set.clear();
		topEnrichment.clear();
		final var predicates = Util.predicates(substitutionMapping.values().stream()).collect(Collectors.toSet());
		predicates.remove(signatureMapper.bottomPredicate());
		if (predicates.contains(signatureMapper.topClassPredicate())) {
			predicates.forEach(next -> {
				if (next.getArity() == 1) {
					final var variable = signatureMapper.nextUniversalVariable();
					topEnrichment.put(next,
							Expressions.makeRule(
									Expressions.makePositiveLiteral(signatureMapper.topClassPredicate(), variable),
									Expressions.makePositiveLiteral(next, variable)));
				}
				if (next.getArity() == 2) {
					final var first = signatureMapper.nextUniversalVariable();
					final var second = signatureMapper.nextUniversalVariable();
					topEnrichment.put(next,
							Expressions.makeRule(
									Expressions.makePositiveLiteral(signatureMapper.topClassPredicate(), first),
									Expressions.makePositiveLiteral(next, first, second)));
					topEnrichment.put(next,
							Expressions.makeRule(
									Expressions.makePositiveLiteral(signatureMapper.topClassPredicate(), second),
									Expressions.makePositiveLiteral(next, first, second)));
				}
			});
		}
		if (predicates.contains(signatureMapper.sameAs())) {
			// EQ1

			Util.allConstants(substitutionMapping.values().stream())
					.forEach(next -> eq1Map.put(next, Expressions.makeFact(signatureMapper.sameAs(), next, next)));

			// EQ2
			predicates.forEach(next -> {
				final var terms = IntStream.range(0, next.getArity())
						.mapToObj(i -> (Term) signatureMapper.nextUniversalVariable()).collect(Collectors.toList());
				terms.forEach(t -> eq2_5Map.put(next,
						Expressions.makeRule(Expressions.makePositiveLiteral(signatureMapper.sameAs(), t, t),
								Expressions.makePositiveLiteral(next, terms))));
			});

			// EQ5
			predicates.forEach(next -> {
				final var terms = IntStream.range(0, next.getArity())
						.mapToObj(i -> (Term) signatureMapper.nextUniversalVariable()).collect(Collectors.toList());
				final var y = signatureMapper.nextUniversalVariable();
				final var literal = Expressions.makePositiveLiteral(next, terms);
				IntStream.range(0, next.getArity()).forEach(i -> {
					final var termsWithI = new ArrayList<>(terms);
					termsWithI.set(i, y);
					eq2_5Map.put(next, Expressions.makeRule(Expressions.makePositiveLiteral(next, termsWithI), literal,
							Expressions.makePositiveLiteral(signatureMapper.sameAs(), y, terms.get(i))));
				});
			});

		}
	}

	public Stream<OWLAxiom> reverse(final Stream<Statement> support,
			final SetMultimap<Statement, OWLAxiom> reversingMap) {
		return support.map(reversingMap::get).flatMap(Set::stream);
	}

	public void setModuleSetting(final ModuleSetting moduleSetting) {
		Objects.requireNonNull(moduleSetting);
		substitutionMapping.clear();
		datalogMapping.forEach((key, value) -> {
			substitutionMapping.put(value, ModuleSetting.apply(moduleSetting, value, signatureMapper));
		});
		precalcEnrichment();
	}

	public Pair<SetMultimap<Statement, OWLAxiom>, KnowledgeBase> toDatalogProgram(final Predicate<OWLAxiom> filter) {
		final KnowledgeBase knowledgeBase = new KnowledgeBase();
		final SetMultimap<Statement, OWLAxiom> reversingMap = HashMultimap.create();
		datalogMapping.keySet().stream().filter(filter).forEach(next -> {
			final var rules = datalogMapping.get(next).stream().map(substitutionMapping::get);
			rules.forEach(r -> reversingMap.put(r, next));
		});
		knowledgeBase.addStatements(reversingMap.keySet());
		enrich(knowledgeBase);
		return Pair.of(reversingMap, knowledgeBase);
	}

}
