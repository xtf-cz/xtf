package cz.xtf;

public enum Product {
	EAP("eap"),
	JWS_TOMCAT7("webserver"),
	JWS_TOMCAT8("webserver"),
	AMQ("amq"),
	JDG("datagrid"),
	JDV("datavirt"),
	BRMS("decisionserver"),
	BPMS("processserver"),
	SSO("sso"),
	SECRETS("secrets"),
	DEMOS("demos");
	
	private final String templatePath;

	private Product(String templatePath) {
		this.templatePath = templatePath;
	}

	public String getTemplatePath() {
		return templatePath;
	}
}
