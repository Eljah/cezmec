package org.cezmec;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Prompt examples are visibly segregated and never counted as crowd responses. */
@Component
public class SeedData implements ApplicationRunner {
    private final JdbcTemplate db; private final boolean examples;
    public SeedData(JdbcTemplate db,@Value("${cezmec.seed-examples:true}") boolean examples) { this.db=db;this.examples=examples; }
    @Override @Transactional public void run(ApplicationArguments args) {
        language("ru","Русский","Русский","Объект","субъект","Подписи сохранены по примерам автора: G — Объект, R — субъект. Это имена тел, а не грамматические роли.");
        language("tt","Татарский","Татарча","объект","субъект","Примеры автора требуют независимой проверки носителями. Форму внутри цветной метки можно изменять: субъектка / субъектны.");
        language("ddo","Цезский","","G","R","G и R — нейтральные метки, НЕ цезские переводы. Укажите реальные слова в своей фразе, диалект и систему письма.");
        language("en","Английский","English","figure","landmark","Colour identifies a referent, not grammatical case or semantic agency.");
        if (!examples || db.queryForObject("SELECT COUNT(*) FROM submissions WHERE id='seed-v1'",Integer.class)>0) return;
        db.update("INSERT INTO visitors(id,token_hash) VALUES ('seed-v1','seed-not-a-browser-credential')");
        db.update("INSERT INTO submissions(id,visitor_id,request_key,payload_hash,examples_viewed,others_viewed,consent_version) VALUES ('seed-v1','seed-v1','seed-v1','seed',FALSE,FALSE,'prompt-example')");
        example("example-ru-enter","enter-g","ru","{G:Объект} входит в {R:субъект}","Объект входит в субъект","OBSERVED");
        example("example-ru-admit","enter-r","ru","{G:Объект} впускает в себя {R:субъект}","Объект впускает в себя субъект","PERMISSIVE");
        example("example-ru-absorb","enter-r","ru","{G:Объект} вбирает в себя {R:субъект}","Объект вбирает в себя субъект","CAUSATIVE");
        example("example-tt-enter","enter-g","tt","{G:объект} {R:субъектка} керә","объект субъектка керә","OBSERVED");
        example("example-tt-admit","enter-r","tt","{G:объект} {R:субъектны} кертә","объект субъектны кертә","CAUSATIVE");
    }
    private void language(String code,String name,String nativeName,String green,String red,String notes) {
        if(db.queryForObject("SELECT COUNT(*) FROM languages WHERE code=?",Integer.class,code)>0)return;
        db.update("INSERT INTO languages(code,name,native_name,green_label,red_label,starter_template,text_direction,notes) VALUES (?,?,?,?,?,'{G} {R}','ltr',?)",code,name,nativeName,green,red,notes);
    }
    private void example(String id,String scene,String language,String annotated,String plain,String reading) {
        db.update("INSERT INTO expressions(id,submission_id,scene_id,scene_version,language_code,annotated_text,plain_text,translation,gloss,dialect,reading_type,proficiency,implicit_roles,is_example) VALUES (?,'seed-v1',?,'1',?,?,?,'','','',?,'UNSPECIFIED','',TRUE)",id,scene,language,annotated,plain,reading);
    }
}
