package com.amiva;

import java.util.Collection;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// Borra los datos de prueba de unos usuarios: actividad, su organización y el usuario.
// activity es inmutable: se desactiva su trigger solo dentro de esta transacción (si algo falla, rollback lo reactiva)
public final class TestCleanup {

    private TestCleanup() {
    }

    public static void deleteUsers(JdbcTemplate jdbc, Collection<String> emails) {
        new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource())).executeWithoutResult(tx -> {
            jdbc.execute("ALTER TABLE activity DISABLE TRIGGER trg_activity_immutable");
            for (String email : emails) {
                jdbc.update("""
                        DELETE FROM activity WHERE organization_id IN (
                            SELECT o.id FROM organization o JOIN app_user u ON u.id = o.owner_id
                            WHERE lower(u.email) = lower(?))
                        """, email);
                jdbc.update("DELETE FROM organization WHERE owner_id IN "
                        + "(SELECT id FROM app_user WHERE lower(email) = lower(?))", email);
                jdbc.update("DELETE FROM app_user WHERE lower(email) = lower(?)", email);
            }
            jdbc.execute("ALTER TABLE activity ENABLE TRIGGER trg_activity_immutable");
        });
    }

    public static void deleteUsers(JdbcTemplate jdbc, String... emails) {
        deleteUsers(jdbc, java.util.List.of(emails));
    }
}
