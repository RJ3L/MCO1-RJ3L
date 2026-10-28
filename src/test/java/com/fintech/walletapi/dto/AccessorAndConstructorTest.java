package com.fintech.walletapi.dto;

import com.fintech.walletapi.entity.Account;
import com.fintech.walletapi.entity.Customer;
import com.fintech.walletapi.entity.ExchangeRate;
import com.fintech.walletapi.entity.TransferRecord;
import com.fintech.walletapi.entity.User;
import com.fintech.walletapi.exception.ErrorResponseDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class AccessorAndConstructorTest {

    static final List<Class<?>> POJO_CLASSES = List.of(
            Account.class, Customer.class, User.class, TransferRecord.class, ExchangeRate.class,
            AccountDTO.class, BalanceUpdateDTO.class, ApiResponse.class, CustomerDTO.class, LoginDTO.class, RegisterDTO.class,
            TransferRequestDTO.class, TransferRecordDTO.class, ExchangeRateDTO.class, ForexQuoteDTO.class,
            ForexSyncResultDTO.class, ErrorResponseDTO.class);

    private static final Map<Class<?>, Object[]> SAMPLES = Map.of(
            Long.class, new Object[]{1L, 42L, Long.MAX_VALUE},
            String.class, new Object[]{"PHP", "Juan dela Cruz", ""},
            BigDecimal.class, new Object[]{new BigDecimal("0.00"), new BigDecimal("100.50"), new BigDecimal("999999.99")},
            LocalDateTime.class, new Object[]{LocalDateTime.of(2026, 1, 1, 0, 0),
                    LocalDateTime.of(2026, 10, 28, 23, 59), LocalDateTime.of(1999, 12, 31, 12, 30)},
            LocalDate.class, new Object[]{LocalDate.of(2026, 10, 26), LocalDate.of(2025, 1, 2), LocalDate.of(1999, 12, 31)},
            List.class, new Object[]{List.of(), List.of("GBP"), List.of("USD", "EUR", "SGD")},
            int.class, new Object[]{0, 200, 404},
            Object.class, new Object[]{"text payload", 42, List.of(1, 2, 3)});

    private static Object sample(Class<?> type, int index) {
        if (type == Customer.class) {
            Customer c = new Customer();
            c.setId((long) index);
            return c;
        }
        if (type == User.class) {
            User u = new User();
            u.setUsername("user" + index);
            return u;
        }
        Object[] values = SAMPLES.get(type);
        if (values == null) {
            throw new IllegalStateException("No sample values for " + type);
        }
        return values[index];
    }

    private static Object newInstance(Class<?> type) throws Exception {
        if (type == ErrorResponseDTO.class) {
            return new ErrorResponseDTO(500, "x", "y");
        }
        return type.getDeclaredConstructor().newInstance();
    }

    private static Optional<Method> getterFor(Class<?> type, String property) {
        for (String prefix : new String[]{"get", "is"}) {
            try {
                return Optional.of(type.getMethod(prefix + property));
            } catch (NoSuchMethodException ignored) {
            }
        }
        return Optional.empty();
    }

    static Stream<Arguments> accessorCases() {
        List<Arguments> cases = new ArrayList<>();
        for (Class<?> type : POJO_CLASSES) {
            Arrays.stream(type.getDeclaredMethods())
                    .filter(m -> Modifier.isPublic(m.getModifiers()))
                    .filter(m -> m.getName().startsWith("set") && m.getParameterCount() == 1)
                    .sorted(Comparator.comparing(Method::getName))
                    .forEach(setter -> {
                        String property = setter.getName().substring(3);
                        getterFor(type, property).ifPresent(getter -> {
                            for (int i = 0; i < 3; i++) {
                                cases.add(Arguments.of(type.getSimpleName(), property, i + 1, type, setter, getter));
                            }
                        });
                    });
        }
        return cases.stream();
    }

    @ParameterizedTest(name = "TC-POJO | {0}.set{1} / get{1} | sample value #{2} round-trips")
    @MethodSource("accessorCases")
    void setterThenGetterReturnsSameValue(String className, String property, int sampleNo,
                                          Class<?> type, Method setter, Method getter) throws Exception {
        Object target = newInstance(type);
        Object value = sample(setter.getParameterTypes()[0], sampleNo - 1);

        setter.invoke(target, value);

        assertEquals(value, getter.invoke(target), className + "." + property);
    }

    @Test
    @DisplayName("TC-POJO-CHK | AccessorAndConstructorTest | every POJO class has at least one accessor pair under test")
    void everyClassCovered() {
        List<String> covered = accessorCases().map(a -> (String) a.get()[0]).distinct().toList();
        for (Class<?> type : POJO_CLASSES) {
            assertTrue(covered.contains(type.getSimpleName()), type.getSimpleName());
        }
    }

    @Test
    @DisplayName("TC-CTOR-01 | ApiResponse(int,String,T) / ApiResponse(int,String) | payload and no-payload variants")
    void apiResponseConstructors() {
        ApiResponse<String> withData = new ApiResponse<>(200, "ok", "data");
        ApiResponse<String> noData = new ApiResponse<>(404, "missing");
        assertEquals("data", withData.getData());
        assertNull(noData.getData());
        assertEquals(404, noData.getStatus());
        assertNotNull(noData.getTimestamp());
    }

    @Test
    @DisplayName("TC-CTOR-02 | CustomerDTO(...) / ForexQuoteDTO(...) / ExchangeRate(...) | all fields populated")
    void otherConstructors() {
        CustomerDTO full = new CustomerDTO(1L, "Alice", "a@x.com", "0917", "alice");
        CustomerDTO basic = new CustomerDTO(2L, "Bob");
        ForexQuoteDTO quote = new ForexQuoteDTO("PHP", "USD", BigDecimal.TEN, BigDecimal.ONE, BigDecimal.TEN);
        ExchangeRate rate = new ExchangeRate("USD", "PHP", new BigDecimal("58"));
        assertEquals("alice", full.getUsername());
        assertNull(basic.getEmail());
        assertEquals("USD", quote.getToCurrency());
        assertEquals("PHP", rate.getQuoteCurrency());
    }

    @Test
    @DisplayName("TC-CTOR-03 | ErrorResponseDTO(int,String,String) | fields set and timestamp stamped")
    void errorResponseConstructor() {
        ErrorResponseDTO error = new ErrorResponseDTO(403, "Forbidden", "nope");
        assertEquals(403, error.getStatus());
        assertEquals("Forbidden", error.getErrorType());
        assertEquals("nope", error.getMessage());
        assertNotNull(error.getTimestamp());
    }
}
