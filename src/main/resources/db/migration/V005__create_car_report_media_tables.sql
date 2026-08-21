CREATE TABLE cars(
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vin TEXT NOT NULL,
    avby_listing_url TEXT,
    make TEXT,
    model TEXT,
    year INT,
    listing_status TEXT NOT NULL DEFAULT 'active',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX cars_avby_listing_url_idx ON cars (avby_listing_url) WHERE avby_listing_url IS NOT NULL;

CREATE TABLE reports(
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    car_id UUID REFERENCES cars(id),
    inspector_id UUID REFERENCES inspectors(id),
    version_no INT NOT NULL DEFAULT 1,
    status TEXT NOT NULL DEFAULT 'draft',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE report_section(
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    report_id UUID REFERENCES reports(id),
    section_key TEXT NOT NULL,
    order_no INT NOT NULL,
    summary TEXT NOT NULL
);

CREATE TABLE report_media(
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    report_id UUID REFERENCES reports(id),
    section_id UUID REFERENCES report_section(id),
    kind TEXT NOT NULL,
    s3_key TEXT NOT NULL,
    status TEXT NOT NULL,
    order_no INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);