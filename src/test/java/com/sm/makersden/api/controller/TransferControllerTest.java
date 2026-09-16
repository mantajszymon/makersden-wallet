package com.sm.makersden.api.controller;

import com.sm.makersden.api.dto.AccountResponse;
import com.sm.makersden.api.dto.CreateAccountRequest;
import com.sm.makersden.api.dto.TransferRequest;
import com.sm.makersden.support.ConcurrentTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TransferControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void transferMovesMoneyBetweenAccounts() throws Exception {
        // given
        String fromAccountId = createAccount(new BigDecimal("100.00"));
        String toAccountId = createAccount(new BigDecimal("10.00"));

        // when
        postTransfer(fromAccountId, toAccountId, new BigDecimal("25.00"), newIdempotencyKey())
                .andExpect(status().isNoContent());

        // then
        assertThat(getBalance(fromAccountId)).isEqualByComparingTo("75.00");
        assertThat(getBalance(toAccountId)).isEqualByComparingTo("35.00");
    }

    @Test
    void repeatingTheSameIdempotencyKeyDoesNotTransferAgain() throws Exception {
        // given
        String fromAccountId = createAccount(new BigDecimal("100.00"));
        String toAccountId = createAccount(new BigDecimal("10.00"));
        String idempotencyKey = newIdempotencyKey();
        postTransfer(fromAccountId, toAccountId, new BigDecimal("25.00"), idempotencyKey)
                .andExpect(status().isNoContent());

        // when
        postTransfer(fromAccountId, toAccountId, new BigDecimal("25.00"), idempotencyKey)
                .andExpect(status().isNoContent());

        // then
        assertThat(getBalance(fromAccountId)).isEqualByComparingTo("75.00");
        assertThat(getBalance(toAccountId)).isEqualByComparingTo("35.00");
    }

    @Test
    void repeatingTheSameIdempotencyKeyWithADifferentPayloadStillAppliesOnlyTheOriginalTransfer() throws Exception {
        // given
        String fromAccountId = createAccount(new BigDecimal("100.00"));
        String originalDestination = createAccount(new BigDecimal("10.00"));
        String differentDestination = createAccount(BigDecimal.ZERO);
        String idempotencyKey = newIdempotencyKey();
        postTransfer(fromAccountId, originalDestination, new BigDecimal("25.00"), idempotencyKey)
                .andExpect(status().isNoContent());

        // when: same key, different destination and amount
        postTransfer(fromAccountId, differentDestination, new BigDecimal("50.00"), idempotencyKey)
                .andExpect(status().isNoContent());

        // then: the second call's payload was ignored — only the original transfer applied
        assertThat(getBalance(fromAccountId)).isEqualByComparingTo("75.00");
        assertThat(getBalance(originalDestination)).isEqualByComparingTo("35.00");
        assertThat(getBalance(differentDestination)).isEqualByComparingTo("0.00");
    }

    @Test
    void transferWithAWholeNumberAmountPadsToTwoDecimalPlaces() throws Exception {
        // given
        String fromAccountId = createAccount(new BigDecimal("100.00"));
        String toAccountId = createAccount(BigDecimal.ZERO);
        String requestBody = "{\"fromAccountId\":\"" + fromAccountId + "\",\"toAccountId\":\"" + toAccountId
                + "\",\"amount\":25}";

        // when
        mockMvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", newIdempotencyKey())
                        .content(requestBody))
                .andExpect(status().isNoContent());

        // then
        assertThat(getBalance(fromAccountId)).isEqualByComparingTo("75.00");
        assertThat(getBalance(toAccountId)).isEqualByComparingTo("25.00");
    }

    @Test
    void missingIdempotencyKeyHeaderReturnsBadRequest() throws Exception {
        // given
        String fromAccountId = createAccount(new BigDecimal("100.00"));
        String toAccountId = createAccount(BigDecimal.ZERO);
        TransferRequest request = new TransferRequest(fromAccountId, toAccountId, new BigDecimal("1.00"));

        // when / then
        mockMvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void blankIdempotencyKeyHeaderReturnsBadRequest() throws Exception {
        // given
        String fromAccountId = createAccount(new BigDecimal("100.00"));
        String toAccountId = createAccount(BigDecimal.ZERO);
        TransferRequest request = new TransferRequest(fromAccountId, toAccountId, new BigDecimal("1.00"));

        // when / then
        mockMvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Idempotency-Key must not be blank"));
    }

    @Test
    void missingFromAccountIdReturnsBadRequest() throws Exception {
        // given
        String toAccountId = createAccount(BigDecimal.ZERO);
        TransferRequest request = new TransferRequest(null, toAccountId, new BigDecimal("1.00"));

        // when / then
        mockMvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", newIdempotencyKey())
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("fromAccountId must not be blank"));
    }

    @Test
    void missingToAccountIdReturnsBadRequest() throws Exception {
        // given
        String fromAccountId = createAccount(new BigDecimal("10.00"));
        TransferRequest request = new TransferRequest(fromAccountId, null, new BigDecimal("1.00"));

        // when / then
        mockMvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", newIdempotencyKey())
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("toAccountId must not be blank"));
    }

    @Test
    void missingAmountReturnsBadRequest() throws Exception {
        // given: MoneyValidation treats a null amount as InvalidAmountException, not an NPE,
        // so this should reach the same ProblemDetail-shaped 400 as any other invalid amount
        String fromAccountId = createAccount(new BigDecimal("10.00"));
        String toAccountId = createAccount(BigDecimal.ZERO);
        TransferRequest request = new TransferRequest(fromAccountId, toAccountId, null);

        // when / then
        mockMvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", newIdempotencyKey())
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void aValidationFailureDoesNotConsumeTheIdempotencyKeyForALaterRetry() throws Exception {
        // given: a malformed request (blank fromAccountId) is rejected by the controller's own
        // guard, before ever reaching LedgerService/IdempotencyKeyStore — so the key must still
        // be free to use afterwards, unlike a domain failure (e.g. insufficient funds), which
        // *is* claimed because it happens inside the idempotent action itself.
        String toAccountId = createAccount(BigDecimal.ZERO);
        String fromAccountId = createAccount(new BigDecimal("100.00"));
        String idempotencyKey = newIdempotencyKey();
        mockMvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", idempotencyKey)
                        .content(objectMapper.writeValueAsString(new TransferRequest(null, toAccountId, new BigDecimal("1.00")))))
                .andExpect(status().isBadRequest());

        // when: the same key is reused for a well-formed transfer
        postTransfer(fromAccountId, toAccountId, new BigDecimal("25.00"), idempotencyKey)
                .andExpect(status().isNoContent());

        // then: the earlier validation failure did not "use up" the key — this transfer applied
        assertThat(getBalance(fromAccountId)).isEqualByComparingTo("75.00");
        assertThat(getBalance(toAccountId)).isEqualByComparingTo("25.00");
    }

    @Test
    void selfTransferReturnsBadRequest() throws Exception {
        // given
        String accountId = createAccount(new BigDecimal("10.00"));

        // when / then
        postTransfer(accountId, accountId, new BigDecimal("1.00"), newIdempotencyKey())
                .andExpect(status().isBadRequest());
    }

    @Test
    void insufficientFundsReturnsConflict() throws Exception {
        // given
        String fromAccountId = createAccount(new BigDecimal("10.00"));
        String toAccountId = createAccount(BigDecimal.ZERO);

        // when / then
        postTransfer(fromAccountId, toAccountId, new BigDecimal("10.01"), newIdempotencyKey())
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void transferFromAnUnknownAccountReturnsNotFound() throws Exception {
        // given
        String toAccountId = createAccount(BigDecimal.ZERO);

        // when / then
        postTransfer("does-not-exist", toAccountId, new BigDecimal("1.00"), newIdempotencyKey())
                .andExpect(status().isNotFound());
    }

    @Test
    void transferToAnUnknownAccountReturnsNotFound() throws Exception {
        // given
        String fromAccountId = createAccount(new BigDecimal("10.00"));

        // when / then
        postTransfer(fromAccountId, "does-not-exist", new BigDecimal("1.00"), newIdempotencyKey())
                .andExpect(status().isNotFound());
    }

    @Test
    void nonPositiveAmountReturnsBadRequest() throws Exception {
        // given
        String fromAccountId = createAccount(new BigDecimal("10.00"));
        String toAccountId = createAccount(BigDecimal.ZERO);

        // when / then
        postTransfer(fromAccountId, toAccountId, BigDecimal.ZERO, newIdempotencyKey())
                .andExpect(status().isBadRequest());
    }

    @Test
    void moreThanTwoDecimalPlacesReturnsBadRequest() throws Exception {
        // given
        String fromAccountId = createAccount(new BigDecimal("10.00"));
        String toAccountId = createAccount(BigDecimal.ZERO);

        // when / then
        postTransfer(fromAccountId, toAccountId, new BigDecimal("1.005"), newIdempotencyKey())
                .andExpect(status().isBadRequest());
    }

    @Test
    void concurrentRequestsWithTheSameIdempotencyKeyApplyTheTransferExactlyOnce() throws Exception {
        // given
        String fromAccountId = createAccount(new BigDecimal("100.00"));
        String toAccountId = createAccount(BigDecimal.ZERO);
        String sharedIdempotencyKey = newIdempotencyKey();
        int concurrentRetries = 20;
        ExecutorService executor = Executors.newFixedThreadPool(concurrentRetries);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        // when
        try {
            for (int retryIndex = 0; retryIndex < concurrentRetries; retryIndex++) {
                futures.add(executor.submit(() -> {
                    startGate.await();
                    postTransfer(fromAccountId, toAccountId, new BigDecimal("10.00"), sharedIdempotencyKey);
                    return null;
                }));
            }
            startGate.countDown();
            ConcurrentTestSupport.awaitAll(futures);
        } finally {
            executor.shutdown();
        }

        // then
        assertThat(getBalance(fromAccountId)).isEqualByComparingTo("90.00");
        assertThat(getBalance(toAccountId)).isEqualByComparingTo("10.00");
    }

    private String createAccount(BigDecimal openingBalance) throws Exception {
        String responseBody = mockMvc.perform(post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateAccountRequest(openingBalance))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(responseBody, AccountResponse.class).id();
    }

    private BigDecimal getBalance(String accountId) throws Exception {
        String responseBody = mockMvc.perform(get("/accounts/{id}", accountId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(responseBody, AccountResponse.class).balance();
    }

    private ResultActions postTransfer(String fromAccountId, String toAccountId, BigDecimal amount,
                                        String idempotencyKey) throws Exception {
        TransferRequest request = new TransferRequest(fromAccountId, toAccountId, amount);
        return mockMvc.perform(post("/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", idempotencyKey)
                .content(objectMapper.writeValueAsString(request)));
    }

    private static String newIdempotencyKey() {
        return UUID.randomUUID().toString();
    }
}
