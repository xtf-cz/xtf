package cz.xtf.sso.api;

import cz.xtf.wait.Waiters;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.Select;

import cz.xtf.sso.api.entity.User;
import cz.xtf.sso.util.DriverUtil;
import cz.xtf.webdriver.WebDriverService;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

public class SsoWebUIApi implements SsoApi {

	public static SsoWebUIApi get(String authUrl, String realm) {
		return new SsoWebUIApi(authUrl, realm, WebDriverService.get().start());
	}

	public static SsoWebUIApi get(String authUrl, String realm, WebDriver webDriver) {
		return  new SsoWebUIApi(authUrl, realm, webDriver);
	}

	private final String authUrl;
	private final String realm;

	private final WebDriver driver;

	private SsoWebUIApi(String authUrl, String realm, WebDriver webDriver) {
		this.authUrl = authUrl;
		this.realm = realm;
		this.driver = webDriver;

		login(driver);
	}

	@Override
	public String createUser(String username, String password, String firstname, String lastname, String email, List<String> rolenames) {
		try {
			String createUserPage = authUrl + "/admin/master/console/#/create/user/" + realm + "/";

			driver.navigate().to(createUserPage);

			DriverUtil.waitFor(driver, By.id("username"));

			driver.findElement(By.id("username")).sendKeys(username);

			DriverUtil.waitFor(driver, By.cssSelector(".btn-primary"));
			driver.findElement(By.cssSelector(".btn-primary")).click();

			// the old page also had "id", there is a race here
			Thread.sleep(3_000L);

			DriverUtil.waitFor(driver, By.id("id"));
			String id = driver.findElement(By.id("id")).getAttribute("value");

			// .../auth/admin/master/console/#/realms/xtf/users/33de31cb-a5c3-486a-8556-ecbc669f2c19/user-credentials
			driver.navigate().to(authUrl + "/admin/master/console/#/realms/" + realm + "/users/" + id + "/user-credentials");
			DriverUtil.waitFor(driver, By.id("password"), By.id("confirmPassword"));

			driver.findElement(By.id("password")).sendKeys(password);
			driver.findElement(By.id("confirmPassword")).sendKeys(password);

			driver.findElement(By.cssSelector(".onoffswitch-active")).click();

			driver.findElement(By.xpath("//button[contains(text(),'Reset Password')]")).click();

			DriverUtil.waitFor(driver, By.xpath("//button[contains(text(),'Change password')]"));
			driver.findElement(By.xpath("//button[contains(text(),'Change password')]")).click();

			if (rolenames != null && rolenames.size() > 0) {
				// Realm Roles
				for (String role : rolenames) {
					driver.navigate().to(authUrl + "/admin/master/console/#/realms/" + realm + "/users/" + id + "/role-mappings");
					DriverUtil.waitFor(driver, By.id("available"));

					// The following bits are a bit hacky. Clicking don't seem to update angular model, but keyboard navigation seems to work fine
					// We therefore navigate to the right role with the arrow keys and click "add selected"
					// and repeat the whole process

					List<String> list = driver.findElement(By.id("available")).findElements(By.tagName("option")).stream().map(WebElement::getText).collect(Collectors.toList());

					new Actions(driver).moveToElement(driver.findElement(By.xpath("//select[@id='available']"))).click().perform();

					// make sure we are at the top of the list
					for (int i = 0; i < list.size() + 1; ++i) {
						new Actions(driver).sendKeys(Keys.ARROW_UP).perform();
					}

					for (String text : list) {
						if (role.equals(text)) {
							driver.findElement(By.xpath("//button[@ng-click='addRealmRole()']")).click();
							break;
						}

						new Actions(driver).sendKeys(Keys.ARROW_DOWN).perform();
					}

					Thread.sleep(3_000L);
				}
			}

			return id;
		} catch (InterruptedException | TimeoutException e) {
			throw new IllegalStateException("Failed to ...", e);
		}
	}

	@Override
	public void createRole(String rolename) {
		try {
			// .../auth/admin/master/console/#/create/role/xtf
			String createRolePage = authUrl + "/admin/master/console/#/create/role/" + realm + "/";

			driver.navigate().to(createRolePage);
			DriverUtil.waitFor(driver, By.id("name"));
			Waiters.sleep(TimeUnit.SECONDS, 5);

			driver.findElement(By.id("name")).sendKeys(rolename);
			DriverUtil.waitFor(driver, By.cssSelector(".btn-primary"));

			driver.findElement(By.cssSelector(".btn-primary")).click();
		} catch (InterruptedException | TimeoutException e) {
			throw new IllegalStateException("Failed to create role", e);
		}
	}

	@Override
	public String createOidcBearerClient(String clientName) {
		try {
			// .../auth/admin/master/console/#/create/client/foobar
			driver.navigate().to(authUrl + "/admin/master/console/#/create/client/" + realm + "/");
			DriverUtil.waitFor(driver, By.id("clientId"));

			driver.findElement(By.id("clientId")).sendKeys(clientName);
			driver.findElement(By.xpath("//button[contains(text(),'Save')]")).click();
			DriverUtil.waitFor(driver, By.id("accessType"));

			Select accessTypeSelect = new Select(driver.findElement(By.id("accessType")));
			accessTypeSelect.selectByVisibleText(OpenIdAccessType.BEARER_ONLY.getLabel());

			String clientId = driver.getCurrentUrl();
			int lastSlash = clientId.lastIndexOf("/");
			clientId = clientId.substring(lastSlash + 1);

			driver.findElement(By.xpath("//button[contains(text(),'Save')]")).click();

			return clientId;
		} catch (InterruptedException | TimeoutException e) {
			throw new IllegalStateException("Failed to ...", e);
		}
	}

	@Override
	public String createOicdConfidentialClient(String clientName, String rootUrl, List<String> redirectUri, String baseUrl, String adminUrl) {
		try {
			// .../auth/admin/master/console/#/create/client/foobar
			driver.navigate().to(authUrl + "/admin/master/console/#/create/client/" + realm + "/");
			DriverUtil.waitFor(driver, By.id("clientId"), By.id("rootUrl"));

			driver.findElement(By.id("clientId")).sendKeys(clientName);
			driver.findElement(By.id("rootUrl")).sendKeys(rootUrl);
			driver.findElement(By.xpath("//button[contains(text(),'Save')]")).click();
			DriverUtil.waitFor(driver, By.id("accessType"));

			Select accessTypeSelect = new Select(driver.findElement(By.id("accessType")));
			accessTypeSelect.selectByVisibleText(OpenIdAccessType.CONFIDENTIAL.getLabel());
			DriverUtil.waitFor(driver, By.id("newRedirectUri"), By.id("rootUrl"));

			driver.findElement(By.id("newRedirectUri")).sendKeys(redirectUri.get(0));
			driver.findElement(By.id("baseUrl")).sendKeys(baseUrl);
			driver.findElement(By.id("adminUrl")).sendKeys(adminUrl);

			String clientId = driver.getCurrentUrl();
			int lastSlash = clientId.lastIndexOf("/");
			clientId = clientId.substring(lastSlash + 1);

			driver.findElement(By.xpath("//button[contains(text(),'Save')]")).click();

			return clientId;
		} catch (InterruptedException | TimeoutException e) {
			throw new IllegalStateException("Failed to ...", e);
		}
	}

	@Override
	public String createInsecureSamlClient(String clientName, String masterSamlUrl, String baseUrl, List<String> redirectUris) {
		try {
			driver.navigate().to(authUrl + "/admin/master/console/#/create/client/" + realm + "/");
			DriverUtil.waitFor(driver, By.id("clientId"));

			driver.findElement(By.id("clientId")).sendKeys(clientName);
			Select accessTypeSelect = new Select(driver.findElement(By.id("protocol")));
			accessTypeSelect.selectByVisibleText(ProtocolType.SAML.getLabel());

			driver.findElement(By.id("masterSamlUrl")).sendKeys(masterSamlUrl);

			driver.findElement(By.xpath("//button[contains(text(),'Save')]")).click();

			DriverUtil.waitFor(driver, By.id("baseUrl"), By.id("newRedirectUri"));

			driver.findElement(By.xpath("//label[@for='samlServerSignature']/span/span[contains(@class, 'onoffswitch-active')]")).click();
			driver.findElement(By.xpath("//label[@for='samlClientSignature']/span/span[contains(@class, 'onoffswitch-active')]")).click();

			driver.findElement(By.id("baseUrl")).sendKeys(baseUrl);

			driver.findElement(By.xpath("//button[contains(text(),'Save')]")).click();

			for (String redirectUri : redirectUris) {
				DriverUtil.waitFor(driver, By.id("newRedirectUri"));
				driver.findElement(By.id("newRedirectUri")).sendKeys(redirectUri);
				driver.findElement(By.xpath("//button[contains(text(),'Save')]")).click();
			}

			String clientUuid = driver.getCurrentUrl();
			int lastSlash = clientUuid.lastIndexOf("/");

			return clientUuid.substring(lastSlash + 1);
		} catch (InterruptedException | TimeoutException e) {
			throw new IllegalStateException("Failed to create saml client");
		}
	}

	@Override
	public String createOidcPublicClient(String clientName, String rootUrl, List<String> redirectUris, List<String> webOrigins) {
		try {
			// .../auth/admin/master/console/#/create/client/foobar
			driver.navigate().to(authUrl + "/admin/master/console/#/create/client/" + realm + "/");
			DriverUtil.waitFor(driver, By.id("clientId"), By.id("rootUrl"));

			driver.findElement(By.id("clientId")).sendKeys(clientName);
			driver.findElement(By.id("rootUrl")).sendKeys(rootUrl);

			driver.findElement(By.xpath("//button[contains(text(),'Save')]")).click();

			DriverUtil.waitFor(driver, By.id("accessType"));

			Select accessTypeSelect = new Select(driver.findElement(By.id("accessType")));
			accessTypeSelect.selectByVisibleText(OpenIdAccessType.PUBLIC.getLabel());

			DriverUtil.waitFor(driver, By.id("newRedirectUri"), By.id("newWebOrigin"), By.id("rootUrl"));

			driver.findElement(By.id("newRedirectUri")).sendKeys(redirectUris.get(0));

			if (webOrigins != null) {
				driver.findElement(By.id("newWebOrigin")).sendKeys(webOrigins.get(0));
			}

			String clientId = driver.getCurrentUrl();
			int lastSlash = clientId.lastIndexOf("/");
			clientId = clientId.substring(lastSlash + 1);

			driver.findElement(By.xpath("//button[contains(text(),'Save')]")).click();

			return clientId;
		} catch (InterruptedException | TimeoutException e) {
			throw new IllegalStateException("Failed to ...", e);
		}
	}

	@Override
	public void addRealmRolesToUser(String username, List<String> rolenames) {
		throw new UnsupportedOperationException("Not yet implemented");
	}

	@Override
	public void addBultinMappersToSamlClient(String clientId) {
		try {
			// .../auth/admin/master/console/#/realms/xtf/clients/dc568c35-8ea3-4ba7-a4d0-e3fd708d2c4d/add-mappers
			driver.navigate().to(authUrl + "/admin/master/console/#/realms/" + realm + "/clients/" + clientId + "/add-mappers");

			DriverUtil.waitFor(driver, By.id("saml-user-property-mapper"), By.xpath("//button[contains(text(),'Add selected')]"));

			driver.findElement(By.xpath("//tbody/tr/td[position()=1 and contains(text(), 'givenName')]/../td[position()=4]/input")).click();
			driver.findElement(By.xpath("//tbody/tr/td[position()=1 and contains(text(), 'surname')]/../td[position()=4]/input")).click();
			driver.findElement(By.xpath("//tbody/tr/td[position()=1 and contains(text(), 'email')]/../td[position()=4]/input")).click();
			driver.findElement(By.xpath("//button[contains(text(),'Add selected')]")).click();
		} catch (InterruptedException | TimeoutException e) {
			throw new IllegalStateException("Failed to ...", e);
		}
	}

	@Override
	public String getUserId(String username) {
		try {
			driver.navigate().to(authUrl + "/admin/master/console/#/realms/" + realm + "/users");
			DriverUtil.waitFor(driver, By.xpath("//button[contains(text(),'View all users')]"));

			driver.findElement(By.xpath("//button[contains(text(),'View all users')]")).click();
			DriverUtil.waitFor(driver, By.xpath("//tbody/tr/td[position()=1]/a[text()='" + username + "']"));

			String href = driver.findElement(By.xpath("//tbody/tr/td[position()=1]/a[text()='" + username + "']")).getAttribute("href");

			int lastSlash = href.lastIndexOf("/");
			return href.substring(lastSlash + 1);
		} catch (InterruptedException | TimeoutException e) {
			throw new IllegalStateException("Failed to ...", e);
		}
	}

	@Override
	public String getClientId(String clientName) {
		throw new UnsupportedOperationException("Not yet implemented");
	}

	@Override
	public String getRealmId() {
		throw new UnsupportedOperationException("Not yet implemented");
	}

	@Override
	public String getRealmPublicKey() {
		try {
			String roleKeysPage = authUrl + "/admin/master/console/#/realms/" + realm + "/keys-settings";

			driver.navigate().to(roleKeysPage);
			DriverUtil.waitFor(driver, By.id("publicKey"));

			return driver.findElement(By.id("publicKey")).getText();
		} catch (InterruptedException | TimeoutException e) {
			throw new IllegalStateException("Failed to get public key", e);
		}
	}

	@Override
	public String getOicdInstallationXmlFile(String clientId) {
		try {
			driver.navigate().to(authUrl + "/admin/master/console/#/realms/" + realm + "/clients/" + clientId + "/installation/");
			DriverUtil.waitFor(driver, By.id("configFormats"));

			Select configFormatSelect = new Select(driver.findElement(By.id("configFormats")));
			configFormatSelect.selectByVisibleText(Provider.OIDC_JBOSS_XML_SUBSYSTEM.getWebUiLabel());

			return driver.findElement(By.xpath("//textarea[contains(text(),'secure-deployment')]")).getText();
		} catch (InterruptedException | TimeoutException e) {
			throw new IllegalStateException("Failed to ...", e);
		}
	}

	@Override
	public String getSamlInstallationXmlFile(String clientId) {
		try {
			driver.navigate().to(authUrl + "/admin/master/console/#/realms/" + realm + "/clients/" + clientId + "/installation/");
			DriverUtil.waitFor(driver, By.id("configFormats"));

			Select configFormatSelect = new Select(driver.findElement(By.id("configFormats")));
			configFormatSelect.selectByVisibleText(Provider.SAML_JBOSS_XML_SUBSYSTEM.getWebUiLabel());

			return driver.findElement(By.xpath("//textarea[contains(text(),'<SingleSignOnService')]")).getText();
		} catch (InterruptedException | TimeoutException e) {
			throw new IllegalStateException("Failed to get saml installation file");
		}
	}

	@Override
	public String getJsonInstallationFile(String clientId) {
		try {
			driver.navigate().to(authUrl + "/admin/master/console/#/realms/" + realm + "/clients/" + clientId + "/installation/");
			DriverUtil.waitFor(driver, By.id("configFormats"));

			Select configFormatSelect = new Select(driver.findElement(By.id("configFormats")));
			configFormatSelect.selectByVisibleText(Provider.OIDC_KEYCLOAK_JSON.getWebUiLabel());

			return driver.findElement(By.xpath("//textarea[contains(text(),'auth-server-url')]")).getText();
		} catch (InterruptedException | TimeoutException e) {
			throw new IllegalStateException("Failed to ...", e);
		}
	}

	@Override
	public void updateUserDetails(User user) {
		try {
			// .../auth/admin/master/console/#/realms/xtf/users/7cf90e9f-d2da-47ef-9865-38d4073221d0
			String userDetailPage = authUrl + "/admin/master/console/#/realms/" + realm + "/users/" + user.id;

			driver.navigate().to(userDetailPage);
			DriverUtil.waitFor(driver, By.id("email"), By.id("firstName"), By.id("lastName"));

			driver.findElement(By.id("email")).clear();
			driver.findElement(By.id("email")).sendKeys(user.email);

			driver.findElement(By.id("firstName")).clear();
			driver.findElement(By.id("firstName")).sendKeys(user.firstName);

			driver.findElement(By.id("lastName")).clear();
			driver.findElement(By.id("lastName")).sendKeys(user.lastName);

			driver.findElements(By.xpath("//button[@class='ng-binding btn btn-primary']")).stream().filter(element -> element.isEnabled() && element.isDisplayed()).findFirst().get().click();
		} catch (InterruptedException | TimeoutException e) {
			throw new IllegalStateException("Failed to ...", e);
		}
	}

	@Override
	public void deleteUser(String userId) {
		throw new UnsupportedOperationException("Not yet implemented");
	}

	@Override
	public void forceNameIdFormat(String clientId) {
		try {
			// .../auth/admin/master/console/#/realms/xtf/clients/dc568c35-8ea3-4ba7-a4d0-e3fd708d2c4d
			driver.navigate().to(authUrl + "/admin/master/console/#/realms/" + realm + "/clients/" + clientId);

			final By xpath = By.xpath("//label[@for='samlForceNameIdFormat']/span/span[contains(@class, 'onoffswitch-active')]");

			DriverUtil.waitFor(driver, xpath, By.xpath("//button[contains(text(),'Save')]"));
			driver.findElement(xpath).click();
			driver.findElement(By.xpath("//button[contains(text(),'Save')]")).click();
		} catch (TimeoutException | InterruptedException e) {
			throw new IllegalStateException("Failed to force name id format!", e);
		}
	}

	@Override
	public void updateClientRedirectUri(String clientId, List<String> redirectUris) {
		throw new UnsupportedOperationException("Not yet implemented");
	}

	public void close() {
		driver.close();
	}

	private void login(WebDriver driver) {
		try {
			driver.navigate().to(authUrl + "/admin");
			DriverUtil.login(driver, "admin", "admin");
		} catch (TimeoutException | InterruptedException e) {
			throw new IllegalStateException("Didn't manage to login!", e);
		}
	}

	public enum OpenIdAccessType {
		CONFIDENTIAL("confidential"),
		PUBLIC("public"),
		BEARER_ONLY("bearer-only");

		private String label;

		OpenIdAccessType(String label) {
			this.label = label;
		}

		public String getLabel() {
			return label;
		}
	}
}
