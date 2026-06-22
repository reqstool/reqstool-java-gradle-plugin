package io.github.reqstool.gradle;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import io.github.reqstool.annotations.SVCs;

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

	@SVCs("SVC_GRADLE_PLUGIN_001")
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
		task.getSvcsAnnotationsFiles().setFrom(extension.getSvcsAnnotationsFiles());
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

	@SVCs("SVC_GRADLE_PLUGIN_003")
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

	@SVCs("SVC_GRADLE_PLUGIN_001")
	@Test
	void testMergeTestNodes_mergesTwoFiles() throws IOException {
		String yaml1 = "requirement_annotations:\n  tests:\n"
				+ "    SVC_001:\n      - elementKind: METHOD\n        fullyQualifiedName: pkg.TestA.test1\n"
				+ "    SVC_002:\n      - elementKind: METHOD\n        fullyQualifiedName: pkg.TestA.test2\n";
		String yaml2 = "requirement_annotations:\n  tests:\n"
				+ "    SVC_001:\n      - elementKind: METHOD\n        fullyQualifiedName: pkg.TestB.test3\n"
				+ "    SVC_003:\n      - elementKind: METHOD\n        fullyQualifiedName: pkg.TestB.test4\n";

		ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
		File f1 = tempDir.resolve("annot1.yml").toFile();
		File f2 = tempDir.resolve("annot2.yml").toFile();
		java.nio.file.Files.writeString(f1.toPath(), yaml1);
		java.nio.file.Files.writeString(f2.toPath(), yaml2);

		ObjectNode mergedTests = mapper.createObjectNode();
		for (File f : List.of(f1, f2)) {
			JsonNode testNode = mapper.readTree(f).path("requirement_annotations").path("tests");
			RequirementsToolTask.mergeTestNodes(mergedTests, testNode);
		}

		assertEquals(2, mergedTests.get("SVC_001").size());
		assertTrue(mergedTests.has("SVC_002"));
		assertTrue(mergedTests.has("SVC_003"));
	}

	@Test
	void testSetSvcsAnnotationsFilesMarksExplicit() {
		RequirementsToolExtension extension = project.getExtensions()
			.create("requirementsTool", RequirementsToolExtension.class, project);

		assertFalse(extension.isSvcsAnnotationsFilesExplicit());

		File file = tempDir.resolve("annotations.yml").toFile();
		extension.setSvcsAnnotationsFiles(file);

		assertTrue(extension.isSvcsAnnotationsFilesExplicit());
		assertTrue(extension.getSvcsAnnotationsFiles().contains(file));
	}

	@Test
	void testDeprecatedSvcsAnnotationsFileSetter() {
		RequirementsToolExtension extension = project.getExtensions()
			.create("requirementsTool", RequirementsToolExtension.class, project);

		File file = tempDir.resolve("annotations.yml").toFile();
		assertDoesNotThrow(() -> extension.setSvcsAnnotationsFile(file));
		assertTrue(extension.getSvcsAnnotationsFiles().contains(file));
	}

	@SVCs("SVC_GRADLE_PLUGIN_002")
	@Test
	void testAssembleZipArtifactHappyPath() throws IOException {
		File outputDir = tempDir.resolve("build/reqstool").toFile();
		File datasetDir = tempDir.resolve("reqstool").toFile();
		datasetDir.mkdirs();
		java.nio.file.Files.writeString(new File(datasetDir, "requirements.yml").toPath(), "requirements: []\n");

		task.getSkip().set(false);
		task.getSkipAssembleZipArtifact().set(false);
		task.getProjectName().set("test-project");
		task.getProjectVersion().set("1.0.0");
		task.getProjectBasedir().set(tempDir.toFile());
		task.getOutputDirectory().set(outputDir);
		task.getDatasetPath().set(datasetDir);
		task.getTestResults().set(java.util.Arrays.asList("build/test-results/**/*.xml"));
		File zipFile = new File(outputDir, "test-project-1.0.0-reqstool.zip");
		task.getZipFile().set(zipFile);

		task.execute();

		assertTrue(zipFile.exists());
		assertTrue(new File(outputDir, RequirementsToolTask.OUTPUT_FILE_ANNOTATIONS_YML_FILE).exists());
	}

	@SVCs("SVC_GRADLE_PLUGIN_003")
	@Test
	void testSkipAssembleZipArtifactBypassesZipCreation() throws IOException {
		File outputDir = tempDir.resolve("build/reqstool").toFile();
		File datasetDir = tempDir.resolve("reqstool").toFile();
		datasetDir.mkdirs();
		java.nio.file.Files.writeString(new File(datasetDir, "requirements.yml").toPath(), "requirements: []\n");

		task.getSkip().set(false);
		task.getSkipAssembleZipArtifact().set(true);
		task.getProjectName().set("test-project");
		task.getProjectVersion().set("1.0.0");
		task.getProjectBasedir().set(tempDir.toFile());
		task.getOutputDirectory().set(outputDir);
		task.getDatasetPath().set(datasetDir);
		task.getTestResults().set(java.util.Arrays.asList("build/test-results/**/*.xml"));
		File zipFile = new File(outputDir, "test-project-1.0.0-reqstool.zip");
		task.getZipFile().set(zipFile);

		task.execute();

		assertFalse(zipFile.exists());
		assertTrue(new File(outputDir, RequirementsToolTask.OUTPUT_FILE_ANNOTATIONS_YML_FILE).exists());
	}

	@SVCs("SVC_GRADLE_PLUGIN_002")
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
