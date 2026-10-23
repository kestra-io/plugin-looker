package io.kestra.plugin.looker.schedules;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;

import io.kestra.core.context.TestRunContextFactory;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;

import jakarta.inject.Inject;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@WireMockTest
@KestraTest
class LookerScheduleTasksTest {
    private static final String LOGIN_PATH = "/api/4.0/login";
    private static final String LOGOUT_PATH = "/api/4.0/logout";
    private static final String TOKEN = "schedule-test-token";

    @Inject
    private TestRunContextFactory runContextFactory;

    @Test
    void shouldRunScheduledPlanById(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubAuthentication();
        stubFor(post(urlPathEqualTo("/api/4.0/scheduled_plans/105/run_once"))
            .withHeader("Authorization", equalTo("token " + TOKEN))
            .willReturn(aResponse().withStatus(200).withBody("{\"id\":\"105\",\"name\":\"Weekly Report\",\"status\":\"success\"}")));

        RunOnce task = RunOnce.builder()
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .clientId(Property.ofValue("client-id"))
            .clientSecret(Property.ofValue("client-secret"))
            .scheduledPlanId(Property.ofValue("105"))
            .build();

        RunOnce.Output output = task.run(context());

        assertThat(output.getResult()).containsEntry("id", "105");
        assertThat(output.getResult()).containsEntry("name", "Weekly Report");
        verify(postRequestedFor(urlPathEqualTo("/api/4.0/scheduled_plans/105/run_once"))
            .withHeader("Authorization", equalTo("token " + TOKEN)));
    }

    @Test
    void shouldRunAdHocScheduledPlan(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubAuthentication();
        stubFor(post(urlPathEqualTo("/api/4.0/scheduled_plans/run_once"))
            .withHeader("Authorization", equalTo("token " + TOKEN))
            .willReturn(aResponse().withStatus(200).withBody("{\"name\":\"AdHoc Plan\",\"status\":\"enqueued\"}")));

        RunOnce task = RunOnce.builder()
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .clientId(Property.ofValue("client-id"))
            .clientSecret(Property.ofValue("client-secret"))
            .scheduledPlan(Property.ofValue(Map.of("name", "AdHoc Plan", "look_id", 42)))
            .build();

        RunOnce.Output output = task.run(context());

        assertThat(output.getResult()).containsEntry("name", "AdHoc Plan");
        verify(postRequestedFor(urlPathEqualTo("/api/4.0/scheduled_plans/run_once"))
            .withHeader("Authorization", equalTo("token " + TOKEN)));
    }

    @Test
    void shouldRejectMalformedScheduledPlanResponse(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubAuthentication();
        stubFor(post(urlPathEqualTo("/api/4.0/scheduled_plans/105/run_once"))
            .willReturn(aResponse().withStatus(200).withBody("{")));

        RunOnce task = RunOnce.builder()
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .clientId(Property.ofValue("client-id"))
            .clientSecret(Property.ofValue("client-secret"))
            .scheduledPlanId(Property.ofValue("105"))
            .build();

        assertThatThrownBy(() -> task.run(context()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("not valid JSON");
    }

    private RunContext context() {
        return this.runContextFactory.of("looker-schedule-test");
    }

    private static void stubAuthentication() {
        stubFor(post(urlPathEqualTo(LOGIN_PATH))
            .willReturn(aResponse().withStatus(200).withBody("{\"access_token\":\"" + TOKEN + "\"}")));
        stubFor(delete(urlPathEqualTo(LOGOUT_PATH))
            .withHeader("Authorization", equalTo("token " + TOKEN))
            .willReturn(aResponse().withStatus(204)));
    }
}
