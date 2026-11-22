package za.co.neroland.neropower.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import com.google.gson.JsonObject;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import za.co.neroland.nerolandcore.link.LinkActionHandler;
import za.co.neroland.nerolandcore.link.LinkActionResult;
import za.co.neroland.nerolandcore.link.NeroLinkRegistry;

/** The local action budget: ten actions per player per sliding minute, on top of the bridge's limiter. */
class LinkRateLimitTest {

    @BeforeAll
    static void registerModule() {
        NeroPowerLinkModule.register();
    }

    @Test
    void eleventhActionInSixtySecondsIsRefused() {
        AtomicLong now = new AtomicLong(0L);
        LinkRateLimiter limiter = new LinkRateLimiter(now::get);
        UUID me = UUID.randomUUID();
        for (int i = 0; i < LinkRateLimiter.MAX_ACTIONS; i++) {
            now.addAndGet(1_000L);
            assertTrue(limiter.tryAcquire(me), "call " + (i + 1));
        }
        assertFalse(limiter.tryAcquire(me), "the 11th call within a minute is refused");
        assertTrue(limiter.tryAcquire(UUID.randomUUID()), "budgets are per player");
        now.set(1_000L + LinkRateLimiter.WINDOW_MS);
        assertTrue(limiter.tryAcquire(me), "the window slides: the oldest call has aged out");
        assertFalse(limiter.tryAcquire(me));
    }

    @Test
    void erasureDropsTheWindowButALogoutSweepDoesNot() {
        AtomicLong now = new AtomicLong(0L);
        LinkRateLimiter limiter = new LinkRateLimiter(now::get);
        UUID me = UUID.randomUUID();
        for (int i = 0; i < LinkRateLimiter.MAX_ACTIONS; i++) {
            limiter.tryAcquire(me);
        }
        limiter.pruneExpired();
        assertFalse(limiter.tryAcquire(me), "a sweep inside the window keeps the budget spent");
        limiter.forget(me);
        assertFalse(limiter.holds(me));
        assertTrue(limiter.tryAcquire(me));
    }

    @Test
    void theModuleSpendsTheBudgetOnEveryKnownAction() {
        LinkActionHandler handler = NeroLinkRegistry.actionHandler("neropower").orElseThrow();
        UUID me = UUID.randomUUID();
        JsonObject params = new JsonObject();
        params.addProperty("h", "0-0");
        for (int i = 0; i < LinkRateLimiter.MAX_ACTIONS; i++) {
            LinkActionResult r = handler.execute(me, i % 2 == 0 ? "scram" : "turbine_trip", params);
            assertFalse(r.message().startsWith("rate limited"), "call " + (i + 1));
        }
        LinkActionResult refused = handler.execute(me, "acknowledge_alarm", params);
        assertFalse(refused.ok());
        assertEquals(LinkActionResult.Error.VALIDATION, refused.error());
        assertTrue(refused.message().startsWith("rate limited"), refused.message());
        NeroPowerLinkModule.forgetPlayer(me);
        assertFalse(NeroPowerLinkModule.holdsPlayer(me), "erasure leaves nothing in memory");
    }
}
