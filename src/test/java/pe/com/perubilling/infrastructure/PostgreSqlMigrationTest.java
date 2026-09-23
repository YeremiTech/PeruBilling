package pe.com.perubilling.infrastructure;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.DriverManager;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pe.com.perubilling.shared.storage.DatabaseArtifactStorage;

@Testcontainers(disabledWithoutDocker = true)
class PostgreSqlMigrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .load()
                .migrate();
    }

    @Test
    void allExpectedTablesExistAfterMigrations() throws Exception {
        Set<String> tables = new HashSet<>();
        try (var connection = DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var statement = connection.prepareStatement(
                        "select table_name from information_schema.tables where table_schema='public'");
                var result = statement.executeQuery()) {
            while (result.next()) {
                tables.add(result.getString(1));
            }
        }

        assertTrue(tables.contains("electronic_document"));
        assertTrue(tables.contains("daily_summary"));
        assertTrue(tables.contains("voiding_batch"));
        assertTrue(tables.contains("document_access_token"));
        assertTrue(tables.contains("outbox_event"));
        assertTrue(tables.contains("audit_event"));
        assertTrue(tables.contains("document_item_allowance_charge"));
        assertTrue(tables.contains("document_allowance_charge"));
        assertTrue(tables.contains("artifact_blob"));
    }

    @Test
    void documentIssueTimeColumnExistsAfterPhase3Migration() throws Exception {
        try (var connection = DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var statement = connection.prepareStatement(
                        "select count(*) from information_schema.columns where table_schema='public' "
                                + "and table_name='electronic_document' and column_name='issue_time'");
                var result = statement.executeQuery()) {
            assertTrue(result.next());
            assertTrue(result.getInt(1) == 1);
        }
    }

    @Test
    void thermalPdfPathColumnExistsAfterLatestMigration() throws Exception {
        try (var connection = DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var statement = connection.prepareStatement(
                        "select count(*) from information_schema.columns where table_schema='public' "
                                + "and table_name='electronic_document' and column_name='thermal_pdf_path'");
                var result = statement.executeQuery()) {
            assertTrue(result.next());
            assertTrue(result.getInt(1) == 1);
        }
    }

    @Test
    void exportCustomerCountryColumnExistsAfterPhase13Migration() throws Exception {
        try (var connection = DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var statement = connection.prepareStatement(
                        "select count(*) from information_schema.columns where table_schema='public' "
                                + "and table_name='electronic_document' and column_name='customer_country_code'");
                var result = statement.executeQuery()) {
            assertTrue(result.next());
            assertTrue(result.getInt(1) == 1);
        }
    }

    @Test
    void lineAllowanceChargeColumnsExistAfterPhase7Migration() throws Exception {
        try (var connection = DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var statement = connection.prepareStatement(
                        "select count(*) from information_schema.columns where table_schema='public' "
                                + "and ((table_name='electronic_document' and column_name in ('allowance_total_amount','charge_total_amount')) "
                                + "or (table_name='electronic_document_item' and column_name in ('line_gross_amount','line_allowance_amount','line_charge_amount')))" );
                var result = statement.executeQuery()) {
            assertTrue(result.next());
            assertTrue(result.getInt(1) == 5);
        }
    }

    @Test
    void phase6SchemaUpgradesCleanlyThroughLatestMigration() throws Exception {
        String schema = "phase6_upgrade";
        Flyway phase6 = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema)
                .defaultSchema(schema)
                .target(MigrationVersion.fromVersion("9"))
                .load();
        phase6.migrate();

        Flyway latest = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema)
                .defaultSchema(schema)
                .load();
        latest.migrate();

        try (var connection = DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var statement = connection.prepareStatement(
                        "select count(*) from information_schema.tables where table_schema=? and table_name in ('document_item_allowance_charge','document_allowance_charge','artifact_blob')")) {
            statement.setString(1, schema);
            try (var result = statement.executeQuery()) {
                assertTrue(result.next());
                assertTrue(result.getInt(1) == 3);
            }
        }
    }

    @Test
    void phase8LegacyLineChargeCode50MigratesTo48() throws Exception {
        String schema = "phase8_catalog53";
        Flyway phase8 = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema)
                .defaultSchema(schema)
                .target(MigrationVersion.fromVersion("10"))
                .load();
        phase8.migrate();

        UUID tenantId = UUID.randomUUID();
        UUID issuerId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID adjustmentId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            connection.setSchema(schema);
            try (var st = connection.prepareStatement(
                    "insert into tenant(id,name,slug,status,created_at,updated_at) values (?,?,?,?,?,?)")) {
                st.setObject(1, tenantId); st.setString(2, "T"); st.setString(3, "t-" + tenantId.toString().substring(0, 8));
                st.setString(4, "ACTIVE"); st.setObject(5, now); st.setObject(6, now); st.executeUpdate();
            }
            try (var st = connection.prepareStatement(
                    "insert into issuer(id,tenant_id,ruc,business_name,address,ubigeo,sunat_environment,active,created_at,updated_at) values (?,?,?,?,?,?,?,?,?,?)")) {
                st.setObject(1, issuerId); st.setObject(2, tenantId); st.setString(3, "20123456786"); st.setString(4, "EMISOR");
                st.setString(5, "Lima"); st.setString(6, "150101"); st.setString(7, "LOCAL"); st.setBoolean(8, true);
                st.setObject(9, now); st.setObject(10, now); st.executeUpdate();
            }
            try (var st = connection.prepareStatement(
                    "insert into electronic_document(id,tenant_id,issuer_id,document_type,series,correlativo,full_number,issue_date,issue_time,operation_type,currency,customer_document_type,customer_document_number,customer_name,status,created_at,updated_at) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
                st.setObject(1, documentId); st.setObject(2, tenantId); st.setObject(3, issuerId); st.setString(4, "INVOICE");
                st.setString(5, "F001"); st.setLong(6, 1); st.setString(7, "F001-1"); st.setObject(8, java.time.LocalDate.of(2026, 9, 12));
                st.setObject(9, java.time.LocalTime.NOON); st.setString(10, "0101"); st.setString(11, "PEN"); st.setString(12, "6");
                st.setString(13, "20123456786"); st.setString(14, "CLIENTE"); st.setString(15, "QUEUED"); st.setObject(16, now); st.setObject(17, now); st.executeUpdate();
            }
            try (var st = connection.prepareStatement(
                    "insert into electronic_document_item(id,tenant_id,document_id,line_number,description,unit_code,quantity,unit_value,unit_price,tax_affectation_code,igv_rate,line_base_amount,line_igv_amount,line_total_amount,line_gross_amount,line_allowance_amount,line_charge_amount,created_at,updated_at) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
                st.setObject(1, itemId); st.setObject(2, tenantId); st.setObject(3, documentId); st.setInt(4, 1); st.setString(5, "Producto");
                st.setString(6, "NIU"); st.setBigDecimal(7, java.math.BigDecimal.ONE); st.setBigDecimal(8, new java.math.BigDecimal("100"));
                st.setBigDecimal(9, new java.math.BigDecimal("118")); st.setString(10, "10"); st.setBigDecimal(11, new java.math.BigDecimal("18"));
                st.setBigDecimal(12, new java.math.BigDecimal("100")); st.setBigDecimal(13, new java.math.BigDecimal("18")); st.setBigDecimal(14, new java.math.BigDecimal("128"));
                st.setBigDecimal(15, new java.math.BigDecimal("100")); st.setBigDecimal(16, java.math.BigDecimal.ZERO); st.setBigDecimal(17, new java.math.BigDecimal("10"));
                st.setObject(18, now); st.setObject(19, now); st.executeUpdate();
            }
            try (var st = connection.prepareStatement(
                    "insert into document_item_allowance_charge(id,tenant_id,document_id,document_item_id,line_number,sequence_number,charge_indicator,reason_code,factor,amount,base_amount,created_at,updated_at) values (?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
                st.setObject(1, adjustmentId); st.setObject(2, tenantId); st.setObject(3, documentId); st.setObject(4, itemId); st.setInt(5, 1);
                st.setInt(6, 1); st.setBoolean(7, true); st.setString(8, "50"); st.setBigDecimal(9, new java.math.BigDecimal("0.100000"));
                st.setBigDecimal(10, new java.math.BigDecimal("10")); st.setBigDecimal(11, new java.math.BigDecimal("100")); st.setObject(12, now); st.setObject(13, now); st.executeUpdate();
            }
        }

        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema).defaultSchema(schema).load().migrate();

        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            connection.setSchema(schema);
            try (var st = connection.prepareStatement(
                    "select reason_code from document_item_allowance_charge where id=?")) {
                st.setObject(1, adjustmentId);
                try (var rs = st.executeQuery()) {
                    assertTrue(rs.next());
                    assertTrue("48".equals(rs.getString(1)));
                }
            }
        }
    }

    @Test
    void databaseArtifactStoragePersistsSharedContentAndDetectsCorruption() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var insertTenant = connection.prepareStatement(
                        "insert into tenant(id,name,slug,status,created_at,updated_at) values (?,?,?,?,?,?)")) {
            insertTenant.setObject(1, tenantId);
            insertTenant.setString(2, "Artifact test tenant");
            insertTenant.setString(3, "artifact-" + tenantId.toString().substring(0, 8));
            insertTenant.setString(4, "ACTIVE");
            insertTenant.setObject(5, now);
            insertTenant.setObject(6, now);
            insertTenant.executeUpdate();
        }

        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var storage = new DatabaseArtifactStorage(JdbcClient.create(dataSource), 1024 * 1024);
        byte[] expected = "signed-xml".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String path = storage.store(tenantId, ownerId, "invoice.xml", expected);
        assertArrayEquals(expected, storage.read(path));

        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var corrupt = connection.prepareStatement(
                        "update artifact_blob set content=? where path=?")) {
            corrupt.setBytes(1, "tampered".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            corrupt.setString(2, path);
            corrupt.executeUpdate();
        }
        assertThrows(IllegalStateException.class, () -> storage.read(path));
    }

    @Test
    void auditEventIsAppendOnlyAtDatabaseLevel() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            try (var insertTenant = connection.prepareStatement(
                    "insert into tenant(id,name,slug,status,created_at,updated_at) values (?,?,?,?,?,?)")) {
                insertTenant.setObject(1, tenantId);
                insertTenant.setString(2, "Audit test tenant");
                insertTenant.setString(3, "audit-" + tenantId.toString().substring(0, 8));
                insertTenant.setString(4, "ACTIVE");
                insertTenant.setObject(5, OffsetDateTime.now());
                insertTenant.setObject(6, OffsetDateTime.now());
                insertTenant.executeUpdate();
            }
            try (var insertAudit = connection.prepareStatement(
                    "insert into audit_event(id,tenant_id,request_path,response_status,created_at,updated_at) values (?,?,?,?,?,?)")) {
                insertAudit.setObject(1, eventId);
                insertAudit.setObject(2, tenantId);
                insertAudit.setString(3, "/integration-test");
                insertAudit.setInt(4, 200);
                insertAudit.setObject(5, OffsetDateTime.now());
                insertAudit.setObject(6, OffsetDateTime.now());
                insertAudit.executeUpdate();
            }

            assertThrows(Exception.class, () -> {
                try (var update = connection.prepareStatement(
                        "update audit_event set response_status=500 where id=?")) {
                    update.setObject(1, eventId);
                    update.executeUpdate();
                }
            });
        }
    }

    @Test
    void seriesActivationCanBeUpdatedAfterLatestMigration() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID issuerId = UUID.randomUUID();
        UUID seriesId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            try (var tenant = connection.prepareStatement(
                    "insert into tenant(id,name,slug,status,created_at,updated_at) values (?,?,?,?,?,?)")) {
                tenant.setObject(1, tenantId);
                tenant.setString(2, "Series activation tenant");
                tenant.setString(3, "series-" + tenantId.toString().substring(0, 8));
                tenant.setString(4, "ACTIVE");
                tenant.setObject(5, now);
                tenant.setObject(6, now);
                tenant.executeUpdate();
            }
            try (var issuer = connection.prepareStatement(
                    "insert into issuer(id,tenant_id,ruc,business_name,address,ubigeo,sunat_environment,active,created_at,updated_at) values (?,?,?,?,?,?,?,?,?,?)")) {
                issuer.setObject(1, issuerId);
                issuer.setObject(2, tenantId);
                issuer.setString(3, "20123456786");
                issuer.setString(4, "SERIES TEST");
                issuer.setString(5, "Lima");
                issuer.setString(6, "150101");
                issuer.setString(7, "LOCAL");
                issuer.setBoolean(8, true);
                issuer.setObject(9, now);
                issuer.setObject(10, now);
                issuer.executeUpdate();
            }
            try (var series = connection.prepareStatement(
                    "insert into document_series(id,tenant_id,issuer_id,document_type,series,current_value,active,created_at,updated_at) values (?,?,?,?,?,?,?,?,?)")) {
                series.setObject(1, seriesId);
                series.setObject(2, tenantId);
                series.setObject(3, issuerId);
                series.setString(4, "INVOICE");
                series.setString(5, "F001");
                series.setLong(6, 0);
                series.setBoolean(7, true);
                series.setObject(8, now);
                series.setObject(9, now);
                series.executeUpdate();
            }
            try (var update = connection.prepareStatement(
                    "update document_series set active=false, updated_at=? where id=?")) {
                update.setObject(1, OffsetDateTime.now());
                update.setObject(2, seriesId);
                assertTrue(update.executeUpdate() == 1);
            }
            try (var select = connection.prepareStatement(
                    "select active from document_series where id=?")) {
                select.setObject(1, seriesId);
                try (var result = select.executeQuery()) {
                    assertTrue(result.next());
                    assertFalse(result.getBoolean(1));
                }
            }
        }
    }

    @Test
    void certificateFingerprintIsUniquePerIssuerNotGlobally() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID issuerA = UUID.randomUUID();
        UUID issuerB = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        String fingerprint = "a".repeat(64);

        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            try (var tenant = connection.prepareStatement(
                    "insert into tenant(id,name,slug,status,created_at,updated_at) values (?,?,?,?,?,?)")) {
                tenant.setObject(1, tenantId);
                tenant.setString(2, "Certificate scope tenant");
                tenant.setString(3, "cert-" + tenantId.toString().substring(0, 8));
                tenant.setString(4, "ACTIVE");
                tenant.setObject(5, now);
                tenant.setObject(6, now);
                tenant.executeUpdate();
            }

            insertIssuer(connection, tenantId, issuerA, "20123456786", now);
            insertIssuer(connection, tenantId, issuerB, "20600000001", now);
            insertCertificate(connection, tenantId, issuerA, UUID.randomUUID(), fingerprint, "1", now);
            insertCertificate(connection, tenantId, issuerB, UUID.randomUUID(), fingerprint, "2", now);

            assertThrows(Exception.class, () ->
                    insertCertificate(connection, tenantId, issuerA, UUID.randomUUID(), fingerprint, "3", now));
        }
    }

    private static void insertIssuer(
            java.sql.Connection connection,
            UUID tenantId,
            UUID issuerId,
            String ruc,
            OffsetDateTime now) throws Exception {
        try (var issuer = connection.prepareStatement(
                "insert into issuer(id,tenant_id,ruc,business_name,address,ubigeo,sunat_environment,active,created_at,updated_at) values (?,?,?,?,?,?,?,?,?,?)")) {
            issuer.setObject(1, issuerId);
            issuer.setObject(2, tenantId);
            issuer.setString(3, ruc);
            issuer.setString(4, "CERTIFICATE TEST");
            issuer.setString(5, "Lima");
            issuer.setString(6, "150101");
            issuer.setString(7, "LOCAL");
            issuer.setBoolean(8, true);
            issuer.setObject(9, now);
            issuer.setObject(10, now);
            issuer.executeUpdate();
        }
    }

    private static void insertCertificate(
            java.sql.Connection connection,
            UUID tenantId,
            UUID issuerId,
            UUID certificateId,
            String fingerprint,
            String serialNumber,
            OffsetDateTime now) throws Exception {
        try (var certificate = connection.prepareStatement(
                "insert into digital_certificate(id,tenant_id,issuer_id,certificate_alias,encrypted_pfx,password_encrypted,fingerprint,subject_dn,serial_number,valid_from,valid_until,active,created_at,updated_at) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            certificate.setObject(1, certificateId);
            certificate.setObject(2, tenantId);
            certificate.setObject(3, issuerId);
            certificate.setString(4, "test");
            certificate.setBytes(5, new byte[] {1, 2, 3});
            certificate.setString(6, "encrypted-password");
            certificate.setString(7, fingerprint);
            certificate.setString(8, "CN=TEST");
            certificate.setString(9, serialNumber);
            certificate.setObject(10, now.minusDays(1));
            certificate.setObject(11, now.plusDays(1));
            certificate.setBoolean(12, true);
            certificate.setObject(13, now);
            certificate.setObject(14, now);
            certificate.executeUpdate();
        }
    }

}
