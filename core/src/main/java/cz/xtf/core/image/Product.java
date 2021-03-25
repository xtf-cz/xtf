package cz.xtf.core.image;

import cz.xtf.core.config.XTFConfig;

public class Product {
    private final String id;

    public Product(String id) {
        this.id = id;
    }

    public Image image() {
        return Image.resolve(id.replaceAll("\\..*", ""));
    }

    public String property(String propertyId) {
        return resolveDefaultingProperty("properties." + propertyId);
    }

    public String version() {
        return resolveDefaultingProperty("version");
    }

    public String templatesRepo() {
        return resolveDefaultingProperty("templates.repo");
    }

    public String templatesBranch() {
        return resolveDefaultingProperty("templates.branch");
    }

    public String helmChartsRepo() {
        return resolveDefaultingProperty("helm.charts.repo");
    }

    public String helmChartsBranch() {
        return resolveDefaultingProperty("helm.charts.branch");
    }

    public String helmChartsLocation() {
        return resolveDefaultingProperty("helm.charts.location");
    }

    public String helmChartExamplesLocation() {
        return resolveDefaultingProperty("helm.charts.examples.location");
    }

    private String resolveDefaultingProperty(String propertyId) {
        String value = XTFConfig.get("xtf." + id + "." + propertyId);
        String defaultingValue = id.contains(".") ? XTFConfig.get("xtf." + id.replaceAll("\\..*", "") + "." + propertyId)
                : null;

        return value != null ? value : defaultingValue;
    }
}
