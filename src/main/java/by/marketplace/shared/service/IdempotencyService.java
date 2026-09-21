package by.marketplace.shared.service;

public interface IdempotencyService {
    boolean claim(String key);
}
