package cz.xtf.core.image;

import cz.xtf.core.config.XTFConfig;

public class Products {

    public static Product resolve(String id) {
        String subId = XTFConfig.get("xtf." + id + ".subid");
        return new Product(subId == null ? id : id + "." + subId);
    }
}