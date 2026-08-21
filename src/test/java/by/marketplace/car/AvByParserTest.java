package by.marketplace.car;

import by.marketplace.car.dto.CarParseData;
import by.marketplace.shared.exception.AppException;
import by.marketplace.shared.exception.ErrorCode;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.catchThrowableOfType;

public class AvByParserTest {

    private static final String FULL_LISTING_HTML = """
            <html>
            <body>
            <span itemprop="brand" content="Audi"></span>

            <div class="card-vin__button"><b>WAUZZZ8</b></div>

            <ul>
              <li class="breadcrumb-item"><span itemprop="position" content="1"></span></li>
              <li class="breadcrumb-item"><span itemprop="position" content="2"></span></li>
              <li class="breadcrumb-item">
                <span itemprop="position" content="3"></span>
                <a itemprop="item" title="A4" href="#">A4</a>
              </li>
            </ul>

            <div class="card__params">2008 г., вариатор, 1,8 л, бензин, 280 000 км</div>
            </body>
            </html>
            """;

    private static final String MISSING_VIN_HTML = """
            <html>
            <body>
            <span itemprop="brand" content="Audi"></span>

            <ul>
              <li class="breadcrumb-item"><span itemprop="position" content="1"></span></li>
              <li class="breadcrumb-item"><span itemprop="position" content="2"></span></li>
              <li class="breadcrumb-item">
                <span itemprop="position" content="3"></span>
                <a itemprop="item" title="A4" href="#">A4</a>
              </li>
            </ul>

            <div class="card__params">2008 г., вариатор, 1,8 л, бензин, 280 000 км</div>
            </body>
            </html>
            """;

    private static final String MISSING_YEAR_HTML = """
            <html>
            <body>
            <span itemprop="brand" content="Audi"></span>

            <div class="card-vin__button"><b>WAUZZZ8</b></div>

            <ul>
              <li class="breadcrumb-item"><span itemprop="position" content="1"></span></li>
              <li class="breadcrumb-item"><span itemprop="position" content="2"></span></li>
              <li class="breadcrumb-item">
                <span itemprop="position" content="3"></span>
                <a itemprop="item" title="A4" href="#">A4</a>
              </li>
            </ul>
            </body>
            </html>
            """;

    private static final String MISSING_MAKE_HTML = """
            <html>
            <body>
            <div class="card-vin__button"><b>WAUZZZ8</b></div>

            <ul>
              <li class="breadcrumb-item"><span itemprop="position" content="1"></span></li>
              <li class="breadcrumb-item"><span itemprop="position" content="2"></span></li>
              <li class="breadcrumb-item">
                <span itemprop="position" content="3"></span>
                <a itemprop="item" title="A4" href="#">A4</a>
              </li>
            </ul>

            <div class="card__params">2008 г., вариатор, 1,8 л, бензин, 280 000 км</div>
            </body>
            </html>
            """;

    private static final String MISSING_MODEL_HTML = """
            <html>
            <body>
            <span itemprop="brand" content="Audi"></span>

            <div class="card-vin__button"><b>WAUZZZ8</b></div>

            <ul>
              <li class="breadcrumb-item"><span itemprop="position" content="1"></span></li>
              <li class="breadcrumb-item"><span itemprop="position" content="2"></span></li>
            </ul>

            <div class="card__params">2008 г., вариатор, 1,8 л, бензин, 280 000 км</div>
            </body>
            </html>
            """;

    private AvByParser avByParser;


    @BeforeEach
    void setUp() {
        avByParser = new AvByParser();

    }

    @Test
    void extractData_returnsAllFields_whenListingHasFullData() {
        Document doc = Jsoup.parse(FULL_LISTING_HTML);

        CarParseData result = avByParser.extractData(doc);


        CarParseData expectedResult = new CarParseData(
                "WAUZZZ8", "Audi", "A4", 2008
        );

        assertThat(result).isEqualTo(expectedResult);

    }

    @Test
    void extractData_throwsParserError_whenMakeMissing() {
        Document doc = Jsoup.parse(MISSING_MAKE_HTML);

        AppException exception = catchThrowableOfType(() -> avByParser.extractData(doc), AppException.class);

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PARSER_ERROR);
    }

    @Test
    void extractData_throwsParserError_whenModelMissing() {
        Document doc = Jsoup.parse(MISSING_MODEL_HTML);

        AppException exception = catchThrowableOfType(() -> avByParser.extractData(doc), AppException.class);

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PARSER_ERROR);
    }

    @Test
    void extractData_returnsNullVin_whenVinBlockMissing() {
        Document doc = Jsoup.parse(MISSING_VIN_HTML);

        CarParseData result = avByParser.extractData(doc);

        CarParseData expectedResult = new CarParseData(null, "Audi", "A4", 2008);

        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    void extractData_returnsNullYear_whenParamsMissing() {
        Document doc = Jsoup.parse(MISSING_YEAR_HTML);

        CarParseData result = avByParser.extractData(doc);

        CarParseData expectedResult = new CarParseData("WAUZZZ8", "Audi", "A4", null);

        assertThat(result).isEqualTo(expectedResult);
    }
}
