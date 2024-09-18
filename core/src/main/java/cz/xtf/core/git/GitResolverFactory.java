package cz.xtf.core.git;

import cz.xtf.core.config.XTFConfig;

class GitResolverFactory {
    static final String GIT_URL = "xtf.git.repository.url";
    static final String GIT_BRANCH = "xtf.git.repository.ref";

    public static GitResolver createResolver() {
        if (XTFConfig.get(GIT_URL) == null || XTFConfig.get(GIT_BRANCH) == null) {
            return new JGitResolver();
        } else {
            return new SystemPropertyGitResolver();
        }
    }
}
