package de.prob.model.eventb.algorithm.graph

import de.prob.animator.domainobjects.EventB
import de.prob.model.eventb.algorithm.ast.Statement

import groovy.transform.Canonical

@Canonical
final class ConditionalStatement {
	final Statement statement
	final EventB condition
}
