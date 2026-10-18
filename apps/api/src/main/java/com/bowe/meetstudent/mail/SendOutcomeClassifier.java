package com.bowe.meetstudent.mail;

import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import jakarta.mail.internet.AddressException;
import org.eclipse.angus.mail.smtp.SMTPAddressFailedException;
import org.eclipse.angus.mail.smtp.SMTPSendFailedException;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;

import javax.net.ssl.SSLException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;

/**
 * Maps a failure to a {@link SendOutcome} by looking at the exception types in the whole cause chain (and at the
 * per-message failures Spring's {@link MailSendException} keeps aside). Only types and SMTP codes are read: the
 * messages are never copied anywhere, because they can carry credentials and addresses.
 */
final class SendOutcomeClassifier {

    private static final int MAX_NODES = 32;

    private SendOutcomeClassifier() {
    }

    static SendOutcome classify(Throwable failure) {
        var chain = flatten(failure);
        if (chain.stream().anyMatch(t -> t instanceof MailAuthenticationException || t instanceof AuthenticationFailedException)) {
            return SendOutcome.AUTH_FAILED;
        }
        if (chain.stream().anyMatch(SendOutcomeClassifier::isTls)) {
            return SendOutcome.TLS_FAILED;
        }
        var codes = chain.stream().mapToInt(SendOutcomeClassifier::smtpCode).filter(c -> c > 0).toArray();
        var permanentCode = java.util.Arrays.stream(codes).anyMatch(c -> c >= 500 && c < 600);
        var temporaryCode = java.util.Arrays.stream(codes).anyMatch(c -> c >= 400 && c < 500);
        if (chain.stream().anyMatch(t -> t instanceof AddressException) || permanentCode) {
            return SendOutcome.REJECTED;
        }
        if (temporaryCode) {
            // 4xx (greylisting, rate limit, mailbox busy): the same mail may well pass a little later. Angus lists
            // such a recipient as "valid unsent", so the address arrays say nothing about permanence: the code does.
            return SendOutcome.FAILED;
        }
        if (chain.stream().anyMatch(SendOutcomeClassifier::hasInvalidAddresses)) {
            return SendOutcome.REJECTED;
        }
        if (chain.stream().anyMatch(SendOutcomeClassifier::isConnection)) {
            return SendOutcome.CONNECTION_FAILED;
        }
        return SendOutcome.FAILED;
    }

    private static boolean isTls(Throwable t) {
        if (t instanceof SSLException) {
            return true;
        }
        if (t instanceof MessagingException) {
            var message = t.getMessage();
            return message != null && (message.contains("STARTTLS") || message.contains("convert socket to TLS"));
        }
        return false;
    }

    /** SMTP reply code carried by Angus' exceptions, 0 when the node has none. */
    private static int smtpCode(Throwable t) {
        if (t instanceof SMTPSendFailedException e) {
            return e.getReturnCode();
        }
        if (t instanceof SMTPAddressFailedException e) {
            return e.getReturnCode();
        }
        return 0;
    }

    private static boolean hasInvalidAddresses(Throwable t) {
        if (t instanceof SendFailedException sent) {
            var invalid = sent.getInvalidAddresses();
            return invalid != null && invalid.length > 0;
        }
        return false;
    }

    private static boolean isConnection(Throwable t) {
        return t instanceof ConnectException || t instanceof SocketTimeoutException || t instanceof UnknownHostException
                || t instanceof NoRouteToHostException || t instanceof SocketException;
    }

    /** Breadth-first walk of causes, chained MessagingExceptions and Spring's per-message failures; bounded and cycle-safe. */
    private static List<Throwable> flatten(Throwable root) {
        var seen = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        var out = new ArrayList<Throwable>();
        var queue = new ArrayDeque<Throwable>();
        queue.add(root);
        while (!queue.isEmpty() && out.size() < MAX_NODES) {
            var t = queue.poll();
            if (t == null || !seen.add(t)) {
                continue;
            }
            out.add(t);
            if (t.getCause() != null) {
                queue.add(t.getCause());
            }
            if (t instanceof MessagingException m && m.getNextException() != null) {
                queue.add(m.getNextException());
            }
            if (t instanceof MailSendException m && m.getMessageExceptions() != null) {
                for (Exception e : m.getMessageExceptions()) {
                    if (e != null) {
                        queue.add(e);
                    }
                }
            }
        }
        return out;
    }
}
