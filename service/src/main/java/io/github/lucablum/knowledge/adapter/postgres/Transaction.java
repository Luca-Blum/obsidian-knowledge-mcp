package io.github.lucablum.knowledge.adapter.postgres;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;

/** One database transaction: rolled back on {@link #close()} unless it was {@link #commit() committed}. */
final class Transaction implements AutoCloseable {

    /** Statements that run together or not at all. */
    interface Work {
        void run(Connection connection) throws SQLException;
    }

    private final Connection connection;
    private boolean committed;

    private Transaction(Connection connection) {
        this.connection = connection;
    }

    /** Runs {@code work} on one connection and commits, or rolls back if it fails. */
    static void run(DataSource dataSource, Work work) {
        try (Transaction transaction = begin(dataSource)) {
            work.run(transaction.connection);
            transaction.commit();
        } catch (SQLException e) {
            throw new IllegalStateException("Transaction failed", e);
        }
    }

    private static Transaction begin(DataSource dataSource) throws SQLException {
        Connection connection = dataSource.getConnection();
        try {
            connection.setAutoCommit(false);
        } catch (SQLException e) {
            connection.close();
            throw e;
        }
        return new Transaction(connection);
    }

    private void commit() throws SQLException {
        connection.commit();
        committed = true;
    }

    @Override
    public void close() throws SQLException {
        try {
            if (!committed) {
                connection.rollback();
            }
            connection.setAutoCommit(true);
        } finally {
            connection.close();
        }
    }
}
