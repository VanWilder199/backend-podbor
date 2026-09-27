package by.marketplace.purchase.bepaid;

import by.marketplace.shared.exception.AppException;
import by.marketplace.shared.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.UUID;

@Slf4j
@Component
public class BePaidClient {
    private final RestClient restClient;

    public BePaidClient(RestClient bePaidRestClient) {
        this.restClient = bePaidRestClient;
    }

    public BePaidCheckoutResponse createCheckout(UUID purchaseId, long amountByn, String description) {


        BePaidCheckoutRequest request = new BePaidCheckoutRequest(
                new BePaidCheckoutRequest.Checkout(
                        "payment",
                        true,
                        new BePaidCheckoutRequest.Settings(
                                "http://localhost:8080/webhooks/bepaid",
                                "http://localhost:8080/purchase/success",
                                "http://localhost:8080/purchase/fail",
                                "http://localhost:8080/purchase/fail",
                                "http://localhost:8080/purchase/cancel"
                        ),
                        new BePaidCheckoutRequest.Order(
                                "BYN",
                                amountByn,
                                description,
                                purchaseId.toString()
                        )
                )
        );

        try {
            BePaidCheckoutResponse response = restClient.post()
                    .uri("/ctp/api/checkouts")
                    .body(request)
                    .retrieve()
                    .body(BePaidCheckoutResponse.class);

            return response;
        } catch (RestClientResponseException e) {
            log.error("bePaid createCheckout failed: purchaseId={}, amountByn={}, status={}, responseBody={}",
                    purchaseId, amountByn, e.getStatusCode(), truncate(e.getResponseBodyAsString()), e);
            throw new AppException(ErrorCode.BEPAID_REQUEST_FAILED, "bePaid createCheckout failed", e);
        } catch (RestClientException e) {
            log.error("bePaid createCheckout failed: purchaseId={}, amountByn={}",
                    purchaseId, amountByn, e);
            throw new AppException(ErrorCode.BEPAID_REQUEST_FAILED, "bePaid createCheckout failed", e);
        }
    }

    public BePaidCheckoutStatusResponse getCheckoutStatus(String token) {

        try {
            BePaidCheckoutStatusResponse response = restClient.get()
                    .uri("/ctp/api/checkouts/{token}", token)
                    .retrieve()
                    .body(BePaidCheckoutStatusResponse.class);
            return response;
        } catch (RestClientResponseException e) {
            log.warn("bePaid getCheckoutStatus failed: status={}", e.getStatusCode(), e);
            throw new AppException(ErrorCode.BEPAID_REQUEST_FAILED, "bePaid getCheckoutStatus failed", e);
        } catch (RestClientException e) {
            log.warn("bePaid getCheckoutStatus failed", e);
            throw new AppException(ErrorCode.BEPAID_REQUEST_FAILED, "bePaid getCheckoutStatus failed", e);
        }
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 500 ? value : value.substring(0, 500) + "...";
    }
}
