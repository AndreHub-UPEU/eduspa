package pe.edu.upeu.eduspace.pago.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlumnoContactoResponse {

    private Long alumnoId;
    private Long usuarioId;
    private String nombreCompleto;
    private String email;
    private String telefono;
    private Boolean esResponsablePago;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HijoResumen {
        private Long alumnoId;
        private String codigo;
        private String nombreCompleto;
        private String nivel;
        private String grado;
        private String seccion;
    }
}

