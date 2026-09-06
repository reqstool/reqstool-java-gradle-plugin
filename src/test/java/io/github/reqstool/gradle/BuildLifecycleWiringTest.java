// Copyright © LFV
package io.github.reqstool.gradle;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers how the plugin joins the build lifecycle, which the fixture under
 * {@code tests/fixtures} cannot: the fixture declares no wiring of its own, so it only
 * ever exercises the case where the plugin is the sole author of these edges.
 *
 * <p>
 * {@code --dry-run} is deliberate. A circular dependency is raised while Gradle builds
 * the task graph, before any task runs, so the failure these tests guard against is
 * reproduced without compiling anything or resolving a single dependency.
 *
 * @see <a href= "https://github.com/reqstool/reqstool-java-gradle-plugin/issues/88">issue
 * #88</a>
 */
class BuildLifecycleWiringTest {

	@TempDir
	Path projectDir;

	/**
	 * Versions 0.1.0 and 0.1.1 wired nothing, so a consumer that wanted the documented
	 * behaviour declared it by hand. That line must not collide with the plugin's own
	 * wiring: 0.1.2 made {@code build} finalized by {@code assembleRequirements}, which
	 * contradicts it, and every such consumer failed at configuration time with "Circular
	 * dependency between the following tasks".
	 */
	@Test
	void consumerDeclaringBuildDependsOnAssembleRequirementsStillConfigures() throws IOException {
		writeProject("""
				tasks.named('assembleRequirements') { dependsOn(tasks.named('check')) }
				tasks.named('build') { dependsOn(tasks.named('assembleRequirements')) }
				""");

		BuildResult result = run();

		assertFalse(result.getOutput().contains("Circular dependency"),
				"consumer-declared build.dependsOn(assembleRequirements) must not collide "
						+ "with the plugin's own wiring:\n" + result.getOutput());
		assertTrue(result.getOutput().contains(":assembleRequirements"),
				"assembleRequirements should be in the task graph for `build`:\n" + result.getOutput());
	}

	/** The plugin on its own still puts the task into {@code build}. */
	@Test
	void assembleRequirementsIsPartOfBuildWithoutConsumerWiring() throws IOException {
		writeProject("");

		BuildResult result = run();

		assertTrue(result.getOutput().contains(":assembleRequirements"),
				"assembleRequirements should run as part of `build`:\n" + result.getOutput());
	}

	/**
	 * The task reads the test-results XML, so it has to come after the tests rather than
	 * merely alongside them.
	 */
	@Test
	void assembleRequirementsRunsAfterCheck() throws IOException {
		writeProject("");

		String output = run().getOutput();

		assertTrue(output.indexOf(":check") < output.indexOf(":assembleRequirements"),
				"check should be ordered before assembleRequirements:\n" + output);
	}

	private BuildResult run() {
		return GradleRunner.create()
			.withProjectDir(projectDir.toFile())
			.withPluginClasspath()
			.withArguments("build", "--dry-run")
			.build();
	}

	private void writeProject(String extraWiring) throws IOException {
		Files.writeString(projectDir.resolve("settings.gradle"), "rootProject.name = 'consumer'\n");
		Files.writeString(projectDir.resolve("build.gradle"), """
				plugins {
				    id 'java'
				    id 'io.github.reqstool.gradle-plugin'
				}

				""" + extraWiring);
	}

}
