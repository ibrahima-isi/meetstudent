package com.bowe.meetstudent.integration.controllers.forum;

import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.mail.EmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Writing on the forum needs a verified email only while mail is enabled (otherwise nobody could verify). The rule is
 * the same for students and approved experts; administrators, pending and rejected experts stay refused anyway.
 */
class ForumEmailVerificationIntegrationTests extends ForumTestSupport {

    @MockitoBean EmailSender emailSender;

    @BeforeEach
    void clean() {
        reports.deleteAll();
        replies.deleteAll();
        threads.deleteAll();
    }

    private void mail(boolean enabled) {
        when(emailSender.isEnabled()).thenReturn(enabled);
    }

    private UserEntity verified(UserEntity user) {
        user.setEmailVerified(true);
        return userRepository.save(user);
    }

    private void postThread(UserEntity user, int expected) throws Exception {
        mvc.perform(post("/api/v1/forum/threads").with(as(user)).contentType(MediaType.APPLICATION_JSON)
                .content(threadBody("Title", "Body", null))).andExpect(status().is(expected));
    }

    private void postReply(UserEntity user, int threadId, int expected) throws Exception {
        mvc.perform(post("/api/v1/forum/threads/" + threadId + "/replies").with(as(user)).contentType(MediaType.APPLICATION_JSON)
                .content(replyBody("A reply"))).andExpect(status().is(expected));
    }

    @Test
    void mailEnabledRefusesUnverifiedStudentsAndExperts() throws Exception {
        mail(true);
        var t = thread(verified(student()), "open");
        postThread(student(), 403);
        postThread(approvedExpert(), 403);
        postReply(student(), t.getId(), 403);
        postReply(approvedExpert(), t.getId(), 403);
    }

    @Test
    void mailEnabledAcceptsVerifiedStudentsAndApprovedExperts() throws Exception {
        mail(true);
        var t = thread(verified(student()), "open");
        postThread(verified(student()), 201);
        postThread(verified(approvedExpert()), 201);
        postReply(verified(student()), t.getId(), 201);
        postReply(verified(approvedExpert()), t.getId(), 201);
    }

    @Test
    void mailDisabledKeepsAcceptingUnverifiedAccounts() throws Exception {
        mail(false);
        var t = thread(student(), "open");
        postThread(student(), 201);
        postThread(approvedExpert(), 201);
        postReply(student(), t.getId(), 201);
        postReply(approvedExpert(), t.getId(), 201);
    }

    @Test
    void aVerifiedEmailDoesNotOpenTheForumToOtherRefusedAccounts() throws Exception {
        mail(true);
        postThread(verified(admin()), 403);
        postThread(verified(pendingExpert()), 403);
        postThread(verified(rejectedExpert()), 403);
        mvc.perform(post("/api/v1/forum/threads").contentType(MediaType.APPLICATION_JSON)
                .content(threadBody("Title", "Body", null))).andExpect(status().isUnauthorized());
    }

    @Test
    void mailEnabledAlsoGuardsEditsAndDeletesOfOwnMessages() throws Exception {
        mail(true);
        var author = student();
        var t = thread(author, "mine");
        mvc.perform(put("/api/v1/forum/threads/" + t.getId()).with(as(author)).contentType(MediaType.APPLICATION_JSON)
                .content(threadBody("Edited", "Body", null))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/forum/threads/" + t.getId()).with(as(author))).andExpect(status().isForbidden());
        verified(author);
        mvc.perform(delete("/api/v1/forum/threads/" + t.getId()).with(as(author))).andExpect(status().isNoContent());
    }

    @Test
    void readingAndReportingStayOpenToUnverifiedAccounts() throws Exception {
        mail(true);
        var t = thread(verified(student()), "open");
        mvc.perform(get("/api/v1/forum/threads/" + t.getId())).andExpect(status().isOk());
        mvc.perform(post("/api/v1/forum/threads/" + t.getId() + "/report").with(as(student()))
                .contentType(MediaType.APPLICATION_JSON).content(reasonBody("Spam"))).andExpect(status().isCreated());
    }
}
