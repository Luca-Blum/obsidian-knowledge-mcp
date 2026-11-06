package io.github.lucablum.knowledge.adapter.postgres;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;

/** One database transaction: rolled back on {@link #close()} unless it was {@link #commit() committed}. */
final class Transaction implements AutoCloseable {

    private final Connection connection;
    private boolean committed;

    private Transaction(Connection connection) {
        this.connection = connection;
    }

    static Transaction begin(DataSource dataSource) throws SQLException {
        Connection connection = dataSource.getConnection();
        try {
            connection.setAutoCommit(false);
        } catch (SQLException e) {
            connection.close();
            throw e;
        }
        return new Transaction(connection);
    }

    Connection connection() {
        return connection;
    }

    void commit() throws SQLException {
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
