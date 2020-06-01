package de.bremen.unloadme.normalform;

import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.commons.lang3.NotImplementedException;
import org.semanticweb.owlapi.model.ClassExpressionType;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLObjectAllValuesFrom;
import org.semanticweb.owlapi.model.OWLObjectComplementOf;
import org.semanticweb.owlapi.model.OWLObjectHasSelf;
import org.semanticweb.owlapi.model.OWLObjectIntersectionOf;
import org.semanticweb.owlapi.model.OWLObjectInverseOf;
import org.semanticweb.owlapi.model.OWLObjectMaxCardinality;
import org.semanticweb.owlapi.model.OWLObjectMinCardinality;
import org.semanticweb.owlapi.model.OWLObjectSomeValuesFrom;
import org.semanticweb.owlapi.model.OWLObjectUnionOf;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;
import org.semanticweb.owlapi.model.OWLSubObjectPropertyOfAxiom;

public class SubClassRewriter {
	
	private final OWLDataFactory dF;
	
	private final OWLSignatureMapper signatureMapper;
	
	SubClassRewriter(final OWLSignatureMapper signatureMapper, final OWLDataFactory dataFactory) {
		this.signatureMapper = signatureMapper;
		dF = dataFactory;
	}
	
	public OWLDataFactory getOWLDataFactory() {
		return dF;
	}
	
	private boolean isAtomic(final OWLClassExpression toTest) {
//		return toTest.getClassExpressionType() == ClassExpressionType.OWL_CLASS && !toTest.isBottomEntity()
//				&& !toTest.isTopEntity();

		return toTest.getClassExpressionType() == ClassExpressionType.OWL_CLASS;
	}
	
	public Stream<OWLAxiom> rewrite(final OWLSubClassOfAxiom... axioms) {
		return Stream.of(axioms).flatMap(this::rewriteHelp);
	}
	
	private Stream<OWLAxiom> rewrite(final OWLSubObjectPropertyOfAxiom owlSubObjectPropertyOfAxiom,
			final OWLSubClassOfAxiom... axioms) {
		return Stream.concat(Stream.of(owlSubObjectPropertyOfAxiom), rewrite(axioms));
	}
	
	private Stream<OWLAxiom> rewriteHelp(final OWLSubClassOfAxiom axiom) {
		final var subClass = axiom.getSubClass();
		final var superClass = axiom.getSuperClass();
		if (!isAtomic(subClass) && !isAtomic(superClass)) {
			// D_1 ⊑ D_2 (both non atomic) => D_1 ⊑ X, X ⊑ D_2
			final var replacement = signatureMapper.replacement(superClass);
			return rewrite(dF.getOWLSubClassOfAxiom(subClass, replacement),
					dF.getOWLSubClassOfAxiom(replacement, superClass));
		}
		switch (subClass.getClassExpressionType()) {
			case OBJECT_ALL_VALUES_FROM:
				final var asAllValues = (OWLObjectAllValuesFrom) subClass;
				if (asAllValues.getProperty() instanceof OWLObjectInverseOf) {
					// ∀S^−.C_1 ⊑ C_2 => ∀P.C_1 ⊑ C_2, S^− ⊑ P
					final var replacement = signatureMapper.replacement(asAllValues.getProperty());
					return rewrite(
							dF.getOWLSubObjectPropertyOfAxiom(replacement.getInverseProperty(),
									asAllValues.getProperty().getNamedProperty()),
							dF.getOWLSubClassOfAxiom(dF.getOWLObjectAllValuesFrom(replacement, asAllValues.getFiller()),
									superClass));
				} else {
					// ∀S.C_1 ⊑ C_2 => ⊤ ⊑ ∃S.X ⊔ C_2, X ⊓ C_1 ⊑ ⊥
					final var replacement = signatureMapper.freshForLeftAll(asAllValues.getFiller());
					return rewrite(
							dF.getOWLSubClassOfAxiom(dF.getOWLThing(), dF.getOWLObjectUnionOf(
									dF.getOWLObjectSomeValuesFrom(asAllValues.getProperty(), replacement), superClass)),
							dF.getOWLSubClassOfAxiom(
									dF.getOWLObjectIntersectionOf(replacement, asAllValues.getFiller()),
									dF.getOWLNothing()));
				}
			case OBJECT_COMPLEMENT_OF:
				// ¬C_1 ⊑ C_2 => ⊤ ⊑ C_1 ⊔ C_2
				final var asComplement = (OWLObjectComplementOf) subClass;
				return rewrite(dF.getOWLSubClassOfAxiom(dF.getOWLThing(),
						dF.getOWLObjectUnionOf(asComplement.getOperand(), superClass)));
			case OBJECT_HAS_SELF:
				final var asHasSelf = (OWLObjectHasSelf) subClass;
				if (asHasSelf.getProperty() instanceof OWLObjectInverseOf) {
					// ∃S^−.Self ⊑ C => ∃P.Self⊑ C, S^− ⊑ P
					final var replacement = signatureMapper.replacement(asHasSelf.getProperty());
					return rewrite(dF.getOWLSubObjectPropertyOfAxiom(asHasSelf.getProperty(), replacement),
							dF.getOWLSubClassOfAxiom(dF.getOWLObjectHasSelf(replacement), superClass));
				}
				break;
			case OBJECT_INTERSECTION_OF:
				final var asIntersection = (OWLObjectIntersectionOf) subClass;
				final var operands = asIntersection.getOperandsAsList();
				if (!isAtomic(operands.get(0))) {
					// D ⊓ C_1 ⊑ C_2 (D not atomic) => X ⊓ C_1 ⊑ C_2, D ⊑ X
					final var replacement = signatureMapper.replacement(operands.get(0));
					return rewrite(dF.getOWLSubClassOfAxiom(dF.getOWLObjectIntersectionOf(replacement, operands.get(1)),
							superClass), dF.getOWLSubClassOfAxiom(operands.get(0), replacement));
				}
				if (!isAtomic(operands.get(1))) {
					// C_1 ⊓ D ⊑ C_2 (D not atomic) => C_1 ⊓ D ⊑ C_2, D ⊑ X
					final var replacement = signatureMapper.replacement(operands.get(1));
					return rewrite(dF.getOWLSubClassOfAxiom(dF.getOWLObjectIntersectionOf(operands.get(0), replacement),
							superClass), dF.getOWLSubClassOfAxiom(operands.get(1), replacement));
				}
				break;
			case OBJECT_MAX_CARDINALITY:
				final var asMaxCardinality = (OWLObjectMaxCardinality) subClass;
				if (asMaxCardinality.getProperty() instanceof OWLObjectInverseOf) {
					// <=mS^−.C_1 ⊑ C_2 => <=mP.C_1⊑ C_2, S^− ⊑ P
					final var replacement = signatureMapper.replacement(asMaxCardinality.getProperty());
					return rewrite(
							dF.getOWLSubObjectPropertyOfAxiom(replacement.getInverseProperty(),
									asMaxCardinality.getProperty().getNamedProperty()),
							dF.getOWLSubClassOfAxiom(dF.getOWLObjectMaxCardinality(asMaxCardinality.getCardinality(),
									replacement, asMaxCardinality.getFiller()), superClass));
				} else {
					// <=(m-1)S.C_1 ⊑ C_2 => ⊤ ⊑ >=mS.C_1 ⊔ C_2
					return rewrite(dF.getOWLSubClassOfAxiom(dF.getOWLThing(),
							dF.getOWLObjectUnionOf(
									dF.getOWLObjectMinCardinality(asMaxCardinality.getCardinality() + 1,
											asMaxCardinality.getProperty(), asMaxCardinality.getFiller()),
									superClass)));
				}
			case OBJECT_MIN_CARDINALITY:
				final var asMinCardinality = (OWLObjectMinCardinality) subClass;
				if (asMinCardinality.getProperty() instanceof OWLObjectInverseOf) {
					// >=S^−.C_1 ⊑ C_2 => >=P.C_1 ⊑ C_2, S^− ⊑ P
					final var replacement = signatureMapper.replacement(asMinCardinality.getProperty());
					return rewrite(dF.getOWLSubObjectPropertyOfAxiom(asMinCardinality.getProperty(), replacement),
							dF.getOWLSubClassOfAxiom(dF.getOWLObjectMinCardinality(asMinCardinality.getCardinality(),
									replacement, asMinCardinality.getFiller()), superClass));
				}
				// >=mS.C_1 ⊑ C_2 => ⊤ ⊑ <=(m-1)S.C_1 ⊔ C_2
				return rewrite(
						dF.getOWLSubClassOfAxiom(dF.getOWLThing(),
								dF.getOWLObjectUnionOf(
										dF.getOWLObjectMaxCardinality(asMinCardinality.getCardinality() - 1,
												asMinCardinality.getProperty(), asMinCardinality.getFiller()),
										superClass)));
			case OBJECT_ONE_OF:
				return Stream.of(axiom);
			case OBJECT_SOME_VALUES_FROM:
				final var asSomeValues = (OWLObjectSomeValuesFrom) subClass;
				if (asSomeValues.getProperty() instanceof OWLObjectInverseOf) {
					// ∃S^−.C_1 ⊑ C_2 => ∃P.C_1 ⊑ C_2, S^− ⊑ P
					final var replacement = signatureMapper.replacement(asSomeValues.getProperty());
					return rewrite(dF.getOWLSubObjectPropertyOfAxiom(asSomeValues.getProperty(), replacement),
							dF.getOWLSubClassOfAxiom(
									dF.getOWLObjectSomeValuesFrom(replacement, asSomeValues.getFiller()), superClass));
				} else if (!isAtomic(asSomeValues.getFiller())) {
					// ∃P.D ⊑ C (D non atomic) => ∃P.X ⊑ C, D ⊑ X
					final var replacement = signatureMapper.replacement(asSomeValues.getFiller());
					return rewrite(
							dF.getOWLSubClassOfAxiom(
									dF.getOWLObjectSomeValuesFrom(asSomeValues.getProperty(), replacement), superClass),
							dF.getOWLSubClassOfAxiom(asSomeValues.getFiller(), replacement));
				}
				break;
			case OBJECT_UNION_OF:
				final var asUnion = (OWLObjectUnionOf) subClass;
				// C_1 ⊔ C_2 ⊑ C => C_1 ⊑ C, C_2 ⊑ C
				return asUnion.operands().flatMap(next -> rewrite(dF.getOWLSubClassOfAxiom(next, superClass)));
			case OWL_CLASS:
				if (subClass.isOWLNothing()) {
					// ⊥ ⊑ C =>
					return Stream.empty();
				}
				break;
			default:
				throw new NotImplementedException(axiom.toString());
		}
		switch (superClass.getClassExpressionType()) {
			case OBJECT_ALL_VALUES_FROM:
				final var asAllValues = (OWLObjectAllValuesFrom) superClass;
				if (asAllValues.getProperty() instanceof OWLObjectInverseOf) {
					// C_1 ⊑ ∀S^−.C_2 => C_1 ⊑ ∀P.C_2, S^− ⊑ P
					final var replacement = signatureMapper.replacement(asAllValues.getProperty());
					return rewrite(dF.getOWLSubObjectPropertyOfAxiom(asAllValues.getProperty(), replacement),
							dF.getOWLSubClassOfAxiom(subClass,
									dF.getOWLObjectAllValuesFrom(replacement, asAllValues.getFiller())));
				} else {
					// C_1 ⊑ ∀S.C_2 => C_1 ⊓ ∃S.¬C_2 ⊑ ⊥
					return rewrite(dF.getOWLSubClassOfAxiom(
							dF.getOWLObjectIntersectionOf(subClass,
									dF.getOWLObjectSomeValuesFrom(asAllValues.getProperty(),
											dF.getOWLObjectComplementOf(asAllValues.getFiller()))),
							dF.getOWLNothing()));
				}
			case OBJECT_COMPLEMENT_OF:
				// C_1 ⊑ ¬C_2 => C_1 ⊓ C_2 ⊑ ⊥
				final var asComplement = (OWLObjectComplementOf) superClass;
				return rewrite(dF.getOWLSubClassOfAxiom(
						dF.getOWLObjectIntersectionOf(subClass, asComplement.getOperand()), dF.getOWLNothing()));
			case OBJECT_HAS_SELF:
				final var asHasSelf = (OWLObjectHasSelf) superClass;
				if (asHasSelf.getProperty() instanceof OWLObjectInverseOf) {
					// C ⊑ ∃S^−.Self => C ⊑ ∃P.Self, P^− ⊑ S
					final var replacement = signatureMapper.replacement(asHasSelf.getProperty());
					return rewrite(
							dF.getOWLSubObjectPropertyOfAxiom(replacement.getInverseProperty(),
									asHasSelf.getProperty().getNamedProperty()),
							dF.getOWLSubClassOfAxiom(subClass, dF.getOWLObjectHasSelf(replacement)));
				}
				break;
			case OBJECT_INTERSECTION_OF:
				// C_1 ⊑ C_2 ⊓ C_3 => C_1 ⊑ C_2, C_1 ⊑ C_3
				final var asIntersection = (OWLObjectIntersectionOf) superClass;
				return asIntersection.operands().flatMap(next -> rewrite(dF.getOWLSubClassOfAxiom(subClass, next)));
			case OBJECT_MAX_CARDINALITY:
				final var asMaxCardinality = (OWLObjectMaxCardinality) superClass;
				if (asMaxCardinality.getProperty() instanceof OWLObjectInverseOf) {
					// C_1 ⊑ <=mS^−.C_2 => C_1⊑ <=mP.C_2, S^− ⊑ P
					final var replacement = signatureMapper.replacement(asMaxCardinality.getProperty());
					return rewrite(dF.getOWLSubObjectPropertyOfAxiom(asMaxCardinality.getProperty(), replacement),
							dF.getOWLSubClassOfAxiom(subClass, dF.getOWLObjectMaxCardinality(
									asMaxCardinality.getCardinality(), replacement, asMaxCardinality.getFiller())));
				} else if (!isAtomic(asMaxCardinality.getFiller())) {
					// C ⊑ <=mS.D => C ⊑ <=mS.X, X ⊑ D
					final var replacement = signatureMapper.replacement(asMaxCardinality.getFiller());
					return rewrite(dF.getOWLSubClassOfAxiom(subClass, dF.getOWLObjectMaxCardinality(
							asMaxCardinality.getCardinality(), asMaxCardinality.getProperty(), replacement)),
//							dF.getOWLSubClassOfAxiom(replacement, asMaxCardinality.getFiller()));

							dF.getOWLSubClassOfAxiom(asMaxCardinality.getFiller(), replacement));
				}
				break;
			case OBJECT_MIN_CARDINALITY:
				final var asMinCardinality = (OWLObjectMinCardinality) superClass;
				if (asMinCardinality.getProperty() instanceof OWLObjectInverseOf) {
					// C_1 ⊑ >=S^−.C_2 => C_1 ⊑ >=P.C_2, P^− ⊑ S
					final var replacement = signatureMapper.replacement(asMinCardinality.getProperty());
					return rewrite(
							dF.getOWLSubObjectPropertyOfAxiom(replacement.getInverseProperty(),
									asMinCardinality.getProperty().getNamedProperty()),
							dF.getOWLSubClassOfAxiom(subClass, dF.getOWLObjectMinCardinality(
									asMinCardinality.getCardinality(), replacement, asMinCardinality.getFiller())));
				}
				final var allConcepts = IntStream
						.range(1, asMinCardinality.getCardinality()).mapToObj(next -> signatureMapper
								.freshForRightMin(next, asMinCardinality.getProperty(), asMinCardinality.getFiller()))
						.collect(Collectors.toSet());
				
				return allConcepts.stream().flatMap(next -> {
					final var firstTwo = rewrite(
							dF.getOWLSubClassOfAxiom(subClass,
									dF.getOWLObjectSomeValuesFrom(asMinCardinality.getProperty(), next)),
							dF.getOWLSubClassOfAxiom(next, asMinCardinality.getFiller()));
					final var other = allConcepts.stream().filter(o -> !o.equals(next)).map(
							o -> dF.getOWLSubClassOfAxiom(dF.getOWLObjectIntersectionOf(next, o), dF.getOWLNothing()));
					return Stream.concat(firstTwo, other);
				});
			case OBJECT_SOME_VALUES_FROM:
				final var asSomeValues = (OWLObjectSomeValuesFrom) superClass;
				if (asSomeValues.getProperty() instanceof OWLObjectInverseOf) {
					// C_1 ⊑ ∃S^−.C_2 => C_1 ⊑ ∃P.C_2, P^− ⊑ S
					final var replacement = signatureMapper.replacement(asSomeValues.getProperty());
					return rewrite(
							dF.getOWLSubObjectPropertyOfAxiom(replacement.getInverseProperty(),
									asSomeValues.getProperty().getNamedProperty()),
							dF.getOWLSubClassOfAxiom(subClass,
									dF.getOWLObjectSomeValuesFrom(replacement, asSomeValues.getFiller())));
				} else if (!isAtomic(asSomeValues.getFiller())) {
					// C ⊑ ∃P.D (D non atomic) => C ⊑ ∃P.X, X ⊑ D
					final var replacement = signatureMapper.replacement(asSomeValues.getFiller());
					return rewrite(
							dF.getOWLSubClassOfAxiom(subClass,
									dF.getOWLObjectSomeValuesFrom(asSomeValues.getProperty(), replacement)),
							dF.getOWLSubClassOfAxiom(replacement, asSomeValues.getFiller()));
				}
				break;
			case OBJECT_UNION_OF:
				final var asUnion = (OWLObjectUnionOf) superClass;
				final var operands = asUnion.getOperandsAsList();
				if (!isAtomic(operands.get(0))) {
					// C_1 ⊑ D \cup C_2 (D not atomic) => C_1 ⊑ X \cup C_2, X ⊑ D
					final var replacement = signatureMapper.replacement(operands.get(0));
					return rewrite(
							dF.getOWLSubClassOfAxiom(subClass, dF.getOWLObjectUnionOf(replacement, operands.get(1))),
							dF.getOWLSubClassOfAxiom(replacement, operands.get(0)));
				}
				if (!isAtomic(operands.get(1))) {
					// C_1 ⊑ C_2 ⊓ D (D not atomic) => C_1 ⊑ C_2 \cup X, X ⊑ D
					final var replacement = signatureMapper.replacement(operands.get(1));
					return rewrite(
							dF.getOWLSubClassOfAxiom(subClass, dF.getOWLObjectUnionOf(operands.get(0), replacement)),
							dF.getOWLSubClassOfAxiom(replacement, operands.get(1)));
				}
				break;
			case OWL_CLASS:
				if (superClass.isOWLThing()) {
					// C ⊑ ⊤ =>
					return Stream.empty();
				}
				break;
			case OBJECT_ONE_OF:
				break;
			default:
				throw new NotImplementedException(axiom.toString());
		}
		return Stream.of(axiom);
	}
	
}
