package com.pakrail;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * FILE 1 OF 3 - Models.java
 *
 * Plain OOP domain models (POJOs) used by the whole project.
 * Each class represents one real-world entity with private fields and
 * public getters/setters (encapsulation). The small classes at the bottom
 * are request/response DTOs used by the HTTP API.
 */
public final class Models {

    private Models() { } // static holder class - never instantiated

    // ==================== USER ====================
    /** A registered passenger account. */
    public static class User {
        private int id;
        private String fullName;
        private String email;
        private String phone;

        public User() { }

        public User(String fullName, String email, String phone) {
            this.fullName = fullName;
            this.email = email;
            this.phone = phone;
        }

        public int getId() { return id; }
        public void setId(int id) { this.id = id; }
        public String getFullName() { return fullName; }
        public void setFullName(String fullName) { this.fullName = fullName; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
    }

    // ==================== STATION ====================
    /** A railway station. Distance is measured from Karachi Cantt (km). */
    public static class Station {
        private int id;
        private String name;
        private String code;
        private int distanceFromKarachi;

        public Station(int id, String name, String code, int distanceFromKarachi) {
            this.id = id;
            this.name = name;
            this.code = code;
            this.distanceFromKarachi = distanceFromKarachi;
        }

        public int getId() { return id; }
        public String getName() { return name; }
        public String getCode() { return code; }
        public int getDistanceFromKarachi() { return distanceFromKarachi; }
    }

    // ==================== TRAIN ====================
    /** A train service, with its own per-km base rate and its coaches. */
    public static class Train {
        private int id;
        private String name;
        private String number;
        private BigDecimal baseRatePerKm;
        private String description;
        private List<Coach> coaches;

        public Train(int id, String name, String number, BigDecimal baseRatePerKm, String description) {
            this.id = id;
            this.name = name;
            this.number = number;
            this.baseRatePerKm = baseRatePerKm;
            this.description = description;
        }

        public int getId() { return id; }
        public String getName() { return name; }
        public String getNumber() { return number; }
        public BigDecimal getBaseRatePerKm() { return baseRatePerKm; }
        public String getDescription() { return description; }
        public List<Coach> getCoaches() { return coaches; }
        public void setCoaches(List<Coach> coaches) { this.coaches = coaches; }
    }

    // ==================== COACH ====================
    /** One coach (class) of a train: A = Economy, B = AC Standard, C = AC Business. */
    public static class Coach {
        private int id;
        private String coachCode;
        private String classType;
        private int totalSeats;
        private BigDecimal fareMultiplier;

        public Coach(int id, String coachCode, String classType, int totalSeats, BigDecimal fareMultiplier) {
            this.id = id;
            this.coachCode = coachCode;
            this.classType = classType;
            this.totalSeats = totalSeats;
            this.fareMultiplier = fareMultiplier;
        }

        public int getId() { return id; }
        public String getCoachCode() { return coachCode; }
        public String getClassType() { return classType; }
        public int getTotalSeats() { return totalSeats; }
        public BigDecimal getFareMultiplier() { return fareMultiplier; }
    }

    // ==================== BOOKING ====================
    /**
     * A confirmed ticket. The first group of fields matches the bookings table.
     * The second group is filled in by SQL joins so the API can return
     * readable names (train, stations, coach) instead of only IDs.
     */
    public static class Booking {
        private int id;
        private String bookingCode;
        private int userId;
        private int trainId;
        private int fromStationId;
        private int toStationId;
        private int coachId;
        private String seatNumbers;
        private BigDecimal totalFare;
        private String passengerName;
        private String passengerCnic;
        private LocalDate travelDate;
        private String status;
        private String qrData;
        private LocalDateTime createdAt;

        // Joined (read-only) display fields
        private String trainName;
        private String trainNumber;
        private String fromStationName;
        private String toStationName;
        private String coachCode;
        private String classType;

        public int getId() { return id; }
        public void setId(int id) { this.id = id; }
        public String getBookingCode() { return bookingCode; }
        public void setBookingCode(String bookingCode) { this.bookingCode = bookingCode; }
        public int getUserId() { return userId; }
        public void setUserId(int userId) { this.userId = userId; }
        public void setTrainId(int trainId) { this.trainId = trainId; }
        public void setFromStationId(int fromStationId) { this.fromStationId = fromStationId; }
        public void setToStationId(int toStationId) { this.toStationId = toStationId; }
        public void setCoachId(int coachId) { this.coachId = coachId; }
        public String getSeatNumbers() { return seatNumbers; }
        public void setSeatNumbers(String seatNumbers) { this.seatNumbers = seatNumbers; }
        public BigDecimal getTotalFare() { return totalFare; }
        public void setTotalFare(BigDecimal totalFare) { this.totalFare = totalFare; }
        public String getPassengerName() { return passengerName; }
        public void setPassengerName(String passengerName) { this.passengerName = passengerName; }
        public String getPassengerCnic() { return passengerCnic; }
        public void setPassengerCnic(String passengerCnic) { this.passengerCnic = passengerCnic; }
        public LocalDate getTravelDate() { return travelDate; }
        public void setTravelDate(LocalDate travelDate) { this.travelDate = travelDate; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getQrData() { return qrData; }
        public void setQrData(String qrData) { this.qrData = qrData; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

        public String getTrainName() { return trainName; }
        public void setTrainName(String trainName) { this.trainName = trainName; }
        public String getTrainNumber() { return trainNumber; }
        public void setTrainNumber(String trainNumber) { this.trainNumber = trainNumber; }
        public String getFromStationName() { return fromStationName; }
        public void setFromStationName(String fromStationName) { this.fromStationName = fromStationName; }
        public String getToStationName() { return toStationName; }
        public void setToStationName(String toStationName) { this.toStationName = toStationName; }
        public String getCoachCode() { return coachCode; }
        public void setCoachCode(String coachCode) { this.coachCode = coachCode; }
        public String getClassType() { return classType; }
        public void setClassType(String classType) { this.classType = classType; }
    }

    // ==================== FARE DTOs ====================
    /** Input for a fare calculation. */
    public static class FareRequest {
        public int trainId;
        public int fromStationId;
        public int toStationId;
        public int coachId;
        public int seatCount = 1;
    }

    /** Result of a fare calculation. */
    public static class FareResponse {
        public BigDecimal farePerSeat;
        public BigDecimal totalFare;
        public int distanceKm;
        public String trainName;
        public String classType;
        public String message;

        public FareResponse(BigDecimal farePerSeat, BigDecimal totalFare, int distanceKm,
                            String trainName, String classType) {
            this.farePerSeat = farePerSeat;
            this.totalFare = totalFare;
            this.distanceKm = distanceKm;
            this.trainName = trainName;
            this.classType = classType;
            this.message = "Fare calculated successfully";
        }
    }

    // ==================== AUTH DTOs ====================
    public static class LoginRequest {
        public String email;
        public String password;
    }

    public static class SignupRequest {
        public String fullName;
        public String email;
        public String password;
        public String phone;
    }

    /** Result of signup / login. The token is a simple in-memory session key. */
    public static class AuthResponse {
        public boolean success;
        public String message;
        public String token;
        public User user;

        public AuthResponse(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }

    // ==================== BOOKING DTO ====================
    public static class BookingRequest {
        public int userId;
        public int trainId;
        public int fromStationId;
        public int toStationId;
        public int coachId;
        public String seatNumbers;   // "5,6,7"
        public String passengerName;
        public String passengerCnic;
        public String travelDate;    // yyyy-MM-dd
    }
}
