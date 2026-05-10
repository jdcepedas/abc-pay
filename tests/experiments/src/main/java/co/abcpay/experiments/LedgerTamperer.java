package co.abcpay.experiments;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Simulates an offline attacker with privileged DB access tampering with the
 * historical ledger. Each method represents one B-suite mutation and is
 * idempotent enough to be run inside a fresh, seeded ledger.
 */
public class LedgerTamperer {

    private final String jdbcUrl;
    private final String user;
    private final String password;

    public LedgerTamperer(String jdbcUrl, String user, String password) {
        this.jdbcUrl = jdbcUrl;
        this.user = user;
        this.password = password;
    }

    public void truncate() throws SQLException {
        try (Connection c = open(); Statement s = c.createStatement()) {
            s.executeUpdate("TRUNCATE TABLE ledger.ledger_entry RESTART IDENTITY");
            s.executeUpdate(
                    "UPDATE ledger.anchor SET last_seq = 0, " +
                    "last_record_hash = '0000000000000000000000000000000000000000000000000000000000000000', " +
                    "anchor_hmac = '', updated_at_iso = '1970-01-01T00:00:00Z' WHERE id = 1");
        }
    }

    /** B1: delete the most recent row. */
    public void deleteLastRow() throws SQLException {
        try (Connection c = open(); Statement s = c.createStatement()) {
            s.executeUpdate(
                    "DELETE FROM ledger.ledger_entry " +
                    "WHERE seq = (SELECT MAX(seq) FROM ledger.ledger_entry)");
        }
    }

    /** B2: alter the payload of a middle row but leave hashes untouched. */
    public void alterMiddleRow() throws SQLException {
        try (Connection c = open()) {
            long count;
            try (Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM ledger.ledger_entry")) {
                rs.next();
                count = rs.getLong(1);
            }
            if (count < 2) throw new IllegalStateException("ledger too small");

            long midOffset = count / 2;
            long mid;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT seq FROM ledger.ledger_entry ORDER BY seq ASC LIMIT 1 OFFSET ?")) {
                ps.setLong(1, midOffset);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new IllegalStateException("midpoint not found");
                    mid = rs.getLong(1);
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE ledger.ledger_entry SET payload_json = ? WHERE seq = ?")) {
                ps.setString(1, "{\"tampered\":true}");
                ps.setLong(2, mid);
                ps.executeUpdate();
            }
        }
    }

    /** B3: insert a forged row at the end without computing a valid chain. */
    public void insertForgedRow() throws SQLException {
        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO ledger.ledger_entry " +
                     "(event_id, event_type, payload_json, payload_hash, prev_hash, record_hash, created_at_iso) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, "forged-event");
            ps.setString(2, "forged.event");
            ps.setString(3, "{\"forged\":true}");
            ps.setString(4, "deadbeef".repeat(8));
            ps.setString(5, "cafebabe".repeat(8));
            ps.setString(6, "abad1dea".repeat(8));
            ps.setString(7, "1970-01-01T00:00:00Z");
            ps.executeUpdate();
        }
    }

    /** B4: swap the payloads of two adjacent rows so both hashes break. */
    public void reorderRows() throws SQLException {
        try (Connection c = open()) {
            long first;
            long second;
            try (Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery(
                         "SELECT seq FROM ledger.ledger_entry ORDER BY seq ASC LIMIT 2")) {
                if (!rs.next()) throw new IllegalStateException("ledger empty");
                first = rs.getLong(1);
                if (!rs.next()) throw new IllegalStateException("only one row");
                second = rs.getLong(1);
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE ledger.ledger_entry a SET payload_json = b.payload_json " +
                    "FROM ledger.ledger_entry b WHERE a.seq = ? AND b.seq = ?")) {
                ps.setLong(1, first); ps.setLong(2, second); ps.executeUpdate();
            }
        }
    }

    private Connection open() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, user, password);
    }
}
