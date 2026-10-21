package com.bowe.meetstudent.integration.controllers.forum;

import com.bowe.meetstudent.entities.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Hide, lock and delete by the administrator, and what the public then sees (end to end). */
class AdminForumModerationIntegrationTests extends ForumTestSupport {

    private static final String ADMIN = "/api/v1/admin/forum";
    private static final String PUBLIC = "/api/v1/forum";

    @BeforeEach
    void clean() {
        reports.deleteAll();
        replies.deleteAll();
        threads.deleteAll();
    }

    // ---- access --------------------------------------------------------------------------------

    @Test
    void everyModerationEndpointIs401ForAnonymousAnd403ForNonAdmins() throws Exception {
        var t = thread(student(), "t");
        var r = reply(t, student(), "r");
        var tid = t.getId();
        var rid = r.getId();
        List<MockHttpServletRequestBuilder> calls = List.of(
                post(ADMIN + "/threads/" + tid + "/hide"), post(ADMIN + "/threads/" + tid + "/unhide"),
                post(ADMIN + "/threads/" + tid + "/lock"), post(ADMIN + "/threads/" + tid + "/unlock"),
                post(ADMIN + "/replies/" + rid + "/hide"), post(ADMIN + "/replies/" + rid + "/unhide"),
                delete(ADMIN + "/replies/" + rid), delete(ADMIN + "/threads/" + tid));
        for (var call : calls) {
            mvc.perform(call).andExpect(status().isUnauthorized());
        }
        for (var who : new UserEntity[] {student(), approvedExpert(), pendingExpert()}) {
            for (var call : List.of(
                    post(ADMIN + "/threads/" + tid + "/hide"), post(ADMIN + "/threads/" + tid + "/lock"),
                    post(ADMIN + "/replies/" + rid + "/hide"), delete(ADMIN + "/replies/" + rid),
                    delete(ADMIN + "/threads/" + tid))) {
                mvc.perform(call.with(as(who))).andExpect(status().isForbidden());
            }
        }
        assertThat(threads.findById(tid)).isPresent();
        assertThat(replies.findById(rid)).isPresent();
        assertThat(threads.findById(tid).orElseThrow().isHidden()).isFalse();
    }

    @Test
    void unknownTargetsAre404() throws Exception {
        var admin = admin();
        for (var call : List.of(
                post(ADMIN + "/threads/987654/hide"), post(ADMIN + "/threads/987654/unhide"),
                post(ADMIN + "/threads/987654/lock"), post(ADMIN + "/threads/987654/unlock"),
                post(ADMIN + "/replies/987654/hide"), post(ADMIN + "/replies/987654/unhide"),
                delete(ADMIN + "/threads/987654"), delete(ADMIN + "/replies/987654"))) {
            mvc.perform(call.with(as(admin))).andExpect(status().isNotFound());
        }
    }

    // ---- hiding a thread -----------------------------------------------------------------------

    @Test
    void aHiddenThreadDisappearsFromThePublicButNotFromTheAdminAndComesBackOnUnhide() throws Exception {
        var author = student();
        var t = thread(author, null, "SECRET-TITLE", Instant.now());
        reply(t, author, "SECRET-REPLY");
        var other = thread(author, "stays");
        var admin = admin();
        var anyone = (RequestPostProcessor) request -> request;

        mvc.perform(post(ADMIN + "/threads/" + t.getId() + "/hide").with(as(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.hidden").value(true))
                .andExpect(jsonPath("$.id").value(t.getId())).andExpect(jsonPath("$.replyCount").value(1));

        for (var viewer : List.of(anyone, as(author), as(student()))) {
            var list = mvc.perform(get(PUBLIC + "/threads").with(viewer)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1)).andReturn().getResponse().getContentAsString();
            assertThat(list).doesNotContain("SECRET-TITLE").doesNotContain("Body of SECRET-TITLE");
            mvc.perform(get(PUBLIC + "/threads/" + t.getId()).with(viewer)).andExpect(status().isNotFound());
            mvc.perform(get(PUBLIC + "/threads/" + t.getId() + "/replies").with(viewer)).andExpect(status().isNotFound());
        }
        // the author can neither edit, delete nor reply to it any more
        mvc.perform(put(PUBLIC + "/threads/" + t.getId()).contentType(MediaType.APPLICATION_JSON)
                .content(threadBody("x", "y", null)).with(as(author))).andExpect(status().isNotFound());
        mvc.perform(delete(PUBLIC + "/threads/" + t.getId()).with(as(author))).andExpect(status().isNotFound());
        mvc.perform(post(PUBLIC + "/threads/" + t.getId() + "/replies").contentType(MediaType.APPLICATION_JSON)
                .content(replyBody("x")).with(as(author))).andExpect(status().isNotFound());

        mvc.perform(get(PUBLIC + "/threads").with(as(admin))).andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get(PUBLIC + "/threads/" + t.getId()).with(as(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.hidden").value(true))
                .andExpect(jsonPath("$.title").value("SECRET-TITLE"));

        // idempotent
        mvc.perform(post(ADMIN + "/threads/" + t.getId() + "/hide").with(as(admin))).andExpect(status().isOk());

        mvc.perform(post(ADMIN + "/threads/" + t.getId() + "/unhide").with(as(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.hidden").value(false));
        mvc.perform(post(ADMIN + "/threads/" + t.getId() + "/unhide").with(as(admin))).andExpect(status().isOk());
        mvc.perform(get(PUBLIC + "/threads")).andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get(PUBLIC + "/threads/" + t.getId())).andExpect(status().isOk());
        assertThat(other.getId()).isNotNull();
    }

    // ---- hiding a reply ------------------------------------------------------------------------

    @Test
    void aHiddenReplyIsNeverReturnedToTheNonAdminsAndReplyCountsFollow() throws Exception {
        var author = student();
        var t = thread(author, "discussion");
        reply(t, author, "fine");
        var bad = reply(t, student(), "SECRET-HIDDEN-BODY");
        var admin = admin();

        mvc.perform(post(ADMIN + "/replies/" + bad.getId() + "/hide").with(as(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.hidden").value(true))
                .andExpect(jsonPath("$.id").value(bad.getId())).andExpect(jsonPath("$.threadId").value(t.getId()))
                .andExpect(jsonPath("$.body").value("SECRET-HIDDEN-BODY"));

        for (var viewer : List.of((RequestPostProcessor) request -> request, as(author), as(student()))) {
            var repliesJson = mvc.perform(get(PUBLIC + "/threads/" + t.getId() + "/replies").with(viewer))
                    .andExpect(jsonPath("$.totalElements").value(1)).andReturn().getResponse().getContentAsString();
            assertThat(repliesJson).doesNotContain("SECRET-HIDDEN-BODY");
            // replyCount in the thread detail and in the list both leave the hidden reply out
            var detail = mvc.perform(get(PUBLIC + "/threads/" + t.getId()).with(viewer))
                    .andExpect(jsonPath("$.replyCount").value(1)).andReturn().getResponse().getContentAsString();
            var list = mvc.perform(get(PUBLIC + "/threads").with(viewer))
                    .andExpect(jsonPath("$.content[0].replyCount").value(1)).andReturn().getResponse().getContentAsString();
            assertThat(detail + list).doesNotContain("SECRET-HIDDEN-BODY");
        }
        // admin sees it, flagged, and counts it
        mvc.perform(get(PUBLIC + "/threads/" + t.getId() + "/replies").with(as(admin)))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[?(@.body=='SECRET-HIDDEN-BODY')].hidden").value(true));
        mvc.perform(get(PUBLIC + "/threads/" + t.getId()).with(as(admin))).andExpect(jsonPath("$.replyCount").value(2));
        mvc.perform(get(PUBLIC + "/threads").with(as(admin))).andExpect(jsonPath("$.content[0].replyCount").value(2));

        // its author cannot edit or delete it any more (404)
        var badAuthorId = replies.findWithAuthorById(bad.getId()).orElseThrow().getAuthor();
        mvc.perform(put(PUBLIC + "/replies/" + bad.getId()).contentType(MediaType.APPLICATION_JSON)
                .content(replyBody("rewrite")).with(as(badAuthorId))).andExpect(status().isNotFound());
        mvc.perform(delete(PUBLIC + "/replies/" + bad.getId()).with(as(badAuthorId))).andExpect(status().isNotFound());

        mvc.perform(post(ADMIN + "/replies/" + bad.getId() + "/hide").with(as(admin))).andExpect(status().isOk());
        mvc.perform(post(ADMIN + "/replies/" + bad.getId() + "/unhide").with(as(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.hidden").value(false));
        mvc.perform(get(PUBLIC + "/threads/" + t.getId() + "/replies")).andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get(PUBLIC + "/threads/" + t.getId())).andExpect(jsonPath("$.replyCount").value(2));
    }

    @Test
    void aReplyCanBeHiddenWhileItsThreadIsHidden() throws Exception {
        var author = student();
        var t = thread(author, "t");
        var r = reply(t, author, "r");
        var admin = admin();
        mvc.perform(post(ADMIN + "/threads/" + t.getId() + "/hide").with(as(admin))).andExpect(status().isOk());
        mvc.perform(post(ADMIN + "/replies/" + r.getId() + "/hide").with(as(admin))).andExpect(status().isOk());
        mvc.perform(post(ADMIN + "/threads/" + t.getId() + "/unhide").with(as(admin))).andExpect(status().isOk());
        mvc.perform(get(PUBLIC + "/threads/" + t.getId() + "/replies")).andExpect(jsonPath("$.totalElements").value(0));
    }

    // ---- locking -------------------------------------------------------------------------------

    @Test
    void lockingRefusesNewRepliesUntilUnlockedAndKeepsTheThreadReadableAndEditable() throws Exception {
        var author = student();
        var t = thread(author, "heated");
        var admin = admin();

        mvc.perform(post(ADMIN + "/threads/" + t.getId() + "/lock").with(as(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.locked").value(true));
        mvc.perform(post(ADMIN + "/threads/" + t.getId() + "/lock").with(as(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.locked").value(true));

        mvc.perform(post(PUBLIC + "/threads/" + t.getId() + "/replies").contentType(MediaType.APPLICATION_JSON)
                .content(replyBody("late")).with(as(student()))).andExpect(status().isConflict());
        mvc.perform(get(PUBLIC + "/threads/" + t.getId())).andExpect(status().isOk()).andExpect(jsonPath("$.locked").value(true));
        mvc.perform(put(PUBLIC + "/threads/" + t.getId()).contentType(MediaType.APPLICATION_JSON)
                .content(threadBody("edited", "still allowed", null)).with(as(author))).andExpect(status().isOk());
        assertThat(replies.count()).isZero();

        mvc.perform(post(ADMIN + "/threads/" + t.getId() + "/unlock").with(as(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.locked").value(false));
        mvc.perform(post(ADMIN + "/threads/" + t.getId() + "/unlock").with(as(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.locked").value(false));
        mvc.perform(post(PUBLIC + "/threads/" + t.getId() + "/replies").contentType(MediaType.APPLICATION_JSON)
                .content(replyBody("welcome back")).with(as(student()))).andExpect(status().isCreated());
    }

    @Test
    void anAdminCanLockAHiddenThread() throws Exception {
        var t = thread(student(), "t");
        var admin = admin();
        mvc.perform(post(ADMIN + "/threads/" + t.getId() + "/hide").with(as(admin))).andExpect(status().isOk());
        mvc.perform(post(ADMIN + "/threads/" + t.getId() + "/lock").with(as(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.locked").value(true)).andExpect(jsonPath("$.hidden").value(true));
    }

    // ---- deleting ------------------------------------------------------------------------------

    @Test
    void anAdminDeletesAThreadForGoodWithItsRepliesAndReports() throws Exception {
        var author = student();
        var t = thread(author, "illegal");
        var r = reply(t, student(), "reply");
        reportOf(t, student(), "on thread", Instant.now());
        reportOf(r, student(), "on reply", Instant.now());
        var keep = thread(author, "keep");
        reportOf(keep, student(), "keeps", Instant.now());

        mvc.perform(delete(ADMIN + "/threads/" + t.getId()).with(as(admin()))).andExpect(status().isNoContent());

        assertThat(threads.findById(t.getId())).isEmpty();
        assertThat(replies.findById(r.getId())).isEmpty();
        assertThat(reports.findAll()).extracting(x -> x.getReason()).containsExactly("keeps");
        mvc.perform(get(PUBLIC + "/threads/" + t.getId())).andExpect(status().isNotFound());
    }

    @Test
    void anAdminCanDeleteAHiddenThreadAndAReply() throws Exception {
        var author = student();
        var t = thread(author, "t");
        t.setHidden(true);
        threads.save(t);
        var live = thread(author, "live");
        var r = reply(live, author, "reply");
        reportOf(r, student(), "on reply", Instant.now());
        var admin = admin();

        mvc.perform(delete(ADMIN + "/threads/" + t.getId()).with(as(admin))).andExpect(status().isNoContent());
        mvc.perform(delete(ADMIN + "/replies/" + r.getId()).with(as(admin))).andExpect(status().isNoContent());
        mvc.perform(delete(ADMIN + "/replies/" + r.getId()).with(as(admin))).andExpect(status().isNotFound());

        assertThat(threads.findById(t.getId())).isEmpty();
        assertThat(replies.findById(r.getId())).isEmpty();
        assertThat(reports.count()).isZero();
        assertThat(threads.findById(live.getId())).isPresent();
        mvc.perform(get(PUBLIC + "/threads/" + live.getId())).andExpect(jsonPath("$.replyCount").value(0));
    }

    @Test
    void anAdminCannotEditSomeoneElsesText() throws Exception {
        var author = student();
        var t = thread(author, "mine");
        var r = reply(t, author, "my reply");
        var admin = admin();
        mvc.perform(put(PUBLIC + "/threads/" + t.getId()).contentType(MediaType.APPLICATION_JSON)
                .content(threadBody("hijack", "hijack", null)).with(as(admin))).andExpect(status().isForbidden());
        mvc.perform(put(PUBLIC + "/replies/" + r.getId()).contentType(MediaType.APPLICATION_JSON)
                .content(replyBody("hijack")).with(as(admin))).andExpect(status().isForbidden());
        assertThat(threads.findById(t.getId()).orElseThrow().getTitle()).isEqualTo("mine");
        assertThat(replies.findById(r.getId()).orElseThrow().getBody()).isEqualTo("my reply");
    }
}
