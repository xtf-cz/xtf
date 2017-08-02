package cz.xtf.git;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import cz.xtf.TestProjectProfileResolver;
import cz.xtf.io.IOUtils;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

public class PomModifier {
	private static final Logger LOGGER = LoggerFactory.getLogger(PomModifier.class);
	private static DocumentBuilderFactory builderFactory; 
	private static DocumentBuilder builder;
	private static TransformerFactory transformerFactory;
	private static Transformer transformer;
	
	private Document parsedDocument;
	private final Path projectPomFile;
	private final Path projectDirectory;
	private final Path gitDirectory;
	private Path parentPomFile;
	private Path parentDirectory;
	
	public PomModifier(final Path projectDirectory, final Path gitDirectory) {
		if (builderFactory == null) {
			builderFactory = DocumentBuilderFactory.newInstance();
			transformerFactory = TransformerFactory.newInstance();
			try {
				builder = builderFactory.newDocumentBuilder();
				transformer = transformerFactory.newTransformer();
				transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
				transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
				transformer.setOutputProperty(OutputKeys.INDENT, "yes");
				transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
			} catch (ParserConfigurationException | TransformerConfigurationException e) {
				throw new IllegalStateException(e);
			}
		}
		this.projectPomFile = gitDirectory.resolve("pom.xml");
		this.projectDirectory = projectDirectory;
		this.gitDirectory = gitDirectory;
	}

	private Element childElement(final Element parentNode, final String elementName) {
		List<Element> children = childElements(parentNode, elementName);
		return children.size() > 0 ? children.get(0) : null;
	}

	private Element createChildElement(final Element parentNode, final String elementName) {
		final List<Element> children = childElements(parentNode, elementName);
		if (children.size() > 0) {
			return children.get(0);
		}
		final Element element = parsedDocument.createElement(elementName);
		parentNode.appendChild(element);
		return element;
	}

	private List<Element> childElements(final Element parentNode, final String elementName) {
		final List<Element> ret = new ArrayList<>();
		final NodeList nodes = parentNode.getChildNodes(); 
		for (int i = 0; i < nodes.getLength(); i++) {
			if (nodes.item(i).getNodeType() != Node.ELEMENT_NODE) {
				continue;
			}
			final Element element = (Element)nodes.item(i);
			if (elementName.equals(element.getTagName())) {
				ret.add(element);
			}
		}
		return ret;
	}

	private String getElementText(final Node element) {
		final NodeList nodes = element.getChildNodes(); 
		for (int i = 0; i < nodes.getLength(); i++) {
			if (nodes.item(i).getNodeType() == Node.TEXT_NODE) {
				return nodes.item(i).getTextContent();
			}
		}
		return null;
	}
	
	private void setElementText(final Node element, final String text) {
		final NodeList nodes = element.getChildNodes(); 
		for (int i = 0; i < nodes.getLength(); i++) {
			if (nodes.item(i).getNodeType() == Node.TEXT_NODE) {
				nodes.item(i).setTextContent(text);
				return;
			}
		}
		element.appendChild(parsedDocument.createTextNode(text));
	}
	
	private boolean modifyProjectPOM() {
		LOGGER.info("Parsing POM {}", projectPomFile);
		if (!projectPomFile.toFile().exists()) {
			LOGGER.debug("Non-Maven project, skipping manipulation");
			return false;
		}
		try {
			parseDocument(projectPomFile);
			final Element root = parsedDocument.getDocumentElement();
			final Element parent = childElement(root, "parent");
			if (parent == null) {
				LOGGER.debug("No parent found, skipping manipulation");
				return false;
			}
			final Element relativePath = childElement(parent, "relativePath");
			if (relativePath == null) {
				LOGGER.error("Relative path required");
				throw new IllegalStateException("Relative path required in parent");
			}
			final String parentLocation = getElementText(relativePath);
			parentDirectory = projectDirectory.resolve(parentLocation).normalize().toAbsolutePath();
			if (!parentDirectory.toFile().exists()) {
				throw new IllegalArgumentException("Parent directory does not exist " + parentDirectory);
			}
			relativePath.setTextContent(parentDirectory.toFile().getName());
			writePOMFile(projectPomFile, root);
		} catch (SAXException | IOException | TransformerException e) {
			throw new IllegalStateException(e);

		}
		return true;
	}

	private void modifyParentPOM(final String activatedProfile) {
		LOGGER.info("Parsing POM {}", parentPomFile);
		try {
			parseDocument(parentPomFile);
			final Element root = parsedDocument.getDocumentElement();
			final Element profiles = childElement(root, "profiles");
			if (profiles == null) {
				LOGGER.debug("No profiles found, skipping manipulation");
				return;
			}
			childElements(profiles, "profile").forEach(profile -> {
				final String id = getElementText(childElement(profile, "id"));
				if (activatedProfile.equals(id)) {
					activateProfile(profile);
				}
				else {
					deactivateProfile(profile);
				}
			});
			writePOMFile(parentPomFile, root);
		} catch (SAXException | IOException | TransformerException e) {
			throw new IllegalStateException(e);

		}
	}

	private Document parseDocument(final Path pomFile) throws SAXException,
			IOException {
		parsedDocument = builder.parse(pomFile.toFile());
		return parsedDocument;
	}

	private void writePOMFile(final Path pomFile, final Element root)
			throws FileNotFoundException, TransformerException, IOException {
		FileOutputStream fos = new FileOutputStream(pomFile.toFile());
		transformer.transform(new DOMSource(root), new StreamResult(fos));
		fos.close();
	}
	
	private void activateProfile(final Element profile) {
		setElementText(createChildElement(createChildElement(profile, "activation"), "activeByDefault"), "true");
	}

	private void deactivateProfile(final Element profile) {
		setElementText(createChildElement(createChildElement(profile, "activation"), "activeByDefault"), "false");
	}
	
	private void copyParentDirectory() {
		final Path parentDestDir = gitDirectory.resolve(parentDirectory.getFileName());
		parentDestDir.toFile().mkdir();
		try {
			IOUtils.copy(parentDirectory, parentDestDir);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
		parentPomFile = parentDestDir.resolve("pom.xml");
	}

	private boolean isPomModifierSupported() {
		return StreamSupport.stream(projectDirectory.spliterator(), false)
				.anyMatch(
						x -> x.getFileName().endsWith("test-eap")
								|| x.getFileName().endsWith("test-amq")
								|| x.getFileName().endsWith("test-jdv")
								|| x.getFileName().endsWith("test-ews")
								|| x.getFileName().endsWith("test-fuse")
								|| x.getFileName().endsWith("test-msa")
								|| x.getFileName().endsWith("test-jdg")
								|| x.getFileName().endsWith("test-sso")
				);

	}

	private boolean isParentModifierSupported() {
		return StreamSupport.stream(projectDirectory.spliterator(), false)
				.anyMatch(
						x -> x.getFileName().endsWith("test-eap")
								|| x.getFileName().endsWith("test-amq")
								|| x.getFileName().endsWith("test-jdv")
								|| x.getFileName().endsWith("test-ews")
								|| x.getFileName().endsWith("test-msa")
								|| x.getFileName().endsWith("test-jdg")
								|| x.getFileName().endsWith("test-sso")
				);
	}
	
	public void modify() {
		if (!isPomModifierSupported() || !modifyProjectPOM()) {
			return;
		}
		copyParentDirectory();

		if (isParentModifierSupported()) {
			modifyParentPOM(TestProjectProfileResolver.get().getProfileName(parentDirectory.toFile().getName()));
		}
	}
}
