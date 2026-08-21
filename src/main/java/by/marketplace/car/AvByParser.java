package by.marketplace.car;

import by.marketplace.car.dto.CarParseData;
import by.marketplace.shared.exception.AppException;
import by.marketplace.shared.exception.ErrorCode;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class AvByParser {
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final int TIMEOUT_MS = 10_000;

    private static final String MAKE_SELECTOR = "span[itemprop=brand]";
    private static final String VIN_SELECTOR = ".card-vin__button b";
    private static final String PARAMS_SELECTOR = ".card__params";
    private static final String BREADCRUMB_ITEM_SELECTOR = ".breadcrumb-item";
    private static final Pattern YEAR_PATTERN = Pattern.compile("(\\d{4})\\s*г");


    public CarParseData parse(String url) {
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .get();

            return extractData(doc);
        } catch (HttpStatusException e) {
            if (e.getStatusCode() == 404) {
                throw new AppException(ErrorCode.CAR_LISTING_NOT_FOUND);
            }
            throw new AppException(ErrorCode.PARSER_ERROR);
        } catch (IOException e) {
            throw new AppException(ErrorCode.PARSER_ERROR);
        }
    }

    CarParseData extractData(Document doc) {
        String make = extractMake(doc);
        String model = extractModel(doc);
        Integer year = extractYear(doc);
        String vin = extractVin(doc);

        return new CarParseData(vin, make, model, year);
    }


    private String extractMake(Document doc) {
        Element makeEl = doc.selectFirst(MAKE_SELECTOR);
        if (makeEl == null) {
            throw new AppException(ErrorCode.PARSER_ERROR);
        }
        return makeEl.attr("content");
    }

    private String extractVin(Document doc) {
        Element vinEl = doc.selectFirst(VIN_SELECTOR);
        return vinEl != null ? vinEl.text() : null;
    }


    private String extractModel(Document doc) {
        Elements breadcrumbItems = doc.select(BREADCRUMB_ITEM_SELECTOR);
        for (Element item : breadcrumbItems) {
            Element positionEl = item.selectFirst("span[itemprop=position]");
            if (positionEl != null && "3".equals(positionEl.attr("content"))) {
                Element linkEl = item.selectFirst("a[itemprop=item]");
                if (linkEl != null) {
                    return linkEl.attr("title");
                }
            }
        }
        throw new AppException(ErrorCode.PARSER_ERROR);
    }

    private Integer extractYear(Document doc) {
        Element paramsEl = doc.selectFirst(PARAMS_SELECTOR);
        if (paramsEl == null) {
            return null;
        }
        Matcher matcher = YEAR_PATTERN.matcher(paramsEl.text());
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : null;
    }



}
