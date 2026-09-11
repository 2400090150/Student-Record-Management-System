import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.Map;

public class Main {

    static final String DB_URL = "jdbc:sqlite:students.db";

    public static void main(String[] args) throws Exception {
        Class.forName("org.sqlite.JDBC");
        initDatabase();

        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/", new HomeHandler());
        server.createContext("/add", new AddHandler());
        server.createContext("/delete", new DeleteHandler());
        server.createContext("/export", new ExportHandler());
        server.createContext("/login", new GoogleLoginHandler());
        server.createContext("/style.css", new CssHandler());
        server.setExecutor(null);
        server.start();

        System.out.println("Student Record Management System running at http://localhost:8080");
    }

    // ---------- Database setup ----------

    static void initDatabase() throws SQLException {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS students (" +
                "  id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "  name TEXT NOT NULL," +
                "  roll_no TEXT NOT NULL UNIQUE," +
                "  department TEXT NOT NULL," +
                "  year INTEGER NOT NULL," +
                "  email TEXT NOT NULL," +
                "  course TEXT," +
                "  cgpa TEXT," +
                "  extra_heading TEXT," +
                "  extra_value TEXT" +
                ")"
            );
            ensureColumn(conn, "course", "TEXT");
            ensureColumn(conn, "cgpa", "TEXT");
            ensureColumn(conn, "extra_heading", "TEXT");
            ensureColumn(conn, "extra_value", "TEXT");
        }
    }

    static void ensureColumn(Connection conn, String column, String type) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA table_info(students)")) {
            while (rs.next()) {
                if (column.equals(rs.getString("name"))) return;
            }
        }
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE students ADD COLUMN " + column + " " + type);
        }
    }

    // ---------- Handlers ----------

    static class HomeHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            StringBuilder rows = new StringBuilder();
            StringBuilder customHeaders = new StringBuilder();
            String error = getQueryParam(ex.getRequestURI().getQuery(), "error");
            int serialNo = 1;

            try (Connection conn = DriverManager.getConnection(DB_URL);
                 Statement headingStmt = conn.createStatement();
                 ResultSet headingRs = headingStmt.executeQuery("SELECT course, cgpa, extra_heading, extra_value FROM students ORDER BY id DESC");
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM students ORDER BY id DESC")) {

                LinkedHashSet<String> headings = new LinkedHashSet<>();
                while (headingRs.next()) {
                    headings.addAll(customInfoMap(headingRs).keySet());
                }
                for (String heading : headings) {
                    customHeaders.append("<th>").append(escape(heading)).append("</th>");
                }

                while (rs.next()) {
                    Map<String, String> customInfo = customInfoMap(rs);
                    rows.append("<tr>")
                        .append("<td>").append(serialNo++).append("</td>")
                        .append("<td>").append(escape(rs.getString("name"))).append("</td>")
                        .append("<td>").append(escape(rs.getString("roll_no"))).append("</td>")
                        .append("<td>").append(escape(rs.getString("department"))).append("</td>")
                        .append("<td>").append(rs.getInt("year")).append("</td>")
                        .append("<td>").append(escape(rs.getString("email"))).append("</td>")
                        ;
                    for (String heading : headings) {
                        rows.append("<td>").append(escape(customInfo.getOrDefault(heading, ""))).append("</td>");
                    }
                    rows.append("<td><a class=\"delete-link\" href=\"/delete?id=")
                        .append(rs.getInt("id")).append("\">Delete</a></td>")
                        .append("</tr>\n");
                }
                int columnCount = 7 + headings.size();
                if (rows.length() == 0) {
                    rows.append("<tr><td colspan=\"").append(columnCount).append("\">No student records yet.</td></tr>");
                }
            } catch (SQLException e) {
                rows.append("<tr><td colspan=\"7\">Database error: ")
                    .append(escape(e.getMessage())).append("</td></tr>");
            }

            String errorBanner = "";
            if (error != null && error.equals("duplicate")) {
                errorBanner = "<div class=\"banner\">That roll number already exists. Please use a unique roll number.</div>";
            }

            String html = readWebFile("index.html")
                    .replace("{{ROWS}}", rows.toString())
                    .replace("{{CUSTOM_HEADERS}}", customHeaders.toString())
                    .replace("{{ERROR_BANNER}}", errorBanner);

            sendResponse(ex, 200, "text/html", html);
        }
    }

    static class AddHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                sendResponse(ex, 405, "text/plain", "Method Not Allowed");
                return;
            }

            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> form = parseForm(body);

            String name = form.getOrDefault("name", "").trim();
            String rollNo = form.getOrDefault("roll_no", "").trim();
            String department = form.getOrDefault("department", "").trim();
            String yearStr = form.getOrDefault("year", "").trim();
            String email = form.getOrDefault("email", "").trim();
            String course = form.getOrDefault("course", "").trim();
            String cgpa = form.getOrDefault("cgpa", "").trim();
            String extraHeading = form.getOrDefault("extra_heading", "").trim();
            String extraValue = form.getOrDefault("extra_info", form.getOrDefault("extra_value", "")).trim();

            String sql = "INSERT INTO students (name, roll_no, department, year, email, course, cgpa, extra_heading, extra_value) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (Connection conn = DriverManager.getConnection(DB_URL);
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, name);
                ps.setString(2, rollNo);
                ps.setString(3, department);
                ps.setInt(4, Integer.parseInt(yearStr));
                ps.setString(5, email);
                ps.setString(6, course);
                ps.setString(7, cgpa);
                ps.setString(8, extraHeading);
                ps.setString(9, extraValue);
                ps.executeUpdate();
                redirect(ex, "/");
            } catch (SQLException e) {
                redirect(ex, "/?error=duplicate");
            } catch (NumberFormatException e) {
                redirect(ex, "/");
            }
        }
    }

    static class DeleteHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            String idStr = getQueryParam(ex.getRequestURI().getQuery(), "id");
            if (idStr != null) {
                try (Connection conn = DriverManager.getConnection(DB_URL);
                     PreparedStatement ps = conn.prepareStatement("DELETE FROM students WHERE id = ?")) {
                    ps.setInt(1, Integer.parseInt(idStr));
                    ps.executeUpdate();
                } catch (Exception ignored) {
                }
            }
            redirect(ex, "/");
        }
    }

    static class CssHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            String css = readWebFile("style.css");
            sendResponse(ex, 200, "text/css", css);
        }
    }

    static class ExportHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            StringBuilder csv = new StringBuilder("\uFEFFSerial No,Name,Roll No,Department,Year,Email,Course,CGPA,Additional Field,Additional Value\r\n");
            int serialNo = 1;

            try (Connection conn = DriverManager.getConnection(DB_URL);
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT name, roll_no, department, year, email, course, cgpa, extra_heading, extra_value FROM students ORDER BY id DESC")) {

                while (rs.next()) {
                    csv.append(serialNo++).append(',')
                        .append(csvEscape(rs.getString("name"))).append(',')
                        .append(csvEscape(rs.getString("roll_no"))).append(',')
                        .append(csvEscape(rs.getString("department"))).append(',')
                        .append(rs.getInt("year")).append(',')
                        .append(csvEscape(rs.getString("email"))).append(',')
                        .append(csvEscape(rs.getString("course"))).append(',')
                        .append(csvEscape(rs.getString("cgpa"))).append(',')
                        .append(csvEscape(rs.getString("extra_heading"))).append(',')
                        .append(csvEscape(rs.getString("extra_value"))).append("\r\n");
                }
            } catch (SQLException e) {
                sendResponse(ex, 500, "text/plain", "Database error");
                return;
            }

            ex.getResponseHeaders().set("Content-Type", "text/csv; charset=UTF-8");
            ex.getResponseHeaders().set("Content-Disposition", "attachment; filename=students.csv");
            byte[] bytes = csv.toString().getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    static class GoogleLoginHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            String clientId = System.getenv("GOOGLE_CLIENT_ID");
            String redirectUri = System.getenv().getOrDefault(
                "GOOGLE_REDIRECT_URI", "http://localhost:8080/oauth2callback");

            if (clientId == null || clientId.isBlank()) {
                sendResponse(ex, 503, "text/html",
                    "<h2>Google login is not configured</h2>"
                    + "<p>Set GOOGLE_CLIENT_ID and GOOGLE_REDIRECT_URI before using Google login.</p>");
                return;
            }

            String location = "https://accounts.google.com/o/oauth2/v2/auth"
                + "?client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
                + "&response_type=code&scope="
                + URLEncoder.encode("openid email profile", StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Location", location);
            ex.sendResponseHeaders(302, -1);
            ex.close();
        }
    }

    // ---------- Helpers ----------

    static String readWebFile(String name) throws IOException {
        return Files.readString(Paths.get("web", name));
    }

    static void sendResponse(HttpExchange ex, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", contentType + "; charset=UTF-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    static void redirect(HttpExchange ex, String location) throws IOException {
        ex.getResponseHeaders().set("Location", location);
        ex.sendResponseHeaders(303, -1);
        ex.close();
    }

    static Map<String, String> parseForm(String body) {
        Map<String, String> map = new HashMap<>();
        for (String pair : body.split("&")) {
            if (pair.isEmpty()) continue;
            String[] kv = pair.split("=", 2);
            String key = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
            String value = kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "";
            map.put(key, value);
        }
        return map;
    }

    static String getQueryParam(String query, String key) {
        if (query == null) return null;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv[0].equals(key)) {
                return kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "";
            }
        }
        return null;
    }

    static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    static String additionalInfo(ResultSet rs) throws SQLException {
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, String> entry : customInfoMap(rs).entrySet()) {
            appendInfo(text, entry.getKey(), entry.getValue());
        }
        return text.toString();
    }

    static Map<String, String> customInfoMap(ResultSet rs) throws SQLException {
        Map<String, String> info = new LinkedHashMap<>();
        addInfoValue(info, "Course", rs.getString("course"));
        addInfoValue(info, "CGPA", rs.getString("cgpa"));
        String extraValue = rs.getString("extra_value");
        if (extraValue != null && extraValue.contains("=")) {
            for (String pair : extraValue.split("&")) {
                String[] values = pair.split("=", 2);
                if (values.length == 2) {
                    addInfoValue(info,
                        URLDecoder.decode(values[0], StandardCharsets.UTF_8),
                        URLDecoder.decode(values[1], StandardCharsets.UTF_8));
                }
            }
        } else {
            addInfoValue(info, rs.getString("extra_heading"), extraValue);
        }
        return info;
    }

    static void addInfoValue(Map<String, String> info, String heading, String value) {
        if (heading == null || heading.isBlank() || value == null || value.isBlank()) return;
        info.putIfAbsent(heading, value);
    }

    static void appendInfo(StringBuilder info, String heading, String value) {
        if (heading == null || heading.isBlank() || value == null || value.isBlank()) return;
        if (info.length() > 0) info.append(" | ");
        info.append(heading).append(": ").append(value);
    }

    static String csvEscape(String s) {
        if (s == null) return "";
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
}
