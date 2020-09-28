package cz.xtf.builder.builders.limits;

public class MemoryResource extends ComputingResource {

    @Override
    public String resourceIdentifier() {
        return "memory";
    }
}
