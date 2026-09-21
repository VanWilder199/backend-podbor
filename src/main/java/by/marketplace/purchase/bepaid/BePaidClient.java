package by.marketplace.purchase.bepaid;

import by.marketplace.shared.exception.AppException;
import by.marketplace.shared.exception.ErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

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
        } catch (RestClientException e) {
            throw new AppException(ErrorCode.BEPAID_REQUEST_FAILED);
        }


    };

    public BePaidCheckoutStatusResponse getCheckoutStatus(String token) {

        try {
            BePaidCheckoutStatusResponse response = restClient.get()
                    .uri("/ctp/api/checkouts/{token}", token)
                    .retrieve()
                    .body(BePaidCheckoutStatusResponse.class);
            return response;
        } catch (RestClientException e) {
            throw new AppException(ErrorCode.BEPAID_REQUEST_FAILED);
        }


    };
}
