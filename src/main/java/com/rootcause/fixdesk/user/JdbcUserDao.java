package com.rootcause.fixdesk.user;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

import com.rootcause.fixdesk.common.Role;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcUserDao implements UserDao {

    private static final String COLUMNS =
            "id, name, email, password_hash, role, active, duty_status, created_at";

    private static final RowMapper<User> MAPPER = (rs, rowNum) -> new User(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getString("email"),
            rs.getString("password_hash"),
            Role.valueOf(rs.getString("role")),
            rs.getBoolean("active"),
            rs.getString("duty_status"),
            rs.getTimestamp("created_at").toLocalDateTime());

    private final JdbcTemplate jdbc;

    public JdbcUserDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<User> findByEmail(String email) {
        List<User> rows = jdbc.query("SELECT " + COLUMNS + " FROM users WHERE email = ?", MAPPER, email);
        return rows.stream().findFirst();
    }

    @Override
    public Optional<User> findById(long id) {
        List<User> rows = jdbc.query("SELECT " + COLUMNS + " FROM users WHERE id = ?", MAPPER, id);
        return rows.stream().findFirst();
    }

    @Override
    public boolean existsByEmail(String email) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email = ?", Integer.class, email);
        return count != null && count > 0;
    }

    @Override
    public boolean isActive(long id) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE id = ? AND active = TRUE", Integer.class, id);
        return count != null && count > 0;
    }

    @Override
    public long insert(String name, String email, String passwordHash, Role role) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO users (name, email, password_hash, role) VALUES (?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, name);
            ps.setString(2, email);
            ps.setString(3, passwordHash);
            ps.setString(4, role.name());
            return ps;
        }, keys);
        Number key = keys.getKey();
        if (key == null) {
            throw new IllegalStateException("users insert returned no id");
        }
        return key.longValue();
    }
}
