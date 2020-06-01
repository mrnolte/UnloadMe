package de.bremen.unloadme;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.modularity.ModuleExtractor;

public class IterativeModuleExtractor implements ModuleExtractor {

	private final ModuleExtractor toIterateOver;

	public IterativeModuleExtractor(final ModuleExtractor toIterateOver) {
		this.toIterateOver = toIterateOver;
	}

	@Override
	public Stream<OWLAxiom> axiomBase() {
		return toIterateOver.axiomBase();
	}

	@Override
	public Stream<OWLAxiom> extract(final Stream<OWLEntity> signature,
			final Optional<Predicate<OWLAxiom>> axiomFilter) {
		final Set<OWLEntity> workingSignature = signature.collect(Collectors.toSet());
		final Set<OWLAxiom> lastModule = Collections.emptySet();
		Set<OWLAxiom> nextModule;
		do {
			nextModule = toIterateOver.extract(workingSignature.stream(), axiomFilter).collect(Collectors.toSet());
			nextModule.stream().flatMap(OWLAxiom::signature).forEach(workingSignature::add);
		} while (!lastModule.equals(nextModule));
		return nextModule.stream();
	}

	public ModuleExtractor getToIterateOver() {
		return toIterateOver;
	}

}
