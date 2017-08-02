package cz.xtf.sso.util;

import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Select;

import cz.xtf.wait.WaitUtil;

import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;

public class DriverUtil {

	public static void login(WebDriver driver, String username, String password) throws TimeoutException, InterruptedException {
		waitFor(driver, By.id("username"), By.id("password"), By.id("kc-login"));
		driver.findElement(By.id("username")).sendKeys(username);
		driver.findElement(By.id("password")).sendKeys(password);
		driver.findElement(By.id("kc-login")).click();

		Thread.sleep(5_000L);
	}

	public static void waitFor(WebDriver driver, By... elements) throws TimeoutException, InterruptedException {
		waitFor(driver, 60_000, elements);
	}

	public static void waitFor(WebDriver driver, long timeout, By... elements) throws TimeoutException, InterruptedException {
		try {
			WaitUtil.waitFor(() -> elementsPresent(driver, elements), null, 1000L, timeout);
		} catch (TimeoutException ex) {
			try {
				for (By element : elements) {
					WebElement webElement = driver.findElement(element);
					if (!webElement.isDisplayed()) {
						throw new TimeoutException("Timeout exception during waiting for web element: " + webElement.getText());
					}
				}
			} catch (NoSuchElementException | StaleElementReferenceException x) {
				throw new TimeoutException("Timeout exception during waiting for web element: " + x.getMessage());
			}
		}
	}

	public static void buttonClick(WebDriver driver, By element) {
		driver.findElement(element).click();
	}

	public static void comboBoxSelectItem(WebDriver driver, By element, String item) {
		Select comboBox = new Select(driver.findElement(element));
		comboBox.selectByVisibleText(item);
	}

	public static BooleanSupplier textAppears(WebDriver driver, By element, String text) {
		return () -> {
			try {
				return text.equalsIgnoreCase(driver.findElement(element).getText());
			} catch (Exception x) {
				// stale elements and such...
				return false;
			}
		};
	}

	public static boolean elementsPresent(WebDriver driver, By... elements) {
		try {
			for (By element : elements) {
				WebElement webElement = driver.findElement(element);
				if (!webElement.isDisplayed()) {
					return false;
				}
			}
			return true;
		} catch (NoSuchElementException | StaleElementReferenceException x) {
			return false;
		}
	}
}
