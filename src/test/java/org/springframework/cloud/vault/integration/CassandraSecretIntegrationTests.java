/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.vault.integration;

import static org.assertj.core.api.Assertions.*;
import static org.junit.Assume.assumeTrue;
import static org.springframework.cloud.vault.SecureBackendAccessors.*;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.cloud.vault.VaultClient;
import org.springframework.cloud.vault.VaultProperties;
import org.springframework.cloud.vault.util.CanConnect;
import org.springframework.cloud.vault.util.Settings;
import org.springframework.web.client.RestTemplate;

/**
 * Integration tests for {@link VaultClient} using the cassandra secret backend. This test
 * requires a running Cassandra instance, see {@link #CASSANDRA_HOST} and other
 * {@code CASSANDRA_*} properties.
 *
 * @author Mark Paluch
 */
public class CassandraSecretIntegrationTests extends AbstractIntegrationTests {

	private final static String CASSANDRA_HOST = "localhost";
	private final static int CASSANDRA_PORT = 9042;

	private final static String CASSANDRA_USERNAME = "cassandra";
	private final static String CASSANDRA_PASSWORD = "cassandra";

	private final static String CREATE_USER_AND_GRANT_CQL = "CREATE USER '{{username}}' WITH PASSWORD '{{password}}' NOSUPERUSER;"
			+ "GRANT SELECT ON ALL KEYSPACES TO {{username}};";

	private VaultProperties vaultProperties = Settings.createVaultProperties();
	private VaultClient vaultClient = new VaultClient(vaultProperties);
	private VaultProperties.Cassandra cassandra = vaultProperties.getCassandra();

	/**
	 * Initialize cassandra secret backend.
	 *
	 * @throws Exception
	 */
	@Before
	public void setUp() throws Exception {

		assumeTrue(CanConnect.to(new InetSocketAddress(CASSANDRA_HOST, CASSANDRA_PORT)));

		cassandra.setEnabled(true);
		cassandra.setRole("readonly");

		if (!prepare().hasSecret(cassandra.getBackend())) {
			prepare().mountSecret(cassandra.getBackend());
		}

		Map<String, String> connection = new HashMap<>();
		connection.put("hosts", CASSANDRA_HOST);
		connection.put("username", CASSANDRA_USERNAME);
		connection.put("password", CASSANDRA_PASSWORD);

		prepare().write(String.format("%s/config/connection", cassandra.getBackend()),
				connection);

		prepare().write(
				String.format("%s/roles/%s", cassandra.getBackend(), cassandra.getRole()),
				Collections.singletonMap("creation_cql", CREATE_USER_AND_GRANT_CQL));

		vaultClient.setRest(new RestTemplate());
	}

	@Test
	public void shouldCreateCredentialsCorrectly() throws Exception {

		Map<String, String> secretProperties = vaultClient.read(database(cassandra),
				Settings.token());

		assertThat(secretProperties).containsKeys("spring.data.cassandra.username",
				"spring.data.cassandra.password");
	}
}
