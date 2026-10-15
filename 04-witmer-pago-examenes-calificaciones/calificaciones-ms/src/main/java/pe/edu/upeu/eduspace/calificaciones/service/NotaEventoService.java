package pe.edu.upeu.eduspace.calificaciones.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.eduspace.calificaciones.dto.NotaPendienteResponse;
import pe.edu.upeu.eduspace.calificaciones.entity.CierrePeriodo;
import pe.edu.upeu.eduspace.calificaciones.entity.CriterioEvaluacion;
import pe.edu.upeu.eduspace.calificaciones.entity.EventoProcesado;
import pe.edu.upeu.eduspace.calificaciones.entity.Nota;
import pe.edu.upeu.eduspace.calificaciones.event.ExamenCalificadoEvent;
import pe.edu.upeu.eduspace.calificaciones.event.TareaCalificadaEvent;
import pe.edu.upeu.eduspace.calificaciones.repository.CierrePeriodoRepository;
import pe.edu.upeu.eduspace.calificaciones.repository.CriterioEvaluacionRepository;
import pe.edu.upeu.eduspace.calificaciones.repository.EventoProcesadoRepository;
import pe.edu.upeu.eduspace.calificaciones.repository.NotaRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotaEventoService {

    private final NotaRepository notaRepository;
    private final CriterioEvaluacionRepository criterioRepository;
    private final CierrePeriodoRepository cierrePeriodoRepository;
    private final EventoProcesadoRepository eventoProcesadoRepository;
    private final PromedioService promedioService;

    // Cola en memoria de notas pendientes de asignar criterio (no se pierden nunca)
    private final List<NotaPendienteResponse> pendientesCola = new CopyOnWriteArrayList<>();

    @Transactional
    public void procesarTareaCalificada(UUID eventId, TareaCalificadaEvent event) {
        if (eventId != null && eventoProcesadoRepository.existsByEventId(eventId)) {
            log.info("Evento tarea.calificada ya procesado previamente: {}", eventId);
            return;
        }

        procesarNotaExterna(
            "TAREA",
            event.getTareaId(),
            event.getTitulo(),
            event.getAlumnoId(),
            event.getMatriculaId(),
            event.getSeccionId(),
            event.getCursoId(),
            event.getPeriodoId(),
            event.getNota()
        );

        if (eventId != null) {
            eventoProcesadoRepository.save(EventoProcesado.builder()
                .eventId(eventId)
                .eventType("tarea.calificada")
                .procesadoEn(LocalDateTime.now())
                .build());
        }
    }

    @Transactional
    public void procesarExamenCalificado(UUID eventId, ExamenCalificadoEvent event) {
        if (eventId != null && eventoProcesadoRepository.existsByEventId(eventId)) {
            log.info("Evento examen.calificado ya procesado previamente: {}", eventId);
            return;
        }

        procesarNotaExterna(
            "EXAMEN",
            event.getExamenId(),
            event.getTitulo(),
            event.getAlumnoId(),
            event.getMatriculaId(),
            event.getSeccionId(),
            event.getCursoId(),
            event.getPeriodoId(),
            event.getNota()
        );

        if (eventId != null) {
            eventoProcesadoRepository.save(EventoProcesado.builder()
                .eventId(eventId)
                .eventType("examen.calificado")
                .procesadoEn(LocalDateTime.now())
                .build());
        }
    }

    private void procesarNotaExterna(String origen,
                                     Long origenId,
                                     String titulo,
                                     Long alumnoId,
                                     Long matriculaId,
                                     Long seccionId,
                                     Long cursoId,
                                     Long periodoId,
                                     BigDecimal valor) {
        if (matriculaId == null || cursoId == null || periodoId == null || valor == null) {
            log.warn("Evento de nota incompleto recibido de origen {}: origenId={}", origen, origenId);
            return;
        }

        // 1. Verificar si el periodo está CERRADO
        Optional<CierrePeriodo> cierreOpt = cierrePeriodoRepository.findBySeccionIdAndPeriodoId(seccionId, periodoId);
        if (cierreOpt.isPresent() && "CERRADO".equalsIgnoreCase(cierreOpt.get().getEstado())) {
            log.warn("El periodo {} de la sección {} está CERRADO. Se descarta modificación directa para alumno {}.",
                periodoId, seccionId, alumnoId);
            return;
        }

        // 2. Buscar criterio correspondiente (origenTipo: 'TAREAS' o 'EXAMENES')
        String tipoBuscado = "TAREA".equalsIgnoreCase(origen) ? "TAREAS" : "EXAMENES";
        Optional<CriterioEvaluacion> criterioOpt = criterioRepository.findFirstByPeriodoIdAndSeccionIdAndCursoIdAndOrigenTipo(
            periodoId, seccionId, cursoId, tipoBuscado
        );

        if (criterioOpt.isEmpty()) {
            // El docente no ha creado el criterio aún -> guardar en cola de pendientes visible al docente
            log.info("No existe criterio de tipo {} para curso={}, periodo={}, seccion={}. Guardando en pendientes.",
                tipoBuscado, cursoId, periodoId, seccionId);

            NotaPendienteResponse pendiente = NotaPendienteResponse.builder()
                .id(UUID.randomUUID().toString())
                .origen(origen)
                .origenId(origenId)
                .titulo(titulo != null ? titulo : origen + " " + origenId)
                .alumnoId(alumnoId)
                .matriculaId(matriculaId)
                .seccionId(seccionId)
                .cursoId(cursoId)
                .periodoId(periodoId)
                .valor(valor)
                .fechaEvento(LocalDateTime.now())
                .build();
            pendientesCola.add(pendiente);
            return;
        }

        CriterioEvaluacion criterio = criterioOpt.get();

        // 3. UPSERT por uk_nota_origen: (matricula_id, criterio_id, origen, origen_id)
        Nota nota = notaRepository.findByCriterioAndMatriculaAndOrigen(criterio.getId(), matriculaId, origen, origenId)
            .orElseGet(() -> Nota.builder()
                .matriculaId(matriculaId)
                .alumnoId(alumnoId)
                .seccionId(seccionId)
                .cursoId(cursoId)
                .periodoId(periodoId)
                .criterio(criterio)
                .origen(origen)
                .origenId(origenId)
                .registradoEn(LocalDateTime.now())
                .build());

        nota.setValor(valor);
        nota.setActualizadoEn(LocalDateTime.now());
        notaRepository.save(nota);

        // 4. Recalcular promedio ponderado
        promedioService.calcularYGuardarPromedio(matriculaId, cursoId, periodoId);

        log.info("Nota de {} aplicada exitosamente: matricula={}, curso={}, valor={}", origen, matriculaId, cursoId, valor);
    }

    public List<NotaPendienteResponse> listarPendientes(Long seccionId, Long cursoId) {
        return pendientesCola.stream()
            .filter(p -> (seccionId == null || seccionId.equals(p.getSeccionId()))
                      && (cursoId == null || cursoId.equals(p.getCursoId())))
            .toList();
    }
}

