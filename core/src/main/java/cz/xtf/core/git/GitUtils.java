package cz.xtf.core.git;

public class GitUtils {
    private static final GitResolver gitResolver = GitResolverFactory.createResolver();

    // Static method to get repo URL and ref
    public static String getRepoUrl() {
        return gitResolver.resolveRepoUrl();
    }

    public static String getRepoRef() {
        return gitResolver.resolveRepoRef();
    }
}
