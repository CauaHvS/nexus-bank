package com.nexusbank.payments.domain.port.out;

import com.nexusbank.payments.domain.model.IdempotencyKey;
import com.nexusbank.payments.domain.model.Transfer;
import com.nexusbank.payments.domain.model.TransferId;
import com.nexusbank.payments.domain.model.TransferStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TransferRepository {
    Transfer save(Transfer transfer);
    Optional<Transfer> findById(TransferId id);
    Optional<Transfer> findByIdempotencyKey(IdempotencyKey key);
    List<Transfer> findDueScheduled(Instant now);
    Optional<Transfer> findByIdAndStatus(String transferId, TransferStatus status);
}
