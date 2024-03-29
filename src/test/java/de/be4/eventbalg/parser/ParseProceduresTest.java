package de.be4.eventbalg.parser;

import org.junit.Test;

import de.be4.eventbalg.core.parser.BException;
import de.be4.eventbalg.core.parser.EventBParser;

public class ParseProceduresTest {

	@Test
	public void untyped() throws BException {
		new EventBParser().parse("procedure dequeue(x,y)=> r,z precondition x>y postcondition r=x & z=y implementation algorithm assert: 1=1 end end end");
	}

	@Test
	public void typed() throws BException {
		new EventBParser().parse("procedure dequeue(NAT x,INT y)=> NAT r,BOOL z precondition TRUE=TRUE postcondition r=x implementation algorithm assert: 1=1 end end end");
	}

	@Test
	public void mixed() throws BException {
		new EventBParser().parse("procedure dequeue(NAT x,y,z,INT m)=> BOOL r, z precondition TRUE=TRUE postcondition r=x implementation algorithm assert: 1=1 end end end");
	}

	@Test
	public void emptyArgs() throws BException {
		new EventBParser().parse("procedure dequeue()=> BOOL r, z precondition TRUE=TRUE postcondition r=x implementation algorithm assert: 1=1 end end end");
	}

	@Test
	public void emptyResults() throws BException {
		new EventBParser().parse("procedure dequeue(NAT x,y,z,INT m)=> precondition TRUE=TRUE postcondition r=x implementation algorithm assert: 1=1 end end end");
	}

	@Test
	public void addTyping() throws BException {
		new EventBParser().parse("procedure dequeue(x,y)=> r,z typing x: NAT typing y: INT <-> INT typing r: BOOL typing z: POW(NAT) precondition TRUE=TRUE postcondition r=x implementation algorithm assert: 1=1 end end end");
	}

	@Test
	public void testDequeue() throws BException {
		new EventBParser().parse("procedure dequeue(queue) => NAT element, newQueue typing queue: POW(NAT) typing newQueue: POW(NAT) precondition queue /= {} postcondition element : queue & newQueue = queue \\ {element}"
				+ " implementation var e type e : NAT init e :: NAT ; var q type q : POW(NAT) init q := queue  invariants @inv q <: queue "
				+ "algorithm assert: q /= {} & q = queue; assign: e :: q ; assign: q := q \\ {e}; assert: e : queue & q = queue \\ {e}; return q,e end end end");
	}

	@Test
	public void testSeen() throws BException {
		new EventBParser().parse("procedure dequeue(x,y)=> r,z sees definitions precondition TRUE=TRUE postcondition r=x implementation algorithm assert: 1=1 end end end");
	}
}
