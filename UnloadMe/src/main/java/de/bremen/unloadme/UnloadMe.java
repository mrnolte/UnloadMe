package de.bremen.unloadme;

import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLProperty;
import org.semanticweb.owlapi.modularity.AtomicDecomposition;
import org.semanticweb.owlapi.modularity.ModuleExtractor;
import org.semanticweb.owlapi.modularity.locality.LocalityClass;
import org.semanticweb.owlapi.modularity.locality.SyntacticLocalityModuleExtractor;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;

import de.bremen.unloadme.modulesettings.InseparabilityRelation;

/**
 * Implementation of the version of datalog based module extraction [1] that is
 * applicable to the {@link AtomicDecomposition} of an ontology according to [2]
 * nested with {@link SyntacticLocalityModuleExtractor}.
 *
 * [1] A. A. Romero, M. Kaminski, B. Cuenca Grau und I. Horrocks: Module
 * Extraction in Expressive Ontology Languages via Datalog Reasoning. Journal of
 * Artificial Intelligence Research, Nr. 55, S. 499 – 564, Februar 2016.
 *
 * [2] R. Nolte: Die Jagd nach Modul x - Analyse beschreibungslogischer
 * Modulextraktionsverfahren auf ihre Anwendbarkeit zur Atomaren Dekomposition
 * einer Ontologie. Master thesis at the University of Bremen (in German) , June
 * 2020.
 *
 * @author Robin Nolte
 *
 */
public class UnloadMe implements ModuleExtractor {
	
	private final SyntacticLocalityModuleExtractor syntacticLocalityModuleExtractor;
	
	private final DatalogBasedModuleExtractor datalogBasedModuleExtractor;
	
	private final NestedModuleExtractor nestedModuleExtractor;
	
	/**
	 * Constructs a new {@link UnloadMe}. This process may take some time, as it
	 * computes a mapping from the input axiom base to a normal form and a datalog
	 * mapping, and checks all axioms for being tautologies.
	 *
	 * @param axiomBase              The axiom base if this
	 *                               {@link DatalogBasedModuleExtractor}
	 * @param ontologyManager        The {@link OWLOntologyManager} to use for
	 *                               tautology checks
	 * @param reasonerFactory        The {@link OWLReasonerFactory} to use for
	 *                               tautology checks
	 * @param inseparabilityRelation The {@link InseparabilityRelation} to use for
	 *                               module extraction
	 * @param localityClass          the {@link LocalityClass} to use for the
	 *                               {@link SyntacticLocalityModuleExtractor}
	 * @throws IllegalArgumentException If any given axiom is unsupported
	 *                                  (unsupported Axioms are: HasKey, contains
	 *                                  top or bottom object property, contains any
	 *                                  data property, SWRL rule). Use
	 *                                  {@link Util#cleanAxiomBase(Stream)} to
	 *                                  conveniently clean the axiom base
	 */
	public UnloadMe(final Stream<OWLAxiom> axiomBase, final LocalityClass localityClass,
			final InseparabilityRelation inseparabilityRelation, final OWLOntologyManager ontologyManager,
			final OWLReasonerFactory reasonerFactory) {
		syntacticLocalityModuleExtractor = new SyntacticLocalityModuleExtractor(localityClass, axiomBase);
		datalogBasedModuleExtractor = new DatalogBasedModuleExtractor(syntacticLocalityModuleExtractor.axiomBase(),
				ontologyManager, reasonerFactory, inseparabilityRelation);
		nestedModuleExtractor = new NestedModuleExtractor(
				Stream.of(syntacticLocalityModuleExtractor, datalogBasedModuleExtractor));
	}
	
	@Override
	public Stream<OWLAxiom> axiomBase() {
		return syntacticLocalityModuleExtractor.axiomBase();
	}
	
	/**
	 * {@inheritDoc} Corresponds to the non-iterative version of Datalog based
	 * Module extraction plus Tautology checks (applicable for the
	 * {@link AtomicDecomposition}).
	 *
	 * @throws IllegalArgumentException if (1) the given signature contains some
	 *                                  entity other than {@link OWLClass} and
	 *                                  {@link OWLProperty}, (2) the used
	 *                                  {@link InseparabilityRelation} is not
	 *                                  {@link InseparabilityRelation#isRobustUnderVocabularyExtension()}
	 *                                  and the given signature contains some
	 *                                  {@link OWLClass} or {@link OWLProperty} that
	 *                                  has been lost in the signature of the normal
	 *                                  form (for example, by removing tautologies)
	 */
	@Override
	public Stream<OWLAxiom> extract(final Stream<OWLEntity> signature,
			final Optional<Predicate<OWLAxiom>> axiomFilter) {
		final Set<OWLEntity> signatureSet = signature.collect(Collectors.toSet());
		final var extractedModule = nestedModuleExtractor.extract(signatureSet.stream(), axiomFilter)
				.collect(Collectors.toSet());
		return extractedModule.stream();
	}
	
	public DatalogBasedModuleExtractor getDatalogBasedModuleExtractor() {
		return datalogBasedModuleExtractor;
	}

	/**
	 * Sets the {@link InseparabilityRelation} to use for module extraction. This
	 * process may take some time, as it computes a mapping from the normal form to
	 * datalog.
	 *
	 * @param relation The new {@link InseparabilityRelation} to use
	 */
	public InseparabilityRelation getInseparabilityRelation() {
		return datalogBasedModuleExtractor.getInseparabilityRelation();
	}
	
	public LocalityClass getLocalityClass() {
		return syntacticLocalityModuleExtractor.getLocalityClass();
	}
	
	/**
	 * Sets the {@link InseparabilityRelation} to use for module extraction. This
	 * process may take some time, as it computes a mapping from the normal form to
	 * datalog.
	 *
	 * @param relation The new {@link InseparabilityRelation} to use
	 */
	public void setInseparabilityRelation(final InseparabilityRelation inseparabilityRelation) {
		datalogBasedModuleExtractor.setInseparabilityRelation(inseparabilityRelation);
	}
	
}
