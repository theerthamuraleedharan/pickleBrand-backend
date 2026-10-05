UPDATE products
SET image_name = 'mango-pickle.jpg'
WHERE name = 'Mango Pickle'
  AND image_name IS NULL;

UPDATE products
SET image_name = 'lemon-pickle.jpg'
WHERE name = 'Lemon Pickle'
  AND image_name IS NULL;

UPDATE products
SET image_name = 'garlic-pickle.jpg'
WHERE name = 'Garlic Pickle'
  AND image_name IS NULL;
