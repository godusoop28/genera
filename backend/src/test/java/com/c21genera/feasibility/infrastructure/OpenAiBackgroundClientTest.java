package com.c21genera.feasibility.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.c21genera.feasibility.infrastructure.OpenAiBackgroundClient.ResponseState;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class OpenAiBackgroundClientTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void readsTextAndWebSearchCitationsOfACompletedResponse() throws Exception {
    ResponseState state =
        OpenAiBackgroundClient.parse(
            mapper.readTree(
                """
                {"id":"resp_1","status":"completed","output":[
                  {"type":"web_search_call","status":"completed"},
                  {"type":"reasoning","summary":[]},
                  {"type":"message","content":[
                    {"type":"output_text","text":"## 1. FACTIBILIDAD","annotations":[
                      {"type":"url_citation","url":"https://www.inmuebles24.com/a","title":"Casa A"},
                      {"type":"url_citation","url":"https://www.inmuebles24.com/a","title":"Casa A"}]},
                    {"type":"output_text","text":" COMPLETA","annotations":[]}]}]}
                """));
    assertThat(state.completed()).isTrue();
    assertThat(state.text()).isEqualTo("## 1. FACTIBILIDAD COMPLETA");
    assertThat(state.citations()).hasSize(1);
  }

  @Test
  void reportsWhyAResponseIsIncomplete() throws Exception {
    ResponseState state =
        OpenAiBackgroundClient.parse(mapper.readTree("{\"status\":\"incomplete\",\"incomplete_details\":{\"reason\":\"max_output_tokens\"},\"output\":[]}"));
    assertThat(state.finished()).isTrue();
    assertThat(state.completed()).isFalse();
    assertThat(state.error()).contains("max_output_tokens");
    assertThat(OpenAiBackgroundClient.parse(mapper.readTree("{\"status\":\"queued\"}")).finished()).isFalse();
  }
}
