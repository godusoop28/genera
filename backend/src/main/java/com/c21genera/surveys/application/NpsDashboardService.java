package com.c21genera.surveys.application;

import com.c21genera.surveys.domain.NpsCategory;
import com.c21genera.surveys.domain.NpsSurvey;
import com.c21genera.surveys.domain.SurveyChannel;
import com.c21genera.surveys.domain.SurveyStatus;
import com.c21genera.surveys.domain.Touchpoint;
import com.c21genera.surveys.infrastructure.NpsSurveyRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Control de Cambios #28, parte 5: NPS global, por punto de contacto, por
 * asesor, tendencia (semanal o mensual) y comentarios abiertos filtrables.
 */
@Service
@Transactional(readOnly = true)
public class NpsDashboardService {

  private static final ZoneId MX = ZoneId.of("America/Mexico_City");

  public record Score(double nps, long responses, long promoters, long passives, long detractors) {

    static Score of(List<NpsSurvey> answered) {
      long p = answered.stream().filter(s -> s.category() == NpsCategory.PROMOTER).count();
      long d = answered.stream().filter(s -> s.category() == NpsCategory.DETRACTOR).count();
      long n = answered.size();
      return new Score(NpsCategory.nps(p, d, n), n, p, n - p - d, d);
    }
  }

  public record TouchpointRow(Touchpoint touchpoint, int number, String label, boolean systemTriggered, Score score, long sent) {}

  public record AdvisorRow(UUID advisorId, String name, Score score) {}

  public record TrendPoint(String period, String label, Score score) {}

  public record CommentRow(
      UUID surveyId, int score, NpsCategory category, String comment, Touchpoint touchpoint, String touchpointLabel, String advisorName, String clientName, UUID expedienteId, String expedienteFolio, UUID leadId, SurveyChannel channel, Instant answeredAt) {}

  public record Dashboard(
      Instant from,
      Instant to,
      Score global,
      long sent,
      double responseRate,
      List<TouchpointRow> byTouchpoint,
      List<AdvisorRow> byAdvisor,
      String trendGranularity,
      List<TrendPoint> trend,
      List<CommentRow> comments,
      Map<SurveyChannel, Long> byChannel) {}

  public record Filter(Instant from, Instant to, UUID advisorId, Touchpoint touchpoint, NpsCategory category) {}

  private final NpsSurveyRepository surveys;
  private final Clock clock;

  NpsDashboardService(NpsSurveyRepository surveys, Clock clock) {
    this.surveys = surveys;
    this.clock = clock;
  }

  /** onlyAdvisor: el asesor que solo puede ver su propio NPS (null = todos). */
  public Dashboard build(Filter f, UUID onlyAdvisor) {
    Instant to = f.to() == null ? clock.instant() : f.to();
    Instant from = f.from() == null ? to.minus(Duration.ofDays(90)) : f.from();
    UUID advisor = onlyAdvisor != null ? onlyAdvisor : f.advisorId();

    List<NpsSurvey> answered =
        surveys.findByAnsweredAtBetween(from, to).stream()
            .filter(s -> s.getScore() != null)
            .filter(s -> advisor == null || advisor.equals(s.getAdvisorUserId()))
            .filter(s -> f.touchpoint() == null || f.touchpoint() == s.getTouchpoint())
            .toList();
    List<NpsSurvey> sentList =
        surveys.findBySentAtBetween(from, to).stream()
            .filter(s -> advisor == null || advisor.equals(s.getAdvisorUserId()))
            .filter(s -> f.touchpoint() == null || f.touchpoint() == s.getTouchpoint())
            .filter(s -> s.getChannel() != SurveyChannel.QR)
            .toList();
    long sentCount = sentList.size();
    long answeredOfSent = sentList.stream().filter(s -> s.getStatus() == SurveyStatus.ANSWERED).count();

    Map<Touchpoint, List<NpsSurvey>> byTp = answered.stream().collect(Collectors.groupingBy(NpsSurvey::getTouchpoint));
    Map<Touchpoint, Long> sentByTp = sentList.stream().collect(Collectors.groupingBy(NpsSurvey::getTouchpoint, Collectors.counting()));
    List<TouchpointRow> touchpoints = new ArrayList<>();
    for (Touchpoint t : Touchpoint.values()) {
      touchpoints.add(new TouchpointRow(t, t.number(), t.label(), t.systemTriggered(), Score.of(byTp.getOrDefault(t, List.of())), sentByTp.getOrDefault(t, 0L)));
    }

    List<AdvisorRow> advisors =
        answered.stream()
            .filter(s -> s.getAdvisorUserId() != null || s.getAdvisorName() != null)
            .collect(Collectors.groupingBy(s -> s.getAdvisorUserId() != null ? s.getAdvisorUserId().toString() : "name:" + s.getAdvisorName()))
            .values()
            .stream()
            .map(list -> new AdvisorRow(list.get(0).getAdvisorUserId(), Objects.requireNonNullElse(list.get(0).getAdvisorName(), "Sin nombre"), Score.of(list)))
            .sorted(Comparator.comparingLong((AdvisorRow r) -> r.score().responses()).reversed())
            .toList();

    boolean weekly = Duration.between(from, to).toDays() <= 120;
    Function<Instant, LocalDate> bucket =
        at -> {
          LocalDate d = LocalDate.ofInstant(at, MX);
          return weekly ? d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) : d.withDayOfMonth(1);
        };
    Map<LocalDate, List<NpsSurvey>> buckets = answered.stream().collect(Collectors.groupingBy(s -> bucket.apply(s.getAnsweredAt()), java.util.TreeMap::new, Collectors.toList()));
    List<TrendPoint> trend = new ArrayList<>();
    LocalDate cursor = bucket.apply(from);
    LocalDate end = LocalDate.ofInstant(to, MX);
    while (!cursor.isAfter(end) && trend.size() < 60) {
      trend.add(new TrendPoint(cursor.toString(), label(cursor, weekly), Score.of(buckets.getOrDefault(cursor, List.of()))));
      cursor = weekly ? cursor.plusWeeks(1) : cursor.plusMonths(1);
    }

    List<CommentRow> comments =
        answered.stream()
            .filter(s -> s.getComment() != null)
            .filter(s -> f.category() == null || s.category() == f.category())
            .sorted(Comparator.comparing(NpsSurvey::getAnsweredAt).reversed())
            .limit(200)
            .map(
                s ->
                    new CommentRow(
                        s.getId(),
                        s.getScore(),
                        s.category(),
                        s.getComment(),
                        s.getTouchpoint(),
                        s.getTouchpoint().label(),
                        s.getAdvisorName(),
                        s.getClientName(),
                        s.getExpedienteId(),
                        s.getExpedienteFolio(),
                        s.getLeadId(),
                        s.getChannel(),
                        s.getAnsweredAt()))
            .toList();

    Map<SurveyChannel, Long> byChannel = new LinkedHashMap<>();
    for (SurveyChannel c : SurveyChannel.values()) {
      byChannel.put(c, answered.stream().filter(s -> s.getChannel() == c).count());
    }

    return new Dashboard(
        from,
        to,
        Score.of(answered),
        sentCount,
        sentCount == 0 ? 0 : Math.round(answeredOfSent * 1000.0 / sentCount) / 10.0,
        touchpoints,
        advisors,
        weekly ? "WEEK" : "MONTH",
        trend,
        comments,
        byChannel);
  }

  private static final String[] MONTHS = {"ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"};

  private static String label(LocalDate d, boolean weekly) {
    return weekly ? d.getDayOfMonth() + " " + MONTHS[d.getMonthValue() - 1] : MONTHS[d.getMonthValue() - 1] + " " + d.getYear();
  }
}
