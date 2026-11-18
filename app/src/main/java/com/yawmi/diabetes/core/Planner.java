package com.yawmi.diabetes.core;

import java.time.*;
import java.util.*;

/** Pure JVM calendar logic. A reminder is never a prescription decision. */
public final class Planner {
    private Planner() {}
    public static final class Medication {
        public final String id,mode,meal,relation,plannedTime;
        public final List<String> times;public final Set<Integer> weekdays;
        public final long anchorMillis;public final int intervalHours,offsetMinutes;public final boolean active;
        public Medication(String id,String mode,List<String> times,Set<Integer> weekdays,long anchorMillis,int intervalHours,String meal,String relation,int offsetMinutes,String plannedTime,boolean active){
            this.id=id;this.mode=mode;this.times=times;this.weekdays=weekdays;this.anchorMillis=anchorMillis;this.intervalHours=intervalHours;this.meal=meal;this.relation=relation;this.offsetMinutes=offsetMinutes;this.plannedTime=plannedTime;this.active=active;
        }
    }
    public static final class Meal {
        public final String id,type;public final long millis;
        public Meal(String id,String type,long millis){this.id=id;this.type=type;this.millis=millis;}
        public String id(){return id;}public String type(){return type;}public long millis(){return millis;}
    }
    public static final class Event {
        private final String id,medicationId;private final long millis;private final boolean estimated;
        public Event(String id,String medicationId,long millis,boolean estimated){this.id=id;this.medicationId=medicationId;this.millis=millis;this.estimated=estimated;}
        public String id(){return id;}public String medicationId(){return medicationId;}public long millis(){return millis;}public boolean estimated(){return estimated;}
    }

    public static List<Event> events(Medication med, List<Meal> meals, long from, long until, ZoneId zone) {
        if (!med.active || until <= from) return List.of();
        List<Event> result = new ArrayList<>();
        if (med.mode.equals("interval")) {
            if (med.intervalHours < 1 || med.intervalHours > 168 || med.anchorMillis <= 0) return List.of();
            long step = med.intervalHours * 3_600_000L;
            long first = Math.max(0, Math.floorDiv(from - med.anchorMillis, step));
            for (long n = first; n < first + 400; n++) {
                long time = med.anchorMillis + n * step;
                if (time >= until) break;
                if (time >= from) result.add(new Event(med.id + "_i_" + n, med.id, time, false));
            }
        } else if (med.mode.equals("meal") && !med.relation.equals("before")) {
            for (Meal meal : meals) {
                if (!meal.type.equals(med.meal)) continue;
                long time = meal.millis + (med.relation.equals("after") ? med.offsetMinutes * 60_000L : 0);
                if (time >= from && time < until) result.add(new Event(med.id + "_m_" + meal.id, med.id, time, false));
            }
        } else {
            LocalDate start = Instant.ofEpochMilli(from).atZone(zone).toLocalDate().minusDays(1);
            LocalDate end = Instant.ofEpochMilli(until).atZone(zone).toLocalDate().plusDays(1);
            List<String> times = med.mode.equals("meal") ? List.of(med.plannedTime) : med.times;
            for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
                if (med.mode.equals("weekly") && !med.weekdays.contains(date.getDayOfWeek().getValue())) continue;
                for (String text : new LinkedHashSet<>(times)) {
                    LocalTime local;
                    try { local = LocalTime.parse(text); } catch (Exception ignored) { continue; }
                    ZonedDateTime intended = date.atTime(local).atZone(zone);
                    if (med.mode.equals("meal")) intended = intended.minusMinutes(med.offsetMinutes);
                    long time = intended.toInstant().toEpochMilli();
                    if (time >= from && time < until) result.add(new Event(med.id + "_" + date + "_" + text, med.id, time, med.mode.equals("meal")));
                }
            }
        }
        return result.stream().sorted(Comparator.comparingLong(Event::millis)).collect(java.util.stream.Collectors.toList());
    }

    public static boolean quiet(LocalTime now, LocalTime start, LocalTime end) {
        if (start.equals(end)) return false;
        return start.isBefore(end) ? !now.isBefore(start) && now.isBefore(end)
                : !now.isBefore(start) || now.isBefore(end);
    }

    public static long nextAwake(long instant, ZoneId zone, LocalTime start, LocalTime end) {
        ZonedDateTime z = Instant.ofEpochMilli(instant).atZone(zone);
        if (!quiet(z.toLocalTime(), start, end)) return instant;
        LocalDate date = z.toLocalDate();
        if (start.isAfter(end) && !z.toLocalTime().isBefore(start)) date = date.plusDays(1);
        return date.atTime(end).atZone(zone).toInstant().toEpochMilli();
    }

    public static double parseNumber(String input) {
        StringBuilder normalized = new StringBuilder();
        for (char c : input.trim().toCharArray()) {
            int digit = Character.digit(c, 10);
            if (digit >= 0) normalized.append(digit);
            else if (c == '\u066B' || c == ',') normalized.append('.');
            else normalized.append(c);
        }
        double value = Double.parseDouble(normalized.toString());
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite value");
        return value;
    }

    public static double toMg(double value, String unit) { return unit.equals("mmol/L") ? value * 18.0182 : value; }
    public static double fromMg(double value, String unit) { return unit.equals("mmol/L") ? value / 18.0182 : value; }
}
