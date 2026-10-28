package com.fintech.walletapi.repository;

import com.fintech.walletapi.entity.TransferRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Data access for the {@link TransferRecord} ledger.
 */
@Repository
public interface TransferRecordRepository extends JpaRepository<TransferRecord, Long> {

    /**
     * Finds every ledger entry where an account was the sender OR the receiver.
     * Spring builds: {@code WHERE source_account_id = ? OR destination_account_id = ?}.
     *
     * <p><b>Pre-condition:</b> none. To get one account's full history pass the same id twice.</p>
     * <p><b>Post-condition:</b> no data is changed.</p>
     *
     * @param sourceAccountId      id to match against the sender column
     * @param destinationAccountId id to match against the receiver column
     * @return matching ledger entries (empty list if none)
     */
    List<TransferRecord> findBySourceAccountIdOrDestinationAccountId(Long sourceAccountId, Long destinationAccountId);
}
