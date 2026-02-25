// Copyright © LFV
package io.github.reqstool.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.tasks.TaskProvider;

/**
 * Gradle plugin for assembling and attaching reqstool ZIP artifacts. Mimics the behavior
 * of the reqstool-maven-plugin.
 */
public class RequirementsToolPlugin implements Plugin<Project> {

	/**
	 * Applies the plugin to a Gradle project. Registers the {@code assembleRequirements}
	 * task and automatically configures Maven publishing if the {@code maven-publish}
	 * plugin is applied.
	 * @param project the Gradle project
	 */
	@Override
	public void apply(Project project) {
		// Create extension for configuration
		RequirementsToolExtension extension = project.getExtensions()
			.create("requirementsTool", RequirementsToolExtension.class, project);

		// Register the assembleRequirements task
		TaskProvider<RequirementsToolTask> assembleTask = project.getTasks()
			.register("assembleRequirements", RequirementsToolTask.class, task -> {
				task.setGroup("build");
				task.setDescription("Assembles reqstool ZIP artifact with requirements annotations and test results");

				// Configure task inputs from extension
				task.getRequirementsAnnotationsFile().set(extension.getRequirementsAnnotationsFile());
				task.getSvcsAnnotationsFile().set(extension.getSvcsAnnotationsFile());
				task.getOutputDirectory().set(extension.getOutputDirectory());
				task.getDatasetPath().set(extension.getDatasetPath());
				task.getTestResults().set(extension.getTestResults());
				task.getSkip().set(extension.getSkip());
				task.getSkipAssembleZipArtifact().set(extension.getSkipAssembleZipArtifact());
				task.getSkipAttachZipArtifact().set(extension.getSkipAttachZipArtifact());
				task.getProjectName().set(project.getName());
				task.getProjectVersion().set(project.provider(() -> String.valueOf(project.getVersion())));
				task.getProjectBasedir().set(project.getProjectDir());

				// Configure ZIP output file
				String archiveBaseName = project.hasProperty("archivesBaseName")
						? String.valueOf(project.property("archivesBaseName")) : project.getName();
				String zipFileName = archiveBaseName + "-" + project.getVersion() + "-reqstool.zip";
				task.getZipFile()
					.set(extension.getOutputDirectory()
						.map(dir -> project.getLayout()
							.getProjectDirectory()
							.file(dir.getAsFile().getPath() + "/" + zipFileName)));
			});

		// Auto-configure Maven publishing if maven-publish plugin is applied
		project.getPlugins().withId("maven-publish", plugin -> {
			configureMavenPublishing(project, assembleTask);
		});
	}

	/**
	 * Configures Maven publishing to automatically attach the reqstool ZIP artifact
	 * to the publication.
	 * @param project the Gradle project
	 * @param assembleTask provider for the assembleRequirements task
	 */
	private void configureMavenPublishing(Project project, TaskProvider<RequirementsToolTask> assembleTask) {
		PublishingExtension publishing = project.getExtensions().getByType(PublishingExtension.class);

		publishing.getPublications().create("reqstool", MavenPublication.class, publication -> {
			String archiveBaseName = project.hasProperty("archivesBaseName")
					? String.valueOf(project.property("archivesBaseName")) : project.getName();

			String zipFileName = archiveBaseName + "-reqstool.zip";

			publication.setGroupId(String.valueOf(project.getGroup()));
			publication.setArtifactId(project.getName());
			publication.setVersion(String.valueOf(project.getVersion()));

			publication.artifact(assembleTask.flatMap(task -> task.getZipFile()), artifact -> {
				artifact.setClassifier("reqstool");
				artifact.setExtension("zip");
			});
		});
	}

}
