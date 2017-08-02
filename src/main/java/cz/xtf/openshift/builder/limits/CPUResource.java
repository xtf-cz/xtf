package cz.xtf.openshift.builder.limits;

public class CPUResource extends ComputingResource {

	@Override
	public String resourceIdentifier() {
		return "cpu";
	}
}
