package cz.xtf.webdriver;

import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.function.Consumer;

public class WebDriverService {
	private static WebDriverService webDriverService;

	private ThreadLocal<WebDriver> webDriver = new ThreadLocal<>();

	private WebDriverService() {

	}

	private WebDriver createWebDriver(final Consumer<DesiredCapabilities> desiredCapabilities) {

		String hostName = GhostDriverService.get().getHostName();

		DesiredCapabilities capabilities = DesiredCapabilities.phantomjs();
		if (desiredCapabilities != null) {
			desiredCapabilities.accept(capabilities);
		}
		try {
			WebDriver driver = new RemoteWebDriver(new URL("http://localhost:" + GhostDriverService.get().getLocalPort() + "/"), capabilities);
			driver.manage().window().setSize(new Dimension(1920, 1080));
			return driver;
		} catch (MalformedURLException e) {
			throw new IllegalStateException("Wrong hostName '" + hostName + "', possibly GhostDriverService::start not called ", e);
		}
	}

	public WebDriver getWebDriver() {
		return webDriver.get();
	}

	public static synchronized WebDriverService get() {
		if (webDriverService == null) {
			webDriverService = new WebDriverService();
		}
		return webDriverService;
	}

	public WebDriver start(final Consumer<DesiredCapabilities> desiredCapabilities) {
		WebDriver driver = createWebDriver(desiredCapabilities);

		webDriver.set(driver);

		return driver;
	}

	public WebDriver start() {
		return start(null);
	}

	public void stop() {
		webDriver.get().close();
		webDriver.set(null);
	}
}
