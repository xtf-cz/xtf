package cz.xtf.core.git;

interface GitResolver {
    String resolveRepoUrl();

    String resolveRepoRef();
}
