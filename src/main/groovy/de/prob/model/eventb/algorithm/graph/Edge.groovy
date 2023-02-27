package de.prob.model.eventb.algorithm.graph

import com.github.krukow.clj_lang.PersistentVector

import de.prob.animator.domainobjects.EventB
import de.prob.model.eventb.algorithm.ast.IAssignment
import de.prob.model.eventb.algorithm.ast.If
import de.prob.model.eventb.algorithm.ast.Skip
import de.prob.model.eventb.algorithm.ast.Statement
import de.prob.model.eventb.algorithm.ast.While

public class Edge {
	def final Statement from
	def final Statement to
	def final PersistentVector<ConditionalStatement> conditions
	def final IAssignment assignment

	def Edge(Statement from, Statement to, PersistentVector<ConditionalStatement> conditions) {
		this.from = from
		this.to = to
		this.conditions = conditions
		this.assignment = from instanceof IAssignment ? from : null
	}

	def Edge(Statement from, Statement to, PersistentVector<ConditionalStatement> conditions, IAssignment assignment) {
		this.from = from
		this.to = to
		this.conditions = conditions
		this.assignment = assignment
	}

	@Override
	public boolean equals(Object that) {
		if (that instanceof Edge) {
			return from.equals(that.getFrom()) &&
					to.equals(that.getTo()) &&
					conditions.equals(that.getConditions())
		}
		return false
	}

	public Edge mergeConditions(Edge that) {
		assert this.assignment == null
		def conditions = this.conditions
		that.conditions.each { ConditionalStatement cond ->
			conditions = conditions.plus(cond)
		}
		return new Edge(from, that.to, conditions, that.assignment)
	}

	public Edge mergeAssignment(Edge that) {
		assert this.assignment == null && that.assignment != null
		def conditions = this.conditions
		that.conditions.each { ConditionalStatement cond ->
			conditions = conditions.add(cond)
		}
		return new Edge(from, that.to, conditions, that.assignment)
	}

	@Override
	public int hashCode() {
		return from.hashCode() * 7 + to.hashCode() * 13 + conditions.hashCode() * 17;
	}

	@Override
	public String toString() {
		def assign = assignment == null ? "" : " + "+assignment.toString()
		return conditions ? "--${conditions.collect {it.condition}}$assign-->" : "-->"
	}

	public String getName(NodeNaming n) {
		def names = conditions.collect { getEventName(n, it.statement, it.condition) }
		if (from instanceof IAssignment || from instanceof Skip) {
			names << n.getName(from)
		}
		names.iterator().join("_")
	}

	private String getEventName(NodeNaming n, While s, EventB condition) {
		def name = n.getName(s)
		if (condition == s.condition) {
			return "enter_$name"
		}
		if (condition == s.notCondition) {
			return "exit_$name"
		}
		return "unknown_branch_$name"
	}

	private String getEventName(NodeNaming n, If s, EventB condition) {
		def name = n.getName(s)
		if (condition == s.condition) {
			return "${name}_then"
		}
		if (condition == s.elseCondition) {
			return "${name}_else"
		}
		return "unknown_branch_$name"
	}

	public String rep() {
		StringBuilder sb = new StringBuilder()
		conditions.each { ConditionalStatement  t ->
			sb.append(t.statement.toString())
			sb.append(" ")
		}
		if (assignment) {
			sb.append(assignment.toString())
		}
		sb.toString()
	}
}
