package io.kestra.plugin.looker.queries;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;

import io.kestra.core.context.TestRunContextFactory;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.common.FetchOutput;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContext;

import jakarta.inject.Inject;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@WireMockTest
@KestraTest
class LookerQueryTasksTest {
    private static final String LOGIN_PATH = "/api/4.0/login";
    private static final String LOGOUT_PATH = "/api/4.0/logout";
    private static final String TOKEN = "query-test-token";

    @Inject
    private TestRunContextFactory runContextFactory;

    @Test
    void shouldRunInlineQueryAndFetchRows(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubAuthentication();
        stubFor(post(urlPathEqualTo("/api/4.0/queries/run/json"))
            .withHeader("Authorization", equalTo("token " + TOKEN))
            .willReturn(aResponse().withStatus(200).withBody("""
                [{"orders.total_revenue":120.5},{"orders.total_revenue":87.0}]
                """)));

        Run task = Run.builder()
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .clientId(Property.ofValue("client-id"))
            .clientSecret(Property.ofValue("client-secret"))
            .model(Property.ofValue("ecommerce"))
            .view(Property.ofValue("orders"))
            .fields(Property.ofValue(List.of("orders.total_revenue")))
            .filters(Property.ofValue(Map.of("orders.created_date", "7 days")))
            .sorts(Property.ofValue(List.of("orders.created_date desc")))
            .limit(Property.ofValue("500"))
            .resultFormat(Property.ofValue(ResultFormat.JSON))
            .fetchType(Property.ofValue(FetchType.FETCH))
            .build();

        FetchOutput output = task.run(context());

        assertThat(output.getSize()).isEqualTo(2L);
        assertThat(output.getRows()).hasSize(2);
        verify(postRequestedFor(urlPathEqualTo("/api/4.0/queries/run/json"))
            .withHeader("Authorization", equalTo("token " + TOKEN))
            .withRequestBody(equalToJson("""
                {
                  "model": "ecommerce",
                  "view": "orders",
                  "fields": ["orders.total_revenue"],
                  "filters": {"orders.created_date": "7 days"},
                  "sorts": ["orders.created_date desc"],
                  "limit": "500"
                }
                """)));
    }

    @Test
    void shouldReturnFirstRowForFetchOne(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubAuthentication();
        stubFor(post(urlPathEqualTo("/api/4.0/queries/run/json"))
            .willReturn(aResponse().withStatus(200).withBody("""
                [{"orders.id":42},{"orders.id":43}]
                """)));

        FetchOutput output = runInlineQuery(wireMockRuntimeInfo, ResultFormat.JSON, FetchType.FETCH_ONE);

        assertThat(output.getRow()).containsEntry("orders.id", 42);
        assertThat(output.getRows()).isNull();
        assertThat(output.getSize()).isEqualTo(2L);
    }

    @Test
    void shouldReturnEmptyRowForFetchOneWhenNoResults(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubAuthentication();
        stubFor(post(urlPathEqualTo("/api/4.0/queries/run/json"))
            .willReturn(aResponse().withStatus(200).withBody("[]")));

        FetchOutput output = runInlineQuery(wireMockRuntimeInfo, ResultFormat.JSON, FetchType.FETCH_ONE);

        assertThat(output.getRow()).isNull();
        assertThat(output.getSize()).isZero();
    }

    @Test
    void shouldStoreOriginalCsvResponseBytes(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubAuthentication();
        String csv = "name,total\nWidget,12.5\n";
        stubFor(post(urlPathEqualTo("/api/4.0/queries/run/csv"))
            .withHeader("Authorization", equalTo("token " + TOKEN))
            .willReturn(aResponse().withStatus(200).withBody(csv)));
        RunContext runContext = context();

        FetchOutput output = inlineQuery(wireMockRuntimeInfo, ResultFormat.CSV, FetchType.STORE).run(runContext);

        assertThat(output.getUri()).isNotNull();
        try (InputStream stored = runContext.storage().getFile(output.getUri())) {
            assertThat(stored.readAllBytes()).isEqualTo(csv.getBytes(StandardCharsets.UTF_8));
        }
        verify(postRequestedFor(urlPathEqualTo("/api/4.0/queries/run/csv"))
            .withHeader("Authorization", equalTo("token " + TOKEN)));
    }

    @Test
    void shouldRejectCsvFetchModes(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubAuthentication();
        stubFor(post(urlPathEqualTo("/api/4.0/queries/run/csv"))
            .willReturn(aResponse().withStatus(200).withBody("name,total\nWidget,12.5\n")));

        assertThatThrownBy(() -> inlineQuery(wireMockRuntimeInfo, ResultFormat.CSV, FetchType.FETCH).run(context()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("fetchType=STORE");
    }

    @Test
    void shouldRejectMalformedInlineQueryResponse(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubAuthentication();
        stubFor(post(urlPathEqualTo("/api/4.0/queries/run/json"))
            .willReturn(aResponse().withStatus(200).withBody("{")));

        assertThatThrownBy(() -> inlineQuery(wireMockRuntimeInfo, ResultFormat.JSON, FetchType.FETCH).run(context()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("not a valid JSON array");
    }

    @Test
    void shouldRejectInlineQueryApiFailure(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubAuthentication();
        stubFor(post(urlPathEqualTo("/api/4.0/queries/run/json"))
            .willReturn(aResponse().withStatus(503).withBody("database unavailable")));

        assertThatThrownBy(() -> inlineQuery(wireMockRuntimeInfo, ResultFormat.JSON, FetchType.FETCH).run(context()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("HTTP status 503")
            .hasMessageNotContaining("database unavailable");
    }

    @Test
    void shouldRunSqlRunnerQuery(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubAuthentication();
        stubFor(post(urlPathEqualTo("/api/4.0/sql_queries"))
            .withHeader("Authorization", equalTo("token " + TOKEN))
            .willReturn(aResponse().withStatus(200).withBody("{\"slug\":\"sql-query-slug\"}")));
        stubFor(post(urlPathEqualTo("/api/4.0/sql_queries/sql-query-slug/run/json"))
            .withHeader("Authorization", equalTo("token " + TOKEN))
            .willReturn(aResponse().withStatus(200).withBody("[{\"count\":3}]")));

        SqlRun task = SqlRun.builder()
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .clientId(Property.ofValue("client-id"))
            .clientSecret(Property.ofValue("client-secret"))
            .connectionName(Property.ofValue("warehouse"))
            .sql(Property.ofValue("SELECT COUNT(*) AS count FROM orders"))
            .resultFormat(Property.ofValue(ResultFormat.JSON))
            .fetchType(Property.ofValue(FetchType.FETCH))
            .build();

        FetchOutput output = task.run(context());

        assertThat(output.getRows()).containsExactly(Map.of("count", 3));
        assertThat(output.getSize()).isEqualTo(1L);
        verify(postRequestedFor(urlPathEqualTo("/api/4.0/sql_queries"))
            .withHeader("Authorization", equalTo("token " + TOKEN))
            .withRequestBody(equalToJson("""
                {
                  "connection_name": "warehouse",
                  "sql": "SELECT COUNT(*) AS count FROM orders"
                }
                """)));
        verify(postRequestedFor(urlPathEqualTo("/api/4.0/sql_queries/sql-query-slug/run/json"))
            .withHeader("Authorization", equalTo("token " + TOKEN)));
    }

    @Test
    void shouldStoreSqlRunnerCsvResult(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubAuthentication();
        stubFor(post(urlPathEqualTo("/api/4.0/sql_queries"))
            .willReturn(aResponse().withStatus(200).withBody("{\"slug\":\"sql-query-slug\"}")));
        stubFor(post(urlPathEqualTo("/api/4.0/sql_queries/sql-query-slug/run/csv"))
            .willReturn(aResponse().withStatus(200).withBody("count\n3\n")));

        SqlRun task = SqlRun.builder()
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .clientId(Property.ofValue("client-id"))
            .clientSecret(Property.ofValue("client-secret"))
            .connectionName(Property.ofValue("warehouse"))
            .sql(Property.ofValue("SELECT COUNT(*) FROM orders"))
            .resultFormat(Property.ofValue(ResultFormat.CSV))
            .fetchType(Property.ofValue(FetchType.STORE))
            .build();

        FetchOutput output = task.run(context());

        assertThat(output.getUri()).isNotNull();
        verify(postRequestedFor(urlPathEqualTo("/api/4.0/sql_queries/sql-query-slug/run/csv"))
            .withHeader("Authorization", equalTo("token " + TOKEN)));
    }

    @Test
    void shouldRejectMalformedSqlQuerySlugResponse(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubAuthentication();
        stubFor(post(urlPathEqualTo("/api/4.0/sql_queries"))
            .willReturn(aResponse().withStatus(200).withBody("{")));

        assertThatThrownBy(() -> sqlQuery(wireMockRuntimeInfo).run(context()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("not valid JSON");
    }

    @Test
    void shouldRejectSqlRunnerApiFailure(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubAuthentication();
        stubFor(post(urlPathEqualTo("/api/4.0/sql_queries"))
            .willReturn(aResponse().withStatus(403).withBody("forbidden")));

        assertThatThrownBy(() -> sqlQuery(wireMockRuntimeInfo).run(context()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("HTTP status 403")
            .hasMessageNotContaining("forbidden");
    }

    private FetchOutput runInlineQuery(WireMockRuntimeInfo wireMockRuntimeInfo, ResultFormat resultFormat, FetchType fetchType) throws Exception {
        return inlineQuery(wireMockRuntimeInfo, resultFormat, fetchType).run(context());
    }

    private Run inlineQuery(WireMockRuntimeInfo wireMockRuntimeInfo, ResultFormat resultFormat, FetchType fetchType) {
        return Run.builder()
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .clientId(Property.ofValue("client-id"))
            .clientSecret(Property.ofValue("client-secret"))
            .model(Property.ofValue("ecommerce"))
            .view(Property.ofValue("orders"))
            .fields(Property.ofValue(List.of("orders.id")))
            .resultFormat(Property.ofValue(resultFormat))
            .fetchType(Property.ofValue(fetchType))
            .build();
    }

    private SqlRun sqlQuery(WireMockRuntimeInfo wireMockRuntimeInfo) {
        return SqlRun.builder()
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .clientId(Property.ofValue("client-id"))
            .clientSecret(Property.ofValue("client-secret"))
            .connectionName(Property.ofValue("warehouse"))
            .sql(Property.ofValue("SELECT 1"))
            .build();
    }

    private RunContext context() {
        return this.runContextFactory.of("looker-query-test");
    }

    private static void stubAuthentication() {
        stubFor(post(urlPathEqualTo(LOGIN_PATH))
            .willReturn(aResponse().withStatus(200).withBody("{\"access_token\":\"" + TOKEN + "\"}")));
        stubFor(delete(urlPathEqualTo(LOGOUT_PATH))
            .withHeader("Authorization", equalTo("token " + TOKEN))
            .willReturn(aResponse().withStatus(204)));
    }
}
