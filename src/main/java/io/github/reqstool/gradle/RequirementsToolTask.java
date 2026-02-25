package io.github.reqstool.gradle;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Task for assembling reqstool ZIP artifact. Combines requirements annotations with test
 * annotations and creates a ZIP artifact containing requirements, SVCs, test results, and
 * combined annotations.
 */
public class RequirementsToolTask extends DefaultTask {

	// Constants matching Maven plugin
	private static final String[] OUTPUT_ARTIFACT_TEST_RESULTS_PATTERN = { "test_results/**/*.xml" };

	public static final String INPUT_FILE_MANUAL_VERIFICATION_RESULTS_YML = "manual_verification_results.yml";

	public static final String INPUT_FILE_REQUIREMENTS_YML = "requirements.yml";

	public static final String INPUT_FILE_SOFTWARE_VERIFICATION_CASES_YML = "software_verification_cases.yml";

	public static final String OUTPUT_FILE_ANNOTATIONS_YML_FILE = "annotations.yml";

	public static final String OUTPUT_ARTIFACT_FILE_REQSTOOL_CONFIG_YML = "reqstool_config.yml";

	public static final String OUTPUT_ARTIFACT_DIR_TEST_RESULTS = "test_results";

	public static final String XML_IMPLEMENTATIONS = "implementations";

	public static final String XML_REQUIREMENT_ANNOTATIONS = "requirement_annotations";

	public static final String XML_TESTS = "tests";

	protected static final String YAML_LANG_SERVER_SCHEMA_ANNOTATIONS = "# yaml-language-server: $schema=https://raw.githubusercontent.com/Luftfartsverket/reqstool-client/main/src/reqstool/resources/schemas/v1/annotations.schema.json";

	protected static final String YAML_LANG_SERVER_SCHEMA_CONFIG = "# yaml-language-server: $schema=https://raw.githubusercontent.com/Luftfartsverket/reqstool-client/main/src/reqstool/resources/schemas/v1/reqstool_config.schema.json";

	private static final ObjectMapper yamlMapper;

	static {
		yamlMapper = new ObjectMapper(new YAMLFactory().enable(YAMLGenerator.Feature.INDENT_ARRAYS_WITH_INDICATOR));
		yamlMapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
	}

	private final RegularFileProperty requirementsAnnotationsFile = getProject().getObjects().fileProperty();

	private final RegularFileProperty svcsAnnotationsFile = getProject().getObjects().fileProperty();

	private final RegularFileProperty outputDirectory = getProject().getObjects().fileProperty();

	private final RegularFileProperty datasetPath = getProject().getObjects().fileProperty();

	private final ListProperty<String> testResults = getProject().getObjects().listProperty(String.class);

	private final Property<Boolean> skip = getProject().getObjects().property(Boolean.class);

	private final Property<Boolean> skipAssembleZipArtifact = getProject().getObjects().property(Boolean.class);

	private final Property<Boolean> skipAttachZipArtifact = getProject().getObjects().property(Boolean.class);

	private final Property<String> projectName = getProject().getObjects().property(String.class);

	private final Property<String> projectVersion = getProject().getObjects().property(String.class);

	private final Property<File> projectBasedir = getProject().getObjects().property(File.class);

	private final RegularFileProperty zipFile = getProject().getObjects().fileProperty();

	/**
	 * Returns the path to the requirements annotations YAML file.
	 * @return file property for requirements annotations
	 */
	@Optional
	@InputFile
	public RegularFileProperty getRequirementsAnnotationsFile() {
		return requirementsAnnotationsFile;
	}

	/**
	 * Returns the path to the SVCs (Software Verification Cases) annotations YAML file.
	 * @return file property for SVCs annotations
	 */
	@Optional
	@InputFile
	public RegularFileProperty getSvcsAnnotationsFile() {
		return svcsAnnotationsFile;
	}

	/**
	 * Returns the absolute path to the output directory.
	 * @return output directory path as a string
	 */
	@Input
	public String getOutputDirectoryPath() {
		File outDir = outputDirectory.getAsFile().getOrNull();
		return outDir != null ? outDir.getAbsolutePath() : "";
	}

	/**
	 * Returns the output directory file property.
	 * @return file property for the output directory
	 */
	@Internal
	public RegularFileProperty getOutputDirectory() {
		return outputDirectory;
	}

	/**
	 * Returns the dataset directory containing requirements and supporting files.
	 * @return file property for the dataset directory
	 */
	@InputDirectory
	@Optional
	public RegularFileProperty getDatasetPath() {
		return datasetPath;
	}

	/**
	 * Returns the list of test result file glob patterns.
	 * @return list property of test result patterns
	 */
	@Input
	public ListProperty<String> getTestResults() {
		return testResults;
	}

	/**
	 * Returns whether this task execution should be skipped.
	 * @return boolean property
	 */
	@Input
	public Property<Boolean> getSkip() {
		return skip;
	}

	/**
	 * Returns whether ZIP artifact assembly should be skipped.
	 * @return boolean property
	 */
	@Input
	public Property<Boolean> getSkipAssembleZipArtifact() {
		return skipAssembleZipArtifact;
	}

	/**
	 * Returns whether artifact attachment should be skipped.
	 * @return boolean property
	 */
	@Input
	public Property<Boolean> getSkipAttachZipArtifact() {
		return skipAttachZipArtifact;
	}

	/**
	 * Returns the name of the project being built.
	 * @return project name property
	 */
	@Input
	public Property<String> getProjectName() {
		return projectName;
	}

	/**
	 * Returns the version of the project being built.
	 * @return project version property
	 */
	@Input
	public Property<String> getProjectVersion() {
		return projectVersion;
	}

	/**
	 * Returns the base directory of the project.
	 * @return project base directory property
	 */
	@Input
	public Property<File> getProjectBasedir() {
		return projectBasedir;
	}

	/**
	 * Returns the path where the reqstool ZIP artifact will be written.
	 * @return file property for the ZIP artifact
	 */
	@OutputFile
	public RegularFileProperty getZipFile() {
		return zipFile;
	}

	/**
	 * Executes the task. Combines requirements and test annotations into a single
	 * annotations file, and optionally assembles a ZIP artifact containing requirements,
	 * annotations, and test results.
	 */
	@TaskAction
	public void execute() {
		if (skip.get()) {
			getLogger().info("Skipping execution of reqstool plugin");
			return;
		}

		getLogger().debug("Assembling and Attaching Reqstool Gradle Zip Artifact");
		getLogger().info("testResults: " + Arrays.toString(testResults.get().toArray()));

		try {
			JsonNode implementationsNode = yamlMapper.createObjectNode();
			JsonNode testsNode = yamlMapper.createObjectNode();

			File reqAnnotFile = requirementsAnnotationsFile.getAsFile().getOrNull();
			if (reqAnnotFile != null && reqAnnotFile.exists()) {
				implementationsNode = yamlMapper.readTree(reqAnnotFile)
					.path(XML_REQUIREMENT_ANNOTATIONS)
					.path(XML_IMPLEMENTATIONS);
			}

			File svcsAnnotFile = svcsAnnotationsFile.getAsFile().getOrNull();
			if (svcsAnnotFile != null && svcsAnnotFile.exists()) {
				testsNode = yamlMapper.readTree(svcsAnnotFile).path(XML_REQUIREMENT_ANNOTATIONS).path(XML_TESTS);
			}

			JsonNode combinedOutputNode = combineOutput(implementationsNode, testsNode);

			File outDir = outputDirectory.getAsFile().get();
			if (!outDir.exists()) {
				outDir.mkdirs();
			}

			writeCombinedOutputToFile(new File(outDir, OUTPUT_FILE_ANNOTATIONS_YML_FILE), combinedOutputNode);

			if (!skipAssembleZipArtifact.get()) {
				assembleZipArtifact();
			}
			else {
				getLogger().info("Skipping zip artifact assembly");
			}

		}
		catch (IOException e) {
			throw new GradleException("Error combining annotations or creating zip file", e);
		}
	}

	/**
	 * Combines implementations and tests nodes into a single requirement annotations node.
	 * @param implementationsNode node containing requirement implementations
	 * @param testsNode node containing test cases
	 * @return combined requirement annotations node
	 */
	static JsonNode combineOutput(JsonNode implementationsNode, JsonNode testsNode) {
		ObjectNode requirementAnnotationsNode = yamlMapper.createObjectNode();
		if (!implementationsNode.isEmpty()) {
			requirementAnnotationsNode.set(XML_IMPLEMENTATIONS, implementationsNode);
		}
		if (!testsNode.isEmpty()) {
			requirementAnnotationsNode.set(XML_TESTS, testsNode);
		}

		ObjectNode newNode = yamlMapper.createObjectNode();
		newNode.set(XML_REQUIREMENT_ANNOTATIONS, requirementAnnotationsNode);

		return newNode;
	}

	/**
	 * Writes the combined annotations to a YAML file with language server schema hints.
	 * @param outputFile the file to write to
	 * @param combinedOutputNode the combined annotations node
	 * @throws IOException if writing fails
	 */
	private void writeCombinedOutputToFile(File outputFile, JsonNode combinedOutputNode) throws IOException {
		File reqAnnotFile = requirementsAnnotationsFile.getAsFile().getOrNull();
		File svcsAnnotFile = svcsAnnotationsFile.getAsFile().getOrNull();

		getLogger()
			.info("Combining " + reqAnnotFile + " and " + svcsAnnotFile + " into " + outputFile.getAbsolutePath());

		try (Writer writer = new PrintWriter(
				new OutputStreamWriter(new FileOutputStream(outputFile), StandardCharsets.UTF_8))) {
			writer.write(YAML_LANG_SERVER_SCHEMA_ANNOTATIONS + System.lineSeparator());
			yamlMapper.writeValue(writer, combinedOutputNode);
		}
	}

	/**
	 * Assembles the reqstool ZIP artifact containing requirements, annotations, and test
	 * results.
	 * @throws IOException if ZIP creation or file reading fails
	 */
	private void assembleZipArtifact() throws IOException {
		String zipArtifactFilename = projectName.get() + "-" + projectVersion.get() + "-reqstool.zip";
		String topLevelDir = projectName.get() + "-" + projectVersion.get() + "-reqstool";

		File zipFileOutput = zipFile.get().getAsFile();
		File outDir = zipFileOutput.getParentFile();

		getLogger().info("Assembling zip file: " + zipFileOutput.getAbsolutePath());

		try (FileOutputStream fos = new FileOutputStream(zipFileOutput);
				ZipOutputStream zipOut = new ZipOutputStream(fos)) {

			Map<String, Object> reqstoolConfigResources = new HashMap<String, Object>();

			File datasetDir = datasetPath.getAsFile().get();
			File requirementsFile = new File(datasetDir, INPUT_FILE_REQUIREMENTS_YML);
			if (!requirementsFile.isFile()) {
				String msg = "Missing mandatory " + INPUT_FILE_REQUIREMENTS_YML + ": "
						+ requirementsFile.getAbsolutePath();
				throw new GradleException(msg);
			}

			addFileToZipArtifact(zipOut, requirementsFile, new File(topLevelDir));
			getLogger().info("added to " + topLevelDir + ": " + requirementsFile);
			reqstoolConfigResources.put("requirements", requirementsFile.getName());

			File svcsFile = new File(datasetDir, INPUT_FILE_SOFTWARE_VERIFICATION_CASES_YML);
			if (svcsFile.isFile()) {
				addFileToZipArtifact(zipOut, svcsFile, new File(topLevelDir));
				getLogger().debug("added to " + topLevelDir + ": " + svcsFile);
				reqstoolConfigResources.put("software_verification_cases", svcsFile.getName());
			}

			File mvrsFile = new File(datasetDir, INPUT_FILE_MANUAL_VERIFICATION_RESULTS_YML);
			if (mvrsFile.isFile()) {
				addFileToZipArtifact(zipOut, mvrsFile, new File(topLevelDir));
				getLogger().debug("added to " + topLevelDir + ": " + mvrsFile);
				reqstoolConfigResources.put("manual_verification_results", mvrsFile.getName());
			}

			File annotationsZipFile = new File(outDir, OUTPUT_FILE_ANNOTATIONS_YML_FILE);
			if (annotationsZipFile.isFile()) {
				addFileToZipArtifact(zipOut, annotationsZipFile, new File(topLevelDir));
				getLogger().debug("added to " + topLevelDir + ": " + annotationsZipFile);
				reqstoolConfigResources.put("annotations", annotationsZipFile.getName());
			}

			Path dir = Paths.get(projectBasedir.get().toURI());
			List<String> patterns = testResults.get()
				.stream()
				.map(pattern -> "glob:" + pattern)
				.collect(Collectors.toList());

			List<PathMatcher> matchers = patterns.stream()
				.map(pattern -> FileSystems.getDefault().getPathMatcher(pattern))
				.collect(Collectors.toList());

			AtomicInteger testResultsCount = new AtomicInteger(0);

			Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					Path relativePath = dir.relativize(file);
					getLogger().debug("Checking file: " + relativePath);

					if (matchers.stream().anyMatch(matcher -> matcher.matches(relativePath))) {
						getLogger().debug("Match found for: " + relativePath);
						addFileToZipArtifact(zipOut, file.toFile(),
								new File(topLevelDir, OUTPUT_ARTIFACT_DIR_TEST_RESULTS));
						testResultsCount.incrementAndGet();
					}
					return FileVisitResult.CONTINUE;
				}
			});

			getLogger().debug("testResults values: " + Arrays.toString(testResults.get().toArray()));
			getLogger().debug("added " + testResultsCount + " test_results");
			reqstoolConfigResources.put("test_results", OUTPUT_ARTIFACT_TEST_RESULTS_PATTERN);

			addReqstoolConfigYamlToZip(zipOut, new File(topLevelDir), reqstoolConfigResources);
		}

		getLogger().info("Assembled zip artifact: " + zipFileOutput.getAbsolutePath());
	}

	/**
	 * Adds a file to the ZIP artifact at the specified target directory.
	 * @param zipOut the ZIP output stream
	 * @param file the file to add
	 * @param targetDirectory the target directory within the ZIP
	 * @throws IOException if reading the file or writing to ZIP fails
	 */
	private void addFileToZipArtifact(ZipOutputStream zipOut, File file, File targetDirectory) throws IOException {
		if (file.exists()) {
			File entryName;
			if (targetDirectory == null || targetDirectory.getName().isEmpty()) {
				entryName = new File(file.getName());
			}
			else {
				entryName = new File(targetDirectory, file.getName());
			}

			getLogger().info("Adding file: " + entryName.toString());

			ZipEntry zipEntry = new ZipEntry(entryName.toString());
			zipOut.putNextEntry(zipEntry);

			// Use Java NIO instead of Commons IO
			byte[] bytes = Files.readAllBytes(file.toPath());
			zipOut.write(bytes, 0, bytes.length);
			zipOut.closeEntry();
		}
	}

	/**
	 * Creates and adds the reqstool_config.yml file to the ZIP artifact.
	 * @param zipOut the ZIP output stream
	 * @param topLevelDir the top-level directory within the ZIP
	 * @param reqstoolConfigResources map of resource files included in the artifact
	 * @throws IOException if writing to ZIP fails
	 */
	private void addReqstoolConfigYamlToZip(ZipOutputStream zipOut, File topLevelDir,
			Map<String, Object> reqstoolConfigResources) throws IOException {
		DumperOptions options = new DumperOptions();
		options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
		options.setPrettyFlow(true);
		Yaml yaml = new Yaml(options);

		LinkedHashMap<String, Object> yamlData = new LinkedHashMap<String, Object>();
		yamlData.put("language", "java");
		yamlData.put("build", "gradle");
		yamlData.put("resources", reqstoolConfigResources);

		ZipEntry zipEntry = new ZipEntry(new File(topLevelDir, OUTPUT_ARTIFACT_FILE_REQSTOOL_CONFIG_YML).toString());
		zipOut.putNextEntry(zipEntry);

		Writer writer = new OutputStreamWriter(zipOut, StandardCharsets.UTF_8);
		writer.write(String.format("%s%n", YAML_LANG_SERVER_SCHEMA_CONFIG));
		writer.write(String.format("# version: %s%n", projectVersion.get()));
		yaml.dump(yamlData, writer);
		writer.flush();

		zipOut.closeEntry();
	}

}
