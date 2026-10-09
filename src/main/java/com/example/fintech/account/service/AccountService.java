package com.example.fintech.account.service;

import com.example.fintech.account.entity.Account;
import com.example.fintech.account.dto.AccountResponse;
import com.example.fintech.account.exception.AccountNotFoundException;
import com.example.fintech.account.repository.AccountRepository;
import com.example.fintech.payment.exception.AccountAccessDeniedException;
import com.example.fintech.user.entity.User;
import com.example.fintech.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class AccountService {

    private static final String ACCOUNTS_CACHE = "accounts";
    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final AccountRepository accountRepository;
    private final UserService userService;
    private final CacheManager cacheManager;

    public AccountService(
            AccountRepository accountRepository,
            UserService userService,
            CacheManager cacheManager
    ) {
        this.accountRepository = accountRepository;
        this.userService = userService;
        this.cacheManager = cacheManager;
    }

    public Account getById(UUID id) {
        return accountRepository.findById(id)
                .orElseThrow(() ->
                        new AccountNotFoundException("Account not found"));
    }

    public UUID getOwnerId(UUID accountId) {
        return accountRepository.findOwnerIdByAccountId(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found"));
    }

    @Transactional
    public Account getByIdForUpdate(UUID id) {
        return accountRepository.findByIdForUpdate(id)
                .orElseThrow(() ->
                        new AccountNotFoundException("Account not found"));
    }

    @Transactional
    public Account getByUserIdAndCurrencyForUpdate(
            UUID userId,
            String currency
    ) {
        return accountRepository
                .findByUserIdAndCurrencyForUpdate(userId, currency)
                .orElseThrow(() ->
                        new AccountNotFoundException(
                                "Source account not found"
                        ));
    }

    @Transactional
    public Account deposit(UUID userId, UUID accountId, BigDecimal amount) {
        Account account = getByIdForUpdate(accountId);

        if (!account.getUser().getId().equals(userId)) {
            throw new AccountAccessDeniedException(
                    "You do not have access to this account"
            );
        }

        account.setBalance(
                account.getBalance().add(amount)
        );
        invalidateAccountListsAfterCommit(userId);

        return account;
    }

    @Cacheable(cacheNames = ACCOUNTS_CACHE, key = "#userId")
    public List<AccountResponse> getAccountsForUser(UUID userId) {
        return accountRepository.findAllByUserId(userId).stream()
                .map(account -> new AccountResponse(
                        account.getId(),
                        account.getCurrency(),
                        account.getBalance()
                ))
                .toList();
    }

    @CacheEvict(cacheNames = ACCOUNTS_CACHE, key = "#userId")
    public Account createAccount(
            UUID userId,
            String currency
    ) {
        User user = userService.getById(userId);

        Account account = new Account(
                user,
                currency,
                BigDecimal.ZERO
        );

        return accountRepository.save(account);
    }

    public void invalidateAccountListsAfterCommit(UUID... userIds) {
        Runnable eviction = () -> {
            Cache cache = cacheManager.getCache(ACCOUNTS_CACHE);
            if (cache == null) {
                return;
            }
            for (UUID userId : userIds) {
                try {
                    cache.evict(userId);
                } catch (RuntimeException exception) {
                    log.warn("Cache eviction failed for accounts and user {}", userId, exception);
                }
            }
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            eviction.run();
                        }
                    }
            );
        } else {
            eviction.run();
        }
    }
}
