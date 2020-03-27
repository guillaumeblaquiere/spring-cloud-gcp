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

package org.springframework.cloud.gcp.autoconfigure.secretmanager;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.google.cloud.secretmanager.v1beta1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1beta1.ProjectName;
import com.google.cloud.secretmanager.v1beta1.Secret;
import com.google.cloud.secretmanager.v1beta1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1beta1.SecretManagerServiceClient.ListSecretsPagedResponse;
import com.google.cloud.secretmanager.v1beta1.SecretVersionName;
import com.google.protobuf.ByteString;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.gcp.core.GcpProjectIdProvider;
import org.springframework.core.env.EnumerablePropertySource;

/**
 * Retrieves secrets from GCP Secret Manager under the current GCP project.
 *
 * @author Daniel Zou
 * @author Eddú Meléndez
 * @since 1.2.2
 */
public class SecretManagerPropertySource extends EnumerablePropertySource<SecretManagerServiceClient> {

	private static final Log LOGGER = LogFactory.getLog(SecretManagerPropertySource.class);

	private static final String LATEST_VERSION_STRING = "latest";

	private final Map<String, Object> properties;

	private final String[] propertyNames;

	public SecretManagerPropertySource(
			String propertySourceName,
			SecretManagerServiceClient client,
			GcpProjectIdProvider projectIdProvider,
			String secretsPrefix) {
		this(propertySourceName, client, projectIdProvider, secretsPrefix, Collections.EMPTY_MAP,
				Collections.EMPTY_MAP);
	}

	public SecretManagerPropertySource(
			String propertySourceName,
			SecretManagerServiceClient client,
			GcpProjectIdProvider projectIdProvider,
			String secretsPrefix,
			Map<String, String> versions,
			Map<String, String> projectIds) {
		super(propertySourceName, client);

		Map<String, Object> propertiesMap = createSecretsPropertiesMap(
				client, projectIdProvider.getProjectId(), secretsPrefix, versions, projectIds);

		this.properties = propertiesMap;
		this.propertyNames = propertiesMap.keySet().toArray(new String[propertiesMap.size()]);
	}

	@Override
	public String[] getPropertyNames() {
		return propertyNames;
	}

	@Override
	public Object getProperty(String name) {
		return properties.get(name);
	}

	private static Map<String, Object> createSecretsPropertiesMap(
			SecretManagerServiceClient client, String projectId, String secretsPrefix, Map<String, String> versions,
			Map<String, String> projectIds) {

		ListSecretsPagedResponse response = client.listSecrets(ProjectName.of(projectId));
		Map<String, Object> secretsMap = new HashMap<>();
		for (Secret secret : response.iterateAll()) {
			String secretId = extractSecretId(secret);
			ByteString secretPayload = getSecretPayload(client, projectId, secretId, versions, projectIds);
			if (secretPayload != null) {
				secretsMap.put(secretsPrefix + secretId, secretPayload);
			}
		}

		return secretsMap;
	}

	private static ByteString getSecretPayload(
			SecretManagerServiceClient client,
			String projectId,
			String secretId,
			Map<String, String> versions,
			Map<String, String> projectIds) {

		String version = versions.containsKey(secretId) ? versions.get(secretId) : LATEST_VERSION_STRING;

		// Get the project
		String resolvedProjectId = projectId;
		if (!projectIds.isEmpty() && projectIds.containsKey(secretId)) {
			resolvedProjectId = projectIds.get(secretId);
		}

		SecretVersionName secretVersionName = SecretVersionName.newBuilder()
				.setProject(resolvedProjectId)
				.setSecret(secretId)
				.setSecretVersion(version)
				.build();

		AccessSecretVersionResponse response = client.accessSecretVersion(secretVersionName);
		return response.getPayload().getData();
	}

	/**
	 * Extracts the Secret ID from the {@link Secret}. The secret ID refers to the unique ID
	 * given to the secret when it is saved under a GCP project.
	 *
	 * <p>
	 * The secret ID is extracted from the full secret name of the form:
	 * projects/${PROJECT_ID}/secrets/${SECRET_ID}
	 */
	private static String extractSecretId(Secret secret) {
		String[] secretNameTokens = secret.getName().split("/");
		return secretNameTokens[secretNameTokens.length - 1];
	}
}
