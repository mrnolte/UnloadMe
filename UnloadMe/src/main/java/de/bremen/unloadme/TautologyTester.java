package de.bremen.unloadme;

import java.util.Stack;

import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLRuntimeException;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;

public class TautologyTester {

	private final Stack<OWLReasoner> reasoners = new Stack<>();
	private final OWLOntologyManager ontologyManager;
	private final OWLReasonerFactory reasonerFactory;

	public TautologyTester(final OWLOntologyManager ontologyManager, final OWLReasonerFactory reasonerFactory) {
		this.ontologyManager = ontologyManager;
		this.reasonerFactory = reasonerFactory;
	}

	public boolean isTautology(final OWLAxiom axiom) {
		final OWLReasoner reasoner = nextReasoner();
		try {
			return reasoner.isEntailed(axiom);
		} finally {
			synchronized (reasoners) {
				reasoners.push(reasoner);
			}
		}
	}

	private OWLReasoner nextReasoner() {
		synchronized (reasoners) {
			if (!reasoners.isEmpty()) {
				return reasoners.pop();
			}
		}

		try {
			return reasonerFactory.createNonBufferingReasoner(ontologyManager.createOntology());
		} catch (final OWLOntologyCreationException e) {
			throw new OWLRuntimeException(e);
		}
	}

}
