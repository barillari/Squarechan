OWNER_TOKEN_EXPIRES = 20 * 365 * 24 * 60 * 60 
DEFAULT_ROUNDING = 100
DEFAULT_RADIUS = 10000

RADII = [250, 500, 1000, 5000, 10000, 50000, 100000, 500000]
RADII_TEXT = ["250 m", "500 m", "1 km", "5 km", "10 km", "50 km", "100 km", "500 km"]

RADIUS_MENU = zip(RADII, RADII_TEXT)

ROUND_OPTIONS = [ (10, "10 m"),
                  (100, "100 m"),
                  (1000, "1 km"),
                  (10000, "10 km") ]
