package com.banking.account_service.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
public class AccountEventConsumer {
    public void consumeTransactionCompleted (@Payload Map<String, Object> payload){

    }
}
