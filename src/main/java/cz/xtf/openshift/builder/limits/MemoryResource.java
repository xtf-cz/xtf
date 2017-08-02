package cz.xtf.openshift.builder.limits;

public class MemoryResource extends ComputingResource {

	@Override
	public String resourceIdentifier() {
		return "memory";
	}
}
