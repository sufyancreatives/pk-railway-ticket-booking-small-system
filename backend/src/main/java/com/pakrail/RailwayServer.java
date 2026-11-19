package com.pakrail;

import com.pakrail.Models.*;
import com.pakrail.DatabaseAndServices.*;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

/**
 * FILE 3 OF 3 - RailwayServer.java
 *
 * Pure Java HTTP REST API (no Spring, no frameworks).
 * Uses the JDK's built-in com.sun.net.httpserver.HttpServer on port 8080.
 *
 * Public endpoints:
 *   GET  /api/health
 *   GET  /api/stations
 *   GET  /api/trains
 *   POST /api/fare
 *   GET  /api/seats?coachId=1&date=2026-11-25
 *   POST /api/auth/signup
 *   POST /api/auth/login
 *   POST /api/auth/forgot
 *   POST /api/auth/logout
 * Protected (send X-Token or Authorization: Bearer <token>):
 *   GET  /api/bookings
 *   POST /api/bookings
 */
public class RailwayServer {

    private static final int PORT = 8080;

    public static void main(String[] args) throws Exception {
        DatabaseAndServices.init();

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        // Public endpoints
        server.createContext("/api/health", new HealthHandler());
        server.createContext("/api/stations", new StationsHandler());
        server.createContext("/api/trains", new TrainsHandler());
        server.createContext("/api/fare", new FareHandler());
        server.createContext("/api/seats", new SeatsHandler());

        // Auth
        server.createContext("/api/auth/signup", new SignupHandler());
        server.createContext("/api/auth/login", new LoginHandler());
        server.createContext("/api/auth/forgot", new ForgotHandler());
        server.createContext("/api/auth/logout", new LogoutHandler());

        // Protected
        server.createContext("/api/bookings", new BookingsHandler());

        server.setExecutor(null);
        server.start();
        System.out.println("=================================================");
        System.out.println("  [PK]  Pakistan Railways Booking API");
        System.out.println("  Running at  http://localhost:" + PORT);
        System.out.println("  OOP Java + SQL ready");
        System.out.println("=================================================");
    }

    // ========== UTILS ==========
    private static void enableCORS(HttpExchange ex) {
        Headers h = ex.getResponseHeaders();
        h.add("Access-Control-Allow-Origin", "*");
        h.add("Access-Control-Allow-Methods", "GET, POST, OPTIONS, PUT, DELETE");
        h.add("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Token");
        h.add("Content-Type", "application/json; charset=UTF-8");
    }

    private static void sendJson(HttpExchange ex, int code, String json) throws IOException {
        enableCORS(ex);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void sendError(HttpExchange ex, int code, String msg) throws IOException {
        sendJson(ex, code, "{\"success\":false,\"message\":\"" + escape(msg) + "\"}");
    }

    private static String readBody(HttpExchange ex) throws IOException {
        try (InputStream is = ex.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private static String getToken(HttpExchange ex) {
        Headers h = ex.getRequestHeaders();
        List<String> auth = h.get("Authorization");
        if (auth != null && !auth.isEmpty()) {
            String v = auth.get(0);
            if (v.startsWith("Bearer ")) return v.substring(7).trim();
        }
        List<String> xt = h.get("X-Token");
        if (xt != null && !xt.isEmpty()) return xt.get(0).trim();
        return null;
    }

    private static Integer requireAuth(HttpExchange ex) throws IOException {
        String token = getToken(ex);
        if (token == null) {
            sendError(ex, 401, "Authentication required. Please login.");
            return null;
        }
        Integer userId = AuthService.getUserIdFromToken(token);
        if (userId == null) {
            sendError(ex, 401, "Invalid or expired session. Please login again.");
            return null;
        }
        return userId;
    }

    // Simple JSON helpers (no external lib)
    private static String jsonStr(String key, String val) {
        return "\"" + key + "\":\"" + escape(val) + "\"";
    }
    private static String jsonNum(String key, Number val) {
        return "\"" + key + "\":" + val;
    }
    private static String jsonBool(String key, boolean val) {
        return "\"" + key + "\":" + val;
    }

    // ========== HANDLERS ==========

    static class HealthHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equals(ex.getRequestMethod())) { enableCORS(ex); ex.sendResponseHeaders(204, -1); return; }
            sendJson(ex, 200, "{\"status\":\"ok\",\"service\":\"Pakistan Railways API\",\"version\":\"1.0\"}");
        }
    }

    static class StationsHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equals(ex.getRequestMethod())) { enableCORS(ex); ex.sendResponseHeaders(204, -1); return; }
            try {
                List<Station> stations = DataService.getAllStations();
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < stations.size(); i++) {
                    Station s = stations.get(i);
                    if (i > 0) sb.append(",");
                    sb.append("{")
                      .append(jsonNum("id", s.getId())).append(",")
                      .append(jsonStr("name", s.getName())).append(",")
                      .append(jsonStr("code", s.getCode())).append(",")
                      .append(jsonNum("distanceFromKarachi", s.getDistanceFromKarachi()))
                      .append("}");
                }
                sb.append("]");
                sendJson(ex, 200, sb.toString());
            } catch (Exception e) {
                sendError(ex, 500, e.getMessage());
            }
        }
    }

    static class TrainsHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equals(ex.getRequestMethod())) { enableCORS(ex); ex.sendResponseHeaders(204, -1); return; }
            try {
                List<Train> trains = DataService.getAllTrains();
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < trains.size(); i++) {
                    Train t = trains.get(i);
                    if (i > 0) sb.append(",");
                    sb.append("{")
                      .append(jsonNum("id", t.getId())).append(",")
                      .append(jsonStr("name", t.getName())).append(",")
                      .append(jsonStr("number", t.getNumber())).append(",")
                      .append(jsonNum("baseRatePerKm", t.getBaseRatePerKm())).append(",")
                      .append(jsonStr("description", t.getDescription())).append(",")
                      .append("\"coaches\":[");
                    List<Coach> coaches = t.getCoaches();
                    for (int j = 0; j < coaches.size(); j++) {
                        Coach c = coaches.get(j);
                        if (j > 0) sb.append(",");
                        sb.append("{")
                          .append(jsonNum("id", c.getId())).append(",")
                          .append(jsonStr("coachCode", c.getCoachCode())).append(",")
                          .append(jsonStr("classType", c.getClassType())).append(",")
                          .append(jsonNum("totalSeats", c.getTotalSeats())).append(",")
                          .append(jsonNum("fareMultiplier", c.getFareMultiplier()))
                          .append("}");
                    }
                    sb.append("]}");
                }
                sb.append("]");
                sendJson(ex, 200, sb.toString());
            } catch (Exception e) {
                sendError(ex, 500, e.getMessage());
            }
        }
    }

    static class FareHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equals(ex.getRequestMethod())) { enableCORS(ex); ex.sendResponseHeaders(204, -1); return; }
            if (!"POST".equals(ex.getRequestMethod())) { sendError(ex, 405, "POST only"); return; }
            try {
                String body = readBody(ex);
                // Very simple parse (key:value style expected from frontend)
                FareRequest req = parseFareRequest(body);
                FareResponse res = FareService.calculateFare(req);
                String json = "{"
                    + jsonBool("success", res.totalFare.compareTo(BigDecimal.ZERO) > 0) + ","
                    + jsonStr("message", res.message) + ","
                    + jsonNum("farePerSeat", res.farePerSeat) + ","
                    + jsonNum("totalFare", res.totalFare) + ","
                    + jsonNum("distanceKm", res.distanceKm) + ","
                    + jsonStr("trainName", res.trainName) + ","
                    + jsonStr("classType", res.classType)
                    + "}";
                sendJson(ex, 200, json);
            } catch (Exception e) {
                sendError(ex, 400, e.getMessage());
            }
        }

        private FareRequest parseFareRequest(String body) {
            FareRequest r = new FareRequest();
            r.trainId = extractInt(body, "trainId");
            r.fromStationId = extractInt(body, "fromStationId");
            r.toStationId = extractInt(body, "toStationId");
            r.coachId = extractInt(body, "coachId");
            r.seatCount = Math.max(1, extractInt(body, "seatCount"));
            return r;
        }
    }

    static class SeatsHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equals(ex.getRequestMethod())) { enableCORS(ex); ex.sendResponseHeaders(204, -1); return; }
            try {
                String query = ex.getRequestURI().getQuery();
                int coachId = extractQueryInt(query, "coachId");
                String dateStr = extractQuery(query, "date");
                if (dateStr == null) dateStr = LocalDate.now().plusDays(1).toString();
                LocalDate date = LocalDate.parse(dateStr);

                Set<Integer> booked = DataService.getBookedSeats(coachId, date);
                int total = DataService.getTotalSeats(coachId);

                StringBuilder sb = new StringBuilder("{\"totalSeats\":" + total + ",\"booked\":[");
                int i = 0;
                for (int s : booked) {
                    if (i++ > 0) sb.append(",");
                    sb.append(s);
                }
                sb.append("]}");
                sendJson(ex, 200, sb.toString());
            } catch (Exception e) {
                sendError(ex, 400, e.getMessage());
            }
        }
    }

    static class SignupHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equals(ex.getRequestMethod())) { enableCORS(ex); ex.sendResponseHeaders(204, -1); return; }
            if (!"POST".equals(ex.getRequestMethod())) { sendError(ex, 405, "POST only"); return; }
            try {
                String body = readBody(ex);
                SignupRequest req = new SignupRequest();
                req.fullName = extractStr(body, "fullName");
                req.email = extractStr(body, "email");
                req.password = extractStr(body, "password");
                req.phone = extractStr(body, "phone");
                AuthResponse res = AuthService.signup(req);
                sendAuthResponse(ex, res);
            } catch (Exception e) {
                sendError(ex, 400, e.getMessage());
            }
        }
    }

    static class LoginHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equals(ex.getRequestMethod())) { enableCORS(ex); ex.sendResponseHeaders(204, -1); return; }
            if (!"POST".equals(ex.getRequestMethod())) { sendError(ex, 405, "POST only"); return; }
            try {
                String body = readBody(ex);
                LoginRequest req = new LoginRequest();
                req.email = extractStr(body, "email");
                req.password = extractStr(body, "password");
                AuthResponse res = AuthService.login(req);
                sendAuthResponse(ex, res);
            } catch (Exception e) {
                sendError(ex, 400, e.getMessage());
            }
        }
    }

    static class ForgotHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equals(ex.getRequestMethod())) { enableCORS(ex); ex.sendResponseHeaders(204, -1); return; }
            if (!"POST".equals(ex.getRequestMethod())) { sendError(ex, 405, "POST only"); return; }
            try {
                String body = readBody(ex);
                String email = extractStr(body, "email");
                AuthResponse res = AuthService.forgotPassword(email);
                sendJson(ex, 200, "{" + jsonBool("success", res.success) + "," + jsonStr("message", res.message) + "}");
            } catch (Exception e) {
                sendError(ex, 400, e.getMessage());
            }
        }
    }

    static class LogoutHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equals(ex.getRequestMethod())) { enableCORS(ex); ex.sendResponseHeaders(204, -1); return; }
            String token = getToken(ex);
            if (token != null) AuthService.logout(token);
            sendJson(ex, 200, "{\"success\":true,\"message\":\"Logged out\"}");
        }
    }

    static class BookingsHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equals(ex.getRequestMethod())) { enableCORS(ex); ex.sendResponseHeaders(204, -1); return; }
            Integer userId = requireAuth(ex);
            if (userId == null) return;

            if ("GET".equals(ex.getRequestMethod())) {
                try {
                    List<Booking> list = BookingService.getUserBookings(userId);
                    sendJson(ex, 200, bookingsToJson(list));
                } catch (Exception e) {
                    sendError(ex, 500, e.getMessage());
                }
            } else if ("POST".equals(ex.getRequestMethod())) {
                try {
                    String body = readBody(ex);
                    BookingRequest req = new BookingRequest();
                    req.userId = userId;
                    req.trainId = extractInt(body, "trainId");
                    req.fromStationId = extractInt(body, "fromStationId");
                    req.toStationId = extractInt(body, "toStationId");
                    req.coachId = extractInt(body, "coachId");
                    req.seatNumbers = extractStr(body, "seatNumbers");
                    req.passengerName = extractStr(body, "passengerName");
                    req.passengerCnic = extractStr(body, "passengerCnic");
                    req.travelDate = extractStr(body, "travelDate");

                    Booking b = BookingService.createBooking(req);
                    sendJson(ex, 201, bookingToJson(b));
                } catch (Exception e) {
                    sendError(ex, 400, e.getMessage());
                }
            } else {
                sendError(ex, 405, "GET or POST only");
            }
        }
    }

    // ========== JSON builders ==========
    private static void sendAuthResponse(HttpExchange ex, AuthResponse res) throws IOException {
        StringBuilder sb = new StringBuilder("{");
        sb.append(jsonBool("success", res.success)).append(",");
        sb.append(jsonStr("message", res.message));
        if (res.token != null) {
            sb.append(",").append(jsonStr("token", res.token));
        }
        if (res.user != null) {
            sb.append(",\"user\":{")
              .append(jsonNum("id", res.user.getId())).append(",")
              .append(jsonStr("fullName", res.user.getFullName())).append(",")
              .append(jsonStr("email", res.user.getEmail())).append(",")
              .append(jsonStr("phone", res.user.getPhone() != null ? res.user.getPhone() : ""))
              .append("}");
        }
        sb.append("}");
        sendJson(ex, res.success ? 200 : 400, sb.toString());
    }

    private static String bookingToJson(Booking b) {
        return "{"
            + jsonNum("id", b.getId()) + ","
            + jsonStr("bookingCode", b.getBookingCode()) + ","
            + jsonNum("userId", b.getUserId()) + ","
            + jsonStr("trainName", b.getTrainName()) + ","
            + jsonStr("trainNumber", b.getTrainNumber()) + ","
            + jsonStr("fromStation", b.getFromStationName()) + ","
            + jsonStr("toStation", b.getToStationName()) + ","
            + jsonStr("coachCode", b.getCoachCode()) + ","
            + jsonStr("classType", b.getClassType()) + ","
            + jsonStr("seatNumbers", b.getSeatNumbers()) + ","
            + jsonNum("totalFare", b.getTotalFare()) + ","
            + jsonStr("passengerName", b.getPassengerName()) + ","
            + jsonStr("passengerCnic", b.getPassengerCnic() != null ? b.getPassengerCnic() : "") + ","
            + jsonStr("travelDate", b.getTravelDate().toString()) + ","
            + jsonStr("status", b.getStatus()) + ","
            + jsonStr("qrData", b.getQrData() != null ? b.getQrData() : "") + ","
            + jsonStr("createdAt", b.getCreatedAt() != null ? b.getCreatedAt().toString() : "")
            + "}";
    }

    private static String bookingsToJson(List<Booking> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(bookingToJson(list.get(i)));
        }
        sb.append("]");
        return sb.toString();
    }

    // ========== SIMPLE JSON EXTRACTORS (no lib) ==========
    private static int extractInt(String json, String key) {
        String v = extractStr(json, key);
        try { return Integer.parseInt(v); } catch (Exception e) { return 0; }
    }

    private static String extractStr(String json, String key) {
        // very naive: look for "key":"value" or "key":value
        String pattern = "\"" + key + "\"";
        int idx = json.indexOf(pattern);
        if (idx < 0) return "";
        int colon = json.indexOf(':', idx + pattern.length());
        if (colon < 0) return "";
        int start = colon + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;
        if (start >= json.length()) return "";
        if (json.charAt(start) == '"') {
            int end = json.indexOf('"', start + 1);
            return end > start ? json.substring(start + 1, end) : "";
        } else {
            int end = start;
            while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '.' || json.charAt(end) == '-')) end++;
            return json.substring(start, end);
        }
    }

    private static String extractQuery(String query, String key) {
        if (query == null) return null;
        for (String part : query.split("&")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2 && kv[0].equals(key)) return kv[1];
        }
        return null;
    }

    private static int extractQueryInt(String query, String key) {
        String v = extractQuery(query, key);
        try { return Integer.parseInt(v); } catch (Exception e) { return 0; }
    }
}