package cz.xtf.openshift.builder.limits;

public interface ResourceLimitBuilder {

	ComputingResource addCPUResource();

	ComputingResource addMemoryResource();

}
