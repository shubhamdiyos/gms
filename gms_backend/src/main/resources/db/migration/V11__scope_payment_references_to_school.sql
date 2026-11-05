DROP INDEX IF EXISTS uq_fee_payment_reference;

CREATE UNIQUE INDEX uq_fee_payment_school_reference
    ON fee_payments (school_id, payment_reference)
    WHERE payment_reference IS NOT NULL;
