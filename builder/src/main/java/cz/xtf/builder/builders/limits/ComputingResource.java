package cz.xtf.builder.builders.limits;

public abstract class ComputingResource {
    private String requests;
    private String limits;

    public String getRequests() {
        return requests;
    }

    public ComputingResource setRequests(String requests) {
        this.requests = requests;
        return this;
    }

    public String getLimits() {
        return limits;
    }

    public ComputingResource setLimits(String limits) {
        this.limits = limits;
        return this;
    }

    public abstract String resourceIdentifier();
}
