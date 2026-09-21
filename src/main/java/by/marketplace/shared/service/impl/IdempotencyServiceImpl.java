package by.marketplace.shared.service.impl;

import by.marketplace.jooq.tables.IdempotencyKeys;
import by.marketplace.shared.service.IdempotencyService;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;

@Service
public class IdempotencyServiceImpl implements IdempotencyService {
    private final DSLContext dslContext;

    public IdempotencyServiceImpl(DSLContext dslContext) {
        this.dslContext = dslContext;
    }


    @Override
    public boolean claim(String key) {
        return dslContext.insertInto(IdempotencyKeys.IDEMPOTENCY_KEYS)
                .set(IdempotencyKeys.IDEMPOTENCY_KEYS.IDEMPOTENCY_KEY, key)
                .onConflict(IdempotencyKeys.IDEMPOTENCY_KEYS.IDEMPOTENCY_KEY)
                .doNothing()
                .returning(IdempotencyKeys.IDEMPOTENCY_KEYS.ID)
                .fetchOptional()
                .isPresent();
    }
}
