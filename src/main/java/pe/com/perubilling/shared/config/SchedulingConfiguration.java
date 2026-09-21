package pe.com.perubilling.shared.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Habilita los workers programados únicamente cuando la aplicación lo solicita.
 * Permite ejecutar pruebas, migraciones y tareas de mantenimiento sin carreras de
 * fondo que modifiquen documentos mientras el entorno se está preparando.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfiguration {
}
