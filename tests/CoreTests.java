import com.yawmi.diabetes.core.*;
import java.time.*;
import java.util.*;

public class CoreTests {
    static int checks;
    static void check(boolean condition, String name) { checks++; if (!condition) throw new AssertionError(name); }
    static long at(String value) { return Instant.parse(value).toEpochMilli(); }
    static Planner.Medication med(String mode, List<String> times, Set<Integer> days, long anchor, int hours, String relation) {
        return new Planner.Medication("m1",mode,times,days,anchor,hours,"breakfast",relation,30,"08:00",true);
    }
    public static void main(String[] args) throws Exception {
        ZoneId utc = ZoneId.of("UTC"); long from = at("2026-11-16T00:00:00Z"), until = from + 86_400_000;
        var daily = Planner.events(med("daily",List.of("08:00","20:00","08:00"),Set.of(),0,0,"none"),List.of(),from,until,utc);
        check(daily.size()==2,"duplicate times removed");
        check(daily.get(0).millis()==from+8*3_600_000,"daily wall-clock");
        var interval = Planner.events(med("interval",List.of(),Set.of(),from+7*3_600_000,12,"none"),List.of(),from,until,utc);
        check(interval.size()==2 && interval.get(1).millis()-interval.get(0).millis()==43_200_000,"interval from prescription anchor");
        check(interval.get(0).millis()==from+7*3_600_000,"daily is not interval");
        check(Planner.events(med("weekly",List.of("08:00"),Set.of(2),0,0,"none"),List.of(),from,until,utc).isEmpty(),"weekly wrong day");
        check(Planner.events(med("weekly",List.of("08:00"),Set.of(1),0,0,"none"),List.of(),from,until,utc).size()==1,"weekly correct day");
        check(Planner.events(med("meal",List.of(),Set.of(),0,0,"after"),List.of(),from,until,utc).isEmpty(),"no invented meal time");
        var meal = new Planner.Meal("b1","breakfast",from+9*3_600_000);
        var after = Planner.events(med("meal",List.of(),Set.of(),0,0,"after"),List.of(meal),from,until,utc);
        check(after.size()==1 && after.get(0).millis()==meal.millis()+1_800_000,"meal starts at actual time");
        check(after.get(0).id().contains("b1"),"meal idempotency key");
        var before = Planner.events(med("meal",List.of(),Set.of(),0,0,"before"),List.of(),from,until,utc);
        check(before.size()==1 && before.get(0).estimated() && before.get(0).millis()==from+7*3_600_000+1_800_000,"before-meal explicitly estimated");
        check(Planner.quiet(LocalTime.of(23,0),LocalTime.of(22,0),LocalTime.of(7,0)),"quiet overnight");
        check(Planner.quiet(LocalTime.of(6,59),LocalTime.of(22,0),LocalTime.of(7,0)),"quiet morning");
        check(!Planner.quiet(LocalTime.of(7,0),LocalTime.of(22,0),LocalTime.of(7,0)),"quiet end exclusive");
        check(!Planner.quiet(LocalTime.of(12,0),LocalTime.of(7,0),LocalTime.of(7,0)),"equal quiet bounds disabled");
        check(Planner.nextAwake(from+23*3_600_000,utc,LocalTime.of(22,0),LocalTime.of(7,0))==until+7*3_600_000,"quiet shifts across midnight");
        check(Planner.parseNumber("١٢٣٫٥")==123.5,"Arabic decimal digits");
        check(Planner.parseNumber("۱۲۳,۵")==123.5,"Persian digits");
        check(Math.abs(Planner.toMg(10,"mmol/L")-180.182)<0.001,"glucose unit conversion");
        check(Math.abs(Planner.fromMg(Planner.toMg(7.2,"mmol/L"),"mmol/L")-7.2)<0.00001,"conversion round trip");
        boolean rejected=false;try{Planner.parseNumber("NaN");}catch(Exception e){rejected=true;}check(rejected,"nonfinite rejected");
        var dst = Planner.events(med("daily",List.of("02:30"),Set.of(),0,0,"none"),List.of(),at("2026-03-08T00:00:00Z"),at("2026-03-09T00:00:00Z"),ZoneId.of("America/New_York"));
        check(dst.size()==1,"DST gap one event");
        check(dst.get(0).millis()==at("2026-03-08T07:30:00Z"),"DST gap resolved forward");
        var overlap = Planner.events(med("daily",List.of("01:30"),Set.of(),0,0,"none"),List.of(),at("2026-11-01T00:00:00Z"),at("2026-11-02T00:00:00Z"),ZoneId.of("America/New_York"));
        check(overlap.size()==1,"DST overlap no duplicate dose");
        byte[] content="بياناتي الخاصة / my private data".getBytes(java.nio.charset.StandardCharsets.UTF_8); char[] pass="correct-long-password".toCharArray();
        byte[] encrypted=BackupCipher.encrypt(content,pass);
        check(Arrays.equals(BackupCipher.decrypt(encrypted,pass),content),"portable encrypted backup roundtrip");
        check(!Arrays.equals(encrypted,BackupCipher.encrypt(content,pass)),"random salt and IV");
        rejected=false;try{BackupCipher.decrypt(encrypted,"incorrect-password".toCharArray());}catch(Exception e){rejected=true;}check(rejected,"wrong password rejected");
        encrypted[encrypted.length-1]^=1;rejected=false;try{BackupCipher.decrypt(encrypted,pass);}catch(Exception e){rejected=true;}check(rejected,"tampering rejected");
        rejected=false;try{BackupCipher.encrypt(content,"short".toCharArray());}catch(Exception e){rejected=true;}check(rejected,"short password rejected");
        check(Escapes.html("<script>&\"'").equals("&lt;script&gt;&amp;&quot;&#39;"),"HTML text is escaped");
        check(Escapes.csv("=2+2").equals("\"'=2+2\""),"CSV formula prefix neutralized");
        check(Escapes.csv("  @SUM(A1)").startsWith("\"'"),"CSV leading whitespace formula neutralized");
        check(Escapes.csv("a\"b").equals("\"a\"\"b\""),"CSV quote escaping");
        System.out.println("PASS "+checks+" domain and cryptography checks");
    }
}
