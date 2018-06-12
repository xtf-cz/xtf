package cz.xtf.openshift.builder;

import org.junit.Test;

public class EndpointTest {

	@Test
	public void test() {
		EndpointBuilder bldr  = new EndpointBuilder("ahoj");
		bldr.addIP("1.2.3.4");
		bldr.addIP("5.6.7.8");
		bldr.addPort(100);
		System.out.println(bldr.build());
	}
}
