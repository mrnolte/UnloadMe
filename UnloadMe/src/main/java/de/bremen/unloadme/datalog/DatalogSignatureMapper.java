package de.bremen.unloadme.datalog;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.Pair;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.rulewerk.core.model.api.AbstractConstant;
import org.semanticweb.rulewerk.core.model.api.Constant;
import org.semanticweb.rulewerk.core.model.api.Entity;
import org.semanticweb.rulewerk.core.model.api.ExistentialVariable;
import org.semanticweb.rulewerk.core.model.api.Predicate;
import org.semanticweb.rulewerk.core.model.api.Statement;
import org.semanticweb.rulewerk.core.model.api.UniversalVariable;
import org.semanticweb.rulewerk.core.model.implementation.Expressions;

import de.bremen.unloadme.SignatureMapper;

public class DatalogSignatureMapper extends SignatureMapper<Entity, String> {

	public final static String TOP_BOTTOM_SAME_NAMESPACE = "UNLOADME_RESERVED_ENTITIES_FOR_TOP_BOTTOM_SAME";
	public final static String EXISTENTIAL_VAR_NAMESPACE = "UNLOADME_RESERVED_ENTITIES_EXISTENTIAL_VARS";
	public final static String UNIVERSAL_VAR_NAMESPACE = "UNLOADME_RESERVED_ENTITIES_UNIVERSAL_VARS";
	public final static String DATALOG_NAMESPACE = "UNLOADME_RESERVED_ENTITIES_DATALOG";
	public final static String IMPLICATION_EXISTENTIAL_VAR_REPLACEMENT = "UNLOADME_RESERVED_ENTITIES_IMPLICATION_EXISTENTIAL_VAR_REPLACEMENT";
	public final static String IMPLICATION_CONSTANT_VECTOR_REPLACEMENT = "UNLOADME_RESERVED_ENTITIES_IMPLICATION_CONSTANT_VECTOR_REPLACEMENT";
	public final static String CRITICAL_CONSTANT = "UNLOADME_RESERVED_ENTITIES_CRITICAL_CONSTANT";
	public final static String SUPP_RULE = "UNLOADME_RESERVED_ENTITIES_SUPP_RULE";
	public final static String SUPP_PREDICATE = "UNLOADME_RESERVED_ENTITIES_SUPP_PREDICATE";
	public final static String SUPP_REL = "UNLOADME_RESERVED_ENTITIES_SUPP_REL";

	public DatalogSignatureMapper() {
		super(Stream.empty());
	}

	public AbstractConstant bottomConstant() {
		return extendDepending(TOP_BOTTOM_SAME_NAMESPACE, "bottomConstant",
				(r, i) -> Expressions.makeAbstractConstant(r + "_" + "bottom_constant" + i));
	}

	public final Predicate bottomPredicate() {
		return extendDepending(TOP_BOTTOM_SAME_NAMESPACE, "bottom",
				(r, i) -> Expressions.makePredicate(r + "_" + "bottom" + i, 1));
	}

	public final AbstractConstant criticalConstant() {
		return extendDepending(CRITICAL_CONSTANT, CRITICAL_CONSTANT,
				(r, i) -> Expressions.makeAbstractConstant(r + "_" + i));
	}

	public final AbstractConstant newConstant(final ExistentialVariable depending) {
		return extendDepending(IMPLICATION_EXISTENTIAL_VAR_REPLACEMENT, depending,
				(r, i) -> Expressions.makeAbstractConstant(r + "_" + i));
	}

	public final List<AbstractConstant> newConstantVector(final Predicate predicate) {
		return IntStream.range(0, predicate.getArity())
				.mapToObj(next -> extendDepending(IMPLICATION_CONSTANT_VECTOR_REPLACEMENT, Pair.of(predicate, next),
						(r, i) -> Expressions.makeAbstractConstant("r" + "_" + next + "_" + i)))
				.collect(Collectors.toList());
	}

	public final ExistentialVariable nextExistentialVariable() {
		return extendFresh(EXISTENTIAL_VAR_NAMESPACE, (r, i) -> Expressions.makeExistentialVariable(r + "_" + i));
	}

	public final UniversalVariable nextUniversalVariable() {
		return extendFresh(UNIVERSAL_VAR_NAMESPACE, (r, i) -> Expressions.makeUniversalVariable(r + "_" + i));
	}

	public Predicate sameAs() {
		return extendDepending(TOP_BOTTOM_SAME_NAMESPACE, "sameAs",
				(r, i) -> Expressions.makePredicate(r + "_" + "sameAs" + i, 2));
	}

	public final Predicate suppPredicate(final Predicate p) {
		return extendDepending(SUPP_PREDICATE, p,
				(r, i) -> Expressions.makePredicate(r + "_" + p.getName() + "_" + i, p.getArity()));
	}

	public final Predicate suppRel() {
		return extendDepending(SUPP_REL, "Rel", (r, i) -> Expressions.makePredicate(r + "_" + i, 1));
	}

	public final AbstractConstant suppRuleConstant(final Statement rule) {
		return extendDepending(SUPP_RULE, rule, (r, i) -> Expressions.makeAbstractConstant(r + "_" + i));
	}

	public final Constant toConstant(final OWLIndividual constant) {
		return extendDepending(DATALOG_NAMESPACE, constant,
				(r, i) -> Expressions.makeAbstractConstant(r + "_" + constant.toStringID() + i));
	}

	public final Predicate topClassPredicate() {
		return extendDepending(TOP_BOTTOM_SAME_NAMESPACE, "topClass",
				(r, i) -> Expressions.makePredicate(r + "_" + "topClass" + i, 1));
	}

	public final Predicate toPredicate(final OWLClass clazz) {
		if (clazz.isTopEntity()) {
			return topClassPredicate();
		}
		if (clazz.isBottomEntity()) {
			return bottomPredicate();
		}
		return extendDepending(DATALOG_NAMESPACE, clazz,
				(r, i) -> Expressions.makePredicate(r + "_" + clazz.toStringID() + i, 1));
	}

	public final Predicate toPredicate(final OWLObjectProperty property) {
		if (property.isBottomEntity()) {
			return bottomPredicate();
		}
		return extendDepending(DATALOG_NAMESPACE, property,
				(r, i) -> Expressions.makePredicate(r + "_" + property.toStringID() + i, 2));
	}

}
