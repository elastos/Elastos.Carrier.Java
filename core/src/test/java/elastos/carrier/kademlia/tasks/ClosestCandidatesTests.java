package elastos.carrier.kademlia.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import elastos.carrier.Id;
import elastos.carrier.NodeInfo;

public class ClosestCandidatesTests {
	@Test
	public void testAdd() {
		Id target = Id.random();
		ClosestCandidates cc = new ClosestCandidates(target, 16);

		List<NodeInfo> nodes = new ArrayList<>();
		for (int i = 0; i < 8; i++) {
			NodeInfo node = new NodeInfo(Id.random(), "192.168.1." + (i+1), 12345);
			nodes.add(node);
		}

		cc.add(nodes);

		assertEquals(8, cc.size());
		for (NodeInfo node : nodes) {
			NodeInfo cn = cc.get(node.getId());
			assertEquals(node, cn);
		}

		// Duplicated node id
		List<NodeInfo> nodes2 = new ArrayList<>();
		for (int i = 0; i < 8; i++) {
			NodeInfo node = new NodeInfo(nodes.get(i).getId(), "192.168.1." + (i+16), 12345);
			nodes2.add(node);
		}

		cc.add(nodes2);

		assertEquals(8, cc.size());
		for (NodeInfo node : nodes) {
			NodeInfo cn = cc.get(node.getId());
			assertEquals(node, cn);
		}

		// Duplicated node address
		List<NodeInfo> nodes3 = new ArrayList<>();
		for (int i = 0; i < 8; i++) {
			NodeInfo node = new NodeInfo(Id.random(), "192.168.1." + (i+1), 12345);
			nodes3.add(node);
		}

		assertEquals(8, cc.size());
		for (NodeInfo node : nodes) {
			NodeInfo cn = cc.get(node.getId());
			assertEquals(node, cn);
		}

		// Another 16 good candidates
		List<NodeInfo> nodes4 = new ArrayList<>();
		for (int i = 0; i < 16; i++) {
			NodeInfo node = new NodeInfo(Id.random(), "192.168.1." + (i+16), 12345);
			nodes4.add(node);
		}

		cc.add(nodes4);

		// Check the final result
		List<NodeInfo> all = new ArrayList<>();
		all.addAll(nodes);
		all.addAll(nodes4);
		Collections.sort(all, (n1, n2) -> target.threeWayCompare(n1.getId(), n2.getId()));

		assertEquals(16, cc.size());
		for (int i = 0; i < cc.size(); i++) {
			NodeInfo node = all.get(i);
			NodeInfo cn = cc.get(node.getId());
			assertEquals(node, cn);
		}
	}
}
