package de.prob.model.eventb.algorithm.graph

import de.prob.model.eventb.algorithm.ast.Assertion
import de.prob.model.eventb.algorithm.ast.Assignment
import de.prob.model.eventb.algorithm.ast.Block
import de.prob.model.eventb.algorithm.ast.Skip
import de.prob.model.eventb.algorithm.ast.Statement
import de.prob.model.eventb.algorithm.ast.transform.AssertionExtractor

import spock.lang.Specification

class MergedAssignmentCGGConstructionTest extends Specification {

	def ControlFlowGraph graph(Closure cls) {
		Block b = new Block().make(cls).finish()
		return new MergeAssignment().transform(new ControlFlowGraph(b))
	}

	def nodes(ControlFlowGraph g, String... names) {
		NodeNaming n = new NodeNaming(g.algorithm)
		g.nodes.findAll { names.contains(n.getName(it)) } as Set
	}

	def edge(ControlFlowGraph g, String from, String to) {
		NodeNaming n = new NodeNaming(g.algorithm)
		def f = n.getNode(from)
		assert g.nodes.contains(f)
		Edge e = g.edges.find {
			it.from == n.getNode(from) && it.to == n.getNode(to)
		}
		if (e) {
			def eRep = e.conditions.collect { it.condition.getCode() }
			if (e.assignment instanceof Assignment) {
				return eRep + [
					e.assignment.assignment.getCode()
				]
			}
			return eRep
		}
		null
	}

	def edges(ControlFlowGraph g, String from, String to) {
		NodeNaming n = new NodeNaming(g.algorithm)
		def f = n.getNode(from)
		assert g.nodes.contains(f)
		g.edges.findAll {
			it.from == n.getNode(from) && it.to == n.getNode(to)
		}.collect {
			def assignment = it.assignment ? [
				it.assignment.assignment.getCode()
			]
			: []
			it.conditions.collect {
				it.condition.getCode()
			} + assignment
		} as Set
	}

	def assertions(ControlFlowGraph g, String at) {
		Map<Statement, List<Assertion>> properties = new AssertionExtractor().extractAssertions(g.algorithm)
		NodeNaming n = new NodeNaming(g.algorithm)
		assert g.nodes.contains(n.getNode(at))
		assert properties.containsKey(n.getNode(at))
		properties[n.getNode(at)].collect {
			it.getAssertion().getCode()
		} as Set
	}

	def print(graph) {
		println graph.representation()
	}

	def "empty is has end node"() {
		when:
		def DEBUG = false
		def graph = graph({})

		then:
		if (DEBUG) print(graph)
		graph.nodes.first() instanceof Skip
		graph.size() == 1
	}

	def "one assignment block has two nodes"() {
		when:
		def DEBUG = false
		def graph = graph({ Assign("x := 1") })

		then:
		if (DEBUG) print(graph)
		graph.size() == 2
		graph.nodes == nodes(graph, "assign0", "end_algorithm")
		edge(graph, "assign0", "end_algorithm") == ["x := 1"]
	}

	def "one assert block has one node and one assertion"() {
		when:
		def DEBUG = false
		def graph = graph({ Assert("x = 1") })

		then:
		if (DEBUG) print(graph)
		graph.size() == 1
		graph.nodes == nodes(graph, "end_algorithm")
		assertions(graph, "end_algorithm") == ["x = 1"] as Set
	}

	def "an assert in front of a statement"() {
		when:
		def DEBUG = false
		def graph = graph({
			Assign("x := 2")
			Assert("x = 1")
			Assign("x := 3")
		})

		then:
		if (DEBUG) print(graph)
		graph.nodes == nodes(graph, "assign0", "assign1", "end_algorithm")
		graph.size() == 3
		assertions(graph, "assign1") == ["x = 1"] as Set
		edge(graph, "assign0", "assign1") == ["x := 2"]
		edge(graph, "assign1", "end_algorithm") == ["x := 3"]
	}

	def "an assert before and after a while"() {
		when:
		def DEBUG = false
		def graph = graph({
			Assign("x := 1")
			Assert("x = 1")
			While("x < 10") { Assign("x := x + 1") }
			Assert("x >= 10")
		})

		then:
		if (DEBUG) print(graph)
		graph.size() == 3
		assertions(graph, "while0") == ["x = 1"] as Set
		assertions(graph, "end_algorithm") == ["x >= 10"] as Set
		graph.nodes == nodes(graph, "assign0", "assign1", "end_algorithm", "while0")
		edge(graph, "assign0", "while0") == ["x := 1"]
		edge(graph, "while0", "while0") == ["x < 10", "x := x + 1"]
		edge(graph, "while0", "end_algorithm") == ["not(x < 10)"]
	}

	def "an assert in between whiles"() {
		when:
		def DEBUG = false
		def graph = graph({
			Assign("x := 1")
			Assert("x = 1")
			While("x < 10") { Assign("x := x + 1") }
			Assert("x >= 10")
			While("x > 0") { Assign("x := x - 1") }
			Assert("x = 0")
		})

		then:
		if (DEBUG) print(graph)
		graph.size() == 4
		graph.nodes == nodes(graph, "assign0", "assign1", "assign2", "end_algorithm", "while0", "while1")
		assertions(graph, "while0") == ["x = 1"] as Set
		assertions(graph, "while1") == ["x >= 10"] as Set
		assertions(graph, "end_algorithm") == ["x = 0"] as Set
		edge(graph, "assign0", "while0") == ["x := 1"]
		edge(graph, "while0", "while0") == ["x < 10", "x := x + 1"]
		edge(graph, "while0", "while1") == ["not(x < 10)"]
		edge(graph, "while1", "while1") == ["x > 0", "x := x - 1"]
		edge(graph, "while1", "end_algorithm") == ["not(x > 0)"]
	}

	def "an assert between ifs"() {
		when:
		def DEBUG = false
		def graph = graph({
			Assign("x := 1")
			Assert("x > 0")
			If("x > 0") {
				Then("x := 0 - x")
				Else("x := x - 1")
			}
			Assert("x < 0")
			If("x < 0") {
				Then("x := 0 - x")
				Else("x := x + 1")
			}
			Assert("x > 0")
		})

		then:
		if (DEBUG) print(graph)
		graph.size() == 4
		graph.nodes == nodes(graph, "assign0", "end_algorithm", "if0", "if1")
		assertions(graph, "if0") == ["x > 0"] as Set
		assertions(graph, "if1") == ["x < 0"] as Set
		assertions(graph, "end_algorithm") == ["x > 0"] as Set
		edge(graph, "assign0","if0") == ["x := 1"]
		edges(graph, "if0", "if1") == [
			["x > 0", "x := 0 - x"],
			["not(x > 0)", "x := x - 1"]] as Set
		edges(graph, "if1", "end_algorithm") == [
			["x < 0", "x := 0 - x"],
			["not(x < 0)", "x := x + 1"]] as Set
	}

	def "two decrementers"() {
		when:
		def DEBUG = false
		def graph = graph({
			While("x > 0") { Assign("x := x - 1") }
			Assert("x = 0")
			While("y > 0") { Assign("y := y - 1") }
			Assert("y = 0")
		})

		then:
		if (DEBUG) print(graph)
		graph.size() == 3
		graph.nodes == nodes(graph, "end_algorithm", "while0", "while1")
		graph.edges.size() == 4
		edge(graph, "while0", "while0") == ["x > 0", "x := x - 1"]
		edge(graph, "while0", "while1") == ["not(x > 0)"]
		edge(graph, "while1", "while1") == ["y > 0", "y := y - 1"]
		edge(graph, "while1", "end_algorithm") == ["not(y > 0)"]
	}

	def "an empty if has two nodes"() {
		when:
		def DEBUG = false
		def graph = graph({
			If("x < 4") {
				Then {}
				Else {}
			}
		})

		then:
		if (DEBUG) print(graph)
		graph.size() == 2
		graph.nodes == nodes(graph, "end_algorithm", "if0")
		edges(graph, "if0", "end_algorithm") == [["x < 4"], ["not(x < 4)"]] as Set
	}

	def "an if with then has 2 nodes"() {
		when:
		def DEBUG = false
		def graph = graph({
			If("x < 4") { Then("x := 1") }
		})

		then:
		if (DEBUG) print(graph)
		graph.size() == 2
		graph.nodes == nodes(graph, "end_algorithm", "if0")
		graph.edges.size() == 2
		edges(graph, "if0", "end_algorithm") == [
			["x < 4", "x := 1"],
			["not(x < 4)"]] as Set
	}

	def "an if with else has 2 nodes"() {
		when:
		def DEBUG = false
		def graph = graph({
			If("x < 4") {
				Then {}
				Else("x := 1")
			}
		})

		then:
		if (DEBUG) print(graph)
		graph.size() == 2
		graph.nodes == nodes(graph, "end_algorithm", "if0")
		graph.edges.size() == 2
		edges(graph, "if0", "end_algorithm") == [
			["x < 4"],
			["not(x < 4)", "x := 1"]] as Set
	}

	def "cannot create empty while"() {
		when:
		def DEBUG = false
		def graph = graph({
			While("x < 4") {
			}
		})

		then:
		thrown IllegalArgumentException
	}

	def "a while with one stmt"() {
		when:
		def DEBUG = false
		def graph = graph({
			While("x < 4") { Assign("x := 2") }
		})

		then:
		if (DEBUG) print(graph)
		graph.size() == 2
		graph.nodes == nodes(graph, "end_algorithm", "while0")
		graph.edges.size() == 2
		edge(graph, "while0", "while0") == ["x < 4", "x := 2"]
		edge(graph, "while0", "end_algorithm") == ["not(x < 4)"]
	}

	def "optimized euclid"() {
		when:
		def DEBUG = false
		def graph = graph({
			While("u /= 0") {
				If ("u < v") { Then("u,v := v,u") }
				Assert("u > v")
				Assign("u := u - v")
			}
			Assert("u|->m|->n : IsGCD")
		})

		then:
		if (DEBUG) print(graph)
		graph.nodes == nodes(graph,  "assign1", "end_algorithm", "while0", "if0")
		graph.size() == 4
		graph.edges.size() == 5
		assertions(graph, "assign1") == ["u > v"] as Set
		assertions(graph, "end_algorithm") == ["u|->m|->n : IsGCD"] as Set
		edge(graph, "while0", "if0") == ["u /= 0"]
		edges(graph, "if0", "assign1") == [
			["u < v", "u,v := v,u"],
			["not(u < v)"]] as Set
		edge(graph, "assign1", "while0") == ["u := u - v"]
		edge(graph, "while0", "end_algorithm") == ["not(u /= 0)"]
	}

	def "russische bauernmultiplikation"(){
		when:
		def DEBUG = false
		def graph = graph({
			While("l /= 1") {
				Assign("l,r := l / 2, r * 2")
				If("l mod 2 /= 0") { Then("product := product + r") }
			}
			Assert("product = m * n")
		})

		then:
		if (DEBUG) print(graph)
		graph.size() == 3
		graph.nodes == nodes(graph,  "end_algorithm", "while0", "if0")
		graph.edges.size() == 4
		assertions(graph, "end_algorithm") == ["product = m * n"] as Set
		edge(graph, "while0", "if0") == [
			"l /= 1",
			"l,r := l / 2, r * 2"
		]
		edges(graph, "if0", "while0") == [
			[
				"l mod 2 /= 0",
				"product := product + r"
			],
			["not(l mod 2 /= 0)"]] as Set
		edge(graph, "while0", "end_algorithm") == ["not(l /= 1)"]
	}

	def "complicated while if"() {
		when:
		def DEBUG = false
		def graph = graph({
			While("x : ODD") {
				If ("x = 2") {
					Then("x := x + 1")
					Else {
						If ("x = 3") {
							Then("x := x + 2")
							Else {
								If("x = 4") {
									Then("x := x + 3")
									Else("x := x - 5")
								}
							}
						}
					}
				}
				If ("y = 3") {
					Then("x := y + 2")
					Else("x := y + 3")
				}
				Assign("x := y - 2")
			}
			Assign("z := x + y")
		})

		then:
		if (DEBUG) print(graph)
		graph.size() == 7
		graph.edges.size() == 11
		graph.nodes == nodes(graph, "assign6", "end_algorithm", "while0", "if0", "if1", "if2", "if3")
		edge(graph, "while0","if0") == ["x : ODD"]
		edge(graph, "if0", "if3") == ["x = 2", "x := x + 1"]
		edge(graph, "if0", "if1") == ["not(x = 2)"]
		edge(graph, "if1", "if3") == ["x = 3", "x := x + 2"]
		edge(graph, "if1", "if2") == ["not(x = 3)"]
		edges(graph, "if2", "if3") == [
			["x = 4", "x := x + 3"],
			["not(x = 4)", "x := x - 5"]] as Set
		edges(graph, "if3", "assign6") == [
			["y = 3", "x := y + 2"],
			["not(y = 3)", "x := y + 3"]] as Set
		edge(graph, "assign6", "while0") == ["x := y - 2"]
		edge(graph, "while0", "end_algorithm") == ["not(x : ODD)", "z := x + y"]
	}

	def "complicated while if 2"() {
		when:
		def DEBUG = false
		def graph = graph({
			Assign("y,x := 0,2")
			While("x = 2") {
				Assign("y := y + 1")
				If ("y > 10")  { Then("x := 3") }
			}
			While("x + y < 20") { Assign("x,y := x + 1,y+1") }
			Assert("x + y > 20")
		})

		then:
		if (DEBUG) print(graph)
		graph.size() == 5
		graph.edges.size() == 7
		graph.nodes == nodes(graph, "assign0",
				"end_algorithm", "while0", "while1", "if0")
		assertions(graph, "end_algorithm") == ["x + y > 20"] as Set
		edge(graph, "assign0", "while0") == ["y,x := 0,2"]
		edge(graph, "while0", "if0") == ["x = 2", "y := y + 1"]
		edges(graph, "if0", "while0") == [
			["y > 10", "x := 3"],
			["not(y > 10)"]] as Set
		edge(graph, "while0", "while1") == ["not(x = 2)"]
		edge(graph, "while1", "while1") == [
			"x + y < 20",
			"x,y := x + 1,y+1"
		]
		edge(graph, "while1", "end_algorithm") == ["not(x + y < 20)"]
	}

	def "loop within loop"() {
		when:
		def DEBUG = false
		def graph = graph({
			While("x < 50") {
				If("y > x") {
					Then {
						While("x < y") { Assign("x := x + 1") }
					}
				}
				Assign("y,x := y / 2, x / 2")
			}
			Assign("z := y + x")
		})

		then:
		if (DEBUG) print(graph)
		graph.size() == 4
		graph.edges.size() == 6
		graph.nodes == nodes(graph,"end_algorithm",
				"while0", "while1", "if0")
		edge(graph, "while0", "if0") == ["x < 50"]
		edge(graph, "if0", "while1") == ["y > x"]
		edge(graph, "while1", "while1") == ["x < y", "x := x + 1"]
		edge(graph, "while1", "while0") == [
			"not(x < y)",
			"y,x := y / 2, x / 2"
		]
		edge(graph, "if0", "while0") == [
			"not(y > x)",
			"y,x := y / 2, x / 2"
		]
		edge(graph, "while0","end_algorithm") == ["not(x < 50)", "z := y + x"]
	}

	def "loopity loop loop loop"() {
		when:
		def DEBUG = false
		def graph = graph({
			While("x < 50") {
				If("y > x") {
					Then {
						While("x < y") { Assign("x := x + 1") }
					}
				}
			}
			While("z < 50") {
				If ("z < 0") { Then("z := 0 - z") }
				Assign("z := z + 1")
			}
		})

		then:
		if (DEBUG) print(graph)
		graph.size() == 7
		graph.edges.size() == 11
		graph.nodes == nodes(graph, "assign2", "end_algorithm",
				"while0", "while1", "while2", "if0", "if1")
		edge(graph, "while0", "if0") == ["x < 50"]
		edge(graph, "if0", "while1") == ["y > x"]
		edge(graph, "while1", "while1") == ["x < y", "x := x + 1"]
		edge(graph, "while1", "while0") == ["not(x < y)"]
		edge(graph, "if0", "while0") == ["not(y > x)"]
		edge(graph, "while0", "while2") == ["not(x < 50)"]
		edge(graph, "while2", "if1") == ["z < 50"]
		edges(graph, "if1", "assign2") == [
			["z < 0", "z := 0 - z"],
			["not(z < 0)"]] as Set
		edge(graph, "assign2", "while2") == ["z := z + 1"]
		edge(graph, "while2", "end_algorithm") == ["not(z < 50)"]
	}

	def "correct return"() {
		when:
		def DEBUG = false
		def graph = graph({
			If ("x = 5") {
				Then { Return("x") }
			}
			If ("y = 5") {
				Then { Return("y") }
			}
			Assign("z := 5")
			Return("z")
		})
		then:
		if (DEBUG) print(graph)
		graph.size() == 4
		graph.edges.size() == 5
		graph.nodes == nodes(graph, "if0", "if1", "return2", "end_algorithm")
		edge(graph, "if0", "end_algorithm") == ["x = 5"]
		edge(graph, "if0", "if1") == ["not(x = 5)"]
		edge(graph, "if1", "end_algorithm") == ["y = 5"]
		edge(graph, "if1", "return2") == ["not(y = 5)", "z := 5"]
		edge(graph, "return2", "end_algorithm") == []
	}
}
