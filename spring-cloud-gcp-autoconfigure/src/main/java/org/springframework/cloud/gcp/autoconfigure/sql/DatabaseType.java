/*
 * Copyright 2017-2018 the original author or authors.
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

package org.springframework.cloud.gcp.autoconfigure.sql;

/**
 * Enum class containing MySQL and Postgresql constants.
 *
 * @author João André Martins
 * @author Chengyuan Zhao
 */
public enum DatabaseType {
	/**
	 * MySQL constants.
	 */
	MYSQL("com.mysql.cj.jdbc.Driver", "jdbc:mysql://google/%s?"
			+ "socketFactory=com.google.cloud.sql.mysql.SocketFactory"
			+ "&cloudSqlInstance=%s"),

	/**
	 * Postgresql constants.
	 */
	POSTGRESQL("org.postgresql.Driver", "jdbc:postgresql://google/%s?"
			+ "socketFactory=com.google.cloud.sql.postgres.SocketFactory"
			+ "&cloudSqlInstance=%s");

	private final String jdbcDriverName;

	private final String jdbcUrlTemplate;

	DatabaseType(String jdbcDriverName, String jdbcUrlTemplate) {
		this.jdbcDriverName = jdbcDriverName;
		this.jdbcUrlTemplate = jdbcUrlTemplate;
	}

	public String getJdbcDriverName() {
		return this.jdbcDriverName;
	}

	public String getJdbcUrlTemplate() {
		return this.jdbcUrlTemplate;
	}
}
