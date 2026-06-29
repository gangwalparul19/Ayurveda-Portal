package com.ayurveda.platform.tenant.service;

import com.ayurveda.platform.exception.ResourceNotFoundException;
import com.ayurveda.platform.tenant.entity.SalespersonTarget;
import com.ayurveda.platform.tenant.repository.OrderRepository;
import com.ayurveda.platform.tenant.repository.SalespersonTargetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for managing salesperson targets and tracking achievements.
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class SalespersonTargetService {

    private final SalespersonTargetRepository targetRepository;
    private final OrderRepository orderRepository;

    /**
     * Create or update a target for a salesperson.
     */
    public SalespersonTarget setTarget(Long salespersonUserId, Integer month, Integer year, 
                                       BigDecimal targetAmount) {
        SalespersonTarget target = targetRepository
                .findBySalespersonUserIdAndMonthAndYear(salespersonUserId, month, year)
                .orElse(SalespersonTarget.builder()
                        .salespersonUserId(salespersonUserId)
                        .month(month)
                        .year(year)
                        .achievedAmount(BigDecimal.ZERO)
                        .build());

        target.setTargetAmount(targetAmount);
        
        SalespersonTarget saved = targetRepository.save(target);
        log.info("Set target for salesperson {} for {}/{}: {}", 
                salespersonUserId, month, year, targetAmount);
        return saved;
    }

    /**
     * Get target for a specific salesperson and month/year.
     */
    public SalespersonTarget getTarget(Long salespersonUserId, Integer month, Integer year) {
        return targetRepository.findBySalespersonUserIdAndMonthAndYear(
                salespersonUserId, month, year)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "SalespersonTarget", 
                        String.format("userId=%d, month=%d, year=%d", salespersonUserId, month, year), 
                        null));
    }

    /**
     * Get all targets for a salesperson.
     */
    public List<SalespersonTarget> getAllTargetsForSalesperson(Long salespersonUserId) {
        return targetRepository.findAllBySalespersonUserId(salespersonUserId);
    }

    /**
     * Get yearly achievements for a salesperson.
     */
    public List<SalespersonTarget> getYearlyAchievements(Long salespersonUserId, Integer year) {
        return targetRepository.findYearlyAchievements(salespersonUserId, year);
    }

    /**
     * Get all targets for a specific month/year.
     */
    public List<SalespersonTarget> getTargetsForMonth(Integer month, Integer year) {
        return targetRepository.findAllByMonthAndYear(month, year);
    }

    /**
     * Update achieved amount based on actual orders.
     * This should be called when orders are confirmed/paid.
     */
    public void updateAchievement(Long salespersonUserId, Integer month, Integer year, 
                                  BigDecimal amount) {
        SalespersonTarget target = targetRepository
                .findBySalespersonUserIdAndMonthAndYear(salespersonUserId, month, year)
                .orElse(SalespersonTarget.builder()
                        .salespersonUserId(salespersonUserId)
                        .month(month)
                        .year(year)
                        .targetAmount(BigDecimal.ZERO)
                        .achievedAmount(BigDecimal.ZERO)
                        .build());

        target.setAchievedAmount(target.getAchievedAmount().add(amount));
        targetRepository.save(target);
        
        log.info("Updated achievement for salesperson {} for {}/{}: +{} = {}", 
                salespersonUserId, month, year, amount, target.getAchievedAmount());
    }

    /**
     * Recalculate all achievements for a specific month/year based on actual orders.
     */
    public void recalculateAchievements(Integer month, Integer year) {
        List<SalespersonTarget> targets = targetRepository.findAllByMonthAndYear(month, year);
        
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();

        for (SalespersonTarget target : targets) {
            BigDecimal totalSales = orderRepository
                    .findBySalespersonIdAndOrderDateBetween(
                            target.getSalespersonUserId(), startDate, endDate)
                    .stream()
                    .map(order -> order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            target.setAchievedAmount(totalSales);
            targetRepository.save(target);
        }

        log.info("Recalculated achievements for {}/{} - {} salespersons updated", 
                month, year, targets.size());
    }

    /**
     * Get achievement percentage for a salesperson.
     */
    public Map<String, Object> getAchievementSummary(Long salespersonUserId, Integer month, Integer year) {
        SalespersonTarget target = targetRepository
                .findBySalespersonUserIdAndMonthAndYear(salespersonUserId, month, year)
                .orElse(SalespersonTarget.builder()
                        .salespersonUserId(salespersonUserId)
                        .month(month)
                        .year(year)
                        .targetAmount(BigDecimal.ZERO)
                        .achievedAmount(BigDecimal.ZERO)
                        .build());

        BigDecimal percentageAchieved = BigDecimal.ZERO;
        if (target.getTargetAmount().compareTo(BigDecimal.ZERO) > 0) {
            percentageAchieved = target.getAchievedAmount()
                    .multiply(BigDecimal.valueOf(100))
                    .divide(target.getTargetAmount(), 2, BigDecimal.ROUND_HALF_UP);
        }

        return Map.of(
                "salespersonUserId", salespersonUserId,
                "month", month,
                "year", year,
                "targetAmount", target.getTargetAmount(),
                "achievedAmount", target.getAchievedAmount(),
                "percentageAchieved", percentageAchieved,
                "isTargetMet", percentageAchieved.compareTo(BigDecimal.valueOf(100)) >= 0
        );
    }

    /**
     * Delete a target.
     */
    public void deleteTarget(Long targetId) {
        if (!targetRepository.existsById(targetId)) {
            throw new ResourceNotFoundException("SalespersonTarget", "id", targetId);
        }
        targetRepository.deleteById(targetId);
        log.info("Deleted salesperson target: {}", targetId);
    }
}
