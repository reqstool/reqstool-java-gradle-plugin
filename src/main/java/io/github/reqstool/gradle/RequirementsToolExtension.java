package io.github.reqstool.gradle;

import org.gradle.api.Project;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

/**
 * Extension for configuring the Reqstool Gradle Plugin. Provides zero-configuration
 * defaults matching the Maven plugin behavior.
 */
public class RequirementsToolExtension {

	private final RegularFileProperty requirementsAnnotationsFile;

	private final RegularFileProperty svcsAnnotationsFile;

	private final RegularFileProperty outputDirectory;

	private final RegularFileProperty datasetPath;

	private final ListProperty<String> testResults;

	private final Property<Boolean> skip;

	private final Property<Boolean> skipAssembleZipArtifact;

	private final Property<Boolean> skipAttachZipArtifact;

	public RequirementsToolExtension(Project project) {
		this.requirementsAnnotationsFile = project.getObjects().fileProperty();
		this.svcsAnnotationsFile = project.getObjects().fileProperty();
		this.outputDirectory = project.getObjects().fileProperty();
		this.datasetPath = project.getObjects().fileProperty();
		this.testResults = project.getObjects().listProperty(String.class);
		this.skip = project.getObjects().property(Boolean.class);
		this.skipAssembleZipArtifact = project.getObjects().property(Boolean.class);
		this.skipAttachZipArtifact = project.getObjects().property(Boolean.class);

		// Set defaults matching
		this.requirementsAnnotationsFile.convention(project.getLayout()
			.getBuildDirectory()
			.file("generated/sources/annotationProcessor/java/main/resources/annotations.yml"));
		this.svcsAnnotationsFile.convention(project.getLayout()
			.getBuildDirectory()
			.file("generated/sources/annotationProcessor/java/test/resources/annotations.yml"));
		this.outputDirectory.convention(project.getObjects()
			.fileProperty()
			.fileProvider(project.getLayout().getBuildDirectory().map(d -> d.dir("reqstool").getAsFile())));
		this.datasetPath.convention(project.getLayout().getProjectDirectory().file("reqstool"));

		// Gradle default test results pattern
		this.testResults.convention(project.provider(() -> {
			return java.util.Arrays.asList("build/test-results/**/*.xml");
		}));

		this.skip.convention(false);
		this.skipAssembleZipArtifact.convention(false);
		this.skipAttachZipArtifact.convention(false);
	}

	public RegularFileProperty getRequirementsAnnotationsFile() {
		return requirementsAnnotationsFile;
	}

	public RegularFileProperty getSvcsAnnotationsFile() {
		return svcsAnnotationsFile;
	}

	public RegularFileProperty getOutputDirectory() {
		return outputDirectory;
	}

	public RegularFileProperty getDatasetPath() {
		return datasetPath;
	}

	public ListProperty<String> getTestResults() {
		return testResults;
	}

	public Property<Boolean> getSkip() {
		return skip;
	}

	public Property<Boolean> getSkipAssembleZipArtifact() {
		return skipAssembleZipArtifact;
	}

	public Property<Boolean> getSkipAttachZipArtifact() {
		return skipAttachZipArtifact;
	}

}
