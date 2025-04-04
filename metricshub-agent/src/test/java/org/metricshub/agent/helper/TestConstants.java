package org.metricshub.agent.helper;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TestConstants {

	public static final String GRAFANA_HEALTH_SOURCE_KEY = "grafanaHealth";
	public static final String GRAFANA_HEALTH_SOURCE_REF = "${source::monitors.grafana.simple.sources.grafanaHealth}";
	public static final String GRAFANA_MONITOR_JOB_KEY = "grafana";
	public static final String GRAFANA_DB_STATE_METRIC = "grafana.db.state";
	public static final String HTTP_SERVICE_URL = "https://hws-demo.sentrysoftware.com/api/health";
	public static final String HTTP_ACCEPT_HEADER = "Accept: application/json";
	public static final String SERVICE_VERSION = "service.version";
	public static final String ID_ATTRIBUTE_KEY = "id";
	public static final String HTTP_KEY_TYPE = "http";
	public static final String SENTRY_PARIS_SITE_VALUE = "Sentry-Paris";
	public static final String SENTRY_OTTAWA_SITE_VALUE = "Sentry-Ottawa";
	public static final String GRAFANA_SERVICE_RESOURCE_CONFIG_KEY = "grafana-service";
	public static final String SITE_ATTRIBUTE_KEY = "site";
	public static final String SERVICE_VERSION_ATTRIBUTE_KEY = "service.version";
	public static final String SENTRY_PARIS_RESOURCE_GROUP_KEY = "sentry-paris";
	public static final String SENTRY_OTTAWA_RESOURCE_GROUP_KEY = "sentry-ottawa";
	public static final String SERVER_1_RESOURCE_GROUP_KEY = "server-1";
	public static final String TEST_CONFIG_FILE_PATH = "src/test/resources/config/metricshub.yaml";
	public static final String TOP_LEVEL_RESOURCES_CONFIG_PATH = "src/test/resources/config/top-level-resource-test.yaml";
	public static final String COMPANY_ATTRIBUTE_VALUE = "Sentry Software";
	public static final String COMPANY_ATTRIBUTE_KEY = "company";
	public static final String HOST_TYPE_ATTRIBUTE_KEY = "host.type";
	public static final String OS_TYPE_ATTRIBUTE_KEY = "os.type";
	public static final String HOST_ID_ATTRIBUTE_KEY = "host.id";
	public static final String OS_LINUX = "linux";
	public static final String COMPUTE_HOST_TYPE = "compute";
	public static final String HOSTNAME = "host.my.domain.net";
	public static final String METRIC_DESCRIPTION = "Description";
	public static final String METRIC_INSTRUMENTATION_SCOPE = "org.metricshub.hw.metric";
	public static final String HW_METRIC = "hw.metric";
	public static final String METRIC_UNIT = "{unit}";
	public static final String METRIC_STATE_OK = "ok";
	public static final String METRIC_STATE_DEGRADED = "degraded";
	public static final String METRIC_STATE_FAILED = "failed";
	public static final AttributeKey<String> OTEL_COMPANY_ATTRIBUTE_KEY = AttributeKey.stringKey(COMPANY_ATTRIBUTE_KEY);
	public static final Attributes ATTRIBUTES = Attributes.of(OTEL_COMPANY_ATTRIBUTE_KEY, COMPANY_ATTRIBUTE_VALUE);
	public static final String COPY_SOURCE_REF = "${source::monitors.grafana.simple.sources.copySource}";
	public static final String COPY_SOURCE_KEY = "copySource";
	public static final String COPY_SOURCE_TYPE = "copy";
}
