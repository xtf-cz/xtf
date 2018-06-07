package cz.xtf.openshift;

public class ProductVersion {
	private final String majorVersion;
	private final String versionString;

	public ProductVersion(final String majorVersion, final String versionString) {
		super();
		this.majorVersion = majorVersion;
		this.versionString = versionString;
	}
	
	public String getMajorVersion() {
		return majorVersion;
	}
	
	public String getVersionString() {
		return versionString;
	}

	@Override
	public String toString() {
		return "ProductVersion [majorVersion=" + majorVersion
				+ ", versionString=" + versionString + "]";
	}
}
