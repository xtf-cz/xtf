package cz.xtf.core.bm;

import java.nio.file.Path;
import java.util.Map;

/**
 * Mutable version of {@link BinarySourceBuild}. The hash of the app itself is recomputed every time it is needed so the build
 * is rebuilded if hash differs from previous one.
 */
public class MutableBinarySourceBuild extends BinarySourceBuild {
    public MutableBinarySourceBuild(String builderImage, Path path, Map<String, String> envProperties, String id) {
        super(builderImage, path, envProperties, id);
    }

    @Override
    protected boolean isCached() {
        return false;
    }
}
