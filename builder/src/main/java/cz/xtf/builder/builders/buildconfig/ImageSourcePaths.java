package cz.xtf.builder.builders.buildconfig;

import lombok.Getter;

@Getter
public class ImageSourcePaths {
    private String sourcePath;
    private String destinationPath;

    public ImageSourcePaths(String sourcePath, String destinationPath) {
        this.sourcePath = sourcePath;
        this.destinationPath = destinationPath;
    }
}
