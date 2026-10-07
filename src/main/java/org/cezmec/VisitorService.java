package org.cezmec;

import jakarta.servlet.http.*;
import org.springframework.http.ResponseCookie;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Duration;
import java.util.*;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;

@Service
public class VisitorService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final JdbcTemplate db;
    private final Map<String,Integer> writes = new HashMap<>();
    private long minute = -1;
    private int globalWrites;
    public VisitorService(JdbcTemplate db) { this.db = db; }
    public String resolve(HttpServletRequest request, HttpServletResponse response) {
        if (request.getCookies() != null) for (Cookie c : request.getCookies()) {
            if (c.getName().equals("CEZMEC_V") && c.getValue().matches("[A-Za-z0-9_-]{43}")) {
                List<String> ids = db.query("SELECT id FROM visitors WHERE token_hash=?", (r,n)->r.getString(1), hash(c.getValue()));
                if (!ids.isEmpty()) return ids.get(0);
            }
        }
        byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String id = UUID.randomUUID().toString();
        db.update("INSERT INTO visitors(id,token_hash) VALUES (?,?)", id, hash(token));
        response.addHeader("Set-Cookie", ResponseCookie.from("CEZMEC_V", token)
            .httpOnly(true).secure(request.isSecure()).sameSite("Lax").path("/")
            .maxAge(Duration.ofDays(365)).build().toString());
        return id;
    }
    /** Pilot-level write protection. Identity is a browser credential, not proof of one human. */
    public synchronized void requireWrite(String visitor) {
        long now = System.currentTimeMillis()/60000;
        if (now != minute) { minute=now; writes.clear(); globalWrites=0; }
        int n = writes.getOrDefault(visitor,0);
        if (n >= 60 || globalWrites >= 1000)
            throw new ResponseStatusException(TOO_MANY_REQUESTS,"Слишком много изменений. Подождите минуту.");
        writes.put(visitor,n+1); globalWrites++;
    }
    public static String hash(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
