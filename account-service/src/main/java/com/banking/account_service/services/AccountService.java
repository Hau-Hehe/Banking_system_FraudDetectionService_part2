package com.banking.account_service.services;

import com.banking.account_service.dto.AccountResponse;
import com.banking.account_service.dto.CreatedAccountRequest;
import com.banking.account_service.entity.Account;
import com.banking.account_service.entity.AccountStatus;
import com.banking.account_service.entity.AccountType;
import com.banking.account_service.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.security.SecureRandom;

@Service
@Slf4j
@RequiredArgsConstructor
public class AccountService {
    private final AccountRepository accountRepository;
    private static SecureRandom secureRandom = new SecureRandom();

    public AccountResponse createAccount(CreatedAccountRequest request) {
        log.info("Creating account for: {}", request.getEmail());

        if(accountRepository.existsByEmail(request.getEmail())){
            throw new RuntimeException("Account already exists for email: "+request.getEmail());
        }
        Account account = Account.builder()
                .accountHolderName(request.getAccountHolderName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .accountType(request.getAccountType())
                .status(AccountStatus.ACTIVE)
                .balances(request.getInitialDeposit())
                .accountNumber(generateAccountNumber())
                .dailyTractionalLimit(request.getAccountType() == AccountType.SAVINGS ? new BigDecimal("100000") : new BigDecimal("500000"))
                .build();
        Account savedAccount = accountRepository.save(account);
        log.info("Account created: {}", savedAccount.getAccountNumber());
        return mapToResponse(savedAccount);
    }

    public AccountResponse getAccount(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Account not found"));
        return mapToResponse(account);
    }

    private String generateAccountNumber() {
        String accountNumber;
        do{
        long number = secureRandom.nextLong(1_000_000_000_000L);
        accountNumber = String.format("%012d", number);
        }while(accountRepository.existsByAccountNumber(accountNumber));
        return accountNumber;
    }

    private AccountResponse mapToResponse(Account account) {
        AccountResponse response = AccountResponse.builder()
                .id(account.getId())
                .accountNumber(account.getAccountNumber())
                .accountHolderName(account.getAccountHolderName())
                .email(account.getEmail())
                .phone(account.getPhone())
                .accountType(account.getAccountType())
                .status(account.getStatus())
                .balance(account.getBalances())
                .dailyTransactionLimit(account.getDailyTractionalLimit())
                .createdAt(account.getCreatedAt())
                .build();
        return response;
    }
}
