package org.cezmec;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;
import static org.cezmec.Contracts.*;
import static org.springframework.http.HttpStatus.*;

@Service
public class CorpusService {
    private static final Pattern TOKEN = Pattern.compile("\\{([GR]):([^{}\\r\\n]{1,120})}");
    private final JdbcTemplate db;
    private final SceneCatalog catalog;
    public CorpusService(JdbcTemplate db, SceneCatalog catalog) { this.db=db; this.catalog=catalog; }
    public static String nfc(String s) { return Normalizer.normalize(s,Normalizer.Form.NFC).strip(); }
    public static String plainText(AlternativeInput a) {
        String text = nfc(a.annotatedText());
        Matcher matcher = TOKEN.matcher(text);
        Set<Role> found = new HashSet<>(); StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            found.add(Role.valueOf(matcher.group(1)));
            if (matcher.group(2).isBlank()) throw new ResponseStatusException(BAD_REQUEST,"Пустая метка участника");
            matcher.appendReplacement(out,Matcher.quoteReplacement(matcher.group(2)));
        }
        matcher.appendTail(out);
        if (out.toString().contains("{G:") || out.toString().contains("{R:"))
            throw new ResponseStatusException(BAD_REQUEST,"Проверьте скобки в метках {G:…} и {R:…}");
        for (Role r : Role.values()) {
            if (found.contains(r) && a.implicitRoles().contains(r))
                throw new ResponseStatusException(BAD_REQUEST,"Участник не может быть одновременно указан и опущен");
            if (!found.contains(r) && !a.implicitRoles().contains(r))
                throw new ResponseStatusException(BAD_REQUEST,"Укажите G и R или явно отметьте их опущенными");
        }
        if (out.toString().isBlank()) throw new ResponseStatusException(BAD_REQUEST,"Пустое выражение");
        return out.toString();
    }
    public List<LanguageView> languages(String visitor) {
        return db.query("SELECT * FROM languages ORDER BY CASE code WHEN 'ru' THEN 0 WHEN 'tt' THEN 1 WHEN 'ddo' THEN 2 ELSE 3 END,name",
            (r,n)->new LanguageView(r.getString("code"),r.getString("name"),r.getString("native_name"),
                r.getString("green_label"),r.getString("red_label"),r.getString("starter_template"),
                r.getString("text_direction"),r.getString("notes"),r.getInt("revision"),Objects.equals(visitor,r.getString("owner_id"))));
    }
    private void validateLanguage(LanguageInput l) {
        if (!l.starterTemplate().contains("{G}") || !l.starterTemplate().contains("{R}"))
            throw new ResponseStatusException(BAD_REQUEST,"Шаблон должен содержать {G} и {R}");
        if (l.greenLabel().matches("(?s).*[{}\\r\\n].*") || l.redLabel().matches("(?s).*[{}\\r\\n].*"))
            throw new ResponseStatusException(BAD_REQUEST,"В названиях участников не нужны скобки или переводы строк");
    }
    @Transactional public void addLanguage(LanguageInput l, String visitor) {
        validateLanguage(l);
        db.update("INSERT INTO languages(code,name,native_name,green_label,red_label,starter_template,text_direction,notes,owner_id) VALUES (?,?,?,?,?,?,?,?,?)",
            l.code().toLowerCase(Locale.ROOT),nfc(l.name()),nfc(l.nativeName()),nfc(l.greenLabel()),nfc(l.redLabel()),
            nfc(l.starterTemplate()),l.direction(),nfc(l.notes()),visitor);
    }
    @Transactional public void updateLanguage(String code, int revision, LanguageInput l, String visitor) {
        validateLanguage(l);
        if (!code.equals(l.code().toLowerCase(Locale.ROOT))) throw new ResponseStatusException(BAD_REQUEST,"Код языка неизменяем");
        int changed=db.update("UPDATE languages SET name=?,native_name=?,green_label=?,red_label=?,starter_template=?,text_direction=?,notes=?,revision=revision+1 WHERE code=? AND owner_id=? AND revision=?",
            nfc(l.name()),nfc(l.nativeName()),nfc(l.greenLabel()),nfc(l.redLabel()),nfc(l.starterTemplate()),l.direction(),nfc(l.notes()),code,visitor,revision);
        if (changed!=1) throw new ResponseStatusException(CONFLICT,"Нет прав на шаблон либо его версия уже изменилась");
    }
    private static String fingerprint(ContributionInput input) {
        List<String> parts=new ArrayList<>(List.of(input.sceneId(),input.sceneVersion(),input.languageCode(),input.dialect(),
            input.proficiency().name(),Boolean.toString(input.consent()),Boolean.toString(input.examplesViewed()),Boolean.toString(input.othersViewed())));
        for (AlternativeInput a:input.alternatives()) {
            parts.addAll(List.of(a.annotatedText(),a.translation(),a.gloss(),a.reading().name(),Integer.toString(a.weight()),
                a.implicitRoles().stream().sorted().map(Enum::name).reduce("",String::concat)));
        }
        StringBuilder canonical=new StringBuilder();
        for(String part:parts) canonical.append(part.length()).append(':').append(part);
        return VisitorService.hash(canonical.toString());
    }
    @Transactional public List<String> contribute(ContributionInput input,String visitor) {
        SceneCatalog.Scene scene=catalog.require(input.sceneId());
        if (!scene.version().equals(input.sceneVersion())) throw new ResponseStatusException(CONFLICT,"Сцена обновилась; перезагрузите её");
        if (db.queryForObject("SELECT COUNT(*) FROM languages WHERE code=?",Integer.class,input.languageCode())!=1)
            throw new ResponseStatusException(BAD_REQUEST,"Сначала добавьте язык");
        try { UUID.fromString(input.requestId()); } catch (IllegalArgumentException e) { throw new ResponseStatusException(BAD_REQUEST,"Некорректный requestId"); }
        String hash=fingerprint(input);
        List<Map<String,Object>> old=db.queryForList("SELECT id,payload_hash FROM submissions WHERE visitor_id=? AND request_key=?",visitor,input.requestId());
        if (!old.isEmpty()) {
            if (!hash.equals(old.get(0).get("PAYLOAD_HASH"))) throw new ResponseStatusException(CONFLICT,"Этот requestId уже использован для другого содержимого");
            return db.query("SELECT id FROM expressions WHERE submission_id=? ORDER BY id",(r,n)->r.getString(1),old.get(0).get("ID"));
        }
        Set<String> texts=new HashSet<>();
        for (AlternativeInput a:input.alternatives()) {
            plainText(a);
            if (!texts.add(nfc(a.annotatedText()))) throw new ResponseStatusException(BAD_REQUEST,"В списке повторяется один и тот же вариант");
        }
        String submission=UUID.randomUUID().toString();
        db.update("INSERT INTO submissions(id,visitor_id,request_key,payload_hash,examples_viewed,others_viewed,consent_version) VALUES (?,?,?,?,?,?,?)",
            submission,visitor,input.requestId(),hash,input.examplesViewed(),input.othersViewed(),"public-text-v1");
        List<String> ids=new ArrayList<>();
        for (AlternativeInput a:input.alternatives()) {
            String id=UUID.randomUUID().toString();
            String implicit=a.implicitRoles().stream().sorted().map(Enum::name).reduce("",String::concat);
            db.update("INSERT INTO expressions(id,submission_id,scene_id,scene_version,language_code,annotated_text,plain_text,translation,gloss,dialect,reading_type,proficiency,implicit_roles) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                id,submission,scene.id(),scene.version(),input.languageCode(),nfc(a.annotatedText()),plainText(a),nfc(a.translation()),nfc(a.gloss()),nfc(input.dialect()),a.reading().name(),input.proficiency().name(),implicit);
            db.update("INSERT INTO ratings(expression_id,visitor_id,weight) VALUES (?,?,?)",id,visitor,a.weight());
            ids.add(id);
        }
        return ids;
    }
    public List<ExpressionView> expressions(String scene,String language,String visitor,boolean examples) {
        catalog.require(scene);
        return db.query("""
            SELECT e.*,s.visitor_id AS owner_id,
              (SELECT AVG(CAST(r.weight AS DOUBLE PRECISION)) FROM ratings r WHERE r.expression_id=e.id) AS average_weight,
              (SELECT COUNT(*) FROM ratings r WHERE r.expression_id=e.id) AS ratings_count,
              (SELECT r.weight FROM ratings r WHERE r.expression_id=e.id AND r.visitor_id=?) AS my_weight
            FROM expressions e JOIN submissions s ON s.id=e.submission_id
            WHERE e.scene_id=? AND e.language_code=? AND e.visibility='VISIBLE' AND e.is_example=?
              AND (e.is_example=TRUE OR e.scene_version=?)
            ORDER BY e.created_at,e.id
            """, (r,n)->new ExpressionView(r.getString("id"),r.getString("scene_id"),r.getString("scene_version"),r.getString("language_code"),
                r.getString("annotated_text"),r.getString("plain_text"),r.getString("translation"),r.getString("gloss"),r.getString("dialect"),
                r.getString("reading_type"),r.getString("proficiency"),r.getString("implicit_roles"),r.getBoolean("is_example"),
                r.getObject("average_weight",Double.class),r.getLong("ratings_count"),r.getObject("my_weight",Integer.class),
                Objects.equals(visitor,r.getString("owner_id")),r.getObject("created_at").toString()),visitor,scene,language,examples,SceneCatalog.VERSION);
    }
    private void requirePublicExpression(String id,boolean allowExample) {
        int count=db.queryForObject("SELECT COUNT(*) FROM expressions WHERE id=? AND visibility='VISIBLE'"+(allowExample?"":" AND is_example=FALSE"),Integer.class,id);
        if (count!=1) throw new ResponseStatusException(NOT_FOUND,"Запись не найдена; примеры нельзя оценивать как корпусные ответы");
    }
    @Transactional public void rate(String id,String visitor,int weight) {
        requirePublicExpression(id,false);
        db.update("MERGE INTO ratings(expression_id,visitor_id,weight,updated_at) KEY(expression_id,visitor_id) VALUES (?,?,?,CURRENT_TIMESTAMP)",id,visitor,weight);
    }
    @Transactional public void report(String id,String visitor,String reason) {
        requirePublicExpression(id,true);
        db.update("MERGE INTO reports(expression_id,visitor_id,reason) KEY(expression_id,visitor_id) VALUES (?,?,?)",id,visitor,nfc(reason));
    }
    @Transactional public void deleteOwn(String id,String visitor) {
        Integer count=db.queryForObject("SELECT COUNT(*) FROM expressions e JOIN submissions s ON s.id=e.submission_id WHERE e.id=? AND s.visitor_id=? AND e.is_example=FALSE",Integer.class,id,visitor);
        if (count!=1) throw new ResponseStatusException(FORBIDDEN,"Удалять можно только свою запись");
        db.update("DELETE FROM reports WHERE expression_id=?",id);
        db.update("DELETE FROM ratings WHERE expression_id=?",id);
        db.update("DELETE FROM expressions WHERE id=?",id);
    }
    public List<Map<String,Object>> exportRows(String language,int offset,int limit) {
        // No browser credential, participant identifier, IP, or administrative report is exported.
        return db.query("""
            SELECT e.*,s.examples_viewed,s.others_viewed,s.consent_version,
              (SELECT AVG(CAST(weight AS DOUBLE PRECISION)) FROM ratings WHERE expression_id=e.id) AS average_weight,
              (SELECT COUNT(*) FROM ratings WHERE expression_id=e.id) AS ratings_count
            FROM expressions e JOIN submissions s ON s.id=e.submission_id
            WHERE e.visibility='VISIBLE' AND e.is_example=FALSE AND (?='' OR e.language_code=?)
            ORDER BY e.created_at,e.id LIMIT ? OFFSET ?
            """, (r,n)->{
                Map<String,Object> m=new LinkedHashMap<>();
                for(String key:List.of("id","scene_id","scene_version","language_code","annotated_text","plain_text","translation","gloss","dialect","reading_type","proficiency","implicit_roles","consent_version")) m.put(key,r.getString(key));
                m.put("examples_viewed",r.getBoolean("examples_viewed"));m.put("others_viewed",r.getBoolean("others_viewed"));
                m.put("average_weight",r.getObject("average_weight"));m.put("ratings_count",r.getLong("ratings_count"));
                m.put("created_at",r.getObject("created_at").toString());
                return m;
            },language,language,limit,offset);
    }
    public Map<String,Object> stats() {
        return Map.of("languages",db.queryForObject("SELECT COUNT(*) FROM languages",Long.class),"scenes",catalog.all().size(),
            "expressions",db.queryForObject("SELECT COUNT(*) FROM expressions WHERE is_example=FALSE AND visibility='VISIBLE'",Long.class),
            "ratings",db.queryForObject("SELECT COUNT(*) FROM ratings r JOIN expressions e ON e.id=r.expression_id WHERE e.is_example=FALSE AND e.visibility='VISIBLE'",Long.class));
    }
    public List<Map<String,Object>> reports() {
        return db.queryForList("SELECT r.expression_id,r.reason,r.created_at,e.plain_text,e.visibility FROM reports r JOIN expressions e ON e.id=r.expression_id ORDER BY r.created_at DESC LIMIT 500");
    }
    public void moderate(String id,String visibility) {
        if(db.update("UPDATE expressions SET visibility=? WHERE id=?",visibility,id)!=1) throw new ResponseStatusException(NOT_FOUND,"Запись не найдена");
    }
}
