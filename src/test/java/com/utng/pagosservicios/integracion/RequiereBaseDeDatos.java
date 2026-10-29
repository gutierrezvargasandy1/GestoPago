package com.utng.pagosservicios.integracion;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.DockerClientFactory;

/** Omite (no falla) las pruebas de integracion si no hay Docker ni TEST_DB_URL. */
public class RequiereBaseDeDatos implements ExecutionCondition {

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext contexto) {
        if (BaseDeDatosPrueba.urlExterna() != null) {
            return ConditionEvaluationResult.enabled("Se usa la base indicada en TEST_DB_URL");
        }
        if (dockerDisponible()) {
            return ConditionEvaluationResult.enabled("Docker disponible: PostgreSQL con Testcontainers");
        }
        return ConditionEvaluationResult.disabled(
                "Sin Docker ni TEST_DB_URL: se omiten las pruebas de integracion (ver README)");
    }

    private static boolean dockerDisponible() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }
}
