-- Availability changes must be reversible without reopening hours released by booking
-- cancellation. Add WITHDRAWN for hours removed by a pause or availability edit; these
-- may return to AVAILABLE when current availability rules include them again. RELEASED
-- remains reserved for cancelled bookings and is never restored by availability generation.
--
-- The original status CHECK was unnamed, so find and drop the status constraint that
-- includes RELEASED before replacing it with a named constraint that also allows WITHDRAWN.
DO $$
DECLARE
    status_constraint text;
BEGIN
    SELECT conname
    INTO status_constraint
    FROM pg_constraint
    WHERE conrelid = 'booking.hour_blocks'::regclass
      AND contype = 'c'
      AND pg_get_constraintdef(oid) LIKE '%status%'
      AND pg_get_constraintdef(oid) LIKE '%RELEASED%';

    IF status_constraint IS NOT NULL THEN
        EXECUTE format(
            'ALTER TABLE booking.hour_blocks DROP CONSTRAINT %I',
            status_constraint
        );
    END IF;
END $$;

ALTER TABLE booking.hour_blocks
    ADD CONSTRAINT ck_hour_blocks_status
    CHECK (status IN ('AVAILABLE', 'HELD', 'BOOKED', 'RELEASED', 'WITHDRAWN'));
