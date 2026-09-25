package db.migration;

import com.mrms.rates.RateCsv;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Loads the CGHS rate list of 03.10.2025 (Tier I, effective 13.10.2025) from
 * the CSV shipped with the application. Runs once, like any migration, so the
 * seeded list is part of the schema history. Later lists are imported by an
 * administrator through the application.
 */
public class V7__Seed_cghs_2025_rates extends BaseJavaMigration {

    private static final String CSV = "/db/rates/cghs-2025-tier1.csv";

    @Override
    public void migrate(Context context) throws Exception {
        List<RateCsv.Row> rows;
        try (InputStream in = V7__Seed_cghs_2025_rates.class.getResourceAsStream(CSV)) {
            if (in == null) {
                throw new IllegalStateException("Missing " + CSV);
            }
            rows = RateCsv.parse(in);
        }

        Connection c = context.getConnection();
        try (PreparedStatement list = c.prepareStatement("""
                insert into rate_list (code, title, order_reference, source_url, source_sha256, city_tier,
                                       effective_from, effective_to, notes, active, item_count, imported_by, imported_at)
                values (?, ?, ?, ?, ?, ?, ?, null, ?, true, ?, null, ?)
                """)) {
            list.setString(1, "CGHS-2025-T1");
            list.setString(2, "CGHS rates 2025, Tier I (X) cities, semi private ward");
            list.setString(3, "O.M. F.No.5-16/CGHS(HQ)/HEC/2024(PartI) dated 03.10.2025, Annexure I (A)");
            list.setString(4, "https://dgehs.delhi.gov.in/sites/default/files/inline-files/cghs_rate.pdf");
            list.setString(5, "964b42f58eb0852d93293a658a183cceaafae33ba30a892979262c21695b9746");
            list.setString(6, "X");
            list.setDate(7, Date.valueOf(LocalDate.of(2025, 10, 13)));
            list.setString(8, "Published on the Directorate General of Health Services, Delhi website. Confirm the "
                    + "DGEHS adoption order and its effective date before relying on these rates.");
            list.setInt(9, rows.size());
            list.setTimestamp(10, Timestamp.from(Instant.now()));
            list.executeUpdate();
        }
        long listId;
        try (PreparedStatement q = c.prepareStatement("select id from rate_list where code = 'CGHS-2025-T1'");
             ResultSet rs = q.executeQuery()) {
            rs.next();
            listId = rs.getLong(1);
        }
        try (PreparedStatement item = c.prepareStatement("""
                insert into rate_item (rate_list_id, serial_no, code, name, speciality,
                                       rate_non_nabh, rate_nabh, rate_super_speciality)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            for (RateCsv.Row r : rows) {
                item.setLong(1, listId);
                if (r.serialNo() == null) {
                    item.setNull(2, java.sql.Types.INTEGER);
                } else {
                    item.setInt(2, r.serialNo());
                }
                item.setString(3, r.code());
                item.setString(4, r.name());
                item.setString(5, r.speciality());
                item.setBigDecimal(6, r.nonNabh());
                item.setBigDecimal(7, r.nabh());
                item.setBigDecimal(8, r.superSpeciality());
                item.addBatch();
            }
            item.executeBatch();
        }
    }
}
