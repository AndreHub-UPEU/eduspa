package pe.edu.upeu.eduspace.calificaciones.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@FeignClient(name = "alumnos-ms", fallback = AlumnosClient.AlumnosClientFallback.class)
public interface AlumnosClient {

    @GetMapping("/internal/apoderados/usuario/{usuarioId}/alumnos")
    List<HijoResumen> getHijosPorUsuario(@PathVariable("usuarioId") Long usuarioId);

    @GetMapping("/internal/alumnos/{id}")
    Map<String, Object> getAlumnoPorId(@PathVariable("id") Long alumnoId);

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class HijoResumen {
        private Long alumnoId;
        private String nombres;
        private String apellidos;
        private String numeroDocumento;
    }

    @Component
    class AlumnosClientFallback implements AlumnosClient {

        @Override
        public List<HijoResumen> getHijosPorUsuario(Long usuarioId) {
            return Collections.emptyList();
        }

        @Override
        public Map<String, Object> getAlumnoPorId(Long alumnoId) {
            return Map.of("id", alumnoId, "nombres", "Alumno", "apellidos", String.valueOf(alumnoId));
        }
    }
}

