package org.example.samplemicroservice.boleto.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.example.samplemicroservice.boleto.application.BoletoFailure;
import org.example.samplemicroservice.boleto.application.IssuanceInput;
import tools.jackson.databind.JsonNode;

record IssueBoletoRequest(String customerId, BigDecimal amount, LocalDate dueDate) {
    static IssueBoletoRequest from(JsonNode json) {
        if (!json.isObject()
                || json.size() != 3
                || !json.path("customerId").isString()
                || !json.path("amount").isNumber()
                || !json.path("dueDate").isString()) {
            throw BoletoFailure.INVALID_REQUEST.exception();
        }
        String date = json.path("dueDate").stringValue();
        if (!date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
            throw BoletoFailure.INVALID_REQUEST.exception();
        }
        return new IssueBoletoRequest(
                json.path("customerId").stringValue(), json.path("amount").decimalValue(), LocalDate.parse(date));
    }

    IssuanceInput toInput() {
        return new IssuanceInput(customerId, amount, dueDate);
    }
}
