package de.bremen.unloadme.normalform;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.NotImplementedException;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLClassExpressionVisitorEx;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLObjectAllValuesFrom;
import org.semanticweb.owlapi.model.OWLObjectComplementOf;
import org.semanticweb.owlapi.model.OWLObjectExactCardinality;
import org.semanticweb.owlapi.model.OWLObjectHasSelf;
import org.semanticweb.owlapi.model.OWLObjectHasValue;
import org.semanticweb.owlapi.model.OWLObjectIntersectionOf;
import org.semanticweb.owlapi.model.OWLObjectInverseOf;
import org.semanticweb.owlapi.model.OWLObjectMaxCardinality;
import org.semanticweb.owlapi.model.OWLObjectMinCardinality;
import org.semanticweb.owlapi.model.OWLObjectOneOf;
import org.semanticweb.owlapi.model.OWLObjectPropertyExpression;
import org.semanticweb.owlapi.model.OWLObjectSomeValuesFrom;
import org.semanticweb.owlapi.model.OWLObjectUnionOf;

class ConceptRewriter implements OWLClassExpressionVisitorEx<OWLClassExpression> {
	
	// TODO Comments
	
	private final OWLDataFactory dF;
	
	ConceptRewriter(final OWLDataFactory dataFactory) {
		dF = dataFactory;
	}
	
	@Override
	public <T> OWLClassExpression doDefault(final T object) {
		throw new NotImplementedException(object.toString());
	}
	
	private OWLObjectPropertyExpression mostSimple(final OWLObjectPropertyExpression toSimplify) {
		boolean inverse = false;
		OWLObjectPropertyExpression property = toSimplify;
		while (property instanceof OWLObjectInverseOf) {
			property = ((OWLObjectInverseOf) property).getInverse();
			inverse = !inverse;
		}
		return inverse ? property.getInverseProperty() : property;
	}
	
	@Override
	public OWLClassExpression visit(final OWLClass ce) {
		return ce;
	}
	
	@Override
	public OWLClassExpression visit(final OWLObjectAllValuesFrom ce) {
		return dF.getOWLObjectAllValuesFrom(mostSimple(ce.getProperty()), ce.getFiller().accept(this));
	}
	
	@Override
	public OWLClassExpression visit(final OWLObjectComplementOf ce) {
		return dF.getOWLObjectComplementOf(ce.getOperand().accept(this));
	}
	
	@Override
	public OWLClassExpression visit(final OWLObjectExactCardinality ce) {
		if (ce.getCardinality() == 0) {
			return dF.getOWLObjectMinCardinality(ce.getCardinality(), ce.getProperty(), ce.getFiller()).accept(this);
		}
		return dF
				.getOWLObjectIntersectionOf(
						dF.getOWLObjectMinCardinality(ce.getCardinality(), ce.getProperty(), ce.getFiller()),
						dF.getOWLObjectMaxCardinality(ce.getCardinality(), ce.getProperty(), ce.getFiller()))
				.accept(this);
	}
	
	@Override
	public OWLClassExpression visit(final OWLObjectHasSelf ce) {
		return dF.getOWLObjectHasSelf(mostSimple(ce.getProperty()));
	}
	
	@Override
	public OWLClassExpression visit(final OWLObjectHasValue ce) {
		return dF.getOWLObjectSomeValuesFrom(mostSimple(ce.getProperty()), dF.getOWLObjectOneOf(ce.getFiller()));
	}
	
	@Override
	public OWLClassExpression visit(final OWLObjectIntersectionOf ce) {
		final List<OWLClassExpression> operands = ce.operands().map(next -> next.accept(this))
				.collect(Collectors.toList());
		switch (operands.size()) {
			case 0:
				throw new NotImplementedException("Not implemented: " + ce);
			case 1:
				return operands.get(0);
			case 2:
				return dF.getOWLObjectIntersectionOf(operands);
			default:
				OWLClassExpression constructed = operands.get(0);
				for (int i = 1; i < operands.size(); i++) {
					constructed = dF.getOWLObjectIntersectionOf(constructed, operands.get(i));
				}
				return constructed;
		}
	}
	
	@Override
	public OWLClassExpression visit(final OWLObjectMaxCardinality ce) {
		if (ce.getCardinality() == 0) {
			return dF.getOWLObjectComplementOf(dF.getOWLObjectSomeValuesFrom(ce.getProperty(), ce.getFiller()))
					.accept(this);
		}
		boolean inverse = false;
		OWLObjectPropertyExpression property = mostSimple(ce.getProperty());
		while (property instanceof OWLObjectInverseOf) {
			property = ((OWLObjectInverseOf) property).getInverse();
			inverse = !inverse;
		}
		property = inverse ? property.getInverseProperty() : property;
		return dF.getOWLObjectMaxCardinality(ce.getCardinality(), mostSimple(ce.getProperty()),
				ce.getFiller().accept(this));
	}
	
	@Override
	public OWLClassExpression visit(final OWLObjectMinCardinality ce) {
		if (ce.getCardinality() == 1) {
			return dF.getOWLObjectSomeValuesFrom(ce.getProperty(), ce.getFiller()).accept(this);
		}
		return dF.getOWLObjectMinCardinality(ce.getCardinality(), mostSimple(ce.getProperty()),
				ce.getFiller().accept(this));
	}
	
	@Override
	public OWLClassExpression visit(final OWLObjectOneOf ce) {
		final List<OWLIndividual> operands = ce.operands().collect(Collectors.toList());
		switch (operands.size()) {
			case 0:
				throw new NotImplementedException("Not implemented: " + ce);
			case 1:
				return ce;
			default:
				OWLClassExpression constructed = dF.getOWLObjectOneOf(operands.get(0));
				for (int i = 1; i < operands.size(); i++) {
					constructed = dF.getOWLObjectUnionOf(constructed, dF.getOWLObjectOneOf(operands.get(i)));
				}
				return constructed;
		}
	}
	
	@Override
	public OWLClassExpression visit(final OWLObjectSomeValuesFrom ce) {
		return dF.getOWLObjectSomeValuesFrom(mostSimple(ce.getProperty()), ce.getFiller().accept(this));
	}
	
	@Override
	public OWLClassExpression visit(final OWLObjectUnionOf ce) {
		final List<OWLClassExpression> operands = ce.operands().map(next -> next.accept(this))
				.collect(Collectors.toList());
		switch (operands.size()) {
			case 0:
				throw new NotImplementedException("Not implemented: " + ce);
			case 1:
				return operands.get(0);
			case 2:
				return dF.getOWLObjectUnionOf(operands);
			default:
				OWLClassExpression constructed = operands.get(0);
				for (int i = 1; i < operands.size(); i++) {
					constructed = dF.getOWLObjectUnionOf(constructed, operands.get(i));
				}
				return constructed;
		}
	}
}
