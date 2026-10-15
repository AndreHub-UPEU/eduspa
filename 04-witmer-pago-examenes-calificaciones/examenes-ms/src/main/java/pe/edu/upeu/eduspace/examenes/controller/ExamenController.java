package pe.edu.upeu.eduspace.examenes.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import pe.edu.upeu.eduspace.examenes.dto.*;
import pe.edu.upeu.eduspace.examenes.entity.Intento;
import pe.edu.upeu.eduspace.examenes.repository.IntentoRepository;
import pe.edu.upeu.eduspace.examenes.service.ExamenService;
import pe.edu.upeu.eduspace.examenes.service.ResultadosService;
import pe.edu.upeu.eduspace.examenes.service.RevisionService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/examenes")
@RequiredArgsConstructor
@Tag(name = "Exámenes", description = "Programación, publicación, rendición y resultados de exámenes")
public class ExamenController {

    private final ExamenService examenService;
    private final ResultadosService resultadosService;
    private final RevisionService revisionService;
    private final IntentoRepository intentoRepository;

    @GetMapping
    @Operation(summary = "Listado paginado de exámenes por sección, curso o estado")
    public ResponseEntity<PageResponse<ExamenResponse>> listar(
            @RequestParam(required = false) Long seccionId,
            @RequestParam(required = false) Long cursoId,
            @RequestParam(required = false) Long docenteId,
            @RequestParam(required = false) String estado,
            Pageable pageable) {
        return ResponseEntity.ok(examenService.listarExamenes(seccionId, cursoId, docenteId, estado, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener detalle completo de un examen (para docente)")
    public ResponseEntity<ExamenDetalleResponse> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(examenService.obtenerPorId(id));
    }

    @PostMapping
    @Operation(summary = "Crear nuevo examen en estado BORRADOR (DOCENTE)")
    public ResponseEntity<ExamenResponse> crear(@Valid @RequestBody ExamenCreateRequest req,
                                               @AuthenticationPrincipal Jwt jwt) {
        Long docenteId = jwt != null && jwt.hasClaim("usuarioId") ? jwt.getClaim("usuarioId") : 1L;
        return ResponseEntity.status(HttpStatus.CREATED).body(examenService.crear(req, docenteId));
    }

    @PatchMapping("/{id}/publicar")
    @Operation(summary = "Publicar examen y emitir evento Kafka (DOCENTE)")
    public ResponseEntity<ExamenResponse> publicar(@PathVariable Long id,
                                                   @AuthenticationPrincipal Jwt jwt) {
        Long docenteId = jwt != null && jwt.hasClaim("usuarioId") ? jwt.getClaim("usuarioId") : 1L;
        return ResponseEntity.ok(examenService.publicar(id, docenteId));
    }

    @PatchMapping("/{id}/cerrar")
    @Operation(summary = "Cerrar examen manualmente (DOCENTE, ADMIN)")
    public ResponseEntity<ExamenResponse> cerrar(@PathVariable Long id) {
        return ResponseEntity.ok(examenService.cerrar(id));
    }

    @GetMapping("/disponibles")
    @Operation(summary = "Exámenes disponibles para rendir hoy (ALUMNO)")
    public ResponseEntity<List<ExamenDisponibleResponse>> disponibles(@AuthenticationPrincipal Jwt jwt) {
        Long alumnoId = jwt != null && jwt.hasClaim("usuarioId") ? jwt.getClaim("usuarioId") : 1L;
        return ResponseEntity.ok(examenService.listarDisponiblesAlumno(alumnoId));
    }

    @GetMapping("/{id}/resultados")
    @Operation(summary = "Estadísticas y notas de alumnos en el examen (DOCENTE, ADMIN)")
    public ResponseEntity<ResultadosExamenResponse> resultados(@PathVariable Long id) {
        return ResponseEntity.ok(resultadosService.obtenerEstadisticasExamen(id));
    }

    @GetMapping("/{id}/pendientes-revision")
    @Operation(summary = "Intentos que tienen preguntas abiertas pendientes de calificar (DOCENTE)")
    public ResponseEntity<List<Long>> pendientesRevision(@PathVariable Long id) {
        List<Intento> pendientes = intentoRepository.findIntentosPendientesRevision(id);
        return ResponseEntity.ok(pendientes.stream().map(Intento::getId).toList());
    }

    @PatchMapping("/respuestas/{id}/revisar")
    @Operation(summary = "Calificar puntaje de una respuesta abierta (DOCENTE)")
    public ResponseEntity<Void> revisarRespuesta(@PathVariable Long id,
                                                @Valid @RequestBody RevisionRequest req,
                                                @AuthenticationPrincipal Jwt jwt) {
        Long docenteId = jwt != null && jwt.hasClaim("usuarioId") ? jwt.getClaim("usuarioId") : 1L;
        revisionService.revisarRespuestaAbierta(id, req, docenteId);
        return ResponseEntity.noContent().build();
    }
}

