CREATE TABLE report_requests (
    id BIGSERIAL PRIMARY KEY,
    car_id UUID NOT NULL REFERENCES cars(id),
    buyer_id UUID  NOT NULL REFERENCES users(id),
    email CITEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX report_requests_car_buyer_idx ON report_requests (car_id, buyer_id);