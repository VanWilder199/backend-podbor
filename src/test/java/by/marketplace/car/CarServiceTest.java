package by.marketplace.car;

import by.marketplace.car.dto.CarParseData;
import by.marketplace.car.service.impl.CarServiceImpl;
import org.jooq.DSLContext;
import org.jooq.Record1;
import org.jooq.Result;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;

import java.util.UUID;

import static by.marketplace.jooq.tables.Cars.CARS;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class CarServiceTest {
    private String url = "https://www.av.by/auto/123456789";
    private UUID existingId = UUID.randomUUID();

    private CarServiceImpl carService;

    private DSLContext dsl;

    private AvByParser avByParser;

    @BeforeEach
    void setUp() {
        avByParser = Mockito.mock(AvByParser.class);
    }

    @Test
    void findOrCreateByUrl_returnsExistingId_whenUrlAlreadyKnown() {
        MockDataProvider provider = ctx -> {
            String sql = ctx.sql().toLowerCase();
            if (sql.startsWith("select") && sql.contains("cars")) {
                DSLContext create = DSL.using(SQLDialect.POSTGRES);
                Result<Record1<UUID>> result = create.newResult(CARS.ID);
                Record1<UUID> carRecord = create.newRecord(CARS.ID);
                carRecord.setValue(CARS.ID, existingId);
                result.add(carRecord);
                return new MockResult[]{new MockResult(1, result)};
            }
            throw new IllegalStateException("Unexpected SQL: " + ctx.sql());

        };

        dsl = DSL.using(new MockConnection(provider), SQLDialect.POSTGRES);
        carService = new CarServiceImpl(dsl, avByParser);

       UUID result =  carService.findOrCreateByUrl(url);
       assertThat(result).isEqualTo(existingId);
       Mockito.verify(avByParser,Mockito.never()).parse(Mockito.any());
    }

    @Test
    void findOrCreateByUrl_parsesAndInserts_whenUrlIsNew() {
        Mockito.when(avByParser.parse(url)).thenReturn(new CarParseData("123456789", "Toyota", "Camry", 2020));

        UUID newCardId = UUID.randomUUID();

        MockDataProvider provider = ctx -> {
            String sql = ctx.sql().toLowerCase();

            if (sql.startsWith("select") && sql.contains("cars")) {
                DSLContext create = DSL.using(SQLDialect.POSTGRES);
                Result<Record1<UUID>> emptyResult = create.newResult(CARS.ID);

                return new MockResult[]{new MockResult(0, emptyResult)};
            }

            if (sql.startsWith("insert") && sql.contains("cars")) {
                DSLContext create = DSL.using(SQLDialect.POSTGRES);
                Result<Record1<UUID>> result = create.newResult(CARS.ID);
                Record1<UUID> carRecord = create.newRecord(CARS.ID);
                carRecord.setValue(CARS.ID, newCardId);
                result.add(carRecord);
                return new MockResult[]{new MockResult(1, result)};
            }
            throw new IllegalStateException("Unexpected SQL: " + ctx.sql());

        };

        dsl = DSL.using(new MockConnection(provider), SQLDialect.POSTGRES);
        carService = new CarServiceImpl(dsl, avByParser);

        UUID result =  carService.findOrCreateByUrl(url);

        assertThat(result).isEqualTo(newCardId);
        Mockito.verify(avByParser).parse(url);
    }
}
