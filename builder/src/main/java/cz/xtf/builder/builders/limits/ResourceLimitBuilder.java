package cz.xtf.builder.builders.limits;

public interface ResourceLimitBuilder {

    ComputingResource addCPUResource();

    ComputingResource addMemoryResource();

}
