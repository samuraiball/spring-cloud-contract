/*
 * Copyright 2013-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.contract.stubrunner;

import io.specto.hoverfly.junit.HoverflyRule;
import org.assertj.core.api.BDDAssertions;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.Authentication;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties;
import org.springframework.util.ResourceUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.Map;

import static org.assertj.core.api.Assertions.within;
import static org.assertj.core.api.BDDAssertions.then;

public class AetherStubDownloaderTest {

	@Rule
	public HoverflyRule hoverflyRule = HoverflyRule.inSimulationMode("simulation.json");

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Test
	public void should_throw_an_exception_when_artifact_not_found_in_local_m2() {
		StubRunnerOptions stubRunnerOptions = new StubRunnerOptionsBuilder()
				.withStubsMode(StubRunnerProperties.StubsMode.LOCAL).build();

		AetherStubDownloader aetherStubDownloader = new AetherStubDownloader(
				stubRunnerOptions);

		Map.Entry<StubConfiguration, File> entry = aetherStubDownloader
				.downloadAndUnpackStubJar(new StubConfiguration("non.existing.group",
						"missing-artifact-id", "1.0-SNAPSHOT"));

		BDDAssertions.then(entry).isNull();
	}

	@Test
	public void should_throw_an_exception_when_local_m2_gets_replaced_with_a_temp_dir_and_a_jar_is_not_found_in_remote()
			throws Exception {

		StubRunnerOptions stubRunnerOptions = new StubRunnerOptionsBuilder()
				.withStubsMode(StubRunnerProperties.StubsMode.REMOTE)
				.withStubRepositoryRoot("file://" + folder.newFolder().getAbsolutePath())
				.build();

		AetherStubDownloader aetherStubDownloader = new AetherStubDownloader(
				stubRunnerOptions);

		Map.Entry<StubConfiguration, File> stubConfigurationFileEntry = aetherStubDownloader
				.downloadAndUnpackStubJar(new StubConfiguration("non.existing.group",
						"missing-artifact-id", "1.0-SNAPSHOT"));

		BDDAssertions.then(stubConfigurationFileEntry).isNull();
	}

	@Test
	public void should_use_local_repository_from_settings_xml() throws Exception {
		File tempSettings = File.createTempFile("settings", ".xml");
		String m2repoFolder = "m2repo" + File.separator + "repository";

		FileOutputStream out = new FileOutputStream(tempSettings);

		String text = "<settings><localRepository>"
				+ ResourceUtils.getFile(ResourceUtils.CLASSPATH_URL_PREFIX + m2repoFolder)
						.getAbsolutePath()
				+ "</localRepository></settings>";
		Writer writer = new OutputStreamWriter(out, Charset.defaultCharset().name());
		writer.write(text);
		writer.flush();

		System.setProperty("org.apache.maven.user-settings",
				tempSettings.getAbsolutePath());
		RepositorySystemSession repositorySystemSession = AetherFactories
				.newSession(AetherFactories.newRepositorySystem(), true);

		StubRunnerOptions stubRunnerOptions = new StubRunnerOptionsBuilder()
				.withStubsMode(StubRunnerProperties.StubsMode.LOCAL).build();

		AetherStubDownloader aetherStubDownloader = new AetherStubDownloader(
				stubRunnerOptions);

		Map.Entry<StubConfiguration, File> jar = aetherStubDownloader
				.downloadAndUnpackStubJar(new StubConfiguration(
						"org.springframework.cloud.contract.verifier.stubs",
						"bootService", "0.0.1-SNAPSHOT"));

		then(jar).isNotNull();
		repositorySystemSession.getLocalRepository().getBasedir().getAbsolutePath()
				.endsWith(m2repoFolder);
		System.clearProperty("org.apache.maven.user-settings");
	}

	@Test
	public void should_return_credentials_from_settings_xml() {
		File settings = new File(AetherStubDownloaderTest.class
				.getResource("/.m2/settings.xml").getFile());
		System.setProperty("org.apache.maven.user-settings", settings.getAbsolutePath());

		File configDir = new File(
				AetherStubDownloaderTest.class.getResource("/.m2").getFile());
		System.setProperty("maven.user.config.dir", configDir.getAbsolutePath());

		StubRunnerOptions stubRunnerOptions = new StubRunnerOptionsBuilder()
				.withStubsMode(StubRunnerProperties.StubsMode.REMOTE)
				.withStubRepositoryRoot(AetherStubDownloaderTest.class
						.getResource("/m2repo/repository").toString())
				.withServerId("my-server").build();
		AetherStubDownloader aetherStubDownloader = new AetherStubDownloader(
				stubRunnerOptions) {
			@Override
			Authentication buildAuthentication(String stubServerPassword,
					String username) {
				assert "admin".equals(username);
				// hashed {ha7QXbuAf9wH5uVeYJGWg+SC8fdkufPVfdtpTK8Yk3E=}
				assert "mypassword".equals(stubServerPassword);
				return super.buildAuthentication(stubServerPassword, username);
			}
		};

		Map.Entry<StubConfiguration, File> jar = aetherStubDownloader
				.downloadAndUnpackStubJar(new StubConfiguration(
						"org.springframework.cloud.contract.verifier.stubs",
						"bootService", "0.0.1-SNAPSHOT"));

		BDDAssertions.then(jar).isNotNull();
		System.clearProperty("org.apache.maven.user-settings");
	}

}
