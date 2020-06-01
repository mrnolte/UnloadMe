package de.bremen.unloadme.normalform;

import java.util.List;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.Triple;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectPropertyExpression;

import de.bremen.unloadme.SignatureMapper;

public class OWLSignatureMapper extends SignatureMapper<OWLEntity, String> {
	
	public final static String REPLACEMENT_NAMESPACE = "UNLOADME_NF_RESERVED_ENTITIES_FOR_REPLACEMENT";
	public final static String FRESH_FOR_ALL_NAMESPACE = "UNLOADME_NF_RESERVED_ENTITIES_FRESH_FOR_LEFT_ALL";
	public final static String FRESH_FOR_MIN_NAMESPACE = "UNLOADME_NF_RESERVED_ENTITIES_FRESH_FOR_RIGHT_MIN";
	private final OWLDataFactory dataFactory;
	
	public OWLSignatureMapper(final Stream<OWLEntity> originalSignature, final OWLDataFactory dataFactory) {
		super(originalSignature);
		this.dataFactory = dataFactory;
	}
	
	private OWLClass extendDepending(final String namespace, final OWLClassExpression entity) {
		return extendDepending(namespace, entity, (r, i) -> dataFactory.getOWLClass(r, "NEW_CLASS_" + i));
	}
	
	private OWLObjectProperty extendDepending(final String namespace, final OWLObjectPropertyExpression entity) {
		return extendDepending(namespace, entity, (r, i) -> dataFactory.getOWLObjectProperty(r, "NEW_PROPERTY_" + i));
	}
	
	public OWLClass freshForLeftAll(final OWLClassExpression toReplace) {
		return extendDepending(FRESH_FOR_ALL_NAMESPACE, toReplace);
	}
	
	public OWLClass freshForRightMin(final int i, final OWLObjectPropertyExpression property,
			final OWLClassExpression classExpr) {
		return extendDepending(FRESH_FOR_MIN_NAMESPACE, Triple.of(i, property, classExpr),
				(r, j) -> dataFactory.getOWLClass("NEW_CLASS_" + j));
	}

	public OWLObjectProperty replacement(final List<OWLObjectPropertyExpression> replaced) {
		return extendDepending(REPLACEMENT_NAMESPACE, replaced,
				(r, i) -> dataFactory.getOWLObjectProperty("NEW_CLASS_" + i));
	}
	
	public OWLClass replacement(final OWLClassExpression toReplace) {
		return extendDepending(REPLACEMENT_NAMESPACE, toReplace);
	}
	
	public OWLObjectProperty replacement(final OWLObjectPropertyExpression toReplace) {
		return extendDepending(REPLACEMENT_NAMESPACE, toReplace);
	}
}
