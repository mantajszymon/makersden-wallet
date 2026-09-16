package com.sm.makersden.api.controller;

import com.sm.makersden.api.dto.AccountResponse;
import com.sm.makersden.api.dto.CreateAccountRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void creatingAnAccountReturnsCreatedWithLocationAndBody() throws Exception {
        // given
        CreateAccountRequest request = new CreateAccountRequest(new BigDecimal("100.00"));

        // when
        MvcResult result = mockMvc.perform(post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        // then
        AccountResponse account = objectMapper.readValue(result.getResponse().getContentAsString(), AccountResponse.class);
        assertThat(account.id()).isNotBlank();
        assertThat(account.balance()).isEqualByComparingTo("100.00");
        assertThat(result.getResponse().getHeader("Location")).isEqualTo("/accounts/" + account.id());
    }

    @Test
    void creatingAnAccountWithAZeroOpeningBalanceSucceeds() throws Exception {
        // given
        CreateAccountRequest request = new CreateAccountRequest(BigDecimal.ZERO);

        // when
        MvcResult result = mockMvc.perform(post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        // then
        AccountResponse account = objectMapper.readValue(result.getResponse().getContentAsString(), AccountResponse.class);
        assertThat(account.balance()).isEqualByComparingTo("0.00");
    }

    @ParameterizedTest(name = "rejects opening balance \"{0}\"")
    @ValueSource(strings = {"-0.01", "10.005"})
    void creatingAnAccountWithAnInvalidOpeningBalanceReturnsBadRequest(String invalidBalance) throws Exception {
        // given
        CreateAccountRequest request = new CreateAccountRequest(new BigDecimal(invalidBalance));

        // when / then
        mockMvc.perform(post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void creatingAnAccountWithAWholeNumberOpeningBalancePadsToTwoDecimalPlaces() throws Exception {
        // given
        String requestBody = "{\"openingBalance\":100}";

        // when
        MvcResult result = mockMvc.perform(post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn();

        // then
        AccountResponse account = objectMapper.readValue(result.getResponse().getContentAsString(), AccountResponse.class);
        assertThat(account.balance()).isEqualByComparingTo("100.00");
    }

    @Test
    void creatingAnAccountWithAMissingOpeningBalanceReturnsBadRequest() throws Exception {
        // given
        CreateAccountRequest request = new CreateAccountRequest(null);

        // when / then
        mockMvc.perform(post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void gettingAnExistingAccountReturnsItsBalance() throws Exception {
        // given
        CreateAccountRequest createRequest = new CreateAccountRequest(new BigDecimal("42.00"));
        String createResponseBody = mockMvc.perform(post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andReturn().getResponse().getContentAsString();
        AccountResponse created = objectMapper.readValue(createResponseBody, AccountResponse.class);

        // when
        String getResponseBody = mockMvc.perform(get("/accounts/{id}", created.id()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // then
        AccountResponse fetched = objectMapper.readValue(getResponseBody, AccountResponse.class);
        assertThat(fetched.id()).isEqualTo(created.id());
        assertThat(fetched.balance()).isEqualByComparingTo("42.00");
    }

    @Test
    void gettingAnUnknownAccountReturnsNotFound() throws Exception {
        // when / then
        mockMvc.perform(get("/accounts/{id}", "does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }
}
