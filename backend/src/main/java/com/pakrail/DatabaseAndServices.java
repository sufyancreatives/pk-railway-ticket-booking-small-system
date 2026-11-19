package com.pakrail;

import com.pakrail.Models.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FILE 2 OF 3 - DatabaseAndServices.java
 *
 * This file owns the database and all business logic:
 *   init()          -> create the H2 schema and seed demo data
 *   AuthService     -> signup, login, logout, password hashing, session tokens
 *   FareService     -> distance-based fare calculation
 *   DataService     -> read trains, stations, coaches and booked seats
 *   BookingService  -> create a booking and read bookings back
 *
 * The server (RailwayServer.java) only handles HTTP; it calls these services.
 */
public class DatabaseAndServices {

    // ========== CONFIG ==========
    // H2 in-memory (easy demo). For MySQL change URL + driver.
    private static final String DB_URL = "jdbc:h2:mem:pakrail;DB_CLOSE_DELAY=-1;MODE=MySQL";
    private static final String DB_USER = "sa";
    private static final String DB_PASS = "";

    private static Connection connection;
    private static final Map<String, Integer> activeTokens = new ConcurrentHashMap<>(); // token -> userId

    // ========== INIT ==========
    public static synchronized void init() throws Exception {
        Class.forName("org.h2.Driver");
        connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
        connection.setAutoCommit(true);
        createSchemaAndSeed();
        System.out.println("OK Database initialized & seeded successfully (H2 in-memory)");
    }

    private static void createSchemaAndSeed() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    id INTEGER PRIMARY KEY AUTO_INCREMENT,
                    full_name VARCHAR(100) NOT NULL,
                    email VARCHAR(150) NOT NULL UNIQUE,
                    password_hash VARCHAR(255) NOT NULL,
                    phone VARCHAR(20),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS stations (
                    id INTEGER PRIMARY KEY AUTO_INCREMENT,
                    name VARCHAR(100) NOT NULL UNIQUE,
                    code VARCHAR(10) NOT NULL UNIQUE,
                    distance_from_karachi INT NOT NULL
                )
            """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS trains (
                    id INTEGER PRIMARY KEY AUTO_INCREMENT,
                    name VARCHAR(100) NOT NULL,
                    number VARCHAR(20) NOT NULL UNIQUE,
                    base_rate_per_km DECIMAL(6,2) NOT NULL,
                    description VARCHAR(255)
                )
            """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS coaches (
                    id INTEGER PRIMARY KEY AUTO_INCREMENT,
                    train_id INTEGER NOT NULL,
                    coach_code VARCHAR(10) NOT NULL,
                    class_type VARCHAR(30) NOT NULL,
                    total_seats INT NOT NULL DEFAULT 48,
                    fare_multiplier DECIMAL(4,2) NOT NULL DEFAULT 1.0,
                    FOREIGN KEY (train_id) REFERENCES trains(id),
                    UNIQUE(train_id, coach_code)
                )
            """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS bookings (
                    id INTEGER PRIMARY KEY AUTO_INCREMENT,
                    booking_code VARCHAR(20) NOT NULL UNIQUE,
                    user_id INTEGER NOT NULL,
                    train_id INTEGER NOT NULL,
                    from_station_id INTEGER NOT NULL,
                    to_station_id INTEGER NOT NULL,
                    coach_id INTEGER NOT NULL,
                    seat_numbers VARCHAR(100) NOT NULL,
                    total_fare DECIMAL(10,2) NOT NULL,
                    passenger_name VARCHAR(100) NOT NULL,
                    passenger_cnic VARCHAR(20),
                    travel_date DATE NOT NULL,
                    status VARCHAR(20) DEFAULT 'CONFIRMED',
                    qr_data TEXT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);

            // Seed only if empty
            ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM stations");
            rs.next();
            if (rs.getInt(1) == 0) {
                st.execute("""
                    INSERT INTO stations (name, code, distance_from_karachi) VALUES
                    ('Karachi Cantt', 'KHI', 0),
                    ('Hyderabad', 'HYD', 164),
                    ('Rohri Junction', 'ROH', 480),
                    ('Multan Cantt', 'MUL', 890),
                    ('Lahore Junction', 'LHR', 1215),
                    ('Rawalpindi', 'RWP', 1500),
                    ('Peshawar Cantt', 'PSH', 1680)
                """);
                st.execute("""
                    INSERT INTO trains (name, number, base_rate_per_km, description) VALUES
                    ('Green Line Express', '5UP', 4.50, 'Premium AC service - Fastest Karachi to Islamabad'),
                    ('Tezgam', '7UP', 3.50, 'Iconic express - Karachi to Rawalpindi'),
                    ('Khyber Mail', '1UP', 2.80, 'Longest daily service - Karachi to Peshawar'),
                    ('Karachi Express', '15UP', 3.80, 'Popular express - Karachi to Lahore'),
                    ('Awam Express', '13UP', 2.50, 'Affordable long distance - Karachi to Peshawar'),
                    ('Fareed Express', '37UP', 3.20, 'Reliable service - Karachi to Lahore')
                """);
                st.execute("""
                    INSERT INTO coaches (train_id, coach_code, class_type, total_seats, fare_multiplier)
                    SELECT id, 'A', 'Economy', 72, 1.00 FROM trains
                """);
                st.execute("""
                    INSERT INTO coaches (train_id, coach_code, class_type, total_seats, fare_multiplier)
                    SELECT id, 'B', 'AC Standard', 48, 1.85 FROM trains
                """);
                st.execute("""
                    INSERT INTO coaches (train_id, coach_code, class_type, total_seats, fare_multiplier)
                    SELECT id, 'C', 'AC Business', 36, 2.60 FROM trains
                """);
                System.out.println("OK Seed data inserted");
            }
        }
    }

    // ========== AUTH SERVICE ==========
    public static class AuthService {

        /** SHA-256 hash (demo level). Passwords are never stored in plain text. */
        private static String hashPassword(String password) {
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                byte[] hash = md.digest(password.getBytes(StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                for (byte b : hash) sb.append(String.format("%02x", b));
                return sb.toString();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        public static AuthResponse signup(SignupRequest req) {
            if (req.fullName == null || req.email == null || req.password == null ||
                req.fullName.isBlank() || req.email.isBlank() || req.password.length() < 6) {
                return new AuthResponse(false, "Invalid input. Password must be at least 6 characters.");
            }
            try {
                // check exists
                PreparedStatement check = connection.prepareStatement("SELECT id FROM users WHERE email = ?");
                check.setString(1, req.email.toLowerCase().trim());
                ResultSet rs = check.executeQuery();
                if (rs.next()) {
                    return new AuthResponse(false, "Email already registered. Please login.");
                }

                PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO users (full_name, email, password_hash, phone) VALUES (?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, req.fullName.trim());
                ps.setString(2, req.email.toLowerCase().trim());
                ps.setString(3, hashPassword(req.password));
                ps.setString(4, req.phone != null ? req.phone.trim() : null);
                ps.executeUpdate();

                ResultSet keys = ps.getGeneratedKeys();
                keys.next();
                int userId = keys.getInt(1);

                User user = new User(req.fullName.trim(), req.email.toLowerCase().trim(), req.phone);
                user.setId(userId);

                String token = generateToken();
                activeTokens.put(token, userId);

                AuthResponse res = new AuthResponse(true, "Account created successfully!");
                res.token = token;
                res.user = user;
                return res;
            } catch (Exception e) {
                e.printStackTrace();
                return new AuthResponse(false, "Signup failed: " + e.getMessage());
            }
        }

        public static AuthResponse login(LoginRequest req) {
            try {
                PreparedStatement ps = connection.prepareStatement(
                    "SELECT id, full_name, email, password_hash, phone FROM users WHERE email = ?");
                ps.setString(1, req.email.toLowerCase().trim());
                ResultSet rs = ps.executeQuery();
                if (!rs.next()) {
                    return new AuthResponse(false, "Invalid email or password.");
                }
                String storedHash = rs.getString("password_hash");
                if (!storedHash.equals(hashPassword(req.password))) {
                    return new AuthResponse(false, "Invalid email or password.");
                }

                User user = new User();
                user.setId(rs.getInt("id"));
                user.setFullName(rs.getString("full_name"));
                user.setEmail(rs.getString("email"));
                user.setPhone(rs.getString("phone"));

                String token = generateToken();
                activeTokens.put(token, user.getId());

                AuthResponse res = new AuthResponse(true, "Login successful");
                res.token = token;
                res.user = user;
                return res;
            } catch (Exception e) {
                return new AuthResponse(false, "Login error: " + e.getMessage());
            }
        }

        public static AuthResponse forgotPassword(String email) {
            // Demo: always pretend success (real system would email reset link)
            try {
                PreparedStatement ps = connection.prepareStatement("SELECT id FROM users WHERE email = ?");
                ps.setString(1, email.toLowerCase().trim());
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    return new AuthResponse(true, "Password reset link sent to your email (demo mode).");
                }
                return new AuthResponse(false, "Email not found.");
            } catch (Exception e) {
                return new AuthResponse(false, "Error: " + e.getMessage());
            }
        }

        public static Integer getUserIdFromToken(String token) {
            return activeTokens.get(token);
        }

        public static void logout(String token) {
            activeTokens.remove(token);
        }

        private static String generateToken() {
            byte[] bytes = new byte[24];
            new SecureRandom().nextBytes(bytes);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        }
    }

    // ========== FARE SERVICE (distance-based, different per train & coach) ==========
    /**
     * Fare rule used by the project:
     *   farePerSeat = baseRatePerKm x distanceKm x coachMultiplier
     *   (rounded to the nearest 50 PKR for a realistic ticket price)
     *   totalFare   = farePerSeat x number of seats
     *
     * Example: Green Line (4.50/km) x 1215 km x 1.00 (Economy) = 5450 PKR.
     */
    public static class FareService {

        public static FareResponse calculateFare(FareRequest req) {
            try {
                // Get train
                PreparedStatement tps = connection.prepareStatement(
                    "SELECT name, base_rate_per_km FROM trains WHERE id = ?");
                tps.setInt(1, req.trainId);
                ResultSet trs = tps.executeQuery();
                if (!trs.next()) return error("Train not found");

                String trainName = trs.getString("name");
                BigDecimal baseRate = trs.getBigDecimal("base_rate_per_km");

                // Stations
                PreparedStatement sps = connection.prepareStatement(
                    "SELECT distance_from_karachi FROM stations WHERE id = ?");
                sps.setInt(1, req.fromStationId);
                ResultSet frs = sps.executeQuery();
                if (!frs.next()) return error("From station not found");
                int fromDist = frs.getInt(1);

                sps.setInt(1, req.toStationId);
                ResultSet trs2 = sps.executeQuery();
                if (!trs2.next()) return error("To station not found");
                int toDist = trs2.getInt(1);

                int distance = Math.abs(toDist - fromDist);
                if (distance == 0) return error("From and To stations cannot be the same");

                // Coach
                PreparedStatement cps = connection.prepareStatement(
                    "SELECT class_type, fare_multiplier FROM coaches WHERE id = ? AND train_id = ?");
                cps.setInt(1, req.coachId);
                cps.setInt(2, req.trainId);
                ResultSet crs = cps.executeQuery();
                if (!crs.next()) return error("Coach not found for this train");

                String classType = crs.getString("class_type");
                BigDecimal multiplier = crs.getBigDecimal("fare_multiplier");

                // Fare formula: baseRate * distance * multiplier  (rounded to nearest 10)
                BigDecimal farePerSeat = baseRate
                    .multiply(BigDecimal.valueOf(distance))
                    .multiply(multiplier)
                    .setScale(0, RoundingMode.HALF_UP);

                // Round to nearest 50 for realistic PKR look
                long rounded = Math.round(farePerSeat.doubleValue() / 50.0) * 50L;
                farePerSeat = BigDecimal.valueOf(rounded);

                int seats = Math.max(1, req.seatCount);
                BigDecimal total = farePerSeat.multiply(BigDecimal.valueOf(seats));

                return new FareResponse(farePerSeat, total, distance, trainName, classType);
            } catch (Exception e) {
                e.printStackTrace();
                return error("Calculation error: " + e.getMessage());
            }
        }

        private static FareResponse error(String msg) {
            FareResponse r = new FareResponse(BigDecimal.ZERO, BigDecimal.ZERO, 0, "", "");
            r.message = msg;
            return r;
        }
    }

    // ========== DATA SERVICE (trains, stations, coaches, seats) ==========
    public static class DataService {

        public static List<Station> getAllStations() throws SQLException {
            List<Station> list = new ArrayList<>();
            ResultSet rs = connection.createStatement().executeQuery(
                "SELECT id, name, code, distance_from_karachi FROM stations ORDER BY distance_from_karachi");
            while (rs.next()) {
                list.add(new Station(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getInt(4)));
            }
            return list;
        }

        public static List<Train> getAllTrains() throws SQLException {
            List<Train> list = new ArrayList<>();
            ResultSet rs = connection.createStatement().executeQuery(
                "SELECT id, name, number, base_rate_per_km, description FROM trains ORDER BY id");
            while (rs.next()) {
                Train t = new Train(rs.getInt(1), rs.getString(2), rs.getString(3),
                    rs.getBigDecimal(4), rs.getString(5));
                t.setCoaches(getCoachesForTrain(t.getId()));
                list.add(t);
            }
            return list;
        }

        public static List<Coach> getCoachesForTrain(int trainId) throws SQLException {
            List<Coach> list = new ArrayList<>();
            PreparedStatement ps = connection.prepareStatement(
                "SELECT id, coach_code, class_type, total_seats, fare_multiplier FROM coaches WHERE train_id = ? ORDER BY coach_code");
            ps.setInt(1, trainId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new Coach(rs.getInt(1), rs.getString(2),
                    rs.getString(3), rs.getInt(4), rs.getBigDecimal(5)));
            }
            return list;
        }

        /** Total seats of a coach; 48 is the fallback for an unknown coach. */
        public static int getTotalSeats(int coachId) throws SQLException {
            PreparedStatement ps = connection.prepareStatement(
                "SELECT total_seats FROM coaches WHERE id = ?");
            ps.setInt(1, coachId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : 48;
        }

        /** Returns set of already booked seat numbers for a coach on a given date */
        public static Set<Integer> getBookedSeats(int coachId, LocalDate travelDate) throws SQLException {
            Set<Integer> booked = new HashSet<>();
            PreparedStatement ps = connection.prepareStatement(
                "SELECT seat_numbers FROM bookings WHERE coach_id = ? AND travel_date = ? AND status = 'CONFIRMED'");
            ps.setInt(1, coachId);
            ps.setDate(2, java.sql.Date.valueOf(travelDate));
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String seats = rs.getString(1);
                if (seats != null) {
                    for (String s : seats.split(",")) {
                        try { booked.add(Integer.parseInt(s.trim())); } catch (Exception ignored) {}
                    }
                }
            }
            return booked;
        }
    }

    // ========== BOOKING SERVICE ==========
    /**
     * Booking flow (in order):
     *   1. check that every requested seat is still free on that date
     *   2. calculate the fare again on the server (never trust the client)
     *   3. build a booking code + QR text
     *   4. insert the booking row
     *   5. re-read it with joins and return it to the API
     */
    public static class BookingService {

        public static Booking createBooking(BookingRequest req) {
            try {
                // Validate seats available
                LocalDate date = LocalDate.parse(req.travelDate);
                Set<Integer> booked = DataService.getBookedSeats(req.coachId, date);
                String[] seats = req.seatNumbers.split(",");
                for (String s : seats) {
                    int seat = Integer.parseInt(s.trim());
                    if (booked.contains(seat)) {
                        throw new RuntimeException("Seat " + seat + " is already booked");
                    }
                }

                // Calculate fare
                FareRequest fr = new FareRequest();
                fr.trainId = req.trainId;
                fr.fromStationId = req.fromStationId;
                fr.toStationId = req.toStationId;
                fr.coachId = req.coachId;
                fr.seatCount = seats.length;
                FareResponse fare = FareService.calculateFare(fr);
                if (fare.totalFare.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new RuntimeException(fare.message);
                }

                String bookingCode = "PR" + System.currentTimeMillis() % 100000000L;
                String qrData = "PKRAIL|" + bookingCode + "|" + req.passengerName + "|" +
                    fare.trainName + "|" + req.seatNumbers + "|" + fare.totalFare;

                PreparedStatement ps = connection.prepareStatement(
                    """
                    INSERT INTO bookings (booking_code, user_id, train_id, from_station_id, to_station_id,
                        coach_id, seat_numbers, total_fare, passenger_name, passenger_cnic, travel_date, qr_data)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);

                ps.setString(1, bookingCode);
                ps.setInt(2, req.userId);
                ps.setInt(3, req.trainId);
                ps.setInt(4, req.fromStationId);
                ps.setInt(5, req.toStationId);
                ps.setInt(6, req.coachId);
                ps.setString(7, req.seatNumbers);
                ps.setBigDecimal(8, fare.totalFare);
                ps.setString(9, req.passengerName);
                ps.setString(10, req.passengerCnic);
                ps.setDate(11, java.sql.Date.valueOf(date));
                ps.setString(12, qrData);
                ps.executeUpdate();

                ResultSet keys = ps.getGeneratedKeys();
                keys.next();
                int id = keys.getInt(1);

                // Load full booking for response
                return getBookingById(id);
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e.getMessage());
            }
        }

        public static Booking getBookingById(int id) throws SQLException {
            PreparedStatement ps = connection.prepareStatement("""
                SELECT b.*, t.name as train_name, t.number as train_number,
                       fs.name as from_name, ts.name as to_name,
                       c.coach_code, c.class_type
                FROM bookings b
                JOIN trains t ON b.train_id = t.id
                JOIN stations fs ON b.from_station_id = fs.id
                JOIN stations ts ON b.to_station_id = ts.id
                JOIN coaches c ON b.coach_id = c.id
                WHERE b.id = ?
            """);
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) return null;
            return mapBooking(rs);
        }

        public static List<Booking> getUserBookings(int userId) throws SQLException {
            List<Booking> list = new ArrayList<>();
            PreparedStatement ps = connection.prepareStatement("""
                SELECT b.*, t.name as train_name, t.number as train_number,
                       fs.name as from_name, ts.name as to_name,
                       c.coach_code, c.class_type
                FROM bookings b
                JOIN trains t ON b.train_id = t.id
                JOIN stations fs ON b.from_station_id = fs.id
                JOIN stations ts ON b.to_station_id = ts.id
                JOIN coaches c ON b.coach_id = c.id
                WHERE b.user_id = ?
                ORDER BY b.created_at DESC
            """);
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapBooking(rs));
            return list;
        }

        private static Booking mapBooking(ResultSet rs) throws SQLException {
            Booking b = new Booking();
            b.setId(rs.getInt("id"));
            b.setBookingCode(rs.getString("booking_code"));
            b.setUserId(rs.getInt("user_id"));
            b.setTrainId(rs.getInt("train_id"));
            b.setFromStationId(rs.getInt("from_station_id"));
            b.setToStationId(rs.getInt("to_station_id"));
            b.setCoachId(rs.getInt("coach_id"));
            b.setSeatNumbers(rs.getString("seat_numbers"));
            b.setTotalFare(rs.getBigDecimal("total_fare"));
            b.setPassengerName(rs.getString("passenger_name"));
            b.setPassengerCnic(rs.getString("passenger_cnic"));
            b.setTravelDate(rs.getDate("travel_date").toLocalDate());
            b.setStatus(rs.getString("status"));
            b.setQrData(rs.getString("qr_data"));
            b.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
            b.setTrainName(rs.getString("train_name"));
            b.setTrainNumber(rs.getString("train_number"));
            b.setFromStationName(rs.getString("from_name"));
            b.setToStationName(rs.getString("to_name"));
            b.setCoachCode(rs.getString("coach_code"));
            b.setClassType(rs.getString("class_type"));
            return b;
        }
    }
}