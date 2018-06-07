package cz.xtf.openshift.xtfservices;

import cz.xtf.openshift.Service;
import cz.xtf.tracing.Zipkin;
import cz.xtf.webdriver.GhostDriverService;

/**
 * @author Radek Koubsky
 */
public class ServiceFactory {
	public static Service createZipkinService(){
		return Zipkin.instance();
	}

	public static Service createGhostDriverService(){
		return GhostDriverService.get();
	}
}
