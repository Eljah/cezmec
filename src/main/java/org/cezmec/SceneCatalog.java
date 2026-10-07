package org.cezmec;

import org.springframework.stereotype.Component;
import java.util.*;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import org.springframework.web.server.ResponseStatusException;

/** Stable stimulus identities. Never change a published animation without a new version. */
@Component
public class SceneCatalog {
    public static final String VERSION = "2";
    public record Scene(String id, int number, String version, String kind, String pairId,
        boolean focusFigure, String family, String title, String place, String direction,
        String contact, String causation, String realExample, String researchNote) {}
    private final List<Scene> scenes;
    public SceneCatalog() {
        String[][] base = {
            {"enter","Полость","Вход в полость","IN","TO","NONE","UNSPECIFIED","Шар входит в комнату","Вход не доказывает разрешение или причинение."},
            {"exit","Полость","Выход из полости","IN","FROM","NONE","UNSPECIFIED","Шар выходит из комнаты","Обратная траектория — отдельный стимул, не перемотка."},
            {"inside","Полость","Нахождение внутри","IN","AT","NONE","NONE","Предмет в помещении","Допустимо описание состояния без глагола движения."},
            {"through","Полость","Сквозной проход","IN","VIA","NONE","UNSPECIFIED","Поезд проходит тоннель","Оба конца прохода открыты."},
            {"approach","Дистанция","Приближение без достижения","APUD","TOWARD","NONE","UNSPECIFIED","Человек движется к стене, но не доходит","Не смешивать TOWARD и достигнутое TO."},
            {"reach","Дистанция","Достижение границы","AD","TO","TOUCH","UNSPECIFIED","Предмет достигает стены","В конце есть контакт."},
            {"depart","Дистанция","Удаление","APUD","FROM","NONE","UNSPECIFIED","Человек удаляется от здания","Причина удаления не задана."},
            {"near","Дистанция","Рядом без контакта","APUD","AT","NONE","NONE","Стул рядом со шкафом","Метрика сцены условна."},
            {"on","Поверхность","На горизонтальной поверхности","SUPER","AT","SUPPORT","NONE","Мяч на полке","Есть опора, в отличие от над."},
            {"onto","Поверхность","На поверхность с достижением","SUPER","TO","SUPPORT","UNSPECIFIED","Мяч оказывается на полке","Траектория не задаёт способ движения человека."},
            {"off","Поверхность","С поверхности","SUPER","FROM","SUPPORT_LOST","UNSPECIFIED","Мяч покидает полку","Потеря опоры показана."},
            {"above","Вертикаль","Над без контакта","ABOVE","AT","NONE","NONE","Предмет висит над столом","Не эквивалент горизонтальной опоре SUPER."},
            {"overpass","Вертикаль","Проход сверху","ABOVE","VIA","NONE","UNSPECIFIED","Мяч перелетает препятствие","Движение по дуге, а не просто состояние над."},
            {"under","Вертикаль","Под","SUB","AT","NONE","NONE","Мяч под мостом","Ориентация задана плоскостью земли."},
            {"underpass","Вертикаль","Проход снизу","SUB","VIA","NONE","UNSPECIFIED","Мяч проходит под мостом","Просвет шире и выше шара."},
            {"front","Ориентация","Перед, со стороны камеры","FRONT","AT","NONE","NONE","Предмет перед экраном","Рамка отсчёта наблюдателя; не собственный перед объекта."},
            {"behind","Ориентация","За, с перекрытием","BEHIND","AT","NONE","NONE","Предмет за экраном","Фиксированная камера видит часть шара за невысоким экраном; прозрачность не используется."},
            {"around","Ориентация","Полный обход","AROUND","VIA","NONE","UNSPECIFIED","Обход колонны","Глубина и перекрытие рассчитываются 3D-рендерером."},
            {"along","Ориентация","Вдоль без контакта","ALONG","VIA","NONE","UNSPECIFIED","Движение вдоль стены","Движение параллельно протяжённой границе."},
            {"contact","Поверхность","Контакт с вертикальной поверхностью","POSS_CANDIDATE","AT","TOUCH","NONE","Магнит на боковой стенке","POSS — гипотеза соответствия цезской серии, не готовый перевод."},
            {"attach","Поверхность","Присоединение сбоку","POSS_CANDIDATE","TO","TOUCH","UNSPECIFIED","Магнит приближается к стенке","Физическая прочность крепления не доказана."},
            {"detach","Поверхность","Отделение сбоку","POSS_CANDIDATE","FROM","CONTACT_LOST","UNSPECIFIED","Магнит отходит от стенки","Нет предположения об инструменте."},
            {"among","Масса","Внутри массы / среди","CONT_CANDIDATE","AT","SURROUND","NONE","Камень среди песка","Текстура — условная масса; нужен пилот с носителями."},
            {"into-mass","Масса","Движение в массу","CONT_CANDIDATE","TO","SURROUND","UNSPECIFIED","Предмет погружается в массу","Не смешивать полость и массу."},
            {"out-of-mass","Масса","Движение из массы","CONT_CANDIDATE","FROM","SURROUND_LOST","UNSPECIFIED","Предмет выходит из массы","Материал намеренно не назван участнику."},
            {"between","Ориентация","Между частями одного тела","BETWEEN","AT","NONE","NONE","Мяч между стойками одной рамы","Две стойки принадлежат одному референту R или G."},
            {"through-gap","Ориентация","Через промежуток","BETWEEN","VIA","NONE","UNSPECIFIED","Мяч проходит между стойками","Реальная 3D-рама; траектория проходит в свободном промежутке между стойками."},
            {"rise","Вертикаль","Вверх относительно ориентира","VERTICAL","UP","NONE","UNSPECIFIED","Предмет поднимается рядом со стеной","Сила, поднимающая предмет, не показана."},
            {"fall","Вертикаль","Вниз относительно ориентира","VERTICAL","DOWN","NONE","UNSPECIFIED","Предмет опускается рядом со стеной","Свободное падение и намеренное опускание не различены."},
            {"admit","Взаимодействие","Открывание прохода перед входом","IN","TO","NONE","GATE_OPENS","Дверь открывается, затем шар входит","Показан доступ, но намерение и разрешение остаются интерпретацией."},
            {"block","Взаимодействие","Закрытый проход и остановка","IN","TOWARD","BARRIER","GATE_CLOSED","Шар останавливается у закрытой двери","Отсутствует достигнутое нахождение внутри."},
            {"push","Взаимодействие","Контакт и совместное смещение","AD","TO","PUSH_CONTACT","CONTACT_TRANSFER","Тело толкает мяч","Каузальность поддержана контактом и последовательностью движения."},
            {"pull","Взаимодействие","Движение с натянутой связью","AD","TO","TETHER","TENSION","Тело тянет мяч на связи","Связь — часть взаимодействия, не третий именуемый участник."},
            {"carry","Взаимодействие","Перенос с опорой","SUPER","ALONG","SUPPORT","SHARED_MOTION","Платформа перемещает лежащий мяч","Не приписывать платформе одушевлённость."},
            {"cover","Взаимодействие","Закрывание в проекции","OCCLUSION","TO","UNSPECIFIED","COVER_MOVES","Панель закрывает предмет","Вид сверху; перекрытие не доказывает контакт."},
            {"uncover","Взаимодействие","Открывание в проекции","OCCLUSION","FROM","UNSPECIFIED","COVER_MOVES","Панель открывает предмет","Парный стимул с той же геометрией."}
        };
        List<Scene> out = new ArrayList<>();
        for (String[] b : base) for (boolean figure : new boolean[]{true, false}) {
            String id = b[0] + (figure ? "-g" : "-r");
            out.add(new Scene(id, out.size()+1, VERSION, b[0], b[0]+(figure?"-r":"-g"),
                figure, b[1], b[2], b[3], b[4], b[5], b[6], b[7], b[8]));
        }
        scenes = List.copyOf(out);
    }
    public List<Scene> all() { return scenes; }
    public Scene require(String id) {
        return scenes.stream().filter(s -> s.id().equals(id)).findFirst()
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Неизвестная сцена"));
    }
}
