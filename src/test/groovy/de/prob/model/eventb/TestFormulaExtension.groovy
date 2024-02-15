package de.prob.model.eventb

import org.eventb.core.ast.Predicate
import org.eventb.core.ast.extension.ICompatibilityMediator
import org.eventb.core.ast.extension.IExtendedFormula
import org.eventb.core.ast.extension.IExtensionKind
import org.eventb.core.ast.extension.IFormulaExtension
import org.eventb.core.ast.extension.IPriorityMediator
import org.eventb.core.ast.extension.IWDMediator

class TestFormulaExtension implements IFormulaExtension {
	@Override
	String getSyntaxSymbol() {
		return "TestOp"
	}

	@Override
	Predicate getWDPredicate(IExtendedFormula formula, IWDMediator wdMediator) {
		return wdMediator.makeTrueWD()
	}

	@Override
	boolean conjoinChildrenWD() {
		return true
	}

	@Override
	String getId() {
		return "TestOpId"
	}

	@Override
	String getGroupId() {
		return "TestOpGroupId"
	}

	@Override
	IExtensionKind getKind() {
		return ATOMIC_EXPRESSION
	}

	@Override
	Object getOrigin() {
		return null
	}

	@Override
	void addCompatibilities(ICompatibilityMediator mediator) {}

	@Override
	void addPriorities(IPriorityMediator mediator) {}
}
