package cz.xtf;

/**
 * This class is deprecated and will be deleted in one future releases.
 */
@Deprecated
public enum Product {
	EAP("eap"),
	JWS("webserver"),
	AMQ("amq"),
	AMQ7("amq7"),
	JDG("datagrid"),
	JDV("datavirt"),
	BRMS("decisionserver"),
	BPMS("processserver"),
	SSO("sso"),
	SECRETS("secrets"),
	JAVA("openjdk");
	
	private final String templatePath;

	private Product(String templatePath) {
		this.templatePath = templatePath;
	}

	public String getTemplatePath() {
		return templatePath;
	}
}
