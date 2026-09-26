package com.farm2home.customer.service;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the V8/V9 migrations actually seeded the complete, current Andhra Pradesh district AND
 * city/town master data - a data-completeness concern the rest of this package's Mockito-based
 * unit tests (which mock every repository) can't meaningfully cover; see
 * LocationMasterServiceImplTest for the service-layer logic those tests already own.
 *
 * <p>Deliberately plain JDBC against the same local Postgres every service's application.yml
 * already points at (same URL/credentials as customer-service's application.yml - this is a
 * dev-only database, not a secret), not a {@code @SpringBootTest}/Testcontainers integration test:
 * this environment has no Docker daemon (see farm2home_env_setup notes / every existing
 * Testcontainers-gated test in this repo), so a Testcontainers-based test here could never
 * actually run. Skips cleanly via {@link Assumptions} - a SKIPPED result, not a FAILURE - when
 * that database isn't reachable (e.g. a clean CI checkout with no local Postgres), rather than
 * failing for an environmental reason unrelated to this migration's correctness.
 */
class AndhraPradeshDistrictDataTest {

    private static final String URL = "jdbc:postgresql://localhost:5432/farm2home?currentSchema=customer";
    private static final String USERNAME = "farm2home";
    private static final String PASSWORD = "farm2home@123";

    // Verbatim from the task's required list - kept independent of the migration file's own
    // literal INSERT statements so this test actually catches a typo/omission in either place.
    private static final Set<String> REQUIRED_DISTRICTS = Set.of(
            "Alluri Sitharama Raju", "Anakapalli", "Anantapur", "Annamayya", "Bapatla", "Chittoor",
            "Dr. B.R. Ambedkar Konaseema", "East Godavari", "Eluru", "Guntur", "Kakinada", "Krishna",
            "Kurnool", "Nandyal", "NTR", "Palnadu", "Parvathipuram Manyam", "Prakasam",
            "Sri Potti Sriramulu Nellore", "Sri Sathya Sai", "Srikakulam", "Tirupati",
            "Visakhapatnam", "Vizianagaram", "West Godavari", "YSR Kadapa");

    private static Connection connection;

    @BeforeAll
    static void connectOrSkip() {
        try {
            connection = DriverManager.getConnection(URL, USERNAME, PASSWORD);
        } catch (SQLException ex) {
            connection = null;
            Assumptions.abort("Local Postgres not reachable at " + URL + " - skipping (not a "
                    + "failure of this migration, see this class's Javadoc): " + ex.getMessage());
        }
    }

    @AfterAll
    static void closeConnection() throws SQLException {
        if (connection != null) {
            connection.close();
        }
    }

    private List<String> apDistrictNames() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT d.name FROM location_districts d JOIN location_states s ON s.id = d.state_id "
                        + "WHERE s.code = 'AP' ORDER BY d.name")) {
            try (ResultSet rs = ps.executeQuery()) {
                List<String> names = new ArrayList<>();
                while (rs.next()) {
                    names.add(rs.getString(1));
                }
                return names;
            }
        }
    }

    @Test
    @DisplayName("Andhra Pradesh state exists")
    void andhraPradeshStateExists() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT count(*) FROM location_states WHERE code = 'AP'");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("exactly the 26 required districts exist under Andhra Pradesh - no more, no fewer")
    void exactlyTheRequired26DistrictsExist() throws SQLException {
        List<String> actual = apDistrictNames();
        assertThat(actual).hasSize(26);
        assertThat(new HashSet<>(actual)).isEqualTo(REQUIRED_DISTRICTS);
    }

    @Test
    @DisplayName("every row returned for Andhra Pradesh genuinely has state_id = Andhra Pradesh (sanity check on the join itself)")
    void everyDistrictTrulyBelongsToAndhraPradesh() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT count(*) FROM location_districts "
                        + "WHERE state_id = (SELECT id FROM location_states WHERE code = 'AP')");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(26);
        }
    }

    @Test
    @DisplayName("no duplicate district names under Andhra Pradesh")
    void noDuplicateDistrictNamesUnderAndhraPradesh() throws SQLException {
        List<String> actual = apDistrictNames();
        assertThat(actual).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("Chittoor exists under Andhra Pradesh")
    void chittoorExists() throws SQLException {
        assertThat(apDistrictNames()).contains("Chittoor");
    }

    @Test
    @DisplayName("the outdated 'Nellore' name was corrected, not left alongside the new one")
    void oldNellooreNameWasRenamedNotDuplicated() throws SQLException {
        List<String> actual = apDistrictNames();
        assertThat(actual).contains("Sri Potti Sriramulu Nellore");
        assertThat(actual).doesNotContain("Nellore");
    }

    // --- V9 (city/town master data) -----------------------------------------------------------

    @Test
    @DisplayName("every one of the 26 Andhra Pradesh districts has at least one city/town")
    void everyAndhraPradeshDistrictHasAtLeastOneCity() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT d.name FROM location_districts d "
                        + "LEFT JOIN location_cities c ON c.district_id = d.id "
                        + "WHERE d.state_id = (SELECT id FROM location_states WHERE code = 'AP') "
                        + "GROUP BY d.name HAVING count(c.id) = 0");
             ResultSet rs = ps.executeQuery()) {
            List<String> districtsWithNoCity = new ArrayList<>();
            while (rs.next()) {
                districtsWithNoCity.add(rs.getString(1));
            }
            assertThat(districtsWithNoCity).isEmpty();
        }
    }

    @Test
    @DisplayName("no orphan cities anywhere - every location_cities row's district_id resolves "
            + "to a real district (the FK already guarantees this; this proves it, and would "
            + "catch a future migration that weakens/removes that constraint)")
    void noOrphanCitiesAnywhere() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT count(*) FROM location_cities c "
                        + "LEFT JOIN location_districts d ON d.id = c.district_id "
                        + "WHERE d.id IS NULL");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            assertThat(rs.getInt(1)).isZero();
        }
    }

    @Test
    @DisplayName("no duplicate (district, city) combination under Andhra Pradesh")
    void noDuplicateCityWithinTheSameAndhraPradeshDistrict() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT d.name, c.name, count(*) FROM location_cities c "
                        + "JOIN location_districts d ON d.id = c.district_id "
                        + "WHERE d.state_id = (SELECT id FROM location_states WHERE code = 'AP') "
                        + "GROUP BY d.name, c.name HAVING count(*) > 1");
             ResultSet rs = ps.executeQuery()) {
            assertThat(rs.next()).isFalse();
        }
    }

    @Test
    @DisplayName("Annamayya contains every place this task explicitly requires: Pileru, "
            + "Madanapalle, Rayachoti, Rajampet, Punganur")
    void annamayyaContainsEveryRequiredPlace() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT c.name FROM location_cities c "
                        + "JOIN location_districts d ON d.id = c.district_id "
                        + "WHERE d.name = 'Annamayya' ORDER BY c.name")) {
            try (ResultSet rs = ps.executeQuery()) {
                List<String> annamayyaCities = new ArrayList<>();
                while (rs.next()) {
                    annamayyaCities.add(rs.getString(1));
                }
                assertThat(annamayyaCities).contains(
                        "Pileru", "Madanapalle", "Rayachoti", "Rajampet", "Punganur");
            }
        }
    }

    @Test
    @DisplayName("Pileru belongs to Annamayya specifically, not to any other district")
    void pileruBelongsToAnnamayyaOnly() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT d.name FROM location_cities c "
                        + "JOIN location_districts d ON d.id = c.district_id "
                        + "WHERE c.name = 'Pileru' "
                        + "AND d.state_id = (SELECT id FROM location_states WHERE code = 'AP')")) {
            try (ResultSet rs = ps.executeQuery()) {
                List<String> districtsOwningPileru = new ArrayList<>();
                while (rs.next()) {
                    districtsOwningPileru.add(rs.getString(1));
                }
                assertThat(districtsOwningPileru).containsExactly("Annamayya");
            }
        }
    }
}
