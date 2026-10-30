package com.bowe.meetstudent.integration.controllers;

import com.bowe.meetstudent.support.ResetStateExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import com.bowe.meetstudent.TestDataUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(ResetStateExtension.class)
class SiteSettingsControllerIntegrationTests {

    private static final String URL = "/api/v1/site-settings";

    @Autowired
    private MockMvc mockMvc;

    private org.springframework.test.web.servlet.ResultActions patchAsAdmin(String json) throws Exception {
        return mockMvc.perform(patch(URL).contentType(MediaType.APPLICATION_JSON).content(json)
                .with(TestDataUtil.mockUser("ROLE_ADMIN")));
    }

    @Test
    void anonymousVisitorReadsTheDefaultsBeforeAnyEdit() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contactEmail").value("contact@meetstudent.com"))
                .andExpect(jsonPath("$.aboutFr").value(not(blankOrNullString())))
                .andExpect(jsonPath("$.aboutEn").value(not(blankOrNullString())));
    }

    @Test
    void adminChangesTheContactEmailAndEveryoneSeesIt() throws Exception {
        patchAsAdmin("{\"contactEmail\":\"hello@example.sn\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contactEmail").value("hello@example.sn"));

        mockMvc.perform(get(URL)).andExpect(jsonPath("$.contactEmail").value("hello@example.sn"));
    }

    @Test
    void changingOneLanguageLeavesTheOtherAndTheEmailAlone() throws Exception {
        patchAsAdmin("{\"aboutEn\":\"English only.\"}").andExpect(status().isOk());
        patchAsAdmin("{\"aboutFr\":\"Premier paragraphe.\\n\\nSecond paragraphe.\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aboutFr").value("Premier paragraphe.\n\nSecond paragraphe."))
                .andExpect(jsonPath("$.aboutEn").value("English only."))
                .andExpect(jsonPath("$.contactEmail").value("contact@meetstudent.com"));
    }

    @Test
    void anonymousAndNonAdminCannotWrite() throws Exception {
        mockMvc.perform(patch(URL).contentType(MediaType.APPLICATION_JSON).content("{\"contactEmail\":\"x@example.sn\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch(URL).contentType(MediaType.APPLICATION_JSON).content("{\"contactEmail\":\"x@example.sn\"}")
                        .with(TestDataUtil.mockUser("ROLE_STUDENT")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get(URL)).andExpect(jsonPath("$.contactEmail").value("contact@meetstudent.com"));
    }

    @Test
    void rejectsAnInvalidEmailABlankTextAndAnOverlongText() throws Exception {
        patchAsAdmin("{\"contactEmail\":\"not-an-email\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.contactEmail").exists());
        patchAsAdmin("{\"contactEmail\":\"   \"}").andExpect(status().isBadRequest());
        patchAsAdmin("{\"aboutFr\":\"   \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.aboutFr").exists());
        patchAsAdmin("{\"aboutEn\":\"" + "a".repeat(5001) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.aboutEn").exists());
        patchAsAdmin("{\"aboutEn\":\"" + "a".repeat(5000) + "\"}").andExpect(status().isOk());
    }

    @Test
    void storesTextAsGivenWithoutTheSurroundingBlanks() throws Exception {
        patchAsAdmin("{\"contactEmail\":\"  Hello@Example.sn \",\"aboutFr\":\"  <b>Bonjour</b>  \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contactEmail").value("Hello@Example.sn"))
                .andExpect(jsonPath("$.aboutFr").value("<b>Bonjour</b>"));
    }

    private static final String[] LINK_FIELDS = {
            "whatsappUrl", "telegramUrl", "discordUrl", "facebookUrl", "twitterUrl", "instagramUrl", "tiktokUrl"};

    @Test
    void socialLinksAreNullUntilAnAdminSetsThem() throws Exception {
        var result = mockMvc.perform(get(URL)).andExpect(status().isOk());
        for (String field : LINK_FIELDS) {
            result.andExpect(jsonPath("$." + field).value(org.hamcrest.Matchers.nullValue()));
        }
    }

    @Test
    void adminSetsTheSevenLinksAndEveryoneSeesThemWithTheTextUntouched() throws Exception {
        patchAsAdmin("{\"whatsappUrl\":\"https://wa.me/221770000000\",\"telegramUrl\":\"https://t.me/meetstudent\","
                + "\"discordUrl\":\"https://discord.gg/abc\",\"facebookUrl\":\"https://facebook.com/meetstudent\","
                + "\"twitterUrl\":\"https://x.com/meetstudent\",\"instagramUrl\":\"https://instagram.com/meetstudent\","
                + "\"tiktokUrl\":\"https://www.tiktok.com/@meetstudent\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.whatsappUrl").value("https://wa.me/221770000000"));

        mockMvc.perform(get(URL))
                .andExpect(jsonPath("$.telegramUrl").value("https://t.me/meetstudent"))
                .andExpect(jsonPath("$.discordUrl").value("https://discord.gg/abc"))
                .andExpect(jsonPath("$.facebookUrl").value("https://facebook.com/meetstudent"))
                .andExpect(jsonPath("$.twitterUrl").value("https://x.com/meetstudent"))
                .andExpect(jsonPath("$.instagramUrl").value("https://instagram.com/meetstudent"))
                .andExpect(jsonPath("$.tiktokUrl").value("https://www.tiktok.com/@meetstudent"))
                .andExpect(jsonPath("$.contactEmail").value("contact@meetstudent.com"));
    }

    @Test
    void nullKeepsALinkAndABlankStringErasesIt() throws Exception {
        patchAsAdmin("{\"whatsappUrl\":\"https://wa.me/1\",\"telegramUrl\":\"https://t.me/a\"}").andExpect(status().isOk());

        patchAsAdmin("{\"contactEmail\":\"x@example.sn\"}")
                .andExpect(jsonPath("$.whatsappUrl").value("https://wa.me/1"));
        patchAsAdmin("{\"whatsappUrl\":null}")
                .andExpect(jsonPath("$.whatsappUrl").value("https://wa.me/1"));
        patchAsAdmin("{\"whatsappUrl\":\"\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.whatsappUrl").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.telegramUrl").value("https://t.me/a"));
        patchAsAdmin("{\"telegramUrl\":\"   \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telegramUrl").value(org.hamcrest.Matchers.nullValue()));
        mockMvc.perform(get(URL)).andExpect(jsonPath("$.whatsappUrl").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void theBlankEraseRuleDoesNotLeakToTheOtherFields() throws Exception {
        patchAsAdmin("{\"contactEmail\":\"\"}").andExpect(status().isBadRequest());
        patchAsAdmin("{\"aboutFr\":\"\"}").andExpect(status().isBadRequest());
        patchAsAdmin("{\"aboutEn\":\" \"}").andExpect(status().isBadRequest());
        mockMvc.perform(get(URL))
                .andExpect(jsonPath("$.contactEmail").value("contact@meetstudent.com"))
                .andExpect(jsonPath("$.aboutFr").value(not(blankOrNullString())))
                .andExpect(jsonPath("$.aboutEn").value(not(blankOrNullString())));
    }

    @Test
    void rejectsLinksThatAreNotAbsoluteHttpsUrlsWithAFieldMessage() throws Exception {
        String[] bad = {
                "http://example.com/a", "javascript:alert(1)", "data:text/html,x", "ftp://example.com", "//example.com/a",
                "/relative/path", "example.com", "https://", "https:///path", "HTTPS://example.com", "https://exa mple.com",
                "https://example.com/a b", "https://example.com/\\u00a0", "https://example.com/\\u2003x",
                "https://example.com/\\u0000", "https://user@example.com", "https://#frag", "https://:443/x"};
        for (String field : LINK_FIELDS) {
            for (String value : bad) {
                patchAsAdmin("{\"" + field + "\":\"" + value + "\"}")
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$." + field).exists());
            }
        }
        mockMvc.perform(get(URL)).andExpect(jsonPath("$.whatsappUrl").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void capsALinkAt255CharactersAndStripsTheSurroundingBlanks() throws Exception {
        String url255 = "https://example.com/" + "a".repeat(255 - "https://example.com/".length());
        patchAsAdmin("{\"facebookUrl\":\"" + url255 + "\"}").andExpect(status().isOk());
        patchAsAdmin("{\"facebookUrl\":\"" + url255 + "a\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.facebookUrl").exists());
        patchAsAdmin("{\"instagramUrl\":\"  https://instagram.com/x \\n\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instagramUrl").value("https://instagram.com/x"));
    }

    @Test
    void anAdminCanSetTheLinksAloneWithoutResendingTheText() throws Exception {
        patchAsAdmin("{\"aboutEn\":\"English only.\"}").andExpect(status().isOk());
        patchAsAdmin("{\"tiktokUrl\":\"https://tiktok.com/@x\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aboutEn").value("English only."))
                .andExpect(jsonPath("$.tiktokUrl").value("https://tiktok.com/@x"));
    }
}
