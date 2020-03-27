/*
 * Copyright 2017-2020 the original author or authors.
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

package org.springframework.cloud.gcp.autoconfigure.secretmanager.it;

import java.util.stream.StreamSupport;

import com.google.api.gax.rpc.NotFoundException;
import com.google.cloud.secretmanager.v1beta1.*;
import com.google.cloud.secretmanager.v1beta1.SecretManagerServiceClient.ListSecretsPagedResponse;
import com.google.protobuf.ByteString;
import io.grpc.StatusRuntimeException;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.gcp.autoconfigure.core.GcpContextAutoConfiguration;
import org.springframework.cloud.gcp.autoconfigure.secretmanager.GcpSecretManagerAutoConfiguration;
import org.springframework.cloud.gcp.autoconfigure.secretmanager.GcpSecretManagerBootstrapConfiguration;
import org.springframework.cloud.gcp.core.GcpProjectIdProvider;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assumptions.assumeThat;

public class SecretManagerIntegrationTests {

	private static final String TEST_SECRET_ID = "spring-cloud-gcp-it-secret";

	private static final String VERSIONED_SECRET_ID = "spring-cloud-gcp-it-versioned-secret";

	private GcpProjectIdProvider projectIdProvider;

	private SecretManagerServiceClient client;

	@BeforeClass
	public static void prepare() {
		assumeThat(System.getProperty("it.secretmanager"))
				.as("Secret manager integration tests are disabled. "
						+ "Please use '-Dit.secretmanager=true' to enable them.")
				.isEqualTo("true");
	}

	@Before
	public void setupSecretManager() {
		ConfigurableApplicationContext context = new SpringApplicationBuilder()
				.sources(GcpContextAutoConfiguration.class, GcpSecretManagerAutoConfiguration.class)
				.web(WebApplicationType.NONE)
				.run();

		this.projectIdProvider = context.getBeanFactory().getBean(GcpProjectIdProvider.class);
		this.client = context.getBeanFactory().getBean(SecretManagerServiceClient.class);

		// Clean up the test secrets in the project from prior runs.
		deleteSecret(TEST_SECRET_ID);
		deleteSecret(VERSIONED_SECRET_ID);

		// The custom project is the same one as the default project. Integration test not relevant.
		// TODO Which other project to use?
		String customProject = projectIdProvider.getProjectId();
		deleteSecret(TEST_SECRET_ID,customProject);
		deleteSecret(VERSIONED_SECRET_ID,customProject);

	}

	@Test
	public void testConfiguration() {
		createSecret(TEST_SECRET_ID, "the secret data.");

		ConfigurableApplicationContext context = new SpringApplicationBuilder()
				.sources(GcpContextAutoConfiguration.class, GcpSecretManagerBootstrapConfiguration.class)
				.web(WebApplicationType.NONE)
				.properties("spring.cloud.gcp.secretmanager.bootstrap.enabled=true")
				.run();

		assertThat(context.getEnvironment().getProperty(TEST_SECRET_ID))
				.isEqualTo("the secret data.");

		byte[] byteArraySecret = context.getEnvironment().getProperty(TEST_SECRET_ID, byte[].class);
		assertThat(byteArraySecret).isEqualTo("the secret data.".getBytes());
	}

	@Test
	public void testConfigurationDisabled() {
		createSecret(TEST_SECRET_ID, "the secret data.");

		ConfigurableApplicationContext context = new SpringApplicationBuilder()
				.sources(GcpContextAutoConfiguration.class, GcpSecretManagerBootstrapConfiguration.class)
				.web(WebApplicationType.NONE)
				.properties("spring.cloud.gcp.secretmanager.enabled=false")
				.run();

		assertThat(context.getEnvironment().getProperty(TEST_SECRET_ID, String.class)).isNull();
	}

	@Test
	public void testSecretsWithSpecificVersion() {
		createSecret(VERSIONED_SECRET_ID, "the secret data");
		createSecret(VERSIONED_SECRET_ID, "the secret data v2");
		createSecret(VERSIONED_SECRET_ID, "the secret data v3");

		ConfigurableApplicationContext context = new SpringApplicationBuilder()
				.sources(GcpContextAutoConfiguration.class, GcpSecretManagerBootstrapConfiguration.class)
				.web(WebApplicationType.NONE)
				.properties("spring.cloud.gcp.secretmanager.bootstrap.enabled=true")
				.properties("spring.cloud.gcp.secretmanager.versions." + VERSIONED_SECRET_ID + "=2")
				.run();

		String versionedSecret = context.getEnvironment().getProperty(VERSIONED_SECRET_ID, String.class);
		assertThat(versionedSecret).isEqualTo("the secret data v2");
	}

	@Test
	public void testSecretsWithSpecificProjectId() {
		createSecret(TEST_SECRET_ID, "the secret data", projectIdProvider.getProjectId());

		// The custom project is the same one as the default project. Integration test not relevant.
		// TODO Which other project to use?
		String customProject = projectIdProvider.getProjectId();
		ConfigurableApplicationContext context = new SpringApplicationBuilder()
				.sources(GcpContextAutoConfiguration.class, GcpSecretManagerBootstrapConfiguration.class)
				.web(WebApplicationType.NONE)
				.properties("spring.cloud.gcp.secretmanager.bootstrap.enabled=true")
				.properties("spring.cloud.gcp.secretmanager.bootstrap.projectIds." + TEST_SECRET_ID + "="
						+ customProject)
				.run();

		assertThat(secretExists(TEST_SECRET_ID, customProject)).isTrue();
		// TODO Check if not created in the default project
		// assertThat(secretExists(TEST_SECRET_ID)).isFalse();

		String secret = context.getEnvironment().getProperty(TEST_SECRET_ID, String.class);
		assertThat(secret).isEqualTo("the secret data");
	}

	@Test
	public void testSecretsWithSpecificProjectIdAndVersion() {
		createSecret(VERSIONED_SECRET_ID, "the secret data", projectIdProvider.getProjectId());
		createSecret(VERSIONED_SECRET_ID, "the secret data v2", projectIdProvider.getProjectId());

		// The custom project is the same one as the default project. Integration test not relevant.
		// TODO Which other project to use?
		String customProject = projectIdProvider.getProjectId();
		ConfigurableApplicationContext context = new SpringApplicationBuilder()
				.sources(GcpContextAutoConfiguration.class, GcpSecretManagerBootstrapConfiguration.class)
				.web(WebApplicationType.NONE)
				.properties("spring.cloud.gcp.secretmanager.bootstrap.enabled=true")
				.properties("spring.cloud.gcp.secretmanager.bootstrap.versions." + VERSIONED_SECRET_ID + "=2")
				.properties("spring.cloud.gcp.secretmanager.bootstrap.projectIds." + VERSIONED_SECRET_ID
						+ "=" + customProject)
				.run();

		assertThat(secretExists(VERSIONED_SECRET_ID, customProject)).isTrue();
		// TODO Check if not created in the default project
		// assertThat(secretExists(VERSIONED_SECRET_ID)).isFalse();

		String versionedSecret = context.getEnvironment().getProperty(VERSIONED_SECRET_ID, String.class);
		assertThat(versionedSecret).isEqualTo("the secret data v2");
	}

	@Test
	public void testSecretsWithMissingVersion() {
		createSecret(VERSIONED_SECRET_ID, "the secret data");
		createSecret(VERSIONED_SECRET_ID, "the secret data v2");

		assertThatThrownBy(() -> new SpringApplicationBuilder()
				.sources(GcpContextAutoConfiguration.class, GcpSecretManagerBootstrapConfiguration.class)
				.web(WebApplicationType.NONE)
				.properties("spring.cloud.gcp.secretmanager.bootstrap.enabled=true")
				.properties("spring.cloud.gcp.secretmanager.versions." + VERSIONED_SECRET_ID + "=7")
				.run())
						.hasCauseInstanceOf(StatusRuntimeException.class);
	}

	/**
	 * Creates the secret with the specified payload if the secret does not already exist.
	 * Otherwise creates a new version of the secret under the existing {@code secretId}.
	 */
	private void createSecret(String secretId, String payload) {
		createSecret(secretId, payload, projectIdProvider.getProjectId());
	}

	/**
	 * Creates the secret with the specified payload if the secret does not already exist.
	 */
	private void createSecret(String secretId, String payload, String projectId) {
		ProjectName projectName = ProjectName.of(projectId);
		if (!secretExists(secretId)) {
			// Creates the secret.
			Secret secret = Secret.newBuilder()
					.setReplication(
							Replication.newBuilder()
									.setAutomatic(Replication.Automatic.newBuilder().build())
									.build())
					.build();
			CreateSecretRequest request = CreateSecretRequest.newBuilder()
					.setParent(projectName.toString())
					.setSecretId(secretId)
					.setSecret(secret)
					.build();
			client.createSecret(request);
		}

		createSecretPayload(secretId, payload);
	}

	private void createSecretPayload(String secretId, String data) {
		// Create the secret payload.
		SecretName name = SecretName.of(projectIdProvider.getProjectId(), secretId);
		SecretPayload payloadObject = SecretPayload.newBuilder()
				.setData(ByteString.copyFromUtf8(data))
				.build();
		AddSecretVersionRequest payloadRequest = AddSecretVersionRequest.newBuilder()
				.setParent(name.toString())
				.setPayload(payloadObject)
				.build();
		client.addSecretVersion(payloadRequest);
	}

	private boolean secretExists(String secretId) {
		return secretExists(secretId, projectIdProvider.getProjectId());
	}

	private boolean secretExists(String secretId, String projectId) {
		ProjectName projectName = ProjectName.of(projectId);
		ListSecretsPagedResponse listSecretsResponse = this.client.listSecrets(projectName);
		return StreamSupport.stream(listSecretsResponse.iterateAll().spliterator(), false)
				.anyMatch(secret -> secret.getName().contains(secretId));
	}

	private void deleteSecret(String secretId) {
		this.deleteSecret(secretId, projectIdProvider.getProjectId());
	}

	private void deleteSecret(String secretId, String projectId) {
		try {
			this.client.deleteSecret(SecretName.of(projectId, secretId));
		}
		catch (NotFoundException e) {
			// Can happen when cleaning project and custom project. Don't pollute the logs with this
			// LOGGER.debug("Skipped deleting " + secretId + " because it does not exist.");
		}
	}
}
