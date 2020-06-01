package de.bremen.unloadme.normalform;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLDataFactory;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;

public class NormalFormManager {

	/**
	 * The axioms whose normal form has to be managed.
	 */
	private final Set<OWLAxiom> axiomBase;

	private final NormalFormRewriter rewriter;

	private final SetMultimap<OWLAxiom, OWLAxiom> normalisation;

	private final SetMultimap<OWLAxiom, OWLAxiom> orig;

	public NormalFormManager(final Stream<OWLAxiom> axiomBase, final OWLDataFactory dataFactory) {
		this.axiomBase = axiomBase.collect(Collectors.toSet());

		// instantiate NormalFormRewriter
		rewriter = new NormalFormRewriter(axiomBase().flatMap(OWLAxiom::signature), dataFactory);

		// calculate normal form
//		normalisation = axiomBase().parallel()
		normalisation = axiomBase()
				.collect(ImmutableSetMultimap.flatteningToImmutableSetMultimap(next -> next, rewriter::visitAll));

		// calculate orig
		orig = Multimaps.invertFrom(normalisation, HashMultimap.create());
	}

	public Stream<OWLAxiom> axiomBase() {
		return axiomBase.stream();
	}

	public Stream<OWLAxiom> normalFormOf(final OWLAxiom axiom) {
		return normalisation.get(axiom).stream();
	}

	public Stream<OWLAxiom> normalFormOfAxiomBase() {
		return axiomBase().flatMap(this::normalFormOf).distinct();
	}

	public Stream<OWLAxiom> orig(final OWLAxiom axiom) {
		return orig.get(axiom).stream();
	}

}
