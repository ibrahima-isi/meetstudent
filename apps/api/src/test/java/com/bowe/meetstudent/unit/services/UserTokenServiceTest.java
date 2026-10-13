package com.bowe.meetstudent.unit.services;

import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.entities.UserToken;
import com.bowe.meetstudent.entities.enums.UserTokenPurpose;
import com.bowe.meetstudent.exceptions.InvalidActionTokenException;
import com.bowe.meetstudent.exceptions.TooManyRequestsException;
import com.bowe.meetstudent.mail.MailProperties;
import com.bowe.meetstudent.repositories.UserTokenRepository;
import com.bowe.meetstudent.services.AddressIssueLock;
import com.bowe.meetstudent.services.UserTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");
    private static final Duration TTL = Duration.ofHours(24);
    private static final UserTokenPurpose VERIFY = UserTokenPurpose.EMAIL_VERIFICATION;

    @Mock
    private UserTokenRepository repository;

    @Mock
    private AddressIssueLock addressLock;

    @Mock
    private com.bowe.meetstudent.repositories.UserRepository userRepository;

    private MailProperties properties;
    private UserTokenService service;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        properties = new MailProperties();
        service = new UserTokenService(repository, properties, Clock.fixed(NOW, ZoneOffset.UTC), addressLock, userRepository);
        user = UserEntity.builder().email("jane@example.com").firstname("Jane").build();
        user.setId(7);
        lenient().when(userRepository.findByIdForUpdate(anyInt())).thenReturn(Optional.of(user));
        lenient().when(repository.save(any(UserToken.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(repository.consumeIfOpen(any(), any())).thenReturn(1);
    }

    private static String sha256Hex(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static UserToken issuedAt(Instant at) {
        var token = new UserToken();
        token.setIssuedAt(at);
        token.setEmail("jane@example.com");
        return token;
    }

    private UserToken savedToken() {
        var captor = ArgumentCaptor.forClass(UserToken.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    private UserToken storedToken(String raw) throws Exception {
        var token = UserToken.builder().user(user).purpose(VERIFY).tokenHash(sha256Hex(raw)).email("jane@example.com")
                .issuedAt(NOW.minusSeconds(3600)).expiresAt(NOW.plusSeconds(3600)).build();
        token.setId(1);
        lenient().when(repository.findUserIdByTokenHash(sha256Hex(raw))).thenReturn(Optional.of(7));
        lenient().when(repository.findByTokenHash(sha256Hex(raw))).thenReturn(Optional.of(token));
        return token;
    }

    // ---- issue ----

    @Test
    void issueReturnsA43CharBase64UrlToken() {
        var raw = service.issue(user, VERIFY, TTL);

        assertNotNull(raw);
        assertTrue(raw.matches("[A-Za-z0-9_-]{43}"), raw);
    }

    @Test
    void twoIssuesProduceDifferentTokens() {
        assertNotEquals(service.issue(user, VERIFY, TTL), service.issue(user, VERIFY, TTL));
    }

    @Test
    void storesOnlyTheSha256HexOfTheToken() throws Exception {
        var raw = service.issue(user, VERIFY, TTL);

        var saved = savedToken();
        assertEquals(64, saved.getTokenHash().length());
        assertTrue(saved.getTokenHash().matches("[0-9a-f]{64}"));
        assertEquals(sha256Hex(raw), saved.getTokenHash());
        assertNotEquals(raw, saved.getTokenHash());
    }

    @Test
    void expiryIsNowPlusTtlAndTheTokenRecordsTheAddressItWasIssuedFor() {
        service.issue(user, VERIFY, TTL);

        var saved = savedToken();
        assertEquals(NOW.plus(TTL), saved.getExpiresAt());
        assertEquals(NOW, saved.getIssuedAt());
        assertEquals("jane@example.com", saved.getEmail());
        assertEquals(VERIFY, saved.getPurpose());
        assertSame(user, saved.getUser());
        assertNull(saved.getConsumedAt());
    }

    @Test
    void issuingSerialisesOnTheAddressBeforeAnyLimitCheck() {
        service.issue(user, VERIFY, TTL);

        var order = inOrder(addressLock, repository);
        order.verify(addressLock).acquire("jane@example.com");
        order.verify(repository).findByUserIdAndPurposeAndIssuedAtAfterOrderByIssuedAtAsc(eq(7), eq(VERIFY), any());
    }

    @Test
    void issuingAResetTokenKeepsEarlierResetLinksAlive() {
        service.issue(user, UserTokenPurpose.PASSWORD_RESET, Duration.ofHours(1));

        verify(repository, never()).consumeOpen(any(), any(), any());
    }

    @Test
    void issuingConsumesTheOpenTokensOfTheSameUserAndPurposeOnly() {
        service.issue(user, VERIFY, TTL);

        verify(repository).consumeOpen(7, VERIFY, NOW);
        verify(repository, never()).consumeOpen(anyInt(), eq(UserTokenPurpose.PASSWORD_RESET), any());
        verify(repository, never()).consumeOpen(eq(8), any(), any());
    }

    @Test
    void issuingPurgesThisUsersRowsOlderThanSevenDays() {
        service.issue(user, VERIFY, TTL);

        verify(repository).deleteIssuedBefore(NOW.minus(Duration.ofDays(7)));
    }

    @Test
    void consumeOutstandingMarksOpenTokensConsumed() {
        service.consumeOutstanding(7, VERIFY);

        verify(repository).consumeOpen(7, VERIFY, NOW);
    }

    @Test
    void cooldownRejectsAnIssueWithinSixtySecondsAndSaysWhenToRetry() {
        var recent = issuedAt(NOW.minusSeconds(10));
        when(repository.findByUserIdAndPurposeAndIssuedAtAfterOrderByIssuedAtAsc(eq(7), eq(VERIFY), any())).thenReturn(List.of(recent));

        var ex = assertThrows(TooManyRequestsException.class, () -> service.issue(user, VERIFY, TTL));

        assertTrue(ex.getRetryAfterSeconds() >= 1 && ex.getRetryAfterSeconds() <= 60, "was " + ex.getRetryAfterSeconds());
        verify(repository, never()).save(any(UserToken.class));
        verify(repository, never()).consumeOpen(any(), any(), any());
    }

    @Test
    void cooldownDoesNotApplyWhenTheRecentTokenWasIssuedForAnotherAddress() {
        var forOldAddress = issuedAt(NOW.minusSeconds(10));
        forOldAddress.setEmail("old-address@example.com");
        when(repository.findByUserIdAndPurposeAndIssuedAtAfterOrderByIssuedAtAsc(eq(7), eq(VERIFY), any())).thenReturn(List.of(forOldAddress));

        assertNotNull(service.issue(user, VERIFY, TTL));
    }

    @Test
    void cooldownIgnoresTheCaseOfTheAddress() {
        var sameAddress = issuedAt(NOW.minusSeconds(10));
        sameAddress.setEmail("JANE@example.com");
        when(repository.findByUserIdAndPurposeAndIssuedAtAfterOrderByIssuedAtAsc(eq(7), eq(VERIFY), any())).thenReturn(List.of(sameAddress));

        assertThrows(TooManyRequestsException.class, () -> service.issue(user, VERIFY, TTL));
    }

    @Test
    void thirdTokenForTheSameAddressInADayIsAcceptedButTheFourthIsRefusedWhoeverAsks() {
        var other = UserEntity.builder().email("JANE@Example.com").build();
        other.setId(8);
        List<UserToken> three = List.of(issuedAt(NOW.minusSeconds(7200)), issuedAt(NOW.minusSeconds(5400)), issuedAt(NOW.minusSeconds(3600)));
        three.get(0).setEmail("jane@example.com");
        three.get(1).setEmail("Jane@example.com");
        three.get(2).setEmail("jane@example.com");
        when(repository.findAddressTokens(eq("JANE@Example.com"), eq(VERIFY), any())).thenReturn(three);

        var ex = assertThrows(TooManyRequestsException.class, () -> service.issue(other, VERIFY, TTL));

        // oldest token is 2 h old: it leaves the 24 h window in 22 h
        assertEquals(79_200, ex.getRetryAfterSeconds());
        verify(repository, never()).save(any(UserToken.class));
        verify(repository, never()).consumeOpen(any(), any(), any());
    }

    @Test
    void twoTokensForTheAddressLeaveRoomForAThird() {
        List<UserToken> two = List.of(issuedAt(NOW.minusSeconds(7200)), issuedAt(NOW.minusSeconds(3600)));
        when(repository.findAddressTokens(eq("jane@example.com"), eq(VERIFY), any())).thenReturn(two);

        assertNotNull(service.issue(user, VERIFY, TTL));
    }

    @Test
    void theAddressCapIsCountedPerPurpose() {
        service.issue(user, UserTokenPurpose.PASSWORD_RESET, Duration.ofHours(1));

        verify(repository).findAddressTokens(eq("jane@example.com"), eq(UserTokenPurpose.PASSWORD_RESET), any());
        verify(repository, never()).findAddressTokens(any(), eq(VERIFY), any());
    }

    @Test
    void consumeRejectsAnOrphanedTokenWhoseAccountWasDeleted() throws Exception {
        var token = storedToken("raw-token-1");
        token.setUser(null);

        assertThrows(InvalidActionTokenException.class, () -> service.consume("raw-token-1", VERIFY));
        verify(repository, never()).consumeIfOpen(any(), any());
    }

    @Test
    void sixthIssueInTwentyFourHoursIsRejected() {
        List<UserToken> five = List.of(1, 2, 3, 4, 5).stream()
                .map(i -> issuedAt(NOW.minusSeconds(3600L * i))).toList();
        when(repository.findByUserIdAndPurposeAndIssuedAtAfterOrderByIssuedAtAsc(eq(7), eq(VERIFY), any()))
                .thenReturn(five.reversed());

        var ex = assertThrows(TooManyRequestsException.class, () -> service.issue(user, VERIFY, TTL));

        assertTrue(ex.getRetryAfterSeconds() >= 1);
        verify(repository, never()).save(any(UserToken.class));
    }

    @Test
    void fifthIssueInTwentyFourHoursIsAccepted() {
        List<UserToken> four = List.of(4, 3, 2, 1).stream()
                .map(i -> issuedAt(NOW.minusSeconds(3600L * i))).toList();
        when(repository.findByUserIdAndPurposeAndIssuedAtAfterOrderByIssuedAtAsc(eq(7), eq(VERIFY), any())).thenReturn(four);

        assertNotNull(service.issue(user, VERIFY, TTL));
    }

    // ---- consume ----

    @Test
    void consumeReturnsTheTokenAndMarksItConsumed() throws Exception {
        var token = storedToken("raw-token-1");

        var consumed = service.consume("raw-token-1", VERIFY);

        assertSame(token, consumed);
        assertEquals(NOW, token.getConsumedAt());
        verify(repository).consumeIfOpen(1, NOW);
    }

    @Test
    void aSecondConsumeOfTheSameTokenFails() throws Exception {
        storedToken("raw-token-1");
        service.consume("raw-token-1", VERIFY);

        assertThrows(InvalidActionTokenException.class, () -> service.consume("raw-token-1", VERIFY));
    }

    @Test
    void consumeRejectsAnUnknownToken() {
        when(repository.findUserIdByTokenHash(any())).thenReturn(Optional.empty());

        assertThrows(InvalidActionTokenException.class, () -> service.consume("nope", VERIFY));
    }

    @Test
    void consumeRejectsBlankAndNullTokens() {
        assertThrows(InvalidActionTokenException.class, () -> service.consume(null, VERIFY));
        assertThrows(InvalidActionTokenException.class, () -> service.consume("  ", VERIFY));
    }

    @Test
    void consumeRejectsAnExpiredToken() throws Exception {
        var token = storedToken("raw-token-1");
        token.setExpiresAt(NOW);

        assertThrows(InvalidActionTokenException.class, () -> service.consume("raw-token-1", VERIFY));
        verify(repository, never()).consumeIfOpen(any(), any());
    }

    @Test
    void consumeRejectsAnAlreadyConsumedToken() throws Exception {
        var token = storedToken("raw-token-1");
        token.setConsumedAt(NOW.minusSeconds(5));

        assertThrows(InvalidActionTokenException.class, () -> service.consume("raw-token-1", VERIFY));
    }

    @Test
    void consumeRejectsATokenOfAnotherPurpose() throws Exception {
        storedToken("raw-token-1");

        assertThrows(InvalidActionTokenException.class, () -> service.consume("raw-token-1", UserTokenPurpose.PASSWORD_RESET));
    }

    @Test
    void consumeRejectsATokenIssuedForAnAddressTheUserNoLongerHas() throws Exception {
        storedToken("raw-token-1");
        user.setEmail("new-address@example.com");

        assertThrows(InvalidActionTokenException.class, () -> service.consume("raw-token-1", VERIFY));
        verify(repository, never()).consumeIfOpen(any(), any());
    }

    @Test
    void consumeRejectsATokenAnotherRequestConsumedFirst() throws Exception {
        storedToken("raw-token-1");
        when(repository.consumeIfOpen(any(), any())).thenReturn(0);

        assertThrows(InvalidActionTokenException.class, () -> service.consume("raw-token-1", VERIFY));
    }
}
