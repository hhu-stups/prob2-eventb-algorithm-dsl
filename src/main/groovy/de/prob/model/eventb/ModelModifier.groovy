package de.prob.model.eventb

import javax.xml.parsers.SAXParser
import javax.xml.parsers.SAXParserFactory

import org.eventb.core.ast.extension.IFormulaExtension

import de.prob.Main
import de.prob.model.eventb.algorithm.Procedure
import de.prob.model.eventb.theory.Theory
import de.prob.model.eventb.translate.TheoryExtractor
import de.prob.model.representation.DependencyGraph.Edge;
import de.prob.model.representation.ElementComment
import de.prob.model.representation.ModelElementList
import de.prob.model.representation.DependencyGraph.ERefType
import de.prob.scripting.EventBFactory


public class ModelModifier extends AbstractModifier {

	EventBModel model

	/**
	 * Creates an interface to allow the user to mutate the model object.
	 * The user can also specify an additional parameter 'startProB' which will
	 * determine if a ProB instance will be bound to the new
	 * model class. If not, a ProB instance can be lazily created later by calling
	 * the getStateSpace() method on the model object.
	 * @param model to be copied
	 * @param startProB default = true
	 */
	public ModelModifier(EventBModel model, Set<IFormulaExtension> typeEnvironment=Collections.emptySet()) {
		super(typeEnvironment)
		this.model = model
	}

	/**
	 * @deprecated This constructor relies on the ProB Java API's deprecated global injector.
	 *     Call {@link #ModelModifier(EventBModel)} instead and use injection to get a blank {@link EventBModel}.
	 */
	@Deprecated
	def ModelModifier() {
		this(Main.getInjector().getInstance(EventBFactory.class).modelCreator.get())
	}

	def ModelModifier newMM(EventBModel model) {
		new ModelModifier(model.calculateDependencies(), typeEnvironment)
	}

	def ModelModifier context(Map<String, ?> properties, Closure<?> definition) throws ModelGenerationException {
		def props = validateProperties(properties, [name: String, "extends": [String, null], comment: [String, null]])
		def model = this.model
		def name = properties["name"]
		def oldcontext = model.getContexts().getElement(name)
		def c = oldcontext ?: new Context(name)
		ContextModifier cm = new ContextModifier(c, typeEnvironment).addComment(props["comment"])

		if (props["extends"]) {
			Context ctx = model.getContexts().getElement(props["extends"])
			if (ctx == null) {
				throw new IllegalArgumentException("Tried to load context ${props['extends']} but could not find it.")
			}
			cm = cm.setExtends(ctx)
		}

		cm = cm.make(definition)
		addContext(cm.getContext())
	}

	def ModelModifier machine(Map<String, ?> properties, Closure<?> definition) throws ModelGenerationException {
		def props = validateProperties(properties, [name: String, refines: [String, null], sees: [List, []], comment: [String, null]])
		def model = this.model
		def name = props["name"]
		def oldmachine = model.getMachines().getElement(name)
		EventBMachine m = oldmachine ?: new EventBMachine(name)
		MachineModifier mm = new MachineModifier(m, typeEnvironment).addComment(props["comment"])

		if (props["refines"]) {
			EventBMachine machine = model.getMachines().getElement(props["refines"])
			if (machine == null) {
				throw new IllegalArgumentException("Tried to load machine ${props['refines']} but could not find it")
			}
			mm = mm.setRefines(machine)
		}

		ModelElementList<Context> seenContexts = m.getSees()
		def sees = props["sees"].findAll { seenContexts.getElement(it) == null }
		sees.each { c ->
			Context context = model.getContexts().getElement(c)
			if (context == null) {
				throw new IllegalArgumentException("Tried to load context $c but could not find it")
			}
			model = model.addRelationship(name, c, ERefType.SEES)
			seenContexts = seenContexts.addElement(context)
		}

		mm = mm.setSees(seenContexts).make(definition)
		addMachine(mm.getMachine())
	}

	def ModelModifier refine(String machineName, String refinementName) {
		final EventBMachine m = model.getMachines().getElement(machineName)
		if (m == null) {
			throw new IllegalArgumentException("Can only refine an existing machine in the model")
		}

		def sees = m.getSees().collect { it.getName() }
		ModelModifier modelM = machine(name: refinementName, refines: machineName, sees: sees, comment: m.comment) {
			m.variables.each { variable(it) }
			m.events.each { Event e ->
				refine(name: e.getName(), extended: true, type: e.getType(), comment: e.comment) {}
			}
		}
		modelM
	}

	def ModelModifier addContext(Context newContext) {
		if (model.getContext(newContext.getName())) {
			return replaceContext(model.getContext(newContext.getName()), newContext)
		}
		newMM(model.addContext(newContext).calculateDependencies())
	}

	def ModelModifier addMachine(EventBMachine newMachine) {
		if (model.getMachine(newMachine.getName())) {
			return replaceMachine(model.getMachine(newMachine.getName()), newMachine)
		}
		newMM(model.addMachine(newMachine).calculateDependencies())
	}

	def ModelModifier removeContext(Context context) {
		EventBModel m = model
		m = m.withContexts(m.contexts.removeElement(context))
		m.graph.getIncomingEdges(context.getName()).each { Edge e ->
			if (e.relationship == ERefType.EXTENDS) {
				Context ctx = m.getContext(e.getFrom().getElementName())
				Context ctx2 = ctx.withExtends(ctx.extends.removeElement(context))
				m = newMM(m).replaceContext(ctx, ctx2).getModel()
			} else if (e.relationship == ERefType.SEES) {
				EventBMachine mch = m.getMachine(e.getFrom().getElementName())
				EventBMachine mch2 = mch.withSees(mch.sees.removeElement(context))
				m = newMM(m).replaceMachine(mch, mch2).getModel()
			}
		}
		newMM(m)
	}

	def ModelModifier removeMachine(EventBMachine machine) {
		EventBModel m = model
		m = m.withMachines(m.machines.removeElement(machine))
		m.graph.getIncomingEdges(machine.getName()).each { Edge e ->
			if (e.relationship == ERefType.REFINES) {
				EventBMachine mch = m.getMachine(e.getFrom().getElementName())
				EventBMachine mch2 = mch.withRefinesMachine(null)
				m = newMM(m).replaceMachine(mch, mch2).getModel()
			}
		}
		newMM(m)
	}

	def ModelModifier replaceContext(Context oldContext, Context newContext) {
		EventBModel m = model
		if (m.mainComponent == oldContext) {
			m = m.withMainComponent(newContext)
		}
		m = m.withContexts(m.contexts.replaceElement(oldContext, newContext))
		m.graph.getIncomingEdges(oldContext.getName()).each { Edge e ->
			if (e.relationship == ERefType.EXTENDS) {
				Context ctx = m.getContext(e.getFrom().getElementName())
				Context ctx2 = ctx.withExtends(ctx.extends.replaceElement(oldContext, newContext))
				m = newMM(m).replaceContext(ctx, ctx2).getModel()
			} else if (e.relationship == ERefType.SEES) {
				EventBMachine mch = m.getMachine(e.getFrom().getElementName())
				EventBMachine mch2 = mch.withSees(mch.sees.replaceElement(oldContext, newContext))
				m = newMM(m).replaceMachine(mch, mch2).getModel()
			}
		}
		newMM(m)
	}

	def ModelModifier replaceMachine(EventBMachine oldMachine, EventBMachine newMachine) {
		EventBModel m = model
		if (m.mainComponent == oldMachine) {
			m = m.withMainComponent(newMachine)
		}
		m = m.withMachines(m.machines.replaceElement(oldMachine, newMachine))
		m.graph.getIncomingEdges(oldMachine.getName()).each { Edge e ->
			if (e.relationship == ERefType.REFINES) {
				EventBMachine ma = m.getMachine(e.getFrom().getElementName())
				EventBMachine ma2 = ma.withRefinesMachine(newMachine)
				m = newMM(m).replaceMachine(ma, ma2).getModel()
			}
		}
		newMM(m)
	}

	def ModelModifier procedure(Map<String, ?> properties, Closure<?> definition) {
		def props = validateProperties(properties, [name: String, seen: [String, null]])
		Context ctx = props["seen"] ? model.getContext(props["seen"]) : null
		Procedure proc = new Procedure(properties["name"], ctx, typeEnvironment).make(definition)
		addProcedure(proc)
	}

	def ModelModifier addProcedure(Procedure procedure) {
		ModelModifier mm = this.addContext(procedure.getContext()).addMachine(procedure.getAbstractMachine()).addMachine(procedure.getImplementation())
		newMM(mm.getModel().addTo(Procedure.class, procedure))
	}

	def ModelModifier loadTheories(Map<String, ?> properties) {
		validateProperties(properties, [workspace: String, project: String, theories: String[]])
		SAXParserFactory parserFactory = SAXParserFactory.newInstance();
		SAXParser saxParser = parserFactory.newSAXParser();

		Map<String, Theory> theoryMap = [:]
		ModelElementList<Theory> theories = new ModelElementList<Theory>()
		HashSet<IFormulaExtension> types = new HashSet<IFormulaExtension>()
		types.addAll(typeEnvironment)
		validate('theories', properties["theories"]).each { String name ->
			def workspace = validate('workspace', properties["workspace"])
			def project = validate('project', properties["project"])
			validate('name', name)
			TheoryExtractor extractor = new TheoryExtractor(workspace, project, name, theoryMap);
			saxParser.parse(new File(workspace + File.separator + project + File.separator + name + ".dtf"), extractor);
			theories = theories.addMultiple(extractor.getTheories())
			types.addAll(extractor.getTypeEnv())
		}
		def model = model.withTheories(theories)
		new ModelModifier(model, types)
	}

	def ModelModifier make(Closure<?> definition) throws ModelGenerationException {
		runClosure definition
	}
}
