# Notice: Do not use this! The math behind this specific implementation has been disproven!


# UnloadMe


*UnloadMe* (UnloadMe: Nested Locality- And Datalog-based Module Extractor) is a new open source implementation of a repair [1] of *Datalog-based module extraction* (DBM) [2] to fit the requirements of the *atomic decomposition* (AD) algorithm [3,4].
It may be used to extract modules for a variety of inseparability relations from OWL ontologies that in a lot of cases are (much) smaller than standard syntactic locality based modules.
UnloadMe uses both [OWL API](https://github.com/owlcs/owlapi) 5 and [Rulewerk](https://github.com/knowsys/rulewerk).

## Installation
We use maven to install UnloadMe:
1. Install version `0.7.0-SNAPSHOT` of Rulewerk following the Rulewerk installation instructions (we use the snapshot due to various bugs in version `0.6.0`).
2. Install version `5.1.15-SNAPSHOT` of OWL API following the OWL API installation instructions (we use the snapshot due to a AD bug in version `5.1.14`).
3. Use `maven install` to install UnloadMe.

## Usage
The most important class is `de.bremen.unloadme.UnloadMe` which is the module extractor that fulfills the AD requirements and is nested with syntactic locality module extraction to speed things up. Choose a `LocalityClass` and a `InseparabilityRelation` fitting your needs (for more information see [5] and [1]).

UnlaodMe currently does not support OWL axioms containing data properties, the universal or the bottom object property and HasKey-axioms.
Furthermore, nominals may not be part of the seed signature. If you rely on any of these, take a look at the original DBM implementation, [PrisM](https://github.com/anaphylactic/PrisM). Note that PrisM is proven to be not applicable for the AD [1].

When using fact or implication inseparability make sure that tautologies in your ontology do not introduce any new `OWLClasses` or `OWLObjectProperties` that are not present elsewhere in the ontology. Otherwise it is unknown if returned modules actually are inseparabe from the ontology w.r.t. the seed signature.

Feel free to report any bugs that occurr.

## Publications
The currently only publication describing UnloadMe is [1].

## References
[1] Nolte, M.R. Die Jagd auf Module x: Analyse beschreibungslogischer Modulextraktionsverfahrenen auf ihre Anwendbarkeit zur Atomaren Dekomposition einer Ontologie. Master thesis at the University of Bremen. In German. June 2020.

[2] Romero, A.A. and Kaminski, M. and Grau, B.C. and Horrocks, I. Module extraction in expressive ontology languages via datalog reasoning. Journal of Artificial Intelligence Research (JAIR). February 2016. p. 499-564.

[3] C. Del Vescovo, B. Parsia, U. Sattler und T. Schneider: The Modular Structure of an Ontology: Atomic Decomposition. In: Proceedings of the 22nd International Joint Conference on Artificial Intelligence (IJCAI-11), Vol. 3, Menlo Park, CA, USA, AAAI Press, 2011.

[4] C. Del Vescovo, M. Horridge, B. Parsia, U. Sattler, T. Schneider und H. Zhao: Modular Structures and Atomic Decomposition in Ontologies. Draft. 2019. Available at http://www.informatik.uni-bremen.de/~schneidt/dl2019/AD.pdf (as of 03 January 2020).

[5] B. Cuenca Grau, I. Horrocks, U. Sattler und Y. Kazakov: Modular Reuse of Ontologies: Theory and Practice. Journal of Artificial Intelligence Research, Nr. 31, p. 273 – 318, February 2008.
