ALTER TABLE products
ADD COLUMN IF NOT EXISTS category VARCHAR(20) NOT NULL DEFAULT 'VEG';

UPDATE products
SET category = 'VEG'
WHERE category IS NULL;

ALTER TABLE products
ALTER COLUMN category SET NOT NULL;

ALTER TABLE products
ALTER COLUMN category DROP DEFAULT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_products_category'
          AND conrelid = 'products'::regclass
    ) THEN
        ALTER TABLE products
        ADD CONSTRAINT chk_products_category
        CHECK (category IN ('VEG', 'NON_VEG', 'MIXED'));
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_products_category
ON products(category);
