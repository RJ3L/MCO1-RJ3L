package com.fintech.walletapi.repository;

import com.fintech.walletapi.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    List<Account> findByStatus(String status);

    List<Account> findByCustomer_User_Username(String username);

    boolean existsByCustomer_IdAndCurrency(Long customerId, String currency);

    boolean existsByCustomer_Id(Long customerId);
}
