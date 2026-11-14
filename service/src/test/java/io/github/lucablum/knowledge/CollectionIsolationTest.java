package io.github.lucablum.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The deploy script {@code deploy/postgres/isolate-collection.sql} gives each Collection its own role, which owns the
 * Collection's schema and cannot read another's.
 */
@QuarkusTest
class CollectionIsolationTest {

    private static final Path SCRIPT = Path.of("../deploy/postgres/isolate-collection.sql");

    private static final List<String> COLLECTIONS = List.of("isolation_engineering", "isolation_training");

    @Inject
    AgroalDataSource superuser;

    @ConfigProperty(name = "quarkus.datasource.jdbc.url")
    String url;

    @BeforeEach
    void noCollectionRoles() throws SQLException {
        try (Connection connection = superuser.getConnection(); Statement statement = connection.createStatement()) {
            for (String collection : COLLECTIONS) {
                statement.execute("DROP SCHEMA IF EXISTS %s CASCADE".formatted(collection));
                statement.execute("DROP ROLE IF EXISTS %s".formatted(collection));
            }
        }
    }

    @Test
    void whenMigrate_givenIsolatedCollection_thenItsRoleBuildsTheIndexInItsSchema() throws Exception {
        isolate("isolation_engineering");

        migrateAs("isolation_engineering");

        assertThat(countNotesAs("isolation_engineering", "isolation_engineering")).isZero();
    }

    @Test
    void whenReadOtherSchema_givenTwoIsolatedCollections_thenPermissionDenied() throws Exception {
        isolate("isolation_engineering");
        isolate("isolation_training");
        migrateAs("isolation_training");

        assertThatThrownBy(() -> countNotesAs("isolation_engineering", "isolation_training"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("permission denied");
    }

    @Test
    void whenCreateTableInPublic_givenIsolatedCollection_thenPermissionDenied() throws Exception {
        isolate("isolation_engineering");

        assertThatThrownBy(() -> executeAs("isolation_engineering", "CREATE TABLE public.leak (id INT)"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("permission denied");
    }

    @Test
    void whenIsolateTwice_givenSchemaBuiltBeforeTheRoleExisted_thenHandItsIndexToTheRole() throws Exception {
        try (Connection connection = superuser.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA isolation_engineering");
        }
        migrate(superuser, "isolation_engineering");

        isolate("isolation_engineering");
        isolate("isolation_engineering");

        migrateAs("isolation_engineering");
        assertThat(countNotesAs("isolation_engineering", "isolation_engineering")).isZero();
    }

    /** Runs the deploy script and its procedure in one session, as the {@code postgres-init} service does. */
    private void isolate(String collection) throws SQLException, IOException {
        try (Connection connection = superuser.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(Files.readString(SCRIPT));
            statement.execute("CALL pg_temp.isolate_collection('%s', '%s')".formatted(collection, password(collection)));
        }
    }

    private void migrateAs(String collection) {
        migrate(Flyway.configure().dataSource(url, collection, password(collection)), collection);
    }

    private static void migrate(AgroalDataSource dataSource, String schema) {
        migrate(Flyway.configure().dataSource(dataSource), schema);
    }

    private static void migrate(FluentConfiguration flyway, String schema) {
        flyway.schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration")
                .placeholders(Map.of("dimension", "3"))
                .load()
                .migrate();
    }

    private int countNotesAs(String collection, String schema) throws SQLException {
        try (Connection connection = connectAs(collection);
                ResultSet count = connection.createStatement()
                        .executeQuery("SELECT count(*) FROM %s.note".formatted(schema))) {
            count.next();
            return count.getInt(1);
        }
    }

    private void executeAs(String collection, String sql) throws SQLException {
        try (Connection connection = connectAs(collection); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private Connection connectAs(String collection) throws SQLException {
        return DriverManager.getConnection(url, collection, password(collection));
    }

    private static String password(String collection) {
        return collection + "-password";
    }
}
