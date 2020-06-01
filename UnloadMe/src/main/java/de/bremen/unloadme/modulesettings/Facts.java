package de.bremen.unloadme.modulesettings;

import java.util.Set;

import org.semanticweb.rulewerk.core.model.api.Fact;

public class Facts {

	private final Set<Fact> relevantFacts;
	private final Set<Fact> initialFacts;

	public Facts(final Set<Fact> relevantFacts, final Set<Fact> initialFacts) {
		super();
		this.relevantFacts = relevantFacts;
		this.initialFacts = initialFacts;
	}

	public Set<Fact> getInitialFacts() {
		return initialFacts;
	}

	public Set<Fact> getRelevantFacts() {
		return relevantFacts;
	}

}
