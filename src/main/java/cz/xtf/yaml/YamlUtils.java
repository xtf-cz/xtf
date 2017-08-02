package cz.xtf.yaml;

import org.jboss.dmr.ModelNode;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;

public class YamlUtils {
	public static ModelNode yaml2dmr(String yaml) throws IOException {
		ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
		Object obj = yamlMapper.readValue(yaml, Object.class);

		ObjectMapper jsonMapper = new ObjectMapper();
		String yamlAsJson = jsonMapper.writeValueAsString(obj);

		return ModelNode.fromJSONString(yamlAsJson);
	}

	public static String dmr2yaml(ModelNode modelNode) throws IOException {
		ObjectMapper jsonMapper = new ObjectMapper();
		ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

		String jsonString = modelNode.toJSONString(false);
		Object obj = jsonMapper.readValue(jsonString, Object.class);

		return yamlMapper.writeValueAsString(obj);
	}
}
