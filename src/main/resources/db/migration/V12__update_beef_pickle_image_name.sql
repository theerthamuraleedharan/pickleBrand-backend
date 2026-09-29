UPDATE products
SET image_name = 'beef-pickle.jpg'
WHERE name = 'Beef'
  AND image_name IS DISTINCT FROM 'beef-pickle.jpg';
