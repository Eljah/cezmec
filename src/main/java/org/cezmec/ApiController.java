package org.cezmec;

import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import static org.cezmec.Contracts.*;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final CorpusService corpus; private final SceneCatalog catalog; private final VisitorService visitors;
    private final String adminToken;
    public ApiController(CorpusService corpus,SceneCatalog catalog,VisitorService visitors,@Value("${cezmec.admin-token:}") String adminToken) {
        this.corpus=corpus;this.catalog=catalog;this.visitors=visitors;this.adminToken=adminToken;
    }
    private String actor(HttpServletRequest q,HttpServletResponse p,boolean write) {
        String id=visitors.resolve(q,p);if(write) visitors.requireWrite(id);return id;
    }
    @GetMapping("/session") public Map<String,Object> session(HttpServletRequest q,HttpServletResponse p,CsrfToken token) {
        actor(q,p,false);
        p.setHeader("Cache-Control","no-store");
        return Map.of("csrfToken",token.getToken(),"csrfHeader",token.getHeaderName(),"consentVersion","public-text-v1");
    }
    @GetMapping("/scenes") public List<SceneCatalog.Scene> scenes() { return catalog.all(); }
    @GetMapping("/stats") public Map<String,Object> stats() { return corpus.stats(); }
    @GetMapping("/languages") public List<LanguageView> languages(HttpServletRequest q,HttpServletResponse p) {
        return corpus.languages(actor(q,p,false));
    }
    @PostMapping(value="/languages",consumes=MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String,String> addLanguage(@Valid @RequestBody LanguageInput input,HttpServletRequest q,HttpServletResponse p) {
        corpus.addLanguage(input,actor(q,p,true));return Map.of("code",input.code().toLowerCase(Locale.ROOT));
    }
    @PutMapping(value="/languages/{code}",consumes=MediaType.APPLICATION_JSON_VALUE)
    public Map<String,Boolean> updateLanguage(@PathVariable String code,@RequestParam int revision,@Valid @RequestBody LanguageInput input,HttpServletRequest q,HttpServletResponse p) {
        corpus.updateLanguage(code,revision,input,actor(q,p,true));return Map.of("saved",true);
    }
    @GetMapping("/expressions") public List<ExpressionView> expressions(@RequestParam String sceneId,@RequestParam String languageCode,
        @RequestParam(defaultValue="false") boolean examples,HttpServletRequest q,HttpServletResponse p) {
        p.setHeader("Cache-Control","no-store");
        return corpus.expressions(sceneId,languageCode,actor(q,p,false),examples);
    }
    @PostMapping(value="/contributions",consumes=MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> contribute(@Valid @RequestBody ContributionInput input,HttpServletRequest q,HttpServletResponse p) {
        return Map.of("ids",corpus.contribute(input,actor(q,p,true)));
    }
    @PutMapping(value="/expressions/{id}/rating",consumes=MediaType.APPLICATION_JSON_VALUE)
    public Map<String,Boolean> rate(@PathVariable String id,@Valid @RequestBody RatingInput input,HttpServletRequest q,HttpServletResponse p) {
        corpus.rate(id,actor(q,p,true),input.weight());return Map.of("saved",true);
    }
    @PostMapping(value="/expressions/{id}/report",consumes=MediaType.APPLICATION_JSON_VALUE)
    public Map<String,Boolean> report(@PathVariable String id,@Valid @RequestBody ReportInput input,HttpServletRequest q,HttpServletResponse p) {
        corpus.report(id,actor(q,p,true),input.reason());return Map.of("saved",true);
    }
    @DeleteMapping("/expressions/{id}") public Map<String,Boolean> delete(@PathVariable String id,HttpServletRequest q,HttpServletResponse p) {
        corpus.deleteOwn(id,actor(q,p,true));return Map.of("deleted",true);
    }
    @GetMapping("/export") public Map<String,Object> export(@RequestParam(defaultValue="") String languageCode,
        @RequestParam(defaultValue="0") int offset,@RequestParam(defaultValue="500") int limit) {
        if(offset<0 || limit<1 || limit>1000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"offset ≥ 0; limit от 1 до 1000");
        List<Map<String,Object>> rows=corpus.exportRows(languageCode,offset,limit);
        return Map.of("schemaVersion",1,"stimulusVersion",SceneCatalog.VERSION,"rows",rows,"offset",offset,"nextOffset",rows.size()==limit?offset+limit:-1,
            "note","Public unverified text; examples excluded; averages are not objective semantic truth.");
    }
    private void requireAdmin(String header) {
        String wanted="Bearer "+adminToken;
        if(adminToken.isBlank() || header==null || !MessageDigest.isEqual(wanted.getBytes(StandardCharsets.UTF_8),header.getBytes(StandardCharsets.UTF_8)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Требуется настроенный токен модератора");
    }
    @GetMapping("/admin/reports") public List<Map<String,Object>> reports(@RequestHeader(value="Authorization",required=false) String header) {
        requireAdmin(header);return corpus.reports();
    }
    @PutMapping(value="/admin/expressions/{id}/visibility",consumes=MediaType.APPLICATION_JSON_VALUE)
    public Map<String,Boolean> moderate(@RequestHeader(value="Authorization",required=false) String header,@PathVariable String id,@Valid @RequestBody VisibilityInput input) {
        requireAdmin(header);corpus.moderate(id,input.visibility());return Map.of("saved",true);
    }
}
