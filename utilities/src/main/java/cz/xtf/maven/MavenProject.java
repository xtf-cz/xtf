package cz.xtf.maven;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;

public class MavenProject {
	private static final Logger LOGGER = LoggerFactory.getLogger(MavenProject.class);
	private static final XPathFactory XPATH = XPathFactory.newInstance();
	private final Document pom;

	public static MavenProject loadPom(Path path) {
		try {
			Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(path.toFile());
			return new MavenProject(doc);
		} catch (SAXException | ParserConfigurationException | IOException ex) {
			throw new RuntimeException("Unable to parse pom", ex);
		}
	}

	public static MavenProject newPom(String groupId, String artifactId, String version) {
		try {
			Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
			doc.appendChild(doc.createElement("project"));

			MavenProject result = new MavenProject(doc);
			result.getOrCreateElement("project", "groupId").setTextContent(groupId);
			result.getOrCreateElement("project", "artifactId").setTextContent(artifactId);
			result.getOrCreateElement("project", "version").setTextContent(version);

			return result;
		} catch (ParserConfigurationException ex) {
			throw new RuntimeException("Unable to create document", ex);
		}
	}

	private MavenProject(Document document) {
		this.pom = document;
	}

	public String getGroupId() {
		return getOrCreateElement("project", "groupId").getTextContent();
	}

	public String getArtifactId() {
		return getOrCreateElement("project", "artifactId").getTextContent();
	}

	public String getVersion() {
		return getOrCreateElement("project", "version").getTextContent();
	}

	public void addDependency(String groupId, String artifactId, String version) {
		Node dependencies = getOrCreateElement("project", "dependencies");

		Node dependency = dependencies.appendChild(pom.createElement("dependency"));
		dependency.appendChild(pom.createElement("groupId")).setTextContent(groupId);
		dependency.appendChild(pom.createElement("artifactId")).setTextContent(artifactId);
		dependency.appendChild(pom.createElement("version")).setTextContent(version);
	}

	public void addRepository(String id, String url) {
		Node repositories = getOrCreateElement("project", "repositories");

		Node repository = null;
		try {
			repository = (Node) XPATH.newXPath().evaluate(String.format("/project/repositories/repository[url='%s']", url),
					pom, XPathConstants.NODE);
		} catch (XPathExpressionException ex) {
			LOGGER.warn("Error searching for element", ex);
		}

		if (repository == null) {
			repository = repositories.appendChild(pom.createElement("repository"));
			repository.appendChild(pom.createElement("id")).setTextContent(id);
			repository.appendChild(pom.createElement("url")).setTextContent(url);
		}
	}

	public void addOpenShiftProfile() {
		Node profiles = getOrCreateElement("project", "profiles");

		Node profile = null;
		try {
			profile = (Node) XPATH.newXPath().evaluate("/project/profiles/profile[id='openshift']", pom, XPathConstants.NODE);
		} catch (XPathExpressionException ex) {
			LOGGER.warn("Error searching for element", ex);
		}

		if (profile == null) {
			profile = profiles.appendChild(pom.createElement("profile"));
			getOrCreateElement(profile, "id").setTextContent("openshift");
			getOrCreateElement(profile, "build", "plugins", "plugin", "artifactId").setTextContent("maven-war-plugin");
			getOrCreateElement(profile, "build", "plugins", "plugin", "version").setTextContent("${version.war.plugin}");
			getOrCreateElement(profile, "build", "plugins", "plugin", "configuration", "outputDirectory").setTextContent(
					"deployments");
			getOrCreateElement(profile, "build", "plugins", "plugin", "configuration", "warName").setTextContent("ROOT");
		}
	}

	public void removePlugin(String artifactId) {
		Node plugin = null;
		try {
			plugin = (Node) XPATH.newXPath().evaluate(String.format("/project/build/plugins/plugin[artifactId='%s']", artifactId),
					pom, XPathConstants.NODE);
		} catch (XPathExpressionException ex) {
			LOGGER.warn("Error searching for element", ex);
		}

		if (plugin != null) {
			LOGGER.debug("Removing plugin: {}", artifactId);
			plugin.getParentNode().removeChild(plugin);
		}

	}

	public void changeProperty(String name, String value) {
		Node property = null;
		try {
			property = (Node) XPATH.newXPath().evaluate(String.format("/project/properties/%s", name),
					pom, XPathConstants.NODE);
		} catch (XPathExpressionException ex) {
			LOGGER.warn("Error searching for element", ex);
		}
		if (property != null) {
			property.setTextContent(value);
		}

	}
	public void save(Path path) {
		try (FileOutputStream fos = new FileOutputStream(path.toFile())){
			Transformer transformer = TransformerFactory.newInstance().newTransformer();
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
			transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
			transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
			transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");

			transformer.transform(new DOMSource(pom), new StreamResult(fos));
			fos.flush();
		} catch (TransformerConfigurationException | TransformerFactoryConfigurationError ex) {
			throw new RuntimeException("Unable to create transformer", ex);
		} catch (TransformerException | IOException ex) {
			throw new RuntimeException("Unable to save XML", ex);
		}
	}

	private Node getOrCreateElement(String... path) {
		return getOrCreateElement(pom, path);
	}

	private Node getOrCreateElement(Node root, String... path) {
		if (path == null || path.length == 0) {
			LOGGER.debug("Trying to create element with no path");
			return root;
		} else {
			Node result = null;

			try {
				result = (Node) buildXpath(path).evaluate(root, XPathConstants.NODE);
			} catch (XPathExpressionException ex) {
				LOGGER.warn("Error searching for element", ex);
			}

			if (result == null) {
				Node parent = getOrCreateElement(root, subarray(path, path.length - 1));
				result = parent.appendChild(pom.createElement(path[path.length - 1]));
			}

			return result;
		}
	}

	private XPathExpression buildXpath(String... path) {
		StringBuilder builder = new StringBuilder();
		for (String pathSegment : path) {
			if (builder.length() > 0) {
				builder.append("/");
			}
			builder.append(pathSegment);
		}

		try {
			return XPATH.newXPath().compile(builder.toString());
		} catch (XPathExpressionException e) {
			LOGGER.error("Unable to compile xpath '{}'", builder);
			throw new RuntimeException("Unable to compile xpath");
		}
	}

	private String[] subarray(String[] original, int size) {
		return Arrays.copyOfRange(original, 0, size);
	}
}
