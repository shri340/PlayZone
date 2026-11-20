package playzone.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import playzone.exceptions.EquipmentNotAvailableException;
import playzone.exceptions.EquipmentNotFoundException;
import playzone.model.Equipment;
import playzone.model.Rental;
import playzone.model.RentalItem;
import playzone.people.Borrower;
import playzone.people.Staff;
import playzone.people.Student;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight local web server for PlayZone.
 * Uses Java's built-in HttpServer, so no external libraries are required.
 */
public class PlayZoneWebServer {
    private static final double GST = 0.18;
    private static final int[] startingUnits = {10, 12, 20, 8, 10, 6};
    private static final Equipment[] equipment = {
        new Equipment(101, "Cricket Bat", "Cricket", 80.0, 10),
        new Equipment(102, "Football", "Football", 50.0, 12),
        new Equipment(103, "Badminton Racket", "Badminton", 40.0, 20),
        new Equipment(104, "Tennis Racket", "Tennis", 90.0, 8),
        new Equipment(105, "Basketball", "Basketball", 50.0, 10),
        new Equipment(106, "Carrom Board", "Indoor", 60.0, 6)
    };

    private static final Rental[] rentals = new Rental[500];
    private static int rentalCount = 0;
    private static int tokenCounter = 1;
    private static final Path WEB_ROOT = Path.of("web").toAbsolutePath().normalize();

    // GameBond: in-memory campus game lobbies. Nicknames only; no contact details.
    private static final GameLobby[] gameLobbies = new GameLobby[300];
    private static int gameLobbyCount = 0;
    private static int nextGameLobbyId = 1;

    // Fifty clearly labelled demo member profiles used to populate the GameBond discovery engine.
    private static final GameMember[] gameMembers = new GameMember[50];
    private static int gameMemberCount = 0;

    private static class GameMember {
        final String nickname, game, skill, time;
        final double budget;

        GameMember(String nickname, String game, String skill, String time, double budget) {
            this.nickname = nickname;
            this.game = game;
            this.skill = skill;
            this.time = time;
            this.budget = budget;
        }
    }

    private static class GameLobby {
        final String id, host, game, skill, time;
        final int maxPlayers;
        final double totalCost;
        final String[] players;
        int playerCount;

        GameLobby(String id, String host, String game, String skill, String time,
                  int maxPlayers, double totalCost) {
            this.id = id; this.host = host; this.game = game;
            this.skill = skill; this.time = time;
            this.maxPlayers = maxPlayers; this.totalCost = totalCost;
            this.players = new String[maxPlayers];
            this.players[0] = host;
            this.playerCount = 1;
        }

        boolean contains(String nickname) {
            for (int i = 0; i < playerCount; i++)
                if (players[i].equalsIgnoreCase(nickname)) return true;
            return false;
        }

        double shareIfJoined() {
            return totalCost / (playerCount + 1);
        }

        double currentShare() {
            return totalCost / Math.max(1, playerCount);
        }
    }

    public static void main(String[] args) throws IOException {
        int defaultPort = Integer.getInteger("playzone.port", 8080);
        String renderPort = System.getenv("PORT");
        int port = (renderPort == null || renderPort.isBlank())
                ? defaultPort : Integer.parseInt(renderPort);
        seedDemoGameBondMembers();
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/", PlayZoneWebServer::handleApi);
        server.createContext("/", PlayZoneWebServer::serveStatic);
        server.setExecutor(null);
        server.start();

        System.out.println("PlayZone website is running!");
        System.out.println("Open: http://localhost:" + port);
        System.out.println("Keep this terminal open while using the website.");
    }

    private static void handleApi(HttpExchange exchange) throws IOException {
        addCorsHeaders(exchange);
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        try {
            if ("OPTIONS".equalsIgnoreCase(method)) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if ("/api/health".equals(path) && "GET".equalsIgnoreCase(method)) {
                sendJson(exchange, 200, "{\"status\":\"ok\",\"service\":\"PlayZone API\"}");
            } else if ("/api/equipment".equals(path) && "GET".equalsIgnoreCase(method)) {
                sendJson(exchange, 200, equipmentJson());
            } else if ("/api/insights".equals(path) && "GET".equalsIgnoreCase(method)) {
                sendJson(exchange, 200, insightsJson());
            } else if ("/api/gamebond/members".equals(path) && "GET".equalsIgnoreCase(method)) {
                sendJson(exchange, 200, gameMembersJson());
            } else if ("/api/gamebond/lobbies".equals(path) && "GET".equalsIgnoreCase(method)) {
                sendJson(exchange, 200, gameLobbiesJson());
            } else if ("/api/gamebond/lobbies".equals(path) && "POST".equalsIgnoreCase(method)) {
                createGameLobby(exchange);
            } else if ("/api/gamebond/recommend".equals(path) && "POST".equalsIgnoreCase(method)) {
                recommendGameLobbies(exchange);
            } else if ("/api/gamebond/join".equals(path) && "POST".equalsIgnoreCase(method)) {
                joinGameLobby(exchange);
            } else if ("/api/report".equals(path) && "GET".equalsIgnoreCase(method)) {
                sendJson(exchange, 200, reportJson());
            } else if ("/api/rentals".equals(path) && "GET".equalsIgnoreCase(method)) {
                sendJson(exchange, 200, rentalsJson());
            } else if ("/api/rentals".equals(path) && "POST".equalsIgnoreCase(method)) {
                createRental(exchange);
            } else if (path.startsWith("/api/rentals/") && "GET".equalsIgnoreCase(method)) {
                String id = path.substring("/api/rentals/".length());
                findRental(exchange, id);
            } else {
                sendJson(exchange, 404, "{\"error\":\"API endpoint not found.\"}");
            }
        } catch (EquipmentNotFoundException | EquipmentNotAvailableException e) {
            sendJson(exchange, 400, "{\"error\":\"" + escape(e.getMessage()) + "\"}");
        } catch (IllegalArgumentException e) {
            sendJson(exchange, 400, "{\"error\":\"" + escape(e.getMessage()) + "\"}");
        } catch (Exception e) {
            e.printStackTrace();
            sendJson(exchange, 500, "{\"error\":\"Server error. Please try again.\"}");
        } finally {
            exchange.close();
        }
    }

    private static synchronized void createRental(HttpExchange exchange)
            throws IOException, EquipmentNotFoundException, EquipmentNotAvailableException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String name = jsonString(body, "name");
        String type = jsonString(body, "type");

        if (name.isBlank()) throw new IllegalArgumentException("Borrower name is required.");
        if (!type.equalsIgnoreCase("Student") && !type.equalsIgnoreCase("Staff")) {
            throw new IllegalArgumentException("Borrower type must be Student or Staff.");
        }

        Matcher itemMatcher = Pattern.compile("\\{\\s*\"id\"\\s*:\\s*(\\d+)\\s*,\\s*\"quantity\"\\s*:\\s*(\\d+)\\s*\\}")
                .matcher(body);
        int[] indexes = new int[100];
        int[] quantities = new int[100];
        int count = 0;
        int[] requested = new int[equipment.length];

        while (itemMatcher.find()) {
            if (count >= indexes.length) throw new IllegalArgumentException("Too many equipment lines.");
            int id = Integer.parseInt(itemMatcher.group(1));
            int quantity = Integer.parseInt(itemMatcher.group(2));
            if (quantity <= 0) throw new IllegalArgumentException("Quantity must be greater than zero.");

            int index = findEquipmentIndex(id);
            if (requested[index] + quantity > equipment[index].getUnits()) {
                throw new EquipmentNotAvailableException(
                    "Only " + (equipment[index].getUnits() - requested[index]) + " "
                    + equipment[index].getName() + "(s) available.");
            }
            requested[index] += quantity;
            indexes[count] = index;
            quantities[count] = quantity;
            count++;
        }

        if (count == 0) throw new IllegalArgumentException("Add at least one equipment item.");
        if (rentalCount >= rentals.length) throw new IllegalArgumentException("Rental storage is full.");

        // Build the bill first. Inventory is changed only after every item has passed validation.
        RentalItem[] rentalItems = new RentalItem[count];
        for (int i = 0; i < count; i++) {
            rentalItems[i] = new RentalItem(equipment[indexes[i]], quantities[i]);
        }

        Borrower borrower = type.equalsIgnoreCase("Student")
                ? new Student(name) : new Staff(name);
        Rental rental = new Rental(borrower, rentalItems, tokenCounter);

        // Commit stock changes after successful validation and object construction.
        for (int i = 0; i < count; i++) {
            equipment[indexes[i]].reduceUnits(quantities[i]);
        }

        tokenCounter++;
        rentals[rentalCount++] = rental;
        sendJson(exchange, 201, rentalJson(rental));
    }

    private static int findEquipmentIndex(int id) throws EquipmentNotFoundException {
        for (int i = 0; i < equipment.length; i++) {
            if (equipment[i].getId() == id) return i;
        }
        throw new EquipmentNotFoundException("Equipment ID " + id + " does not exist.");
    }


    // -------------------- GAMEBOND: CAMPUS MULTIPLAYER MATCHMAKER --------------------
    private static final String[] ALLOWED_GAMES = {
        "Chess", "Carrom", "Badminton", "Football", "Basketball",
        "Table Tennis", "Volleyball", "Cricket"
    };
    private static final String[] ALLOWED_SKILLS = {"Beginner", "Intermediate", "Advanced"};
    private static final String[] ALLOWED_TIMES = {"Morning", "Afternoon", "Evening"};

    /**
     * Populate GameBond with 50 fictional sample members and one open game circle per member.
     * These are demo profiles, not real students or collected personal information.
     */
    private static synchronized void seedDemoGameBondMembers() {
        if (gameMemberCount > 0) return;

        String[] games = {"Chess", "Carrom", "Badminton", "Football", "Basketball",
                          "Table Tennis", "Volleyball", "Cricket"};
        String[] skills = {"Beginner", "Intermediate", "Advanced"};
        String[] times = {"Morning", "Afternoon", "Evening"};

        for (int i = 0; i < 50; i++) {
            String nickname = "CampusPlayer" + String.format(java.util.Locale.US, "%02d", i + 1);
            String game = games[(i * 3 + i / 4) % games.length];
            String skill = skills[(i + i / 5) % skills.length];
            String time = times[(i * 2 + i / 7) % times.length];
            double budget = 60 + ((i * 37) % 191);
            GameMember member = new GameMember(nickname, game, skill, time, budget);
            gameMembers[gameMemberCount++] = member;

            int maxPlayers = 2 + (i % 4);
            double totalCost = Math.round(budget * (1.35 + (i % 3) * 0.15));
            String id = "GB" + String.format(java.util.Locale.US, "%04d", nextGameLobbyId++);
            gameLobbies[gameLobbyCount++] = new GameLobby(
                    id, nickname, game, skill, time, maxPlayers, totalCost);
        }
    }

    private static synchronized String gameMembersJson() {
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < gameMemberCount; i++) {
            GameMember member = gameMembers[i];
            if (i > 0) out.append(",");
            out.append("{")
                .append("\"nickname\":\"").append(escape(member.nickname)).append("\",")
                .append("\"game\":\"").append(escape(member.game)).append("\",")
                .append("\"skill\":\"").append(escape(member.skill)).append("\",")
                .append("\"time\":\"").append(escape(member.time)).append("\",")
                .append("\"budget\":").append(money(member.budget)).append(",")
                .append("\"demo\":true}");
        }
        return out.append("]").toString();
    }

    private static boolean oneOf(String value, String[] choices) {
        for (String choice : choices) if (choice.equalsIgnoreCase(value)) return true;
        return false;
    }

    private static int skillRank(String skill) {
        if ("Beginner".equalsIgnoreCase(skill)) return 0;
        if ("Intermediate".equalsIgnoreCase(skill)) return 1;
        return 2;
    }

    private static int jsonInt(String json, String key, int fallback) {
        Matcher m = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*(-?\\d+)").matcher(json);
        return m.find() ? Integer.parseInt(m.group(1)) : fallback;
    }

    private static double jsonDouble(String json, String key, double fallback) {
        Matcher m = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)").matcher(json);
        return m.find() ? Double.parseDouble(m.group(1)) : fallback;
    }

    private static synchronized void createGameLobby(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String nickname = jsonString(body, "nickname");
        String game = jsonString(body, "game");
        String skill = jsonString(body, "skill");
        String time = jsonString(body, "time");
        int maxPlayers = jsonInt(body, "maxPlayers", 2);
        double totalCost = jsonDouble(body, "totalCost", -1);

        if (nickname.isBlank() || nickname.length() > 22)
            throw new IllegalArgumentException("Choose a nickname of 1–22 characters.");
        if (!oneOf(game, ALLOWED_GAMES)) throw new IllegalArgumentException("Choose a supported game.");
        if (!oneOf(skill, ALLOWED_SKILLS)) throw new IllegalArgumentException("Choose a skill level.");
        if (!oneOf(time, ALLOWED_TIMES)) throw new IllegalArgumentException("Choose a valid time slot.");
        if (maxPlayers < 2 || maxPlayers > 10)
            throw new IllegalArgumentException("Player capacity must be between 2 and 10.");
        if (totalCost < 0 || totalCost > 100000)
            throw new IllegalArgumentException("Enter an estimated total expense from ₹0 to ₹100000.");
        if (gameLobbyCount >= gameLobbies.length)
            throw new IllegalArgumentException("GameBond lobby storage is full.");

        String id = "GB" + String.format(java.util.Locale.US, "%04d", nextGameLobbyId++);
        GameLobby lobby = new GameLobby(id, nickname, game, skill, time, maxPlayers, totalCost);
        gameLobbies[gameLobbyCount++] = lobby;
        sendJson(exchange, 201, gameLobbyJson(lobby, 100, lobby.shareIfJoined()));
    }

    private static synchronized void recommendGameLobbies(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String nickname = jsonString(body, "nickname");
        String game = jsonString(body, "game");
        String skill = jsonString(body, "skill");
        String time = jsonString(body, "time");
        double budget = jsonDouble(body, "budget", 0);

        if (nickname.isBlank()) throw new IllegalArgumentException("Enter your player nickname.");
        if (!oneOf(game, ALLOWED_GAMES)) throw new IllegalArgumentException("Choose a supported game.");
        if (!oneOf(skill, ALLOWED_SKILLS)) throw new IllegalArgumentException("Choose a skill level.");
        if (!oneOf(time, ALLOWED_TIMES)) throw new IllegalArgumentException("Choose a time slot.");
        if (budget < 0 || budget > 100000) throw new IllegalArgumentException("Enter a valid per-person budget.");

        StringBuilder out = new StringBuilder("[");
        boolean first = true;
        int[] scores = new int[gameLobbyCount];
        boolean[] include = new boolean[gameLobbyCount];
        double[] shares = new double[gameLobbyCount];

        for (int i = 0; i < gameLobbyCount; i++) {
            GameLobby lobby = gameLobbies[i];
            if (!lobby.game.equalsIgnoreCase(game) || lobby.playerCount >= lobby.maxPlayers
                    || lobby.contains(nickname)) continue;
            double share = lobby.shareIfJoined();
            if (share > budget) continue;

            int score = 40; // game match
            score += lobby.time.equalsIgnoreCase(time) ? 25 : 0;
            int skillGap = Math.abs(skillRank(skill) - skillRank(lobby.skill));
            score += Math.max(4, 20 - skillGap * 8);
            score += 15; // within the player's stated budget
            scores[i] = score;
            shares[i] = share;
            include[i] = true;
        }

        // Rank matching open groups using arrays only (no collections or maps).
        boolean[] emitted = new boolean[gameLobbyCount];
        for (int rank = 0; rank < gameLobbyCount; rank++) {
            int best = -1;
            for (int i = 0; i < gameLobbyCount; i++) {
                if (include[i] && !emitted[i] && (best == -1 || scores[i] > scores[best])) best = i;
            }
            if (best == -1) break;
            emitted[best] = true;
            if (!first) out.append(",");
            out.append(gameLobbyJson(gameLobbies[best], scores[best], shares[best]));
            first = false;
        }
        sendJson(exchange, 200, out.append("]").toString());
    }

    private static synchronized void joinGameLobby(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String id = jsonString(body, "id");
        String nickname = jsonString(body, "nickname");
        if (id.isBlank() || nickname.isBlank())
            throw new IllegalArgumentException("Lobby ID and player nickname are required.");
        if (nickname.length() > 22) throw new IllegalArgumentException("Nickname must be 22 characters or fewer.");

        for (int i = 0; i < gameLobbyCount; i++) {
            GameLobby lobby = gameLobbies[i];
            if (!lobby.id.equalsIgnoreCase(id)) continue;
            if (lobby.contains(nickname)) throw new IllegalArgumentException("You are already in this game group.");
            if (lobby.playerCount >= lobby.maxPlayers) throw new IllegalArgumentException("This game group is already full.");
            lobby.players[lobby.playerCount++] = nickname;
            sendJson(exchange, 200, gameLobbyJson(lobby, 100, lobby.currentShare()));
            return;
        }
        sendJson(exchange, 404, "{\"error\":\"Game group not found. Refresh recommendations.\"}");
    }

    private static synchronized String gameLobbiesJson() {
        StringBuilder out = new StringBuilder("[");
        boolean first = true;
        for (int i = 0; i < gameLobbyCount; i++) {
            GameLobby lobby = gameLobbies[i];
            if (lobby.playerCount >= lobby.maxPlayers) continue;
            if (!first) out.append(",");
            out.append(gameLobbyJson(lobby, 0, lobby.shareIfJoined()));
            first = false;
        }
        return out.append("]").toString();
    }

    private static String gameLobbyJson(GameLobby lobby, int score, double share) {
        StringBuilder players = new StringBuilder("[");
        for (int i = 0; i < lobby.playerCount; i++) {
            if (i > 0) players.append(",");
            players.append("\"").append(escape(lobby.players[i])).append("\"");
        }
        players.append("]");
        return "{" +
            "\"id\":\"" + lobby.id + "\"," +
            "\"host\":\"" + escape(lobby.host) + "\"," +
            "\"game\":\"" + escape(lobby.game) + "\"," +
            "\"skill\":\"" + lobby.skill + "\"," +
            "\"time\":\"" + lobby.time + "\"," +
            "\"maxPlayers\":" + lobby.maxPlayers + "," +
            "\"playerCount\":" + lobby.playerCount + "," +
            "\"totalCost\":" + money(lobby.totalCost) + "," +
            "\"shareIfJoined\":" + money(share) + "," +
            "\"currentShare\":" + money(lobby.currentShare()) + "," +
            "\"compatibility\":" + score + "," +
            "\"players\":" + players + "}";
    }

    /**
     * Rule-based stock intelligence derived from actual rental quantities.
     * This is transparent decision support, not a trained ML forecast.
     */
    private static synchronized String insightsJson() {
        int[] rented = new int[equipment.length];
        int[] frequency = new int[equipment.length];
        for (int r = 0; r < rentalCount; r++) {
            for (RentalItem line : rentals[r].getItems()) {
                for (int i = 0; i < equipment.length; i++) {
                    if (equipment[i].getId() == line.getEquipment().getId()) {
                        rented[i] += line.getQuantity();
                        frequency[i]++;
                        break;
                    }
                }
            }
        }
        int max = 0, low = 0;
        for (int i = 0; i < equipment.length; i++) {
            max = Math.max(max, rented[i]);
            if (equipment[i].getUnits() < 10) low++;
        }
        StringBuilder items = new StringBuilder("[");
        for (int i = 0; i < equipment.length; i++) {
            if (i > 0) items.append(",");
            Equipment e = equipment[i];
            String risk = e.getUnits() <= 3 ? "CRITICAL"
                    : e.getUnits() < 10 ? "WATCH" : "HEALTHY";
            int score = max == 0 ? 0 : (int)Math.round(rented[i] * 100.0 / max);
            int restock = Math.max(0, startingUnits[i] - e.getUnits());
            items.append("{")
                .append("\"id\":").append(e.getId()).append(",")
                .append("\"name\":\"").append(escape(e.getName())).append("\",")
                .append("\"available\":").append(e.getUnits()).append(",")
                .append("\"rented\":").append(rented[i]).append(",")
                .append("\"rentalFrequency\":").append(frequency[i]).append(",")
                .append("\"demandScore\":").append(score).append(",")
                .append("\"risk\":\"").append(risk).append("\",")
                .append("\"suggestedRestock\":").append(restock).append("}");
        }
        items.append("]");
        String leader = "No rental activity yet";
        if (max > 0) {
            int best = 0;
            for (int i = 1; i < rented.length; i++) if (rented[i] > rented[best]) best = i;
            leader = "Demand leader: " + equipment[best].getName();
        }
        return "{\"lowStockCount\":" + low + ",\"totalRentals\":" + rentalCount
                + ",\"headline\":\"" + escape(leader) + "\",\"items\":" + items + "}";
    }

    private static synchronized String equipmentJson() {
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < equipment.length; i++) {
            Equipment e = equipment[i];
            if (i > 0) out.append(",");
            out.append("{")
               .append("\"id\":").append(e.getId()).append(",")
               .append("\"name\":\"").append(escape(e.getName())).append("\",")
               .append("\"category\":\"").append(escape(e.getCategory())).append("\",")
               .append("\"rent\":").append(e.getRentPerDay()).append(",")
               .append("\"units\":").append(e.getUnits()).append(",")
               .append("\"status\":\"").append(e.getUnits() < 10 ? "LOW" : "OK").append("\"")
               .append("}");
        }
        return out.append("]").toString();
    }

    private static synchronized String rentalsJson() {
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < rentalCount; i++) {
            if (i > 0) out.append(",");
            out.append(rentalSummaryJson(rentals[i]));
        }
        return out.append("]").toString();
    }

    private static synchronized String reportJson() {
        double revenue = 0;
        int[] popularity = new int[equipment.length];
        for (int i = 0; i < rentalCount; i++) {
            Rental r = rentals[i];
            revenue += r.getFinalTotal(GST);
            for (RentalItem line : r.getItems()) {
                for (int j = 0; j < equipment.length; j++) {
                    if (equipment[j].getId() == line.getEquipment().getId()) {
                        popularity[j] += line.getQuantity();
                        break;
                    }
                }
            }
        }

        int popularIndex = -1;
        for (int i = 0; i < popularity.length; i++) {
            if (popularIndex == -1 || popularity[i] > popularity[popularIndex]) popularIndex = i;
        }
        String popular = (popularIndex < 0 || popularity[popularIndex] == 0)
                ? "No rentals yet" : equipment[popularIndex].getName();

        return "{\"totalRentals\":" + rentalCount
                + ",\"revenue\":" + String.format(java.util.Locale.US, "%.2f", revenue)
                + ",\"popular\":\"" + escape(popular) + "\"}";
    }

    private static synchronized void findRental(HttpExchange exchange, String id) throws IOException {
        for (int i = 0; i < rentalCount; i++) {
            if (rentals[i].getRentalId().equalsIgnoreCase(id)) {
                sendJson(exchange, 200, rentalJson(rentals[i]));
                return;
            }
        }
        sendJson(exchange, 404, "{\"error\":\"No bill found for " + escape(id) + "\"}");
    }

    private static String rentalSummaryJson(Rental r) {
        return "{\"id\":\"" + r.getRentalId() + "\",\"name\":\""
                + escape(r.getBorrower().getName()) + "\",\"type\":\""
                + r.getBorrower().getBorrowerType() + "\",\"total\":"
                + String.format(java.util.Locale.US, "%.2f", r.getFinalTotal(GST)) + "}";
    }

    private static String rentalJson(Rental r) {
        StringBuilder lines = new StringBuilder("[");
        RentalItem[] items = r.getItems();
        for (int i = 0; i < items.length; i++) {
            if (i > 0) lines.append(",");
            RentalItem item = items[i];
            lines.append("{\"id\":").append(item.getEquipment().getId())
                 .append(",\"name\":\"").append(escape(item.getEquipment().getName()))
                 .append("\",\"quantity\":").append(item.getQuantity())
                 .append(",\"rate\":").append(item.getEquipment().getRentPerDay())
                 .append(",\"amount\":").append(String.format(java.util.Locale.US, "%.2f", item.getLineTotal()))
                 .append("}");
        }
        double subtotal = r.getSubtotal();
        double discount = r.getDiscountAmount();
        double taxable = subtotal - discount;
        double gst = taxable * GST;
        String room = (r.getTokenNumber() & 1) == 1 ? "Store Room A" : "Store Room B";

        return "{\"id\":\"" + r.getRentalId() + "\","
                + "\"name\":\"" + escape(r.getBorrower().getName()) + "\","
                + "\"type\":\"" + r.getBorrower().getBorrowerType() + "\","
                + "\"token\":" + r.getTokenNumber() + ","
                + "\"room\":\"" + room + "\","
                + "\"items\":" + lines + "],"
                + "\"subtotal\":" + money(subtotal) + ","
                + "\"discount\":" + money(discount) + ","
                + "\"discountRate\":" + money(r.getBorrower().getDiscountRate() * 100) + ","
                + "\"gst\":" + money(gst) + ","
                + "\"total\":" + money(r.getFinalTotal(GST)) + "}";
    }

    private static String money(double value) {
        return String.format(java.util.Locale.US, "%.2f", value);
    }

    private static String jsonString(String json, String key) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"")
                .matcher(json);
        return m.find() ? m.group(1).trim() : "";
    }

    private static void serveStatic(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (path.equals("/")) path = "/index.html";

        Path requested = WEB_ROOT.resolve(path.substring(1)).normalize();
        if (!requested.startsWith(WEB_ROOT) || !Files.exists(requested) || Files.isDirectory(requested)) {
            byte[] notFound = "404 - File not found".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(404, notFound.length);
            exchange.getResponseBody().write(notFound);
            exchange.close();
            return;
        }

        String contentType = requested.toString().endsWith(".css") ? "text/css; charset=utf-8"
                : requested.toString().endsWith(".js") ? "application/javascript; charset=utf-8"
                : "text/html; charset=utf-8";
        exchange.getResponseHeaders().set("Content-Type", contentType);
        byte[] data = Files.readAllBytes(requested);
        exchange.sendResponseHeaders(200, data.length);
        exchange.getResponseBody().write(data);
        exchange.close();
    }

    private static void addCorsHeaders(HttpExchange exchange) {
        // Set CORS_ALLOWED_ORIGIN on the backend to the deployed frontend URL.
        // The wildcard default keeps local development simple; production should use
        // the exact Render Static Site origin.
        String allowedOrigin = System.getenv().getOrDefault("CORS_ALLOWED_ORIGIN", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", allowedOrigin);
        exchange.getResponseHeaders().set("Vary", "Origin");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }

    private static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] data = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, data.length);
        exchange.getResponseBody().write(data);
    }

    private static String escape(String value) {
        return value.replace("\\\\", "\\\\\\\\").replace("\"", "\\\\\"")
                .replace("\\n", " ").replace("\\r", " ");
    }
}
