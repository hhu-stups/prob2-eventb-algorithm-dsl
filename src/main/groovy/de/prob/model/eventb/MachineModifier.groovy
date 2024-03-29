package de.prob.model.eventb

import org.eventb.core.ast.extension.IFormulaExtension

import de.prob.model.eventb.Event.EventType
import de.prob.model.eventb.algorithm.ast.Block
import de.prob.model.representation.BEvent
import de.prob.model.representation.ElementComment
import de.prob.model.representation.Invariant
import de.prob.model.representation.Machine
import de.prob.model.representation.ModelElementList
import de.prob.model.representation.Variable



/**
 * The {@link MachineModifier} provides an API to programmatically modify or
 * construct {@link EventBMachine}s. Basic elements can be added to the machine
 * via methods like {@link MachineModifier#invariant(String)}.
 * <br>
 * Machines can also be constructed using JavaBuilder syntax which adds an element
 * and returns the {@link MachineModifier} object itself to allow the method calls
 * to be chained together.
 * <br>
 * For example: <br>
 * <code>modifier.var_block("x","x:NAT","x:=0").invariant("x < 10")</code>
 * <br>
 * These methods can then be put together to create a Groovy DSL.
 * <br>
 * <code>
 * modifier.make {<br>
 * <br>
 * var_block name: "x", invariant: "x:NAT", init: "x:=0"<br>
 * <br>
 * event(name: "inc") { action x:=x+1 }<br>
 * <br>
 * }<br>
 * </code>
 * @author Joy Clark
 */
public class MachineModifier extends AbstractModifier {
	EventBMachine machine
	EventBModel model

	public MachineModifier(EventBMachine machine, Set<IFormulaExtension> typeEnvironment = Collections.emptySet()) {
		super(typeEnvironment)
		this.machine = validate('machine',machine)
	}

	private newMM(EventBMachine machine) {
		new MachineModifier(machine, typeEnvironment)
	}

	def MachineModifier setSees(ModelElementList<Context> seenContexts) {
		newMM(machine.withSees(validate("seenContexts", seenContexts)))
	}

	def MachineModifier setRefines(EventBMachine refined) {
		validate("refined", refined)
		newMM(machine.withRefinesMachine(refined))
	}

	def MachineModifier variables(String... variables) throws ModelGenerationException {
		MachineModifier mm = this
		validate("variables", variables).each {
			mm = mm.variable(it)
		}
		mm
	}

	def MachineModifier variable(String varName, String comment="") throws ModelGenerationException {
		parseIdentifier(varName)
		variable(new EventBVariable(varName, null, comment))
	}

	def MachineModifier variable(EventBVariable variable) throws ModelGenerationException {
		newMM(machine.withVariables(machine.variables.addElement(variable)))
	}

	def MachineModifier var(Map<String, ?> properties) throws ModelGenerationException {
		Map validated = validateProperties(validate("properties", properties), [name: String, invariant: Object, init: Object])
		var(validated.name, validated.invariant, validated.init)
	}

	def MachineModifier var(final String name, String invariant, String init) throws ModelGenerationException {
		MachineModifier mm = variable(name)
		mm = mm.invariant("typing_$name", invariant)
		mm = mm.initialisation({ action "init_$name", init })
		mm
	}

	def MachineModifier var(String name, Map<String, String> inv, Map<String, String> init) throws ModelGenerationException {
		MachineModifier mm = variable(name)
		mm = mm.invariant(inv)
		mm = mm.initialisation({ action init })
		mm
	}

	def MachineModifier removeVariable(String name) {
		def var = machine.variables.getElement(name)
		var ? removeVariable(var) : this
	}

	def MachineModifier removeVariable(EventBVariable variable) {
		newMM(machine.withVariables(machine.variables.removeElement(variable)))
	}

	def MachineModifier invariants(Map<?, ?> invariants) throws ModelGenerationException {
		MachineModifier mm = this
		validate("invariants", invariants).each { k,v ->
			mm = mm.invariant(k,v)
		}
		mm
	}

	def MachineModifier invariants(String... invariants) throws ModelGenerationException {
		MachineModifier mm = this
		invariants.each {
			mm = mm.invariant(it)
		}
		mm
	}

	def MachineModifier theorems(Map<String, String> invariants) throws ModelGenerationException {
		MachineModifier mm = this
		invariants.each { k,v ->
			mm = mm.theorem(k,v)
		}
		mm
	}

	def MachineModifier theorems(String... invariants) throws ModelGenerationException {
		MachineModifier mm = this
		invariants.each {
			mm = mm.theorem(it)
		}
		mm
	}

	def MachineModifier theorem(Map<String, String> properties) throws ModelGenerationException {
		invariant(properties, true)
	}

	def MachineModifier theorem(String thm) throws ModelGenerationException {
		invariant(validate("thm", thm), true)
	}

	def MachineModifier theorem(String name, String pred) throws ModelGenerationException {
		invariant(validate("name", name), validate("pred", pred), true)
	}

	def MachineModifier invariant(Map<String, String> properties, boolean theorem=false) throws ModelGenerationException {
		Definition prop = getDefinition(properties)
		return invariant(prop.label, prop.formula, theorem)
	}

	def MachineModifier invariant(String pred, boolean theorem=false) throws ModelGenerationException {
		invariant(theorem ? "thm0" : "inv0", validate("pred", pred), theorem)
	}

	def MachineModifier invariant(String name, String predicate, boolean theorem=false, String comment="") throws ModelGenerationException {
		validateAll(name, predicate)
		invariant(new EventBInvariant(name, parsePredicate(predicate), theorem, comment))
	}

	def MachineModifier invariant(EventBInvariant invariant) {
		def newproofs = machine.getProofs().findAll { ProofObligation po ->
			!po.getName().endsWith("/INV")
		}

		def inv = invariant
		def uniqueName = getUniqueName(invariant.getName(), machine.getAllInvariants())
		if (uniqueName != invariant.getName()) {
			inv = new EventBInvariant(uniqueName, invariant.getPredicate(), invariant.isTheorem(), invariant.getComment())
		}

		machine = machine.withInvariants(machine.invariants.addElement(inv))
		machine = machine.withProofs(new ModelElementList<>(newproofs))
		newMM(machine)
	}

	def MachineModifier removeInvariant(String name) {
		def inv = machine.invariants.getElement(name)
		inv ? removeInvariant(inv) : this
	}

	/**
	 * Removes an invariant from the machine.
	 * @param invariant to be removed
	 * @return whether or not the removal was successful
	 */
	def MachineModifier removeInvariant(EventBInvariant invariant) {
		// only variant well-definedness may not use existing invariants in a prove
		// thus, these seem to be the only proof obligations we can keep
		def newproofs = machine.getProofs().findAll { po ->
			po.getName().equals("VWD")
		}

		newMM(machine.withInvariants(machine.invariants.removeElement(invariant))
				.withProofs(new ModelElementList<ProofObligation>(newproofs)))
	}

	def MachineModifier variant(String expression, String comment="") throws ModelGenerationException {
		variant(new Variant(parseExpression(expression), comment))
	}

	def MachineModifier variant(Variant variant) {
		def mm = removePOsForVariant()
		newMM(mm.machine.withVariant(variant))
	}

	def MachineModifier removePOsForVariant() {
		def newproofs = machine.getProofs().findAll { po ->
			!(po.getName().equals("VWD") ||
					po.getName().equals("FIN") ||
					po.getName().endsWith("/VAR") ||
					po.getName().endsWith("/NAT"))
		}

		newMM(machine.withProofs(new ModelElementList<>(newproofs)))
	}

	def MachineModifier removeVariant(Variant variant) {
		if (machine.variant != variant) {
			return this
		}
		def mm = removePOsForVariant()
		newMM(mm.machine.withVariant(null))
	}

	def MachineModifier initialisation(Map<String, ?> properties, Closure<?> cls={}) throws ModelGenerationException {
		validate("properties", properties)
		if (properties["extended"] == true) {
			return initialisation(cls,true)
		}
		this
	}

	def MachineModifier initialisation(Closure<?> cls, boolean extended=false) throws ModelGenerationException {
		def refines = machine.refinesMachine == null ? null : "INITIALISATION"
		event("INITIALISATION", refines, EventType.ORDINARY, extended, null, validate("cls",cls))
	}

	def MachineModifier refine(Map<String, ?> properties, Closure<?> cls={}) throws ModelGenerationException {
		validate("properties", properties)
		properties["refines"] = properties["name"]
		event(properties, validate("cls", cls))
	}

	def MachineModifier event(Map<String, ?> properties, Closure<?> cls={}) throws ModelGenerationException {
		def props = validateProperties(properties, [name: String, refines: [String, null],
			extended: [Boolean, false], comment: [String, null], type: [
				EventType,
				EventType.ORDINARY
			]])

		event(props["name"], props["refines"], props["type"],props["extended"],props["comment"], validate("cls",cls))
	}

	def MachineModifier event(String name, String refinedEvent, EventType type, boolean extended, String comment=null, Closure<?> cls={}) throws ModelGenerationException {
		validateAll(name, type, cls)
		if (refinedEvent && machine.refinesMachine == null) {
			throw new IllegalArgumentException("Machine refinement hierarchy is incorrect. Could not find Event $refinedEvent to refine")
		}
		if (refinedEvent && !machine.refinesMachine.getEvent(refinedEvent)) {
			throw new IllegalArgumentException("The event $refinedEvent does not exist in the refined machine and therefore cannot be refined in the existing context.")
		}
		def event = machine.getEvent(name) ?: new Event(name, type, extended ? Event.Inheritance.EXTENDS : Event.Inheritance.NONE)
		def em = new EventModifier(event, "INITIALISATION" == name, typeEnvironment).setType(type)
		em = refinedEvent ? em.refines(machine.refinesMachine.getEvent(refinedEvent), extended) : em
		em = em.addComment(comment).make(cls)

		addEvent(em.getEvent())
	}

	def MachineModifier addEvent(Event event) {
		if (machine.getEvent(event.getName())) {
			return replaceEvent(machine.getEvent(event.getName()), event)
		}
		def mm = removePOsForEvent(event.name)
		newMM(mm.machine.withEvents(mm.machine.events.addElement(event)))
	}

	def MachineModifier replaceEvent(Event oldEvent, Event newEvent) {
		def mm = removePOsForEvent(oldEvent.name)
		newMM(mm.machine.withEvents(mm.machine.events.replaceElement(oldEvent, newEvent)))
	}

	/**
	 * Generates a new {@link Event} in the machine that is identical to
	 * the specified event for copying. The new {@link Event} object will
	 * have the specified name. If an existing {@link Event} in the machine
	 * has the same name, this will be overwritten.
	 * @param name of the event to be duplicated
	 * @param newName of the cloned event
	 */
	def MachineModifier duplicateEvent(String eventName, String newName) {
		validateAll(eventName, newName)
		Event event = machine.getEvent(eventName)
		if (!event) {
			throw new IllegalArgumentException("Can only duplicate an event that exists! Event with name $eventName was not found.")
		}
		addEvent(event.withName(newName))
	}

	def MachineModifier removeEvent(String name) {
		def evt = machine.events.getElement(name)
		evt ? removeEvent(evt) : this
	}

	/**
	 * Removes an event from the machine.
	 * @param event to be removed
	 * @return whether or not the removal was successful
	 */
	def MachineModifier removeEvent(Event event) {
		MachineModifier mm = removePOsForEvent(event.name)
		newMM(mm.machine.withEvents(mm.machine.events.removeElement(event)))
	}

	def MachineModifier removePOsForEvent(String name) {
		def proofs = machine.getProofs()
		proofs.each {
			if (it.name.startsWith(validate('name', name) + "/")) {
				proofs = proofs.removeElement(it)
			}
		}
		newMM(machine.withProofs(proofs))
	}

	def MachineModifier addComment(String comment) {
		if (!comment) {
			return this
		}

		def existingComment = machine.comment
		newMM(machine.withComment(existingComment == null ? comment : existingComment + "\n" + comment))
	}

	def MachineModifier algorithm(Closure<?> definition) throws ModelGenerationException {
		newMM(machine.addTo(Block.class, new Block([],typeEnvironment).make(definition)))
	}

	def MachineModifier algorithm(Block algorithm) {
		newMM(machine.addTo(Block.class, algorithm))
	}

	def MachineModifier make(Closure<?> definition) throws ModelGenerationException {
		runClosure definition
	}



}
