package by.marketplace.car;

import by.marketplace.car.dto.CarParseData;
import by.marketplace.shared.exception.AppException;
import by.marketplace.shared.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
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
        long start = System.nanoTime();
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .get();

            CarParseData data = extractData(doc, url);
            log.info("av.by parsed: url={}, durationMs={}, make={}, model={}, year={}, hasVin={}",
                    url, elapsedMs(start), data.make(), data.model(), data.year(), data.vin() != null);
            return data;
        } catch (HttpStatusException e) {
            if (e.getStatusCode() == 404) {
                log.info("av.by listing not found: url={}, durationMs={}", url, elapsedMs(start));
                throw new AppException(ErrorCode.CAR_LISTING_NOT_FOUND, "av.by listing not found", e);
            }
            log.warn("av.by HTTP error: url={}, status={}, durationMs={}",
                    url, e.getStatusCode(), elapsedMs(start), e);
            throw new AppException(ErrorCode.PARSER_ERROR, "av.by HTTP error", e);
        } catch (IOException e) {
            log.warn("av.by request failed: url={}, durationMs={}", url, elapsedMs(start), e);
            throw new AppException(ErrorCode.PARSER_ERROR, "av.by request failed", e);
        }
    }

    CarParseData extractData(Document doc) {
        return extractData(doc, null);
    }

    CarParseData extractData(Document doc, String url) {
        String make = extractMake(doc, url);
        String model = extractModel(doc, url);
        Integer year = extractYear(doc, url);
        String vin = extractVin(doc, url);

        return new CarParseData(vin, make, model, year);
    }


    private String extractMake(Document doc, String url) {
        Element makeEl = doc.selectFirst(MAKE_SELECTOR);
        if (makeEl == null) {
            log.warn("av.by layout changed? field=make, selector={}, url={}", MAKE_SELECTOR, url);
            throw new AppException(ErrorCode.PARSER_ERROR);
        }
        return makeEl.attr("content");
    }

    private String extractVin(Document doc, String url) {
        Element vinEl = doc.selectFirst(VIN_SELECTOR);
        if (vinEl == null) {
            log.debug("av.by element not found: field=vin, selector={}, url={}", VIN_SELECTOR, url);
        }
        return vinEl != null ? vinEl.text() : null;
    }


    private String extractModel(Document doc, String url) {
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
        log.warn("av.by layout changed? field=model, selector={}, url={}", BREADCRUMB_ITEM_SELECTOR, url);
        throw new AppException(ErrorCode.PARSER_ERROR);
    }

    private Integer extractYear(Document doc, String url) {
        Element paramsEl = doc.selectFirst(PARAMS_SELECTOR);
        if (paramsEl == null) {
            log.debug("av.by element not found: field=year, selector={}, url={}", PARAMS_SELECTOR, url);
            return null;
        }
        Matcher matcher = YEAR_PATTERN.matcher(paramsEl.text());
        boolean found = matcher.find();
        if (!found) {
            log.debug("av.by year not parsed from params");
        }
        return found ? Integer.parseInt(matcher.group(1)) : null;
    }

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

}
