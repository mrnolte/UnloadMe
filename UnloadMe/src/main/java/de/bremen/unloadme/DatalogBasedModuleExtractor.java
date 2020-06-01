package de.bremen.unloadme;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLProperty;
import org.semanticweb.owlapi.modularity.AbstractModuleExtractor;
import org.semanticweb.owlapi.modularity.AtomicDecomposition;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;

import de.bremen.unloadme.datalog.DatalogSignatureMapper;
import de.bremen.unloadme.datalog.ProgramManager;
import de.bremen.unloadme.datalog.SupportComputer;
import de.bremen.unloadme.modulesettings.InseparabilityRelation;
import de.bremen.unloadme.modulesettings.ModuleSetting;
import de.bremen.unloadme.normalform.NormalFormManager;

/**
 * Implementation of the (iterative) version of datalog based module extraction
 * [1] according to [2].
 * {@link DatalogBasedModuleExtractor#extractSingleModule(Set, Optional)}
 * corresponds to the non-iterative version (not applicable for the
 * {@link AtomicDecomposition}), while
 * {@link DatalogBasedModuleExtractor#extract(Stream)} (and overloaded methods)
 * correspond to iterative version that is applicable for the
 * {@link AtomicDecomposition}.
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
public class DatalogBasedModuleExtractor extends AbstractModuleExtractor {

	private final Set<OWLAxiom> axiomBaseWithoutTautologies;

	private InseparabilityRelation inseparabilityRelation;

	private ModuleSetting moduleSetting;

	private final NormalFormManager nfManager;

	private final ProgramManager programManager;

	private final SupportComputer supportComputer;

	private final DatalogSignatureMapper datalogSignaturemapper = new DatalogSignatureMapper();

	/**
	 * Constructs a new {@link DatalogBasedModuleExtractor}. This process may take
	 * some time, as it computes a mapping from the input axiom base to a normal
	 * form and a datalog mapping, and checks all axioms for being tautologies.
	 *
	 * @param axiomBase              The axiom base if this
	 *                               {@link DatalogBasedModuleExtractor}
	 * @param ontologyManager        The {@link OWLOntologyManager} to use for
	 *                               tautology checks
	 * @param reasonerFactory        The {@link OWLReasonerFactory} to use for
	 *                               tautology checks
	 * @param inseparabilityRelation The {@link InseparabilityRelation} to use for
	 *                               module extraction
	 * @throws IllegalArgumentException If any given axiom is unsupported
	 *                                  (unsupported Axioms are: HasKey, contains
	 *                                  top or bottom object property, contains any
	 *                                  data property, SWRL rule). Use
	 *                                  {@link Util#cleanAxiomBase(Stream)} to
	 *                                  conveniently clean the axiom base
	 */
	public DatalogBasedModuleExtractor(final Stream<OWLAxiom> axiomBase, final OWLOntologyManager ontologyManager,
			final OWLReasonerFactory reasonerFactory, final InseparabilityRelation inseparabilityRelation) {
		super(axiomBase);
		checkAxiomBase();
		// filter tautologies
		final TautologyTester tautologyTester = new TautologyTester(ontologyManager, reasonerFactory);
		axiomBaseWithoutTautologies = axiomBase().parallel().filter(next -> !tautologyTester.isTautology(next))
				.collect(Collectors.toSet());

		// normal form
		nfManager = new NormalFormManager(axiomBaseWithoutTautologies.stream(), ontologyManager.getOWLDataFactory());

		programManager = new ProgramManager(nfManager.normalFormOfAxiomBase(), datalogSignaturemapper);
		supportComputer = new SupportComputer(datalogSignaturemapper);

		setInseparabilityRelation(inseparabilityRelation);
	}

	private void checkAxiomBase() {
		final var unsupportedAxioms = axiomBase().filter(next -> !Util.isSupportedAxiom(next))
				.collect(Collectors.toSet());
		if (!unsupportedAxioms.isEmpty()) {
			throw new IllegalArgumentException("Unsupported Axioms: " + unsupportedAxioms);
		}
	}

	private void checkSafety(final Set<OWLEntity> seedSignatureSet) {
		if (inseparabilityRelation.isRobustUnderVocabularyExtension()) {
			return;
		}
		if (!Util.cleanSignature(nfManager.normalFormOfAxiomBase().flatMap(OWLAxiom::signature))
				.collect(Collectors.toSet()).containsAll(seedSignatureSet)) {
			throw new IllegalArgumentException(
					"NF/Taut: Unsafe to use du to uncertain robustness under vocabulary extension of selected inseparability relation. Select MODEL or BOOLEAN_PEQ instead.");
		}
	}

	/**
	 * {@inheritDoc} Corresponds to the iterative version of Datalog based Module
	 * extraction plus Tautology checks (applicable for the
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
		final Set<OWLEntity> signatureSet = Util.cleanSignature(signature).collect(Collectors.toSet());
		checkSafety(signatureSet);
		int previousSize;
		Set<OWLAxiom> module;
		do {
			previousSize = signatureSet.size();
			module = extractSingleModule(signatureSet, axiomFilter).collect(Collectors.toSet());
			Util.cleanSignature(module.stream().flatMap(OWLAxiom::signature)).forEach(signatureSet::add);
		} while (previousSize != signatureSet.size());

		return module.stream();
	}

	private Stream<OWLAxiom> extractSingleModule(final Set<OWLEntity> signatureSet,
			final Optional<Predicate<OWLAxiom>> axiomFilter) {
		Stream<OWLAxiom> filtered = axiomBaseWithoutTautologies.stream();
		if (!axiomFilter.isEmpty()) {
			filtered = filtered.filter(axiomFilter.get());
		}
		final Set<OWLAxiom> filteredAxiomBase = filtered.collect(Collectors.toSet());
		final var facts = moduleSetting
				.getFacts(signatureSet.stream()
						.map(next -> next instanceof OWLClass ? datalogSignaturemapper.toPredicate((OWLClass) next)
								: datalogSignaturemapper.toPredicate((OWLObjectProperty) next))
						.collect(Collectors.toSet()));
		final Set<OWLAxiom> inNF = filteredAxiomBase.stream().flatMap(nfManager::normalFormOf)
				.collect(Collectors.toSet());
		final var datalogkB = programManager.toDatalogProgram(inNF::contains);
		try {
			final var support = supportComputer.computeSupport(datalogkB.getValue(), facts);
			final var owlNFSupport = programManager.reverse(support.stream(), datalogkB.getKey());
			var result = owlNFSupport.flatMap(nfManager::orig);
			if (!axiomFilter.isEmpty()) {
				result = result.filter(axiomFilter.get());
			}
			return result;
		} catch (final IOException e) {
			throw new RuntimeException(e);
		} catch (final RuntimeException e) {
			throw new RuntimeException(signatureSet.toString(), e);
		}
	}

	/**
	 * Corresponds to the non-iterative version of Datalog based module extraction
	 * plus Tautology check that is NOT applicable for the
	 * {@link AtomicDecomposition}.
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
	public Stream<OWLAxiom> extractSingleModule(final Stream<OWLEntity> signature,
			final Optional<Predicate<OWLAxiom>> axiomFilter) {
		final var signatureSet = signature.collect(Collectors.toSet());
		checkSafety(signatureSet);
		return extractSingleModule(signatureSet, axiomFilter);
	}

	public InseparabilityRelation getInseparabilityRelation() {
		return inseparabilityRelation;
	}

	/**
	 * @return The tautologies within the axiom base
	 */
	public final Stream<OWLAxiom> getTautologies() {
		return axiomBase().filter(next -> !axiomBaseWithoutTautologies.contains(next));
	}

	/**
	 * Sets the {@link InseparabilityRelation} to use for module extraction. This
	 * process may take some time, as it computes a mapping from the normal form to
	 * datalog.
	 *
	 * @param relation The new {@link InseparabilityRelation} to use
	 */
	public void setInseparabilityRelation(final InseparabilityRelation relation) {
		if (inseparabilityRelation == relation) {
			return;
		}
		inseparabilityRelation = Objects.requireNonNull(relation);
		moduleSetting = inseparabilityRelation.computeModuleSetting(datalogSignaturemapper);

		programManager.setModuleSetting(moduleSetting);
		supportComputer.setModuleSetting(moduleSetting, programManager.getCompleteDatalogKnowledgeBase());
	}

}
