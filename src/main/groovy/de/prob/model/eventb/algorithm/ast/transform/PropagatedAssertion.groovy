package de.prob.model.eventb.algorithm.ast.transform

import de.prob.animator.domainobjects.EventB

import groovy.transform.Canonical

@Canonical
final class PropagatedAssertion {
	final List<EventB> conditions
	final EventB assertion
}
