package org.example.lifecomposer.controller;

import org.example.lifecomposer.Service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Review follow-up (P1): the "keep at least one loginable administrator" rule must
 * hold under concurrency, not only for sequential calls.
 *
 * <p>Two administrators are demoted (and banned) from two threads released by a
 * barrier. Because the guard is part of the UPDATE statement and the operation
 * runs in an immediate write transaction, exactly one of the two can win and the
 * invariant {@code countLoginableAdmins() >= 1} always holds.</p>
 */
class AdminLastAdminConcurrencyTest extends BaseControllerTest {

    private static final String UA = "TestClient/1.0";

    @Test
    @DisplayName("two concurrent demotions leave exactly one loginable administrator")
    void concurrentDemotionsKeepOneAdministrator() throws Exception {
        UserService userService = context.getBean(UserService.class);
        loginAsAdmin();
        registerUser("adminTwo", "pass123");

        int firstId = userId("admin");
        int secondId = userId("adminTwo");
        jdbcTemplate.update("UPDATE users SET type = 2 WHERE id IN (?, ?)", firstId, secondId);
        assertThat(loginableAdmins()).isEqualTo(2);

        List<UserService.AdminRemovalResult> results = runConcurrently(
                () -> userService.revokeAdminPermission(firstId),
                () -> userService.revokeAdminPermission(secondId));

        assertThat(results).containsExactlyInAnyOrder(
                UserService.AdminRemovalResult.DONE,
                UserService.AdminRemovalResult.LAST_ADMIN);
        assertThat(loginableAdmins()).isEqualTo(1);
    }

    @Test
    @DisplayName("two concurrent bans leave exactly one loginable administrator")
    void concurrentBansKeepOneAdministrator() throws Exception {
        UserService userService = context.getBean(UserService.class);
        loginAsAdmin();
        registerUser("adminThree", "pass123");

        int firstId = userId("admin");
        int secondId = userId("adminThree");
        jdbcTemplate.update("UPDATE users SET type = 2 WHERE id IN (?, ?)", firstId, secondId);
        assertThat(loginableAdmins()).isEqualTo(2);

        List<UserService.AdminRemovalResult> results = runConcurrently(
                () -> userService.banUser(firstId, "1d"),
                () -> userService.banUser(secondId, "1d"));

        assertThat(results).containsExactlyInAnyOrder(
                UserService.AdminRemovalResult.DONE,
                UserService.AdminRemovalResult.LAST_ADMIN);
        assertThat(loginableAdmins()).isEqualTo(1);
    }

    @Test
    @DisplayName("a burst of demotions can never empty the administrator set")
    void demotionBurstKeepsTheInvariant() throws Exception {
        UserService userService = context.getBean(UserService.class);
        loginAsAdmin();
        int firstId = userId("admin");
        for (int i = 0; i < 3; i++) {
            registerUser("burstAdmin" + i, "pass123");
        }
        jdbcTemplate.update("UPDATE users SET type = 2 WHERE username LIKE 'burstAdmin%'");
        assertThat(loginableAdmins()).isEqualTo(4);

        List<Integer> targets = List.of(firstId, userId("burstAdmin0"), userId("burstAdmin1"),
                userId("burstAdmin2"));

        ExecutorService pool = Executors.newFixedThreadPool(targets.size());
        try {
            CountDownLatch ready = new CountDownLatch(targets.size());
            CountDownLatch go = new CountDownLatch(1);
            List<Future<UserService.AdminRemovalResult>> futures = new java.util.ArrayList<>();
            for (int targetId : targets) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await(5, TimeUnit.SECONDS);
                    return userService.revokeAdminPermission(targetId);
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            long done = 0;
            for (Future<UserService.AdminRemovalResult> future : futures) {
                if (future.get(30, TimeUnit.SECONDS) == UserService.AdminRemovalResult.DONE) {
                    done += 1;
                }
            }
            // 4 administrators, 4 simultaneous demotions: at most 3 may win.
            assertThat(done).isEqualTo(3);
        } finally {
            pool.shutdownNow();
        }

        assertThat(loginableAdmins()).isEqualTo(1);
    }

    @Test
    @DisplayName("the API reports the protected-last-admin case as 409, not as a missing user")
    void lastAdminRejectionIsExplicit() throws Exception {
        var adminSession = loginAsAdmin();
        int adminId = userId("admin");

        mockMvc.perform(put("/api/users/" + adminId + "/revoke-admin")
                        .session(adminSession).header("User-Agent", UA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("LAST_ADMIN_PROTECTED"));

        mockMvc.perform(put("/api/users/" + adminId + "/ban")
                        .session(adminSession).header("User-Agent", UA)
                        .contentType("application/json")
                        .content("{\"banTime\":\"0\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("LAST_ADMIN_PROTECTED"));
    }

    private List<UserService.AdminRemovalResult> runConcurrently(
            Callable<UserService.AdminRemovalResult> first,
            Callable<UserService.AdminRemovalResult> second) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            Future<UserService.AdminRemovalResult> a = pool.submit(() -> {
                ready.countDown();
                go.await(5, TimeUnit.SECONDS);
                return first.call();
            });
            Future<UserService.AdminRemovalResult> b = pool.submit(() -> {
                ready.countDown();
                go.await(5, TimeUnit.SECONDS);
                return second.call();
            });
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            return List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
    }

    private long loginableAdmins() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE type = 2 AND is_banned = 0", Long.class);
        return count == null ? 0 : count;
    }

    private int userId(String username) {
        Integer id = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?", Integer.class, username);
        assertThat(id).isNotNull();
        return id;
    }
}
