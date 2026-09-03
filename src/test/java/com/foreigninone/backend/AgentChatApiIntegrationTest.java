package com.foreigninone.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foreigninone.backend.domain.agent.dto.AgentChatRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class AgentChatApiIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    @DisplayName("OpenAI 키 미설정 시 ok:false 로 폴백 응답 (POST /api/agent/chat)")
    void testChatFallsBackWhenNotConfigured() throws Exception {
        AgentChatRequest request = AgentChatRequest.builder()
                .question("이번 달 월급이 왜 달라?")
                .locale("ko")
                .build();

        mockMvc.perform(post("/api/agent/chat")
                        .header("X-Demo-User-Id", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.ok").value(false))
                .andExpect(jsonPath("$.data.text").doesNotExist());
    }

    @Test
    @DisplayName("question 누락 시 400 검증 오류 (POST /api/agent/chat)")
    void testChatRejectsBlankQuestion() throws Exception {
        AgentChatRequest request = AgentChatRequest.builder().question("").build();

        mockMvc.perform(post("/api/agent/chat")
                        .header("X-Demo-User-Id", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
