package cz.xtf.sso.api;

import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.ProtocolMapperRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

import cz.xtf.sso.api.entity.User;

import javax.ws.rs.core.Response;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public class SsoRestApi implements SsoApi {

	public static SsoRestApi get(String authUrl, String realm) {
		return new SsoRestApi(authUrl, realm);
	}

	private final String realmName;
	private final String authUrl;
	private Keycloak client;

	private SsoRestApi(String authUrl, String realmName) {
		if (authUrl.contains("https://")) {
			throw new UnsupportedOperationException("Rest api does not work through https withing openshift router, see (there is issue somewhere)");
		}
		this.realmName = realmName;
		this.authUrl = authUrl;

		initClient();
	}

	/**
	 * Keycloak client is initialized by creating class instance however, if redirected to another node, it needs to be reinitialized.
	 */
	public void initClient() {
		this.client = Keycloak.getInstance(authUrl, "master", "admin", "admin", "admin-cli");
	}

	public <R> R withKeycloakClient(Function<Keycloak, R> f) {
		return f.apply(client);
	}

	public String getRealmName() {
		return realmName;
	}

	@Override
	public String createUser(String username, String password, String firstname, String lastname, String email, List<String> rolenames) {
		if (username.equals("user")) {
			throw new UnsupportedOperationException("Dont't do that! (Sso rest api doesn't create user with username 'user' properly)");
		}

		CredentialRepresentation cr = new CredentialRepresentation();
		cr.setType(CredentialRepresentation.PASSWORD);
		cr.setValue(password);
		cr.setTemporary(false);

		UserRepresentation ur = new UserRepresentation();
		ur.setUsername(username);
		ur.setCredentials(Arrays.asList(cr));
		ur.setFirstName(firstname);
		ur.setLastName(lastname);
		ur.setEmail(email);
		ur.setEnabled(true);

		Response response = client.realm(realmName).users().create(ur);
		response.close();

		String userId = getUserId(username);
		client.realm(realmName).users().get(userId).resetPassword(cr);

		if (rolenames != null && rolenames.size() > 0) {
			addRealmRolesToUser(userId, rolenames);
		}

		return userId;
	}

	@Override
	public void createRole(String rolename) {
		RoleRepresentation role = new RoleRepresentation();
		role.setName(rolename);
		client.realm(realmName).roles().create(role);
	}

	@Override
	public String createOidcBearerClient(String clientName) {
		ClientRepresentation cr = new ClientRepresentation();
		cr.setName(clientName);
		cr.setClientId(clientName);
		cr.setProtocol(ProtocolType.OPENID_CONNECT.getLabel());
		cr.setBearerOnly(true);
		cr.setPublicClient(false);
		cr.setEnabled(true);

		createClient(cr);

		return getClientId(clientName);
	}

	@Override
	public String createOicdConfidentialClient(String clientName, String rootUrl, List<String> redirectUris, String baseUrl, String adminUrl) {
		ClientRepresentation cr = new ClientRepresentation();
		cr.setName(clientName);
		cr.setClientId(clientName);
		cr.setProtocol(ProtocolType.OPENID_CONNECT.getLabel());
		cr.setAdminUrl(adminUrl);
		cr.setRootUrl(rootUrl);
		cr.setBaseUrl(baseUrl);
		cr.setRedirectUris(redirectUris);
		cr.setBearerOnly(false);
		cr.setPublicClient(false);
		cr.setEnabled(true);
		createClient(cr);

		createClient(cr);

		return getClientId(clientName);
	}

	@Override
	public String createInsecureSamlClient(String clientName, String masterSamlUrl, String baseUrl, List<String> redirectUris) {
		HashMap<String, String > attributes = new HashMap<>();
		attributes.put("saml.server.signature", "false");
		attributes.put("saml.client.signature", "false");
		
		ClientRepresentation cr = new ClientRepresentation();
		cr.setName(clientName);
		cr.setClientId(clientName);
		cr.setProtocol(ProtocolType.SAML.getLabel());
		cr.setEnabled(true);
		cr.setAdminUrl(masterSamlUrl);
		cr.setBaseUrl(baseUrl);
		cr.setRedirectUris(redirectUris);
		cr.setAttributes(attributes);

		createClient(cr);

		return getClientId(clientName);
	}

	@Override
	public String createOidcPublicClient(String clientName, String rootUrl, List<String> redirectUris, List<String> webOrigins) {
		ClientRepresentation cr = new ClientRepresentation();
		cr.setName(clientName);
		cr.setClientId(clientName);
		cr.setProtocol(ProtocolType.OPENID_CONNECT.getLabel());
		cr.setPublicClient(true);
		cr.setEnabled(true);
		cr.setRootUrl(rootUrl);
		cr.setRedirectUris(redirectUris);
		cr.setWebOrigins(webOrigins);

		createClient(cr);

		return getClientId(clientName);
	}

	private void createClient(ClientRepresentation cr) {
		Response response = client.realm(realmName).clients().create(cr);
		response.close();
	}

	@Override
	public void addRealmRolesToUser(String userId, List<String> rolenames) {
		List<RoleRepresentation> roles = client.realm(realmName).users().get(userId).roles().realmLevel().listAvailable().stream().filter(r -> rolenames.contains(r.getName())).collect(Collectors.toList());
		client.realm(realmName).users().get(userId).roles().realmLevel().add(roles);
	}

	@Override
	public void addBultinMappersToSamlClient(String clientId) {
		List<ProtocolMapperRepresentation> builtInMappers = new ArrayList<>();
		builtInMappers.add(getX500GivenNameMapper());
		builtInMappers.add(getX500SurnameMapper());
		builtInMappers.add(getX500EmailBuiltInMapper());

		client.realm(realmName).clients().get(clientId).getProtocolMappers().createMapper(builtInMappers);
	}

	@Override
	public String getUserId(String username) {
		return client.realm(realmName).users().search(username, 0, 1).get(0).getId();
	}

	@Override
	public String getClientId(String clientName) {
		return client.realm(realmName).clients().findAll().stream().filter(cr -> cr.getClientId().equals(clientName)).findFirst().get().getId();
	}

	@Override
	public String getRealmId() {
		return client.realm(realmName).toRepresentation().getId();
	}

	@Override
	public String getRealmPublicKey() {
		return client.realm(realmName).toRepresentation().getPublicKey();
	}

	@Override
	public String getOicdInstallationXmlFile(String clientId) {
		return client.realm(realmName).clients().get(clientId).getInstallationProvider(Provider.OIDC_JBOSS_XML_SUBSYSTEM.getProviderId());
	}

	@Override
	public String getSamlInstallationXmlFile(String clientId) {
		return client.realm(realmName).clients().get(clientId).getInstallationProvider(Provider.SAML_JBOSS_XML_SUBSYSTEM.getProviderId());
	}

	@Override
	public String getJsonInstallationFile(String clientId) {
		return client.realm(realmName).clients().get(clientId).getInstallationProvider(Provider.OIDC_KEYCLOAK_JSON.getProviderId());
	}

	@Override
	public void updateUserDetails(User user) {
		UserRepresentation ur = client.realm(realmName).users().get(user.id).toRepresentation();
		ur.setFirstName(user.firstName);
		ur.setLastName(user.lastName);
		ur.setEmail(user.email);

		client.realm(realmName).users().get(user.id).update(ur);
	}

	@Override
	public void deleteUser(String userId) {
		Response response = client.realm(realmName).users().delete(userId);
		response.close();
	}

	@Override
	public void forceNameIdFormat(String clientId) {
		ClientRepresentation cr = client.realm(realmName).clients().get(clientId).toRepresentation();
		cr.getAttributes().put("saml_force_name_id_format", "true");
		
		client.realm(realmName).clients().get(clientId).update(cr);
	}

	@Override
	public void updateClientRedirectUri(String clientId, List<String> redirectUris) {
		ClientRepresentation cr = new ClientRepresentation();
		cr.setRedirectUris(redirectUris);

		client.realm(realmName).clients().get(clientId).update(cr);
	}

	private ProtocolMapperRepresentation getX500GivenNameMapper() {
		Map<String, String> properties = new HashMap<>();
		properties.put("attribute.nameformat", "urn:oasis:names:tc:SAML:2.0:attrname-format:uri");
		properties.put("user.attribute", "firstName");
		properties.put("friendly.name", "givenName");
		properties.put("attribute.name", "urn:oid:2.5.4.42");

		ProtocolMapperRepresentation pmr = new ProtocolMapperRepresentation();
		pmr.setProtocol("saml");
		pmr.setName("X500 givenName");
		pmr.setConsentRequired(true);
		pmr.setConsentText("${givenName}");
		pmr.setProtocolMapper("saml-user-property-mapper");
		pmr.setConfig(properties);

		return pmr;
	}

	private ProtocolMapperRepresentation getX500SurnameMapper() {
		Map<String, String> properties = new HashMap<>();
		properties.put("attribute.nameformat", "urn:oasis:names:tc:SAML:2.0:attrname-format:uri");
		properties.put("user.attribute", "lastName");
		properties.put("friendly.name", "surname");
		properties.put("attribute.name", "urn:oid:2.5.4.4");

		ProtocolMapperRepresentation pmr = new ProtocolMapperRepresentation();
		pmr.setProtocol("saml");
		pmr.setName("X500 surname");
		pmr.setConsentRequired(true);
		pmr.setConsentText("${familyName}");
		pmr.setProtocolMapper("saml-user-property-mapper");
		pmr.setConfig(properties);

		return pmr;
	}

	private ProtocolMapperRepresentation getX500EmailBuiltInMapper() {
		Map<String, String> properties = new HashMap<>();
		properties.put("attribute.nameformat", "urn:oasis:names:tc:SAML:2.0:attrname-format:uri");
		properties.put("user.attribute", "email");
		properties.put("friendly.name", "email");
		properties.put("attribute.name", "urn:oid:1.2.840.113549.1.9.1");

		ProtocolMapperRepresentation pmr = new ProtocolMapperRepresentation();
		pmr.setProtocol("saml");
		pmr.setName("X500 email");
		pmr.setConsentRequired(true);
		pmr.setConsentText("${email}");
		pmr.setProtocolMapper("saml-user-property-mapper");
		pmr.setConfig(properties);

		return pmr;
	}

}
