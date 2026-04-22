package cz.xtf.core.helm;

/**
 * Interface for resolving Helm client binary
 */
interface HelmBinaryPathResolver {
    /**
     * Resolves Helm client binary path
     *
     * @return Helm client binary path
     */
    String resolve();
}
