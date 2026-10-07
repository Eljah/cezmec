package org.cezmec;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.cezmec.Contracts.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:cezmec-tests;DB_CLOSE_DELAY=-1","cezmec.admin-token=test-only-token"})
class CorpusIntegrationTest {
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate db;
    @Autowired SceneCatalog catalog;
    MockMvc mvc;
    @BeforeEach void setup() { mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build(); }
    Cookie visitor() throws Exception {
        var response=mvc.perform(get("/api/session")).andExpect(status().isOk()).andExpect(jsonPath("$.csrfToken").isString()).andReturn().getResponse();
        String h=response.getHeaders("Set-Cookie").stream().filter(s->s.startsWith("CEZMEC_V=")).findFirst().orElseThrow();
        assertTrue(h.contains("HttpOnly"));assertTrue(h.contains("SameSite=Lax"));
        return new Cookie("CEZMEC_V",h.substring("CEZMEC_V=".length()).split(";",2)[0]);
    }
    String language(Cookie cookie) throws Exception {
        String code="qaa-x-"+UUID.randomUUID().toString().substring(0,8);
        mvc.perform(post("/api/languages").cookie(cookie).with(csrf()).contentType("application/json").content(languageBody(code)))
            .andExpect(status().isCreated());return code;
    }
    String languageBody(String code) {
        return """
            {"code":"%s","name":"Тестовый язык","nativeName":"Әә Ӏ ƛʼ","greenLabel":"объект","redLabel":"субъект",
             "starterTemplate":"{G} {R}","direction":"ltr","notes":"Только тест"}
            """.formatted(code);
    }
    String contribution(String code,String requestId,int weight) {
        return """
            {"sceneId":"enter-g","sceneVersion":"1","languageCode":"%s","alternatives":[
             {"annotatedText":"{G:объект} {R:субъектка} керә","translation":"входит","gloss":"G R-DAT enter.PRS","reading":"OBSERVED","weight":%d,"implicitRoles":[]}],
             "dialect":"тест","proficiency":"NATIVE","consent":true,"examplesViewed":false,"othersViewed":false,"requestId":"%s"}
            """.formatted(code,weight,requestId);
    }
    String submit(Cookie cookie,String code,int weight) throws Exception {
        String result=mvc.perform(post("/api/contributions").cookie(cookie).with(csrf()).contentType("application/json")
            .content(contribution(code,UUID.randomUUID().toString(),weight))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(result,"$.ids[0]");
    }
    @Test void catalogHas72VersionedPairedStimuli() {
        assertEquals(72,catalog.all().size());
        Set<String> ids=new HashSet<>();
        for(var s:catalog.all()) {
            assertTrue(ids.add(s.id()));var pair=catalog.require(s.pairId());
            assertEquals(s.kind(),pair.kind());assertNotEquals(s.focusFigure(),pair.focusFigure());
            assertEquals(s.id(),pair.pairId());assertEquals("1",s.version());
        }
    }
    @Test void inflectedUnicodeRolesArePreserved() {
        var a=new AlternativeInput("{G:объект} {R:субъектка} керә","","",Reading.OBSERVED,90,Set.of());
        assertEquals("объект субъектка керә",CorpusService.plainText(a));
        assertEquals("é",CorpusService.nfc("e\u0301"));
    }
    @Test void implicitRolesAreExplicitAndValidated() {
        assertEquals("керә",CorpusService.plainText(new AlternativeInput("керә","","",Reading.OBSERVED,90,Set.of(Role.G,Role.R))));
        assertThrows(ResponseStatusException.class,()->CorpusService.plainText(new AlternativeInput("керә","","",Reading.OBSERVED,90,Set.of())));
        assertThrows(ResponseStatusException.class,()->CorpusService.plainText(new AlternativeInput("{G: } {R:x}","","",Reading.OTHER,0,Set.of())));
    }
    @Test void csrfIsRequired() throws Exception {
        mvc.perform(post("/api/languages").contentType("application/json").content(languageBody("qaa"))).andExpect(status().isForbidden());
    }
    @Test void languageCanBeAddedAndDuplicateRejected() throws Exception {
        Cookie c=visitor();String code=language(c);
        mvc.perform(post("/api/languages").cookie(c).with(csrf()).contentType("application/json").content(languageBody(code))).andExpect(status().isConflict());
        String response=mvc.perform(get("/api/languages").cookie(c)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Map<String,Object>> returned=JsonPath.read(response,"$");
        Map<String,Object> created=returned.stream().filter(l->code.equals(l.get("code"))).findFirst().orElseThrow();
        assertEquals(Boolean.TRUE,created.get("editable"),response);
    }
    @Test void languageTemplateEditingChecksOwnerAndRevision() throws Exception {
        Cookie c=visitor();String code=language(c);
        mvc.perform(put("/api/languages/"+code+"?revision=1").cookie(c).with(csrf()).contentType("application/json").content(languageBody(code))).andExpect(status().isOk());
        mvc.perform(put("/api/languages/"+code+"?revision=1").cookie(c).with(csrf()).contentType("application/json").content(languageBody(code))).andExpect(status().isConflict());
        mvc.perform(put("/api/languages/"+code+"?revision=2").cookie(visitor()).with(csrf()).contentType("application/json").content(languageBody(code))).andExpect(status().isConflict());
    }
    @Test void contributionAndWeightPersist() throws Exception {
        Cookie c=visitor();String code=language(c);submit(c,code,92);
        mvc.perform(get("/api/expressions").cookie(c).param("sceneId","enter-g").param("languageCode",code))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].plainText").value("объект субъектка керә"))
            .andExpect(jsonPath("$[0].averageWeight").value(92.0)).andExpect(jsonPath("$[0].ratingsCount").value(1)).andExpect(jsonPath("$[0].mine").value(true));
    }
    @Test void retryIsIdempotentAndChangedPayloadConflicts() throws Exception {
        Cookie c=visitor();String code=language(c);String request=UUID.randomUUID().toString();
        for(int n=0;n<2;n++) mvc.perform(post("/api/contributions").cookie(c).with(csrf()).contentType("application/json").content(contribution(code,request,85))).andExpect(status().isCreated());
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM expressions WHERE language_code=?",Integer.class,code));
        mvc.perform(post("/api/contributions").cookie(c).with(csrf()).contentType("application/json").content(contribution(code,request,84))).andExpect(status().isConflict());
    }
    @Test void secondVoteUpdatesRatherThanMultipliesAndDifferentVisitorAddsVote() throws Exception {
        Cookie c=visitor();String code=language(c);String id=submit(c,code,80);
        for(int n=0;n<2;n++) mvc.perform(put("/api/expressions/"+id+"/rating").cookie(c).with(csrf()).contentType("application/json").content("{\"weight\":60}")).andExpect(status().isOk());
        mvc.perform(put("/api/expressions/"+id+"/rating").cookie(visitor()).with(csrf()).contentType("application/json").content("{\"weight\":100}")).andExpect(status().isOk());
        mvc.perform(get("/api/expressions").cookie(c).param("sceneId","enter-g").param("languageCode",code))
            .andExpect(jsonPath("$[0].ratingsCount").value(2)).andExpect(jsonPath("$[0].averageWeight").value(80.0)).andExpect(jsonPath("$[0].myWeight").value(60));
    }
    @Test void outOfRangeWeightAndMissingConsentRejected() throws Exception {
        Cookie c=visitor();String code=language(c);
        mvc.perform(post("/api/contributions").cookie(c).with(csrf()).contentType("application/json").content(contribution(code,UUID.randomUUID().toString(),101))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/contributions").cookie(c).with(csrf()).contentType("application/json").content(contribution(code,UUID.randomUUID().toString(),90).replace("\"consent\":true","\"consent\":false"))).andExpect(status().isBadRequest());
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM expressions WHERE language_code=?",Integer.class,code));
    }
    @Test void examplesAreSeparateAndCannotBeRated() throws Exception {
        mvc.perform(get("/api/expressions").param("sceneId","enter-r").param("languageCode","ru").param("examples","true"))
            .andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[0].example").value(true)).andExpect(jsonPath("$[0].ratingsCount").value(0));
        mvc.perform(put("/api/expressions/example-ru-admit/rating").with(csrf()).contentType("application/json").content("{\"weight\":90}")).andExpect(status().isNotFound());
    }
    @Test void exportOmitsExamplesAndPrivateIdentity() throws Exception {
        Cookie c=visitor();String code=language(c);submit(c,code,95);
        String json=mvc.perform(get("/api/export").param("languageCode",code).param("limit","1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.rows.length()").value(1)).andExpect(jsonPath("$.rows[0].examples_viewed").value(false))
            .andReturn().getResponse().getContentAsString();
        assertFalse(json.contains("visitor_id"));assertFalse(json.contains("token_hash"));assertFalse(json.contains("submission_id"));assertFalse(json.contains("seed-v1"));
        mvc.perform(get("/api/export?offset=-1")).andExpect(status().isBadRequest());
    }
    @Test void onlyOwnerCanDelete() throws Exception {
        Cookie c=visitor();String code=language(c);String id=submit(c,code,70);
        mvc.perform(delete("/api/expressions/"+id).cookie(visitor()).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(delete("/api/expressions/"+id).cookie(c).with(csrf())).andExpect(status().isOk());
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM ratings WHERE expression_id=?",Integer.class,id));
    }
    @Test void moderationRequiresTokenAndHidesFromPublicExport() throws Exception {
        Cookie c=visitor();String code=language(c);String id=submit(c,code,50);
        mvc.perform(post("/api/expressions/"+id+"/report").cookie(c).with(csrf()).contentType("application/json").content("{\"reason\":\"Проверить язык\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/admin/reports")).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/expressions/"+id+"/visibility").header("Authorization","Bearer test-only-token").with(csrf()).contentType("application/json").content("{\"visibility\":\"HIDDEN\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/export").param("languageCode",code)).andExpect(jsonPath("$.rows.length()").value(0));
    }
    @Test void sceneVersionMismatchIsRejected() throws Exception {
        Cookie c=visitor();String code=language(c);
        mvc.perform(post("/api/contributions").cookie(c).with(csrf()).contentType("application/json").content(contribution(code,UUID.randomUUID().toString(),50).replace("\"sceneVersion\":\"1\"","\"sceneVersion\":\"0\""))).andExpect(status().isConflict());
    }
    @Test void securityHeadersArePresent() throws Exception {
        mvc.perform(get("/api/stats")).andExpect(status().isOk()).andExpect(header().string("X-Content-Type-Options","nosniff"))
            .andExpect(header().string("Content-Security-Policy",org.hamcrest.Matchers.containsString("script-src 'self'")));
    }
}
