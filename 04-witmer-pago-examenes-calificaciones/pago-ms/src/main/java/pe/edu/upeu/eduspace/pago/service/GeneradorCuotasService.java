package pe.edu.upeu.eduspace.pago.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.eduspace.pago.config.PagoProperties;
import pe.edu.upeu.eduspace.pago.entity.*;
import pe.edu.upeu.eduspace.pago.event.MatriculaEvent;
import pe.edu.upeu.eduspace.pago.repository.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeneradorCuotasService {

    private final ConceptoPagoRepository conceptoPagoRepository;
    private final TarifaRepository tarifaRepository;
    private final DescuentoAlumnoRepository descuentoAlumnoRepository;
    private final CuotaRepository cuotaRepository;
    private final MatriculaLocalRepository matriculaLocalRepository;
    private final PagoProperties pagoProperties;

    @Transactional
    public void generarCuotaMatricula(MatriculaEvent event) {
        log.info("Generando cuota de matrícula para matrícula: {}", event.getMatriculaId());

        ConceptoPago conceptoMatricula = conceptoPagoRepository.findByCodigo("MATRICULA")
            .orElseThrow(() -> new IllegalStateException("Concepto MATRICULA no configurado en BD"));

        // Evitar duplicados por idempotencia (uk_cuota)
        if (cuotaRepository.findByMatriculaMatriculaIdAndConceptoIdAndNumeroCuota(event.getMatriculaId(), conceptoMatricula.getId(), (short) 1).isPresent()) {
            log.info("Cuota de matrícula ya existe para matrícula {}", event.getMatriculaId());
            return;
        }

        MatriculaLocal matricula = matriculaLocalRepository.findById(event.getMatriculaId())
            .orElseThrow(() -> new IllegalStateException("Matrícula local no encontrada: " + event.getMatriculaId()));

        Optional<Tarifa> optTarifa = tarifaRepository.findTarifaAplicable(
            event.getAnioId(), event.getNivelId(), event.getGradoId(), conceptoMatricula.getId()
        );

        if (optTarifa.isEmpty()) {
            log.error("ALERTA ADMINISTRATIVA: No existe tarifa de MATRICULA configurada para año={}, nivel={}, grado={}",
                event.getAnioId(), event.getNivelId(), event.getGradoId());
            return;
        }

        Tarifa tarifa = optTarifa.get();
        BigDecimal montoBase = tarifa.getMonto();
        BigDecimal descuento = calcularDescuento(event.getAlumnoId(), event.getAnioId(), "MATRICULA", montoBase);
        BigDecimal total = montoBase.subtract(descuento).max(BigDecimal.ZERO);

        LocalDate fechaVencimiento = LocalDate.now().plusDays(pagoProperties.getDiasVigenciaPrematricula());

        Cuota cuota = Cuota.builder()
            .matricula(matricula)
            .alumnoId(event.getAlumnoId())
            .anioId(event.getAnioId())
            .concepto(conceptoMatricula)
            .tarifa(tarifa)
            .descripcion("Matrícula escolar " + event.getAnioId())
            .numeroCuota((short) 1)
            .montoBase(montoBase)
            .descuento(descuento)
            .mora(BigDecimal.ZERO)
            .montoTotal(total)
            .saldo(total)
            .fechaVencimiento(fechaVencimiento)
            .estado(total.compareTo(BigDecimal.ZERO) == 0 ? "PAGADA" : "PENDIENTE")
            .avisoEnviado(false)
            .creadaEn(LocalDateTime.now())
            .actualizadaEn(LocalDateTime.now())
            .build();

        cuotaRepository.save(cuota);
        log.info("Cuota de matrícula generada exitosamente con ID: {}, saldo: {}", cuota.getId(), cuota.getSaldo());
    }

    @Transactional
    public void generarCuotasPension(MatriculaEvent event) {
        log.info("Generando cuotas de pensión para matrícula confirmada: {}", event.getMatriculaId());

        ConceptoPago conceptoPension = conceptoPagoRepository.findByCodigo("PENSION")
            .orElseThrow(() -> new IllegalStateException("Concepto PENSION no configurado en BD"));

        MatriculaLocal matricula = matriculaLocalRepository.findById(event.getMatriculaId())
            .orElseThrow(() -> new IllegalStateException("Matrícula local no encontrada: " + event.getMatriculaId()));

        Optional<Tarifa> optTarifa = tarifaRepository.findTarifaAplicable(
            event.getAnioId(), event.getNivelId(), event.getGradoId(), conceptoPension.getId()
        );

        if (optTarifa.isEmpty()) {
            log.error("ALERTA ADMINISTRATIVA: No existe tarifa de PENSION configurada para año={}, nivel={}, grado={}",
                event.getAnioId(), event.getNivelId(), event.getGradoId());
            return;
        }

        Tarifa tarifa = optTarifa.get();
        short totalCuotas = tarifa.getNumCuotas() != null ? tarifa.getNumCuotas() : 10;
        short mesInicio = tarifa.getMesInicio() != null ? tarifa.getMesInicio() : 3;
        short diaVenc = tarifa.getDiaVencimiento() != null ? tarifa.getDiaVencimiento() : 5;
        BigDecimal montoBasePorCuota = tarifa.getMonto();

        int anio = event.getAnioId().intValue(); // Referencia de año

        for (short i = 1; i <= totalCuotas; i++) {
            final short numCuota = i;
            if (cuotaRepository.findByMatriculaMatriculaIdAndConceptoIdAndNumeroCuota(event.getMatriculaId(), conceptoPension.getId(), numCuota).isPresent()) {
                continue; // ya generada
            }

            int mes = mesInicio + (i - 1);
            int anioCuota = anio;
            if (mes > 12) {
                mes -= 12;
                anioCuota += 1;
            }

            LocalDate fechaVenc = LocalDate.of(anioCuota, mes, Math.min(diaVenc, 28));
            BigDecimal descCuota = calcularDescuento(event.getAlumnoId(), event.getAnioId(), "PENSION", montoBasePorCuota);
            BigDecimal totalCuota = montoBasePorCuota.subtract(descCuota).max(BigDecimal.ZERO);

            Cuota cuota = Cuota.builder()
                .matricula(matricula)
                .alumnoId(event.getAlumnoId())
                .anioId(event.getAnioId())
                .concepto(conceptoPension)
                .tarifa(tarifa)
                .descripcion(String.format("Pensión %02d/%d", mes, anioCuota))
                .numeroCuota(numCuota)
                .montoBase(montoBasePorCuota)
                .descuento(descCuota)
                .mora(BigDecimal.ZERO)
                .montoTotal(totalCuota)
                .saldo(totalCuota)
                .fechaVencimiento(fechaVenc)
                .estado(totalCuota.compareTo(BigDecimal.ZERO) == 0 ? "PAGADA" : "PENDIENTE")
                .avisoEnviado(false)
                .creadaEn(LocalDateTime.now())
                .actualizadaEn(LocalDateTime.now())
                .build();

            cuotaRepository.save(cuota);
        }
        log.info("Generadas {} cuotas de pensión para matrícula {}", totalCuotas, event.getMatriculaId());
    }

    @Transactional
    public void anularCuotasPorRetiroOAnulacion(Long matriculaId) {
        log.info("Anulando cuotas pendientes para matrícula {}", matriculaId);
        List<Cuota> cuotas = cuotaRepository.findByMatriculaMatriculaId(matriculaId);
        for (Cuota c : cuotas) {
            if ("PENDIENTE".equals(c.getEstado())) {
                c.setEstado("ANULADA");
                c.setSaldo(BigDecimal.ZERO);
                c.setActualizadaEn(LocalDateTime.now());
                cuotaRepository.save(c);
            }
        }
    }

    private BigDecimal calcularDescuento(Long alumnoId, Long anioId, String tipoAplica, BigDecimal montoBase) {
        List<DescuentoAlumno> descuentos = descuentoAlumnoRepository.findDescuentosVigentesConDetalle(alumnoId, anioId);
        BigDecimal totalDescuento = BigDecimal.ZERO;

        for (DescuentoAlumno da : descuentos) {
            Descuento d = da.getDescuento();
            if ("TODO".equals(d.getAplicaA()) || tipoAplica.equals(d.getAplicaA())) {
                if ("PORCENTAJE".equals(d.getTipo())) {
                    BigDecimal desc = montoBase.multiply(d.getValor()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                    totalDescuento = totalDescuento.add(desc);
                } else if ("MONTO_FIJO".equals(d.getTipo())) {
                    totalDescuento = totalDescuento.add(d.getValor());
                }
            }
        }
        return totalDescuento.min(montoBase);
    }
}

