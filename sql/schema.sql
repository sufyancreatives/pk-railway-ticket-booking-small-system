-- Pakistan Railways Booking System Schema
-- Compatible with MySQL / MariaDB / H2 / SQLite (with minor tweaks)

CREATE TABLE IF NOT EXISTS users (
    id INTEGER PRIMARY KEY AUTO_INCREMENT,
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    phone VARCHAR(20),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS stations (
    id INTEGER PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL UNIQUE,
    code VARCHAR(10) NOT NULL UNIQUE,
    distance_from_karachi INT NOT NULL -- km
);

CREATE TABLE IF NOT EXISTS trains (
    id INTEGER PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    number VARCHAR(20) NOT NULL UNIQUE,
    base_rate_per_km DECIMAL(6,2) NOT NULL, -- PKR
    description VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS coaches (
    id INTEGER PRIMARY KEY AUTO_INCREMENT,
    train_id INTEGER NOT NULL,
    coach_code VARCHAR(10) NOT NULL, -- A, B, C
    class_type VARCHAR(30) NOT NULL, -- Economy, AC Standard, AC Business
    total_seats INT NOT NULL DEFAULT 48,
    fare_multiplier DECIMAL(4,2) NOT NULL DEFAULT 1.0,
    FOREIGN KEY (train_id) REFERENCES trains(id),
    UNIQUE(train_id, coach_code)
);

CREATE TABLE IF NOT EXISTS bookings (
    id INTEGER PRIMARY KEY AUTO_INCREMENT,
    booking_code VARCHAR(20) NOT NULL UNIQUE,
    user_id INTEGER NOT NULL,
    train_id INTEGER NOT NULL,
    from_station_id INTEGER NOT NULL,
    to_station_id INTEGER NOT NULL,
    coach_id INTEGER NOT NULL,
    seat_numbers VARCHAR(100) NOT NULL, -- e.g. "12,13"
    total_fare DECIMAL(10,2) NOT NULL,
    passenger_name VARCHAR(100) NOT NULL,
    passenger_cnic VARCHAR(20),
    travel_date DATE NOT NULL,
    status VARCHAR(20) DEFAULT 'CONFIRMED',
    qr_data TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (train_id) REFERENCES trains(id),
    FOREIGN KEY (from_station_id) REFERENCES stations(id),
    FOREIGN KEY (to_station_id) REFERENCES stations(id),
    FOREIGN KEY (coach_id) REFERENCES coaches(id)
);

-- Seed Stations (approximate distances from Karachi Cantt)
INSERT INTO stations (name, code, distance_from_karachi) VALUES
('Karachi Cantt', 'KHI', 0),
('Hyderabad', 'HYD', 164),
('Rohri Junction', 'ROH', 480),
('Multan Cantt', 'MUL', 890),
('Lahore Junction', 'LHR', 1215),
('Rawalpindi', 'RWP', 1500),
('Peshawar Cantt', 'PSH', 1680);

-- Seed Trains (6 popular Pakistan Railways trains)
INSERT INTO trains (name, number, base_rate_per_km, description) VALUES
('Green Line Express', '5UP', 4.50, 'Premium AC service - Fastest Karachi to Islamabad'),
('Tezgam', '7UP', 3.50, 'Iconic express - Karachi to Rawalpindi'),
('Khyber Mail', '1UP', 2.80, 'Longest daily service - Karachi to Peshawar'),
('Karachi Express', '15UP', 3.80, 'Popular express - Karachi to Lahore'),
('Awam Express', '13UP', 2.50, 'Affordable long distance - Karachi to Peshawar'),
('Fareed Express', '37UP', 3.20, 'Reliable service - Karachi to Lahore');

-- Seed Coaches (exactly 3 coaches per train)
-- Coach A: Economy (multiplier 1.0, 72 seats - typical)
-- Coach B: AC Standard (1.85, 48 seats)
-- Coach C: AC Business (2.60, 36 seats)

INSERT INTO coaches (train_id, coach_code, class_type, total_seats, fare_multiplier)
SELECT id, 'A', 'Economy', 72, 1.00 FROM trains;

INSERT INTO coaches (train_id, coach_code, class_type, total_seats, fare_multiplier)
SELECT id, 'B', 'AC Standard', 48, 1.85 FROM trains;

INSERT INTO coaches (train_id, coach_code, class_type, total_seats, fare_multiplier)
SELECT id, 'C', 'AC Business', 36, 2.60 FROM trains;