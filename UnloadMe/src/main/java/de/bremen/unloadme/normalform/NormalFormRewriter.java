package de.bremen.unloadme.normalform;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.NotImplementedException;
import org.semanticweb.owlapi.model.OWLAsymmetricObjectPropertyAxiom;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLAxiomVisitorEx;
import org.semanticweb.owlapi.model.OWLClassAssertionAxiom;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDifferentIndividualsAxiom;
import org.semanticweb.owlapi.model.OWLDisjointClassesAxiom;
import org.semanticweb.owlapi.model.OWLDisjointObjectPropertiesAxiom;
import org.semanticweb.owlapi.model.OWLDisjointUnionAxiom;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLEquivalentClassesAxiom;
import org.semanticweb.owlapi.model.OWLEquivalentObjectPropertiesAxiom;
import org.semanticweb.owlapi.model.OWLFunctionalObjectPropertyAxiom;
import org.semanticweb.owlapi.model.OWLInverseFunctionalObjectPropertyAxiom;
import org.semanticweb.owlapi.model.OWLInverseObjectPropertiesAxiom;
import org.semanticweb.owlapi.model.OWLIrreflexiveObjectPropertyAxiom;
import org.semanticweb.owlapi.model.OWLNegativeObjectPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLObjectInverseOf;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLObjectPropertyDomainAxiom;
import org.semanticweb.owlapi.model.OWLObjectPropertyExpression;
import org.semanticweb.owlapi.model.OWLObjectPropertyRangeAxiom;
import org.semanticweb.owlapi.model.OWLReflexiveObjectPropertyAxiom;
import org.semanticweb.owlapi.model.OWLSameIndividualAxiom;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;
import org.semanticweb.owlapi.model.OWLSubObjectPropertyOfAxiom;
import org.semanticweb.owlapi.model.OWLSubPropertyChainOfAxiom;
import org.semanticweb.owlapi.model.OWLSymmetricObjectPropertyAxiom;
import org.semanticweb.owlapi.model.OWLTransitiveObjectPropertyAxiom;

public class NormalFormRewriter implements OWLAxiomVisitorEx<Stream<OWLAxiom>> {
	
	private final OWLDataFactory dF;
	private final ConceptRewriter cF;
	private final SubClassRewriter sCR;
	private final OWLSignatureMapper signatureMapper;
	
	public NormalFormRewriter(final Stream<OWLEntity> stream, final OWLDataFactory dF) {
		super();
		this.dF = dF;
		signatureMapper = new OWLSignatureMapper(stream, getOWLDataFactory());
		cF = new ConceptRewriter(dF);
		sCR = new SubClassRewriter(signatureMapper, dF);
	}
	
	@Override
	public <T> Stream<OWLAxiom> doDefault(final T object) {
		throw new NotImplementedException("Not supported: " + object);
	}
	
	public OWLDataFactory getOWLDataFactory() {
		return dF;
	}
	
	@Override
	public Stream<OWLAxiom> visit(final OWLAsymmetricObjectPropertyAxiom axiom) {
		// Asym(R) => Disj(R,R^-)
		final OWLObjectPropertyExpression pE = axiom.getProperty();
		return visit(dF.getOWLDisjointObjectPropertiesAxiom(pE, pE.getInverseProperty()));
	}
	
	@Override
	public Stream<OWLAxiom> visit(final OWLClassAssertionAxiom axiom) {
		// A(c) => {c} ⊑ A
		return visit(dF.getOWLSubClassOfAxiom(dF.getOWLObjectOneOf(axiom.getIndividual()), axiom.getClassExpression()));
	}
	
	@Override
	public Stream<OWLAxiom> visit(final OWLDifferentIndividualsAxiom axiom) {
		// Disj(c_1,...,c_n) => {c_i} ⊓ {c_j} ⊑ ⊥ for all 1<=i,j<=n
		return axiom.asPairwiseAxioms().stream().map(next -> {
			// Disj(c_i,c_j) => {c_i} ⊓ {c_j} ⊑ ⊥
			final var listOfBothIndividuals = next.getOperandsAsList();
			final var first = listOfBothIndividuals.get(0);
			final var second = listOfBothIndividuals.get(1);
			return dF.getOWLSubClassOfAxiom(
					dF.getOWLObjectIntersectionOf(dF.getOWLObjectOneOf(first), dF.getOWLObjectOneOf(second)),
					dF.getOWLNothing());
		}).flatMap(this::visit);
	}
	
	@Override
	public Stream<OWLAxiom> visit(final OWLDisjointClassesAxiom axiom) {
		// Disj(C_1,...,C_n) => C_i ⊓ C_j ⊑ ⊥ for all 1<=i,j<=n
		return axiom.asPairwiseAxioms().stream().map(next -> {
			// Disj(C_1,C_2) => C_1 ⊓ C_2 ⊑ ⊥
			final var listOfBothClasses = next.getOperandsAsList();
			final var first = listOfBothClasses.get(0);
			final var second = listOfBothClasses.get(1);
			return dF.getOWLSubClassOfAxiom(dF.getOWLObjectIntersectionOf(first, second), dF.getOWLNothing());
		}).flatMap(this::visit);
	}
	
	@Override
	public Stream<OWLAxiom> visit(final OWLDisjointObjectPropertiesAxiom axiom) {
		// Disj(R_1,...,R_n) => Disj(R_i,R_j) for all 1<=i,j<=n
		return axiom.asPairwiseAxioms().stream().flatMap(next -> {
			final var listOfBothProperties = next.getOperandsAsList();
			final var first = listOfBothProperties.get(0);
			final var second = listOfBothProperties.get(1);
			if (first instanceof OWLObjectInverseOf) {
				// Disj(R_1^-,R_2) => Disj(Q,R_2), R_1^- ⊑ Q
				final OWLObjectProperty replacement = signatureMapper.replacement(first);
				return visitAll(dF.getOWLDisjointObjectPropertiesAxiom(replacement, second),
						dF.getOWLSubObjectPropertyOfAxiom(first, replacement));
			}
			if (second instanceof OWLObjectInverseOf) {
				// Disj(R_1,R_2^-) => Disj(R_1,Q), R_2^- ⊑ Q
				final OWLObjectProperty replacement = signatureMapper.replacement(second);
				return visitAll(dF.getOWLDisjointObjectPropertiesAxiom(first, replacement),
						dF.getOWLSubObjectPropertyOfAxiom(second, replacement));
			}
			return Stream.of(next);
		});
	}
	
	@Override
	public Stream<OWLAxiom> visit(final OWLDisjointUnionAxiom axiom) {
		// DisjUnion(C,C_1,...,C_n) => Disj(C_1,...,C_n), C ≡ C_1 ⊔ ... ⊔ C_n
		return visitAll(axiom.getOWLDisjointClassesAxiom(), axiom.getOWLEquivalentClassesAxiom());
	}
	
	@Override
	public Stream<OWLAxiom> visit(final OWLEquivalentClassesAxiom axiom) {
		// Equiv(C_1,...,C_n) => C_i ⊑ C_j for all 1<=i,j<=n
		return axiom.asPairwiseAxioms().stream().flatMap(next -> {
			// Equiv(C_1,C_2) => C_1 ⊑ C_2, C_2 ⊑ C_1
			final var listOfBothClassExpressions = next.getOperandsAsList();
			final var first = listOfBothClassExpressions.get(0);
			final var second = listOfBothClassExpressions.get(1);
			return visitAll(dF.getOWLSubClassOfAxiom(first, second), dF.getOWLSubClassOfAxiom(second, first));
		});
	}
	
	@Override
	public Stream<OWLAxiom> visit(final OWLEquivalentObjectPropertiesAxiom axiom) {
		// Equiv(R_1,...,R_n) => R_i ⊑ R_j for all 1<=i,j<=n
		return axiom.asPairwiseAxioms().stream().flatMap(next -> {
			// Equiv(R_1,R_2) => R_1 ⊑ R_2, R_2 ⊑ R_1
			final var listOfBothProperties = next.getOperandsAsList();
			final var first = listOfBothProperties.get(0);
			final var second = listOfBothProperties.get(1);
			return visitAll(dF.getOWLSubObjectPropertyOfAxiom(first, second),
					dF.getOWLSubObjectPropertyOfAxiom(second, first));
		});
	}
	
	@Override
	public Stream<OWLAxiom> visit(final OWLFunctionalObjectPropertyAxiom axiom) {
		// Func(R) => ⊤ ⊑ (<=1 R)
		return visit(dF.getOWLSubClassOfAxiom(dF.getOWLThing(), dF.getOWLObjectMaxCardinality(1, axiom.getProperty())));
	}
	
	@Override
	public Stream<OWLAxiom> visit(final OWLInverseFunctionalObjectPropertyAxiom axiom) {
		// InvFunc(R) => ⊤ ⊑ (<=1 R^-)
		return visit(dF.getOWLSubClassOfAxiom(dF.getOWLThing(),
				dF.getOWLObjectMaxCardinality(1, axiom.getProperty().getInverseProperty())));
	}
	
	@Override
	public Stream<OWLAxiom> visit(final OWLInverseObjectPropertiesAxiom axiom) {
		// Inv(R_1, R_2) => Equiv(R_1,R_2^-)
		return visit(dF.getOWLEquivalentObjectPropertiesAxiom(axiom.getFirstProperty(),
				axiom.getSecondProperty().getInverseProperty()));
	}
	
	@Override
	public Stream<OWLAxiom> visit(final OWLIrreflexiveObjectPropertyAxiom axiom) {
		// IrrRef(R) => ∃R.Self ⊑⊥
		return visit(dF.getOWLSubClassOfAxiom(dF.getOWLObjectHasSelf(axiom.getProperty()), dF.getOWLNothing()));
	}
	
	@Override
	public Stream<OWLAxiom> visit(final OWLNegativeObjectPropertyAssertionAxiom axiom) {
		// ¬R(a,b) => {a} ⊑ ¬∃R.{b}
		return visit(dF.getOWLSubClassOfAxiom(dF.getOWLObjectOneOf(axiom.getSubject()),
				dF.getOWLObjectComplementOf(dF.getOWLObjectHasValue(axiom.getProperty(), axiom.getObject()))));
	}
	
	@Override
	public Stream<OWLAxiom> visit(final OWLObjectPropertyAssertionAxiom axiom) {
		// R(a,b) => {a} ⊑ ∃R.{b}
		return visit(dF.getOWLSubClassOfAxiom(dF.getOWLObjectOneOf(axiom.getSubject()),
				dF.getOWLObjectHasValue(axiom.getProperty(), axiom.getObject())));
	}
	
	@Override
	public Stream<OWLAxiom> visit(final OWLObjectPropertyDomainAxiom axiom) {
		// Domain(R,C) => ∃R.⊤⊑C
		return visit(dF.getOWLSubClassOfAxiom(dF.getOWLObjectSomeValuesFrom(axiom.getProperty(), dF.getOWLThing()),
				axiom.getDomain()));
	}
	
	@Override
	public Stream<OWLAxiom> visit(final OWLObjectPropertyRangeAxiom axiom) {
		// Range(R,C) => ⊤⊑∀R.C
		final var result = visit(dF.getOWLSubClassOfAxiom(dF.getOWLThing(),
				dF.getOWLObjectAllValuesFrom(axiom.getProperty(), axiom.getRange()))).collect(Collectors.toSet());
		return result.stream();
		
	}
	
	@Override
	public Stream<OWLAxiom> visit(final OWLReflexiveObjectPropertyAxiom axiom) {
		final OWLObjectPropertyExpression property = axiom.getProperty();
		if (property instanceof OWLObjectInverseOf) {
			// Ref(R^-) => Ref(R)
			return Stream.of(dF.getOWLReflexiveObjectPropertyAxiom(property.getNamedProperty()));
		}
		return Stream.of(axiom);
	}
	
	@Override
	public Stream<OWLAxiom> visit(final OWLSameIndividualAxiom axiom) {
		// Equiv(c_1,...,c_n) => {c_i} ⊑ {c_j} for all 1<=i,j<=n
		return axiom.asPairwiseAxioms().stream().map(next -> {
			// Equiv(c_i,c_j) => {c_i} ⊑ {c_j}
			final var listOfBothIndividuals = next.getOperandsAsList();
			final var first = listOfBothIndividuals.get(0);
			final var second = listOfBothIndividuals.get(1);
			return dF.getOWLEquivalentClassesAxiom(dF.getOWLObjectOneOf(first), dF.getOWLObjectOneOf(second));
		}).flatMap(this::visit);
	}
	
	@Override
	public Stream<OWLAxiom> visit(final OWLSubClassOfAxiom axiom) {
		return sCR.rewrite(dF.getOWLSubClassOfAxiom(axiom.getSubClass().accept(cF), axiom.getSuperClass().accept(cF)));
	}
	
	@Override
	public Stream<OWLAxiom> visit(final OWLSubObjectPropertyOfAxiom axiom) {
		final var first = axiom.getSubProperty();
		final var second = axiom.getSuperProperty();
		if (second instanceof OWLObjectInverseOf) {
			// R_1 ⊑ R_2^- => R_1^- ⊑ R_2
			return visit(dF.getOWLSubObjectPropertyOfAxiom(first.getInverseProperty(),
					((OWLObjectInverseOf) second).getInverse()));
		}
		if (first instanceof OWLObjectInverseOf) {
			final var firstInverse = ((OWLObjectInverseOf) first).getInverse();
			if (firstInverse instanceof OWLObjectInverseOf) {
				// (R_1^-)^- ⊑ R_2 => R_1 ⊑ R_2
				// can this case actually arise?
				return visit(
						dF.getOWLSubObjectPropertyOfAxiom(((OWLObjectInverseOf) firstInverse).getInverse(), second));
			}
		}
		return Stream.of(axiom);
	}
	
	@Override
	public Stream<OWLAxiom> visit(final OWLSubPropertyChainOfAxiom axiom) {
		final var chain = axiom.getPropertyChain();
		if (chain.size() == 1) {
			// R_1 ∘ ⊑ S => R_1 ⊑ S
			return visit(dF.getOWLSubObjectPropertyOfAxiom(chain.get(0), axiom.getSuperProperty()));
		}
		if (chain.size() > 2) {
			// R_1 ∘ R_2 ∘ ... ∘ R_n ⊑ S => P ∘ ... ∘ R_n ⊑ S, R_1 ∘ R_2 ⊑ P
			final List<OWLObjectPropertyExpression> newChain = new ArrayList<>(chain.size() - 1);
			final var replaced = chain.subList(0, 2);
			final var replacement = signatureMapper.replacement(replaced);
			newChain.add(replacement);
			newChain.addAll(chain.subList(2, chain.size()));
			return visitAll(dF.getOWLSubPropertyChainOfAxiom(newChain, axiom.getSuperProperty()),
					dF.getOWLSubPropertyChainOfAxiom(replaced, replacement));
		}
		if (chain.get(0) instanceof OWLObjectInverseOf) {
			// R_1^- ∘ R_2 ⊑ S => P ∘ R_2 ⊑ S R_1^- ⊑ P
			final var replacement = signatureMapper.replacement(chain.get(0));
			return visitAll(
					dF.getOWLSubPropertyChainOfAxiom(List.of(replacement, chain.get(1)), axiom.getSuperProperty()),
					dF.getOWLSubObjectPropertyOfAxiom(chain.get(0), replacement));
		}
		if (chain.get(1) instanceof OWLObjectInverseOf) {
			// R_1 ∘ R_2^- ⊑ S => R_1 ∘ P ⊑ S R_2^- ⊑ P
			final var replacement = signatureMapper.replacement(chain.get(1));
			return visitAll(
					dF.getOWLSubPropertyChainOfAxiom(List.of(chain.get(0), replacement), axiom.getSuperProperty()),
					dF.getOWLSubObjectPropertyOfAxiom(chain.get(1), replacement));
		}
		return Stream.of(axiom);
	}
	
	@Override
	public Stream<OWLAxiom> visit(final OWLSymmetricObjectPropertyAxiom axiom) {
		// Symm(R) => R ⊑ R^-
		return visit(dF.getOWLSubObjectPropertyOfAxiom(axiom.getProperty(), axiom.getProperty().getInverseProperty()));
	}
	
	@Override
	public Stream<OWLAxiom> visit(final OWLTransitiveObjectPropertyAxiom axiom) {
		// Trans(R) => R ∘ R ⊑ R
		return visit(dF.getOWLSubPropertyChainOfAxiom(List.of(axiom.getProperty(), axiom.getProperty()),
				axiom.getProperty()));
	}
	
	public Stream<OWLAxiom> visitAll(final OWLAxiom... axioms) {
		return Stream.of(axioms).flatMap(next -> next.accept(this));
	}
	
}