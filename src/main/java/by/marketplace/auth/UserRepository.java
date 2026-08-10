package by.marketplace.auth;

import by.marketplace.auth.dto.Channel;
import by.marketplace.jooq.tables.Users;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

import static by.marketplace.jooq.Tables.USERS;

@Repository
@RequiredArgsConstructor
public class UserRepository {

    private final DSLContext dsl;

    public UUID upsertByDestination(Channel channel, String destination) {
        if (channel == Channel.SMS) {
            return this.upsertBySMS(destination);
        }

        return upsertByEmail(destination);
    }

    public Optional<String> findEmailById(UUID id) {
        return dsl.select(USERS.EMAIL)
                .from(USERS)
                .where(USERS.ID.eq(id))
                .fetchOptional(USERS.EMAIL);
    }


    private UUID upsertBySMS(String destination) {
        return dsl.insertInto(USERS)
                .set(Users.USERS.PHONE, destination)
                .onConflict(USERS.PHONE)
                .doUpdate().set(USERS.PHONE, destination)
                .returning(USERS.ID)
                .fetchOne()
                .getId();
    }

    private UUID upsertByEmail(String destination) {
        return  dsl.insertInto(USERS)
                .set(USERS.EMAIL, destination)
                .onConflict(USERS.EMAIL)
                .doUpdate().set(USERS.EMAIL, destination)
                .returning(USERS.ID)
                .fetchOne()
                .getId();
    }
}
