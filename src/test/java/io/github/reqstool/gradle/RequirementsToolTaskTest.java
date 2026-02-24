package io.github.reqstool.gradle;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

class RequirementsToolTaskTest {

	@TempDir
	Path tempDir;

	private Project project;

	private RequirementsToolTask task;

	@BeforeEach
	void setup() {
		project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build();

		task = project.getTasks().register("testTask", RequirementsToolTask.class).get();
	}

	@Test
	void testCombineOutput_bothEmpty() {
		ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
		JsonNode implementations = mapper.createObjectNode();
		JsonNode tests = mapper.createObjectNode();

		JsonNode result = RequirementsToolTask.combineOutput(implementations, tests);

		assertNotNull(result);
		assertTrue(result.has("requirement_annotations"));
		JsonNode reqAnnotations = result.get("requirement_annotations");
		assertFalse(reqAnnotations.has("implementations"));
		assertFalse(reqAnnotations.has("tests"));
	}

	@Test
	void testCombineOutput_withImplementations() throws IOException {
		ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

		String implJson = "{\"impl1\": {\"id\": \"REQ-001\"}}";
		JsonNode implementations = mapper.readTree(implJson);
		JsonNode tests = mapper.createObjectNode();

		JsonNode result = RequirementsToolTask.combineOutput(implementations, tests);

		assertNotNull(result);
		assertTrue(result.has("requirement_annotations"));
		JsonNode reqAnnotations = result.get("requirement_annotations");
		assertTrue(reqAnnotations.has("implementations"));
		assertFalse(reqAnnotations.has("tests"));
	}

	@Test
	void testCombineOutput_withTests() throws IOException {
		ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

		JsonNode implementations = mapper.createObjectNode();
		String testsJson = "{\"test1\": {\"id\": \"SVC-001\"}}";
		JsonNode tests = mapper.readTree(testsJson);

		JsonNode result = RequirementsToolTask.combineOutput(implementations, tests);

		assertNotNull(result);
		assertTrue(result.has("requirement_annotations"));
		JsonNode reqAnnotations = result.get("requirement_annotations");
		assertFalse(reqAnnotations.has("implementations"));
		assertTrue(reqAnnotations.has("tests"));
	}

	@Test
	void testCombineOutput_withBoth() throws IOException {
		ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

		String implJson = "{\"impl1\": {\"id\": \"REQ-001\"}}";
		JsonNode implementations = mapper.readTree(implJson);

		String testsJson = "{\"test1\": {\"id\": \"SVC-001\"}}";
		JsonNode tests = mapper.readTree(testsJson);

		JsonNode result = RequirementsToolTask.combineOutput(implementations, tests);

		assertNotNull(result);
		assertTrue(result.has("requirement_annotations"));
		JsonNode reqAnnotations = result.get("requirement_annotations");
		assertTrue(reqAnnotations.has("implementations"));
		assertTrue(reqAnnotations.has("tests"));
	}

	@Test
	void testTaskConfiguration() {
		RequirementsToolExtension extension = project.getExtensions()
			.create("requirementsTool", RequirementsToolExtension.class, project);

		task.getRequirementsAnnotationsFile().set(extension.getRequirementsAnnotationsFile());
		task.getSvcsAnnotationsFile().set(extension.getSvcsAnnotationsFile());
		task.getOutputDirectory().set(extension.getOutputDirectory());
		task.getDatasetPath().set(extension.getDatasetPath());
		task.getTestResults().set(extension.getTestResults());
		task.getSkip().set(extension.getSkip());
		task.getSkipAssembleZipArtifact().set(extension.getSkipAssembleZipArtifact());
		task.getProjectName().set(project.getName());
		task.getProjectVersion().set("1.0.0");

		assertNotNull(task.getOutputDirectory().get());
		assertNotNull(task.getDatasetPath().get());
		assertFalse(task.getSkip().get());
		assertFalse(task.getSkipAssembleZipArtifact().get());
	}

	@Test
	void testSkipExecution() {
		task.getSkip().set(true);
		task.getProjectName().set(project.getName());
		task.getProjectVersion().set("1.0.0");
		task.getProjectBasedir().set(tempDir.toFile());
		task.getOutputDirectory().set(tempDir.resolve("build/reqstool").toFile());
		task.getDatasetPath().set(tempDir.resolve("reqstool").toFile());
		task.getTestResults().set(java.util.Arrays.asList("build/test-results/**/*.xml"));

		// Should not throw exception when skip is true
		assertDoesNotThrow(() -> task.execute());
	}

	@Test
	void testMissingRequirementsFile() throws IOException {
		// Setup directories
		File outputDir = tempDir.resolve("build/reqstool").toFile();
		File datasetDir = tempDir.resolve("reqstool").toFile();
		datasetDir.mkdirs();

		task.getSkip().set(false);
		task.getSkipAssembleZipArtifact().set(false);
		task.getProjectName().set("test-project");
		task.getProjectVersion().set("1.0.0");
		task.getProjectBasedir().set(tempDir.toFile());
		task.getOutputDirectory().set(outputDir);
		task.getDatasetPath().set(datasetDir);
		task.getTestResults().set(java.util.Arrays.asList("build/test-results/**/*.xml"));
		task.getZipFile().set(new File(outputDir, "test-project-reqstool.zip"));

		// Should throw exception when requirements.yml is missing
		Exception exception = assertThrows(Exception.class, () -> task.execute());
		assertTrue(exception.getMessage().contains("requirements.yml"));
	}

}
