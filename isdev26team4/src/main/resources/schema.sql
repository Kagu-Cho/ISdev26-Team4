CREATE TABLE IF NOT EXISTS room_participant (
    room_number VARCHAR(32) NOT NULL,
    participant_id VARCHAR(128) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    slot_number INT NOT NULL,
    PRIMARY KEY (room_number, participant_id),
    UNIQUE (room_number, slot_number)
);
