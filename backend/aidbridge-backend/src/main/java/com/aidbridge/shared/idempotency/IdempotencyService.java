package com.aidbridge.shared.idempotency;

import com.aidbridge.shared.exception.BusinessException;
import com.aidbridge.shared.util.Hashing;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class IdempotencyService {
    private final IdempotencyRepository records;
    private final IdempotencyLock locks;
    private final ObjectMapper json;

    public record Result<T>(T body, int status, String location, boolean replayed) { }

    /** Caller authorizes first. Claim and result roll back with business data. */
    public <T> Result<T> execute(UUID actor, UUID org, String operation, String key, Object payload,
                                Class<T> responseType, Supplier<Result<T>> action) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Idempotency requires a business transaction");
        }
        if (key == null || !key.matches("[\\x21-\\x7E]{16,128}")) throw BusinessException.invalid();

        String digest = Hashing.sha256(json.writeValueAsBytes(payload));
        byte[] lockHash = HexFormat.of().parseHex(Hashing.sha256(
                (actor + "\n" + operation + "\n" + key).getBytes(StandardCharsets.UTF_8)));
        locks.acquire(ByteBuffer.wrap(lockHash).getLong());

        var previous = records.findByActorIdAndOperationAndRequestKey(actor, operation, key);
        if (previous.isPresent()) {
            var stored = previous.get();
            if (!stored.getOrganizationId().equals(org) || !stored.getRequestDigest().strip().equals(digest)) {
                throw new BusinessException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT",
                        "Idempotency key was used for a different request");
            }
            return new Result<>(json.readValue(stored.getPrivateResponse(), responseType),
                    stored.getResponseStatus(), stored.getResponseLocation(), true);
        }
        Result<T> result = action.get();
        records.save(new IdempotencyRecord(actor, org, operation, key, digest,
                result.status(), json.writeValueAsString(result.body()), result.location()));
        return result;
    }
}
