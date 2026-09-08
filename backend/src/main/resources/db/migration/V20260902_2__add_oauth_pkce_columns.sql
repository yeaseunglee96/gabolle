ALTER TABLE oauth_challenge
    ADD COLUMN code_challenge_hash VARCHAR(64),
    ADD COLUMN code_challenge_method VARCHAR(10);

-- Existing rows are only possible in environments that already ran the initial
-- development migration. They cannot be replayed safely, so invalidate them
-- before enforcing PKCE for newly issued challenges.
UPDATE oauth_challenge
SET code_challenge_hash = repeat('0', 64),
    code_challenge_method = 'S256'
WHERE code_challenge_hash IS NULL
   OR code_challenge_method IS NULL;

ALTER TABLE oauth_challenge
    ALTER COLUMN code_challenge_hash SET NOT NULL,
    ALTER COLUMN code_challenge_method SET NOT NULL;
