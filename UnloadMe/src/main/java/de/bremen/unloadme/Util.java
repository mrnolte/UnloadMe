package de.bremen.unloadme;

import java.util.Set;
import java.util.stream.Stream;

import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.rulewerk.core.model.api.AbstractConstant;
import org.semanticweb.rulewerk.core.model.api.Fact;
import org.semanticweb.rulewerk.core.model.api.Predicate;
import org.semanticweb.rulewerk.core.model.api.Rule;
import org.semanticweb.rulewerk.core.model.api.Statement;

import com.google.common.collect.Sets;

public class Util {
	
	public final static Set<AxiomType<?>> SUPPORTED_AXIOM_TYPES = Sets.newHashSet(AxiomType.ASYMMETRIC_OBJECT_PROPERTY,
			AxiomType.CLASS_ASSERTION, AxiomType.DIFFERENT_INDIVIDUALS, AxiomType.DISJOINT_CLASSES,
			AxiomType.DISJOINT_CLASSES, AxiomType.DISJOINT_OBJECT_PROPERTIES, AxiomType.DISJOINT_UNION,
			AxiomType.EQUIVALENT_CLASSES, AxiomType.EQUIVALENT_OBJECT_PROPERTIES, AxiomType.FUNCTIONAL_OBJECT_PROPERTY,
			AxiomType.INVERSE_FUNCTIONAL_OBJECT_PROPERTY, AxiomType.INVERSE_OBJECT_PROPERTIES,
			AxiomType.IRREFLEXIVE_OBJECT_PROPERTY, AxiomType.NEGATIVE_OBJECT_PROPERTY_ASSERTION,
			AxiomType.OBJECT_PROPERTY_ASSERTION, AxiomType.OBJECT_PROPERTY_DOMAIN, AxiomType.OBJECT_PROPERTY_RANGE,
			AxiomType.REFLEXIVE_OBJECT_PROPERTY, AxiomType.SAME_INDIVIDUAL, AxiomType.SUB_OBJECT_PROPERTY,
			AxiomType.SUB_PROPERTY_CHAIN_OF, AxiomType.SUBCLASS_OF, AxiomType.SYMMETRIC_OBJECT_PROPERTY,
			AxiomType.TRANSITIVE_OBJECT_PROPERTY);
	
	public final static Stream<AbstractConstant> allConstants(final Stream<? extends Statement> statements) {
		return statements.flatMap(next -> {
			if (next instanceof Rule) {
				final var rule = (Rule) next;
				return rule.getAbstractConstants();
			}
			if (next instanceof Fact) {
				final var fact = (Fact) next;
				return fact.getAbstractConstants();
			}
			return Stream.empty();
		});
	}
	
	public final static Stream<OWLAxiom> cleanAxiomBase(final Stream<OWLAxiom> toClear) {
		return toClear.filter(Util::isSupportedAxiom);
	}
	
	public final static Stream<OWLEntity> cleanSignature(final Stream<OWLEntity> toClean) {
		return toClean.filter(next -> (next.isOWLClass() || next.isOWLObjectProperty()) && !next.isBottomEntity()
				&& !next.isTopEntity());
	}
	
	public final static boolean isSupportedAxiom(final OWLAxiom axiom) {
		return SUPPORTED_AXIOM_TYPES.contains(axiom.getAxiomType()) && axiom.datatypesInSignature().count() == 0
				&& axiom.dataPropertiesInSignature().count() == 0
				&& axiom.objectPropertiesInSignature().noneMatch(next -> next.isTopEntity() || next.isBottomEntity());
	}
	
	public final static Stream<Predicate> predicates(final Stream<? extends Statement> stream) {
		return stream.flatMap(next -> {
			if (next instanceof Rule) {
				final var rule = (Rule) next;
				return Stream.concat(rule.getBody().getLiterals().stream().map(lit -> lit.getPredicate()),
						rule.getHead().getLiterals().stream().map(lit -> lit.getPredicate())).distinct();
			}
			if (next instanceof Fact) {
				final var fact = (Fact) next;
				return Stream.of(fact.getPredicate());
			}
			return Stream.empty();
		});
		
	}
	
	private Util() {
		
	}
	
}
