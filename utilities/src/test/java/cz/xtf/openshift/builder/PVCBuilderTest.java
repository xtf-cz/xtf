package cz.xtf.openshift.builder;

import org.junit.Test;

public class PVCBuilderTest {

	@Test
	public void test() {
		PVCBuilder bldr = new PVCBuilder("ahoj");
		bldr.accessRWX();
		bldr.storageSize("1Gi");
		System.out.println(bldr.build());
	}
}
