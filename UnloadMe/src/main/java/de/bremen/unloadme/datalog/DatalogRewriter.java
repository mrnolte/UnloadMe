package de.bremen.unloadme.datalog;

import static java.util.stream.Stream.of;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLAxiomVisitorEx;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDisjointObjectPropertiesAxiom;
import org.semanticweb.owlapi.model.OWLObjectHasSelf;
import org.semanticweb.owlapi.model.OWLObjectIntersectionOf;
import org.semanticweb.owlapi.model.OWLObjectInverseOf;
import org.semanticweb.owlapi.model.OWLObjectMaxCardinality;
import org.semanticweb.owlapi.model.OWLObjectOneOf;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectSomeValuesFrom;
import org.semanticweb.owlapi.model.OWLObjectUnionOf;
import org.semanticweb.owlapi.model.OWLReflexiveObjectPropertyAxiom;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;
import org.semanticweb.owlapi.model.OWLSubObjectPropertyOfAxiom;
import org.semanticweb.owlapi.model.OWLSubPropertyChainOfAxiom;
import org.semanticweb.rulewerk.core.model.api.ExistentialVariable;
import org.semanticweb.rulewerk.core.model.api.Fact;
import org.semanticweb.rulewerk.core.model.api.PositiveLiteral;
import org.semanticweb.rulewerk.core.model.api.Rule;
import org.semanticweb.rulewerk.core.model.api.Statement;
import org.semanticweb.rulewerk.core.model.api.Term;
import org.semanticweb.rulewerk.core.model.api.UniversalVariable;
import org.semanticweb.rulewerk.core.model.implementation.Expressions;

public class DatalogRewriter implements OWLAxiomVisitorEx<Stream<? extends Statement>> {
	
	// TODO nochmal angucken
	
	private final DatalogSignatureMapper signatureMapper;
	
	public DatalogRewriter(final DatalogSignatureMapper mapper) {
		signatureMapper = mapper;
	}
	
	private PositiveLiteral bottom() {
		return Expressions.makePositiveLiteral(signatureMapper.bottomPredicate(), signatureMapper.bottomConstant());
	}
	
	@Override
	public <T> Stream<Rule> doDefault(final T object) {
		throw new IllegalArgumentException("Illegal :" + object);
	}
	
	private final Fact fact(final OWLClass clazz, final Term term) {
		if (clazz.isTopEntity()) {
			return Expressions.makeFact(signatureMapper.topClassPredicate(), term);
		}
		if (clazz.isBottomEntity()) {
			return Expressions.makeFact(signatureMapper.bottomPredicate());
		}
		return Expressions.makeFact(signatureMapper.toPredicate(clazz), term);
	}
	
	private final ExistentialVariable nextExistentialVariable() {
		return signatureMapper.nextExistentialVariable();
	}
	
	private final UniversalVariable nextUniversalVariable() {
		return signatureMapper.nextUniversalVariable();
	}
	
	private final PositiveLiteral positiveLiteral(final OWLClass clazz, final Term term) {
		if (clazz.isTopEntity()) {
			return top(term);
		}
		if (clazz.isBottomEntity()) {
			return bottom();
		}
		return Expressions.makePositiveLiteral(signatureMapper.toPredicate(clazz), term);
	}
	
	private final PositiveLiteral positiveLiteral(final OWLObjectProperty property, final Term first,
			final Term second) {
		if (property.isBottomEntity()) {
			return bottom();
		}
		return Expressions.makePositiveLiteral(signatureMapper.toPredicate(property), first, second);
	}
	
	public Stream<? extends Statement> rewrite(final OWLAxiom axiom) {
		return axiom.accept(this);
	}
	
	private PositiveLiteral sameAs(final Term first, final Term second) {
		return Expressions.makePositiveLiteral(signatureMapper.sameAs(), first, second);
	}
	
	private PositiveLiteral top(final Term term) {
		return Expressions.makePositiveLiteral(signatureMapper.topClassPredicate(), term);
	}
	
	@Override
	public Stream<Rule> visit(final OWLDisjointObjectPropertiesAxiom axiom) {
		final var uniFirst = nextUniversalVariable();
		final var uniSecond = nextUniversalVariable();
		final var properties = axiom.getOperandsAsList();
		return of(Expressions.makeRule(bottom(),
				positiveLiteral(properties.get(0).asOWLObjectProperty(), uniFirst, uniSecond),
				positiveLiteral(properties.get(1).asOWLObjectProperty(), uniFirst, uniSecond)));
	}
	
	@Override
	public Stream<Rule> visit(final OWLReflexiveObjectPropertyAxiom axiom) {
		final var uniVar = nextUniversalVariable();
		return of(Expressions.makeRule(positiveLiteral(axiom.getProperty().asOWLObjectProperty(), uniVar, uniVar),
				top(uniVar)));
	}
	
	@Override
	public Stream<Statement> visit(final OWLSubClassOfAxiom axiom) {
		final var subclass = axiom.getSubClass();
		final var superclass = axiom.getSuperClass();
		
		if (subclass instanceof OWLClass) {
			final var uniFirst = nextUniversalVariable();
			final var subclassLiteral = positiveLiteral((OWLClass) subclass, uniFirst);
			switch (superclass.getClassExpressionType()) {
				case OWL_CLASS:
					final var superAsClass = (OWLClass) superclass;
					if (superAsClass.isBottomEntity()) {
						return of(Expressions.makeRule(bottom(), subclassLiteral));
					}
					return of(Expressions.makeRule(positiveLiteral(superAsClass, uniFirst), subclassLiteral));
				case OBJECT_ONE_OF:
					final var asOneOf = (OWLObjectOneOf) superclass;
					return of(Expressions.makeRule(
							sameAs(uniFirst, signatureMapper.toConstant(asOneOf.operands().findFirst().get())),
							subclassLiteral));
				case OBJECT_UNION_OF:
					final var asUnion = (OWLObjectUnionOf) superclass;
					return of(
							Expressions.makeRule(
									positiveLiteral(asUnion.getOperandsAsList().get(0).asOWLClass(), uniFirst),
									subclassLiteral),
							Expressions.makeRule(
									positiveLiteral(asUnion.getOperandsAsList().get(1).asOWLClass(), uniFirst),
									subclassLiteral));
				case OBJECT_SOME_VALUES_FROM:
					final var asSomeValues = (OWLObjectSomeValuesFrom) superclass;
					final var existential = nextExistentialVariable();
					return of(
							Expressions.makeRule(positiveLiteral(asSomeValues.getProperty().asOWLObjectProperty(),
									uniFirst, existential), subclassLiteral),
							Expressions.makeRule(positiveLiteral(asSomeValues.getFiller().asOWLClass(), existential),
									subclassLiteral));
				case OBJECT_HAS_SELF:
					final var asHasSelf = (OWLObjectHasSelf) superclass;
					return of(Expressions.makeRule(
							positiveLiteral(asHasSelf.getProperty().asOWLObjectProperty(), uniFirst, uniFirst),
							subclassLiteral));
				case OBJECT_MAX_CARDINALITY:
					final var asMaxCardinality = (OWLObjectMaxCardinality) superclass;
					final Set<Statement> rules = new HashSet<>();
					final Map<Integer, Term> ithConstant = IntStream.range(0, asMaxCardinality.getCardinality() + 1)
							.mapToObj(next -> (Integer) next)
							.collect(Collectors.toMap(next -> next, next -> nextUniversalVariable()));
					final Stream<PositiveLiteral> conjunctions = Stream.concat(Stream.of(subclassLiteral),
							ithConstant.values().stream()
									.flatMap(next -> Stream.of(
											positiveLiteral(asMaxCardinality.getProperty().asOWLObjectProperty(),
													uniFirst, next),
											positiveLiteral(asMaxCardinality.getFiller().asOWLClass(), next))));
					final PositiveLiteral[] body = conjunctions.toArray(PositiveLiteral[]::new);
					for (int i = 0; i <= asMaxCardinality.getCardinality(); i++) {
						for (int j = 0; j <= asMaxCardinality.getCardinality(); j++) {
							if (i == j) {
								continue;
							}
							rules.add(Expressions.makeRule(sameAs(ithConstant.get(i), ithConstant.get(j)), body));
						}
					}
					return rules.stream();
				default:
					break;
			}
		}
		
		if (superclass instanceof OWLClass) {
			final var uniSecond = nextUniversalVariable();
			final var superclassLiteral = positiveLiteral((OWLClass) superclass, uniSecond);
			switch (subclass.getClassExpressionType()) {
				case OWL_CLASS:
					final var subAsClass = (OWLClass) subclass;
					if (subAsClass.isTopEntity()) {
						return of(Expressions.makeRule(superclassLiteral, top(uniSecond)));
					}
					break;
				case OBJECT_ONE_OF:
					final var asOneOf = (OWLObjectOneOf) subclass;
					return of(fact((OWLClass) superclass,
							signatureMapper.toConstant(asOneOf.getOperandsAsList().get(0))));
				case OBJECT_INTERSECTION_OF:
					final var asIntersection = (OWLObjectIntersectionOf) subclass;
					return of(Expressions.makeRule(superclassLiteral,
							positiveLiteral(asIntersection.getOperandsAsList().get(0).asOWLClass(), uniSecond),
							positiveLiteral(asIntersection.getOperandsAsList().get(1).asOWLClass(), uniSecond)));
				case OBJECT_SOME_VALUES_FROM:
					final var asSomeValues = (OWLObjectSomeValuesFrom) subclass;
					final var uniExist = nextUniversalVariable();
					return of(Expressions.makeRule(superclassLiteral,
							positiveLiteral(asSomeValues.getProperty().asOWLObjectProperty(), uniSecond, uniExist),
							positiveLiteral(asSomeValues.getFiller().asOWLClass(), uniExist)));
				case OBJECT_HAS_SELF:
					final var asHasSelf = (OWLObjectHasSelf) subclass;
					return of(Expressions.makeRule(superclassLiteral, positiveLiteral(
							
							asHasSelf.getProperty().asOWLObjectProperty(), uniSecond, uniSecond)));
				default:
					break;
			}
		}
		
		throw new UnsupportedOperationException();
	}
	
	@Override
	public Stream<Rule> visit(final OWLSubObjectPropertyOfAxiom axiom) {
		final var first = nextUniversalVariable();
		final var second = nextUniversalVariable();
		if (axiom.getSubProperty() instanceof OWLObjectInverseOf) {
			return of(
					Expressions.makeRule(positiveLiteral(axiom.getSuperProperty().asOWLObjectProperty(), second, first),
							positiveLiteral(axiom.getSubProperty().getNamedProperty(), first, second)));
		}
		return of(Expressions.makeRule(positiveLiteral(axiom.getSuperProperty().asOWLObjectProperty(), first, second),
				positiveLiteral(axiom.getSubProperty().asOWLObjectProperty(), first, second)));
	}
	
	@Override
	public Stream<Rule> visit(final OWLSubPropertyChainOfAxiom axiom) {
		final var first = nextUniversalVariable();
		final var second = nextUniversalVariable();
		final var third = nextUniversalVariable();
		final var chain = axiom.getPropertyChain();
		return of(Expressions.makeRule(positiveLiteral(axiom.getSuperProperty().asOWLObjectProperty(), first, third),
				positiveLiteral(chain.get(0).asOWLObjectProperty(), first, second),
				positiveLiteral(chain.get(1).asOWLObjectProperty(), second, third)));
	}
	
}
