INSERT INTO products (
    name,
    description,
    price,
    stock_quantity,
    weight_grams,
    spice_level,
    image_url,
    active,
    category,
    image_name
)
SELECT
    'Ginger Pickle',
    'A warming ginger pickle slow-cooked with mustard seeds and traditional spices.',
    169.00,
    18,
    350,
    'MEDIUM',
    NULL,
    TRUE,
    'VEG',
    'ginger-pickle.png'
WHERE NOT EXISTS (
    SELECT 1 FROM products WHERE name = 'Ginger Pickle'
);

INSERT INTO products (
    name,
    description,
    price,
    stock_quantity,
    weight_grams,
    spice_level,
    image_url,
    active,
    category,
    image_name
)
SELECT
    'Tomato Pickle',
    'A tangy tomato pickle simmered with garlic, chilli and fragrant South Indian spices.',
    159.00,
    22,
    400,
    'MEDIUM',
    NULL,
    TRUE,
    'VEG',
    'tomato-pickle.svg'
WHERE NOT EXISTS (
    SELECT 1 FROM products WHERE name = 'Tomato Pickle'
);

INSERT INTO products (
    name,
    description,
    price,
    stock_quantity,
    weight_grams,
    spice_level,
    image_url,
    active,
    category,
    image_name
)
SELECT
    'Green Chilli Pickle',
    'Whole green chillies pickled with mustard, lemon and a bold blend of spices.',
    179.00,
    16,
    350,
    'HOT',
    NULL,
    TRUE,
    'VEG',
    'green-chilli-pickle.svg'
WHERE NOT EXISTS (
    SELECT 1 FROM products WHERE name = 'Green Chilli Pickle'
);
