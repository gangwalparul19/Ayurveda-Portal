package com.ayurveda.platform.util;

import com.ayurveda.platform.master.service.ConfigurationService;
import com.ayurveda.platform.tenant.repository.OrderRepository;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Pattern;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Property-Based Tests for Order Number Uniqueness using jqwik.
 *
 * **Validates: Requirements 2.1, 2.2, 2.3**
 *
 * Property 6: Order Number Uniqueness
 *
 * For all orders O1, O2:  O1.id != O2.id  =>  O1.orderNumber != O2.orderNumber
 * Order number format:    orderNumber matches "PREFIX-YYYYMMDD-\d{4}"
 *
 * The {@link OrderNumberGenerator} produces numbers of the form
 * {PREFIX}-YYYYMMDD-XXXX. The sequence resets daily and is incremented via an
 * {@link java.util.concurrent.atomic.AtomicInteger} behind a {@code synchronized}
 * method, so it is designed to be thread-safe. These properties therefore
 * validate uniqueness both for sequential generation and for concurrent
 * generation across multiple threads.
 *
 * Note on bounds: the documented format uses a 4-digit sequence ("\d{4}"), which
 * accommodates up to 9999 orders per day. Generation counts in these properties
 * are kept at or below that bound so generated numbers conform to the documented
 * format while still exercising uniqueness across many values.
 */
class OrderNumberUniquenessPropertyTest {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * Build a generator instance with mocked dependencies.
     * No prior orders exist for today, so generation starts from sequence 0001.
     */
    private OrderNumberGenerator newGenerator(String prefix) {
        OrderRepository orderRepository = mock(OrderRepository.class);
        ConfigurationService configurationService = mock(ConfigurationService.class);

        when(orderRepository.findLastOrderNumberByDate(any(LocalDate.class)))
                .thenReturn(Optional.empty());
        when(configurationService.getOrderNumberPrefix()).thenReturn(prefix);

        return new OrderNumberGenerator(orderRepository, configurationService);
    }

    private Pattern formatPattern(String prefix) {
        // PREFIX-YYYYMMDD-XXXX  (exactly 8 date digits, exactly 4 sequence digits)
        return Pattern.compile(Pattern.quote(prefix) + "-\\d{8}-\\d{4}");
    }

    /**
     * **Validates: Requirements 2.1, 2.2, 2.3**
     *
     * Property: Sequential generation produces unique, well-formed order numbers.
     *
     * For any batch of N (1..9999) order numbers generated from a single
     * generator, every value is unique and matches the format PREFIX-YYYYMMDD-XXXX
     * with today's date.
     */
    @Property(tries = 200)
    @Label("Sequential generation: all order numbers are unique and match PREFIX-YYYYMMDD-XXXX")
    void sequentialGenerationProducesUniqueWellFormedNumbers(
            @ForAll @IntRange(min = 1, max = 9999) int count
    ) {
        OrderNumberGenerator generator = newGenerator("ORD");
        Pattern pattern = formatPattern("ORD");
        String today = LocalDate.now().format(DATE_FORMAT);

        Set<String> generated = new HashSet<>();
        for (int i = 0; i < count; i++) {
            String orderNumber = generator.generateOrderNumber();

            // Requirement 2.1: format conformance
            assert pattern.matcher(orderNumber).matches() :
                    String.format("Order number '%s' does not match format PREFIX-YYYYMMDD-XXXX", orderNumber);

            // Requirement 2.1: contains today's date
            assert orderNumber.startsWith("ORD-" + today) :
                    String.format("Order number '%s' does not start with today's date %s", orderNumber, today);

            // Requirement 2.2: uniqueness - add() returns false if already present
            assert generated.add(orderNumber) :
                    String.format("Duplicate order number generated: '%s' after %d numbers", orderNumber, i);
        }

        assert generated.size() == count :
                String.format("Expected %d unique order numbers, got %d", count, generated.size());
    }

    /**
     * **Validates: Requirements 2.1, 2.3**
     *
     * Property: The sequence increments strictly by one and starts at 0001,
     * regardless of the configured prefix.
     */
    @Property(tries = 200)
    @Label("Sequence increments by one starting at 0001 for any configured prefix")
    void sequenceIncrementsByOneFromOne(
            @ForAll("prefixes") String prefix,
            @ForAll @IntRange(min = 1, max = 500) int count
    ) {
        OrderNumberGenerator generator = newGenerator(prefix);
        Pattern pattern = formatPattern(prefix);

        int previous = 0;
        for (int i = 0; i < count; i++) {
            String orderNumber = generator.generateOrderNumber();

            assert pattern.matcher(orderNumber).matches() :
                    String.format("Order number '%s' does not match format for prefix '%s'", orderNumber, prefix);

            int sequence = extractSequence(orderNumber);

            // Requirement 2.3: starts at 0001 and increments by exactly one
            assert sequence == previous + 1 :
                    String.format("Sequence not strictly incrementing: previous=%d, current=%d (number '%s')",
                            previous, sequence, orderNumber);
            previous = sequence;
        }

        assert previous == count :
                String.format("Final sequence expected %d, got %d", count, previous);
    }

    /**
     * **Validates: Requirements 2.2, 2.3**
     *
     * Property: Concurrent generation across many threads produces unique,
     * well-formed order numbers (no collisions, no lost increments).
     *
     * The generator is designed to be thread-safe (synchronized method backed by
     * an AtomicInteger). This property spawns multiple threads that generate
     * numbers concurrently from a single shared generator and asserts that the
     * total set of produced numbers is exactly unique and matches the format.
     */
    @Property(tries = 50)
    @Label("Concurrent generation: shared generator produces unique, well-formed numbers across threads")
    void concurrentGenerationProducesUniqueNumbers(
            @ForAll @IntRange(min = 2, max = 16) int threadCount,
            @ForAll @IntRange(min = 1, max = 400) int perThread
    ) throws InterruptedException {
        OrderNumberGenerator generator = newGenerator("ORD");
        Pattern pattern = formatPattern("ORD");

        int total = threadCount * perThread; // <= 16 * 400 = 6400 <= 9999
        ConcurrentLinkedQueue<String> results = new ConcurrentLinkedQueue<>();

        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(threadCount);
        Thread[] threads = new Thread[threadCount];

        for (int t = 0; t < threadCount; t++) {
            threads[t] = new Thread(() -> {
                try {
                    startGate.await();
                    for (int i = 0; i < perThread; i++) {
                        results.add(generator.generateOrderNumber());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneGate.countDown();
                }
            });
            threads[t].start();
        }

        // Release all threads at once to maximize contention
        startGate.countDown();
        doneGate.await();

        // Requirement 2.2: every generated number is unique
        Set<String> unique = new HashSet<>(results);
        assert results.size() == total :
                String.format("Expected %d generated numbers, got %d", total, results.size());
        assert unique.size() == total :
                String.format("Concurrent generation produced duplicates: %d unique out of %d generated",
                        unique.size(), total);

        // Requirement 2.1: every number is well-formed
        for (String orderNumber : results) {
            assert pattern.matcher(orderNumber).matches() :
                    String.format("Concurrently generated order number '%s' is malformed", orderNumber);
        }

        // Requirement 2.3: the union of sequences is exactly {1..total} (no gaps, no loss)
        Set<Integer> sequences = new HashSet<>();
        for (String orderNumber : results) {
            sequences.add(extractSequence(orderNumber));
        }
        assert sequences.size() == total :
                String.format("Expected %d distinct sequence numbers, got %d", total, sequences.size());
        assert Collections.max(sequences) == total && Collections.min(sequences) == 1 :
                String.format("Sequence range unexpected: min=%d, max=%d, total=%d",
                        Collections.min(sequences), Collections.max(sequences), total);
    }

    /**
     * **Validates: Requirement 2.2**
     *
     * Property: Two independently-built generators that both continue from the
     * same persisted last sequence will produce overlapping numbers if combined.
     * This documents that uniqueness is guaranteed within a single generator
     * instance (the production singleton). Here we confirm that a single shared
     * instance never repeats a value even when interleaved with format checks.
     */
    @Property(tries = 100)
    @Label("Single shared generator never repeats a value across interleaved calls")
    void singleInstanceNeverRepeats(
            @ForAll @IntRange(min = 1, max = 2000) int count
    ) {
        OrderNumberGenerator generator = newGenerator("ORD");
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < count; i++) {
            String orderNumber = generator.generateOrderNumber();
            assert seen.add(orderNumber) :
                    String.format("Repeated order number '%s' at iteration %d", orderNumber, i);
        }
    }

    // --- Arbitraries ---

    @Provide
    Arbitrary<String> prefixes() {
        // Realistic, configurable prefixes (uppercase letters, 2-5 chars).
        return Arbitraries.strings()
                .withCharRange('A', 'Z')
                .ofMinLength(2)
                .ofMaxLength(5);
    }

    // --- Helpers ---

    /**
     * Extract the trailing numeric sequence from an order number of the form
     * PREFIX-YYYYMMDD-XXXX.
     */
    private int extractSequence(String orderNumber) {
        List<String> parts = java.util.Arrays.asList(orderNumber.split("-"));
        return Integer.parseInt(parts.get(parts.size() - 1));
    }
}
