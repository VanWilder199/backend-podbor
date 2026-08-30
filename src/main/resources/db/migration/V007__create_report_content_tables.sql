ALTER TABLE reports ADD COLUMN price_byn BIGINT;
ALTER TABLE reports ADD COLUMN conclusion_text TEXT;

CREATE TABLE report_section_item(
       id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
       section_id UUID NOT NULL REFERENCES report_section(id),
       item_key TEXT NOT NULL,
       status TEXT NOT NULL,
       comment TEXT,
       order_no INT NOT NULL DEFAULT 0
);

CREATE TABLE paint_panel(
       id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
       code TEXT NOT NULL UNIQUE,
       order_no INT NOT NULL
);

INSERT INTO paint_panel (code, order_no) VALUES
    ('HOOD', 1),
    ('ROOF', 2),
    ('TRUNK', 3),
    ('FRONT_BUMPER', 4),
    ('REAR_BUMPER', 5),
    ('FENDER_FL', 6),
    ('FENDER_FR', 7),
    ('FENDER_RL', 8),
    ('FENDER_RR', 9),
    ('DOOR_FL', 10),
    ('DOOR_FR', 11),
    ('DOOR_RL', 12),
    ('DOOR_RR', 13),
    ('SILL_L', 14),
    ('SILL_R', 15);

CREATE TABLE paint_measurement (
      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      report_id UUID NOT NULL REFERENCES reports(id),
      panel_id UUID NOT NULL REFERENCES paint_panel(id),
      spot TEXT,
      thickness_um INT NOT NULL,
      note TEXT
    );