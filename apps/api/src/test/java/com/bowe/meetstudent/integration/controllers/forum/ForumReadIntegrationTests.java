package com.bowe.meetstudent.integration.controllers.forum;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Public reading of threads and replies: ordering, paging, school filter, hidden messages, privacy of the DTOs. */
class ForumReadIntegrationTests extends ForumTestSupport {

    @BeforeEach
    void clean() {
        replies.deleteAll();
        threads.deleteAll();
    }

    @Test
    void anonymousReadsTheListNewestFirst() throws Exception {
        var author = student("Awa");
        var now = Instant.now();
        thread(author, null, "oldest", now.minusSeconds(300));
        thread(author, null, "newest", now);
        thread(author, null, "middle", now.minusSeconds(100));

        mvc.perform(get("/api/v1/forum/threads"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("newest"))
                .andExpect(jsonPath("$.content[1].title").value("middle"))
                .andExpect(jsonPath("$.content[2].title").value("oldest"))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void sortParameterIsIgnoredAndTheSizeIsCappedAt100() throws Exception {
        var author = student();
        var now = Instant.now();
        thread(author, null, "a-old", now.minusSeconds(10));
        thread(author, null, "z-new", now);

        mvc.perform(get("/api/v1/forum/threads").param("sort", "title,asc").param("size", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("z-new"))
                .andExpect(jsonPath("$.size").value(100));
        mvc.perform(get("/api/v1/forum/threads").param("sort", "nonsense,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("z-new"));
    }

    @Test
    void filtersByschool() throws Exception {
        var author = student();
        var mine = school();
        var other = school();
        thread(author, mine, "about mine", Instant.now());
        thread(author, other, "about other", Instant.now());
        thread(author, null, "general", Instant.now());

        mvc.perform(get("/api/v1/forum/threads").param("schoolId", String.valueOf(mine.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("about mine"))
                .andExpect(jsonPath("$.content[0].schoolId").value(mine.getId()));
        mvc.perform(get("/api/v1/forum/threads").param("schoolId", "999999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void publicPayloadsNeverContainTheEmailOrAnInternalUserId() throws Exception {
        var author = student("Awa");
        var t = thread(author, "hello");
        reply(t, author, "a reply");

        for (var path : new String[] {"/api/v1/forum/threads", "/api/v1/forum/threads/" + t.getId(),
                "/api/v1/forum/threads/" + t.getId() + "/replies"}) {
            var raw = mvc.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(raw).as(path).doesNotContain(author.getEmail()).doesNotContain("Secret").doesNotContain("password")
                    .doesNotContainIgnoringCase("email").doesNotContain("authorId").doesNotContain("userId");
            assertThat(raw).as(path).contains("\"authorName\":\"Awa\"");
        }
    }

    @Test
    void theThreadDtoCarriesTheDocumentedFields() throws Exception {
        var author = approvedExpert();
        var s = school();
        var t = thread(author, s, "docs", Instant.now());
        reply(t, author, "r1");

        mvc.perform(get("/api/v1/forum/threads/" + t.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(t.getId()))
                .andExpect(jsonPath("$.title").value("docs"))
                .andExpect(jsonPath("$.body").value("Body of docs"))
                .andExpect(jsonPath("$.schoolId").value(s.getId()))
                .andExpect(jsonPath("$.schoolName").value(s.getName()))
                .andExpect(jsonPath("$.authorName").value("Moussa"))
                .andExpect(jsonPath("$.authorRole").value("EXPERT"))
                .andExpect(jsonPath("$.authorDeleted").value(false))
                .andExpect(jsonPath("$.locked").value(false))
                .andExpect(jsonPath("$.hidden").value(false))
                .andExpect(jsonPath("$.mine").value(false))
                .andExpect(jsonPath("$.replyCount").value(1))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void mineIsTrueOnlyForTheAuthor() throws Exception {
        var author = student();
        var other = student();
        var t = thread(author, "mine?");
        var r = reply(t, author, "my reply");

        mvc.perform(get("/api/v1/forum/threads/" + t.getId()).with(as(author))).andExpect(jsonPath("$.mine").value(true));
        mvc.perform(get("/api/v1/forum/threads/" + t.getId()).with(as(other))).andExpect(jsonPath("$.mine").value(false));
        mvc.perform(get("/api/v1/forum/threads/" + t.getId()).with(as(author)))
                .andExpect(jsonPath("$.authorName").value("Awa"));
        mvc.perform(get("/api/v1/forum/threads/" + t.getId() + "/replies").with(as(author)))
                .andExpect(jsonPath("$.content[0].id").value(r.getId()))
                .andExpect(jsonPath("$.content[0].mine").value(true));
        mvc.perform(get("/api/v1/forum/threads/" + t.getId() + "/replies").with(as(other)))
                .andExpect(jsonPath("$.content[0].mine").value(false));
    }

    @Test
    void unknownThreadIs404() throws Exception {
        mvc.perform(get("/api/v1/forum/threads/999999")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/forum/threads/999999/replies")).andExpect(status().isNotFound());
    }

    @Test
    void repliesAreListedNewestFirstAndPaged() throws Exception {
        var author = student();
        var t = thread(author, "discussion");
        var now = Instant.now();
        reply(t, author, "first", now.minusSeconds(30));
        reply(t, author, "second", now.minusSeconds(20));
        reply(t, author, "third", now.minusSeconds(10));
        var otherThread = thread(author, "other");
        reply(otherThread, author, "elsewhere");

        mvc.perform(get("/api/v1/forum/threads/" + t.getId() + "/replies").param("size", "2").param("sort", "body,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].body").value("third"))
                .andExpect(jsonPath("$.content[1].body").value("second"))
                .andExpect(jsonPath("$.content[0].threadId").value(t.getId()));
    }

    @Test
    void hiddenRepliesAreNeverReturnedToNonAdminsNeitherTheirBody() throws Exception {
        var author = student();
        var t = thread(author, "moderated");
        reply(t, author, "fine");
        var bad = reply(t, author, "SECRET-HIDDEN-BODY");
        bad.setHidden(true);
        replies.save(bad);

        for (var viewer : new org.springframework.test.web.servlet.request.RequestPostProcessor[] {
                request -> request, as(author), as(student())}) {
            var raw = mvc.perform(get("/api/v1/forum/threads/" + t.getId() + "/replies").with(viewer))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andReturn().getResponse().getContentAsString();
            assertThat(raw).doesNotContain("SECRET-HIDDEN-BODY");
            var thread = mvc.perform(get("/api/v1/forum/threads/" + t.getId()).with(viewer))
                    .andExpect(jsonPath("$.replyCount").value(1)).andReturn().getResponse().getContentAsString();
            assertThat(thread).doesNotContain("SECRET-HIDDEN-BODY");
        }

        mvc.perform(get("/api/v1/forum/threads/" + t.getId() + "/replies").with(as(admin())))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[?(@.body=='SECRET-HIDDEN-BODY')].hidden").value(true));
    }

    @Test
    void hiddenThreadsAreAbsentFromListsAndFromDetailForNonAdmins() throws Exception {
        var author = student();
        var visible = thread(author, "visible");
        var hidden = thread(author, "SECRET-HIDDEN-TITLE");
        hidden.setHidden(true);
        threads.save(hidden);
        reply(hidden, author, "reply in hidden thread");

        var list = mvc.perform(get("/api/v1/forum/threads").with(as(author)))
                .andExpect(jsonPath("$.totalElements").value(1)).andReturn().getResponse().getContentAsString();
        assertThat(list).contains("visible").doesNotContain("SECRET-HIDDEN-TITLE");
        mvc.perform(get("/api/v1/forum/threads/" + hidden.getId())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/forum/threads/" + hidden.getId() + "/replies").with(as(author))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/forum/threads/" + visible.getId())).andExpect(status().isOk());

        var admin = admin();
        mvc.perform(get("/api/v1/forum/threads").with(as(admin))).andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/v1/forum/threads/" + hidden.getId()).with(as(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.hidden").value(true));
        mvc.perform(get("/api/v1/forum/threads/" + hidden.getId() + "/replies").with(as(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void aDeletedAuthorShowsAsDeletedWithoutAName() throws Exception {
        var t = thread(null, "orphan");
        reply(t, null, "orphan reply");

        mvc.perform(get("/api/v1/forum/threads/" + t.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorDeleted").value(true))
                .andExpect(jsonPath("$.authorName").doesNotExist())
                .andExpect(jsonPath("$.authorRole").doesNotExist());
        mvc.perform(get("/api/v1/forum/threads/" + t.getId() + "/replies"))
                .andExpect(jsonPath("$.content[0].authorDeleted").value(true))
                .andExpect(jsonPath("$.content[0].authorName").doesNotExist())
                .andExpect(jsonPath("$.content[0].mine").value(false));
    }

    @Test
    void textIsReturnedAsStoredNeverInterpreted() throws Exception {
        var author = student();
        var t = thread(author, "<img src=x onerror=alert(1)>");
        reply(t, author, "<script>alert('x')</script> & \"quotes\"");

        var raw = mvc.perform(get("/api/v1/forum/threads/" + t.getId() + "/replies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].body").value("<script>alert('x')</script> & \"quotes\""))
                .andReturn().getResponse();
        assertThat(raw.getContentType()).startsWith("application/json");
    }
}
