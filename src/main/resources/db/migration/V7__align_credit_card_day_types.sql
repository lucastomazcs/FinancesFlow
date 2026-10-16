ALTER TABLE credit_cards
    ALTER COLUMN closing_day TYPE INTEGER USING closing_day::INTEGER,
    ALTER COLUMN due_day TYPE INTEGER USING due_day::INTEGER;
