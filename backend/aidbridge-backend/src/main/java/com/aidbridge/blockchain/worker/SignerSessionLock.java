package com.aidbridge.blockchain.worker;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Optional;
import javax.sql.DataSource;

/**
 * A session-level PostgreSQL advisory lock, held across RPC without an open SQL transaction.
 * Every process using the signer must use the same database and this protocol. A live holder is
 * never superseded merely because a job lease expires. Poolers must use session pooling, not
 * transaction pooling. Losing the database session is fail-closed before the next side effect.
 */
public final class SignerSessionLock {
    private final DataSource dataSource;

    public SignerSessionLock(DataSource dataSource) { this.dataSource = dataSource; }

    public Optional<Guard> tryAcquire(long chainId, String signer) {
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            if (!connection.getAutoCommit()) throw new SQLException("Session lock requires auto-commit");
            String key = "aidbridge:signer:" + chainId + ":" + signer.toLowerCase(Locale.ROOT);
            try (var statement = connection.prepareStatement(
                    "SELECT pg_try_advisory_lock(hashtextextended(?, 0)), pg_backend_pid()")) {
                statement.setString(1, key);
                try (var result = statement.executeQuery()) {
                    result.next();
                    if (result.getBoolean(1)) return Optional.of(new Guard(connection, key, result.getInt(2)));
                }
            }
            connection.close();
            return Optional.empty();
        } catch (SQLException e) {
            discard(connection);
            throw new IllegalStateException("SIGNER_LOCK_UNAVAILABLE");
        }
    }

    private static void discard(Connection connection) {
        if (connection == null) return;
        try { connection.abort(Runnable::run); } catch (SQLException ignored) { }
        try { connection.close(); } catch (SQLException ignored) { }
    }

    public static final class Guard implements AutoCloseable {
        private final Connection connection;
        private final String key;
        private final int backendPid;
        private boolean closed;

        private Guard(Connection connection, String key, int backendPid) {
            this.connection = connection; this.key = key; this.backendPid = backendPid;
        }

        public void assertHeld() {
            if (closed) throw new IllegalStateException("SIGNER_LOCK_LOST");
            try (var statement = connection.prepareStatement("SELECT pg_backend_pid()")) {
                try (var result = statement.executeQuery()) {
                    if (!result.next() || result.getInt(1) != backendPid)
                        throw new SQLException("Database session changed");
                }
            } catch (SQLException e) {
                closed = true;
                discard(connection);
                throw new IllegalStateException("SIGNER_LOCK_LOST");
            }
        }

        @Override public void close() {
            if (closed) return;
            closed = true;
            try (var statement = connection.prepareStatement(
                    "SELECT pg_advisory_unlock(hashtextextended(?, 0))")) {
                statement.setString(1, key);
                try (var result = statement.executeQuery()) {
                    if (!result.next() || !result.getBoolean(1)) throw new SQLException("Lock not owned");
                }
            } catch (SQLException e) {
                discard(connection);
                return;
            }
            try { connection.close(); } catch (SQLException e) { discard(connection); }
        }
    }
}
