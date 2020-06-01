package de.bremen.unloadme;

import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.modularity.ModuleExtractor;

public class NestedModuleExtractor implements ModuleExtractor {
	
	private final ModuleExtractor[] nestedExtractors;
	
	public NestedModuleExtractor(final Stream<ModuleExtractor> nestedExtractors) {
		this.nestedExtractors = nestedExtractors.toArray(ModuleExtractor[]::new);
		if (this.nestedExtractors.length == 0) {
			throw new IllegalArgumentException("Needs at least one ModuleExtractor");
		}
	}
	
	@Override
	public Stream<OWLAxiom> axiomBase() {
		return nestedExtractors[0].axiomBase();
	}
	
	@Override
	public Stream<OWLAxiom> extract(final Stream<OWLEntity> signature,
			final Optional<Predicate<OWLAxiom>> axiomFilter) {
		final var signatureSet = signature.collect(Collectors.toSet());

		// Calculating the initial module
		Set<OWLAxiom> module = nestedExtractors[0].extract(signatureSet.stream(), axiomFilter)
				.collect(Collectors.toSet());

		int nextExtractor = 1;
		int lastBetterModuleExtractor = 0;
		// nesting modules until stabilization
		endless: while (true) {
			for (; nextExtractor < nestedExtractors.length; nextExtractor++) {
				if (lastBetterModuleExtractor == nextExtractor) {
					break endless;
				}
				final int previousSize = module.size();
				module = nestedExtractors[nextExtractor].extract(signatureSet.stream(), module::contains)
						.collect(Collectors.toSet());
				if (previousSize > module.size()) {
					lastBetterModuleExtractor = nextExtractor;
				}
			}
			nextExtractor = 0;
		}
		return module.stream();
	}
	
}
