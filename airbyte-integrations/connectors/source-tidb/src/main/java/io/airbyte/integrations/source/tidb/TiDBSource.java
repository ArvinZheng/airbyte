/*
 * Copyright (c) 2022 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.integrations.source.tidb;

import static com.mysql.cj.MysqlType.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import com.mysql.cj.MysqlType;
import io.airbyte.commons.json.Jsons;
import io.airbyte.db.factory.DatabaseDriver;
import io.airbyte.db.jdbc.streaming.AdaptiveStreamingQueryConfig;
import io.airbyte.integrations.base.IntegrationRunner;
import io.airbyte.integrations.base.Source;
import io.airbyte.integrations.base.ssh.SshWrappedSource;
import io.airbyte.integrations.source.jdbc.AbstractJdbcSource;
import io.airbyte.protocol.models.CommonField;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TiDBSource extends AbstractJdbcSource<MysqlType> implements Source {

  private static final Logger LOGGER = LoggerFactory.getLogger(TiDBSource.class);
  private final Set<MysqlType> allowedCursorTypes = Set.of(BIT, BOOLEAN, TINYINT, TINYINT_UNSIGNED, SMALLINT,
      SMALLINT_UNSIGNED, MEDIUMINT, MEDIUMINT_UNSIGNED, INT, INT_UNSIGNED, BIGINT, BIGINT_UNSIGNED, FLOAT,
      FLOAT_UNSIGNED, DOUBLE, DOUBLE_UNSIGNED, DECIMAL, DECIMAL_UNSIGNED, DATE, DATETIME, TIMESTAMP, TIME,
      YEAR, CHAR, VARCHAR, TINYTEXT, TEXT, MEDIUMTEXT, LONGTEXT, ENUM, SET, TINYBLOB, BLOB, MEDIUMBLOB,
      LONGBLOB, BINARY, VARBINARY);

  static final String DRIVER_CLASS = DatabaseDriver.MYSQL.getDriverClassName();
  public static final List<String> SSL_PARAMETERS = List.of(
      "useSSL=true",
      "requireSSL=true",
      "verifyServerCertificate=false");

  public static Source sshWrappedSource() {
    return new SshWrappedSource(new TiDBSource(), List.of("host"), List.of("port"));
  }

  public TiDBSource() {
    super(DRIVER_CLASS, AdaptiveStreamingQueryConfig::new, new TiDBSourceOperations());
  }

  @Override
  public JsonNode toDatabaseConfig(final JsonNode config) {
    final StringBuilder jdbcUrl = new StringBuilder(String.format("jdbc:mysql://%s:%s/%s?",
        config.get("host").asText(),
        config.get("port").asInt(),
        config.get("database").asText()));

    if (config.get("jdbc_url_params") != null
        && !config.get("jdbc_url_params").asText().isEmpty()) {
      jdbcUrl.append(config.get("jdbc_url_params").asText()).append("&");
    }

    // only if config ssl and ssl == true, use ssl to connect db
    if (config.has("ssl") && config.get("ssl").asBoolean()) {
      jdbcUrl.append(String.join("&", SSL_PARAMETERS)).append("&");
    }

    final ImmutableMap.Builder<Object, Object> configBuilder = ImmutableMap.builder()
        .put("username", config.get("username").asText())
        .put("jdbc_url", jdbcUrl.toString());

    if (config.has("password")) {
      configBuilder.put("password", config.get("password").asText());
    }

    return Jsons.jsonNode(configBuilder.build());
  }

  @Override
  public Set<String> getExcludedInternalNameSpaces() {
    return Set.of(
        "information_schema",
        "metrics_schema",
        "performance_schema",
        "mysql");
  }

  public static void main(final String[] args) throws Exception {
    final Source source = TiDBSource.sshWrappedSource();
    LOGGER.info("starting source: {}", TiDBSource.class);
    new IntegrationRunner(source).run(args);
    LOGGER.info("completed source: {}", TiDBSource.class);
  }

  @Override
  protected List<String> getCursorFields(List<CommonField<MysqlType>> fields) {
    return fields.stream()
        .filter(field -> allowedCursorTypes.contains(MysqlType.valueOf(field.getType().toString())))
        .map(field -> field.getName())
        .collect(Collectors.toList());
  }

}
