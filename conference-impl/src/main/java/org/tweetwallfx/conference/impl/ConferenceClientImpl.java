/*
 * MIT License
 *
 * Copyright (c) 2022-2025 TweetWallFX
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.tweetwallfx.conference.impl;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.GenericType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tweetwallfx.conference.api.ConferenceClient;
import org.tweetwallfx.conference.api.Identifiable;
import org.tweetwallfx.conference.api.RatedTalk;
import org.tweetwallfx.conference.api.RatingClient;
import org.tweetwallfx.conference.api.Room;
import org.tweetwallfx.conference.api.ScheduleSlot;
import org.tweetwallfx.conference.api.SessionType;
import org.tweetwallfx.conference.api.Speaker;
import org.tweetwallfx.conference.api.Talk;
import org.tweetwallfx.conference.api.Track;
import org.tweetwallfx.conference.spi.DateTimeRangeImpl;
import org.tweetwallfx.conference.spi.RatedTalkImpl;
import org.tweetwallfx.conference.spi.RoomImpl;
import org.tweetwallfx.conference.spi.ScheduleSlotImpl;
import org.tweetwallfx.conference.spi.SessionTypeImpl;
import org.tweetwallfx.conference.spi.SpeakerImpl;
import org.tweetwallfx.conference.spi.TalkImpl;
import org.tweetwallfx.conference.spi.TrackImpl;
import org.tweetwallfx.conference.spi.util.RestCallHelper;
import org.tweetwallfx.config.Configuration;
import org.tweetwallfx.util.ExpiringValue;

public final class ConferenceClientImpl implements ConferenceClient, RatingClient {

    private static final Logger LOG = LoggerFactory.getLogger(ConferenceClientImpl.class);
    private final ConferenceClientSettings config;
    private final Map<String, SessionType> sessionTypes;
    private final Map<String, Room> rooms;
    private final Map<String, Track> tracks;
    private final ExpiringValue<Map<String, Integer>> talkFavoriteCounts;
    private final ExpiringValue<Map<WeekDay, List<RatedTalk>>> ratedTalks;

    public ConferenceClientImpl() {
        this.config = Configuration.getInstance().getConfigTyped(
                ConferenceClientSettings.CONFIG_KEY,
                ConferenceClientSettings.class);
        this.sessionTypes = RestCallHelper.readOptionalFrom(config.getEventBaseUri() + "session-types", listOfMaps())
                .orElse(List.of())
                .stream()
                .map(this::convertSessionType)
                .collect(Collectors.toMap(Identifiable::getId, Function.identity()));
        LOG.info("SessionType IDs: {}", sessionTypes.keySet());
        this.rooms = RestCallHelper.readOptionalFrom(config.getEventBaseUri() + "rooms", listOfMaps())
                .orElse(List.of())
                .stream()
                .map(this::convertRoom)
                .collect(Collectors.toMap(Identifiable::getId, Function.identity()));
        LOG.info("Room IDs: {}", rooms.keySet());
        this.tracks = RestCallHelper.readOptionalFrom(config.getEventBaseUri() + "tracks", listOfMaps())
                .orElse(List.of())
                .stream()
                .map(this::convertTrack)
                .collect(Collectors.toMap(Identifiable::getId, Function.identity()));
        LOG.info("Track IDs: {}", tracks.keySet());
        this.ratedTalks = new ExpiringValue<>(this::getVotingResults, Duration.ofSeconds(60));
        this.talkFavoriteCounts = new ExpiringValue<>(this::getTalkFavoriteCounts, Duration.ofMinutes(5));

        // trigger initialization of ratedTalks
        LOG.trace("Initialized ratedTalks: {}", ratedTalks.getValue());
        // trigger initialization of talkFavoriteCounts
        LOG.trace("Initialized talkFavoriteCounts: {}", talkFavoriteCounts.getValue());
    }

    @Override
    public String getName() {
        return "DEVOXX_XYZ";
    }

    @Override
    public List<SessionType> getSessionTypes() {
        return List.copyOf(sessionTypes.values());
    }

    @Override
    public List<Room> getRooms() {
        return List.copyOf(rooms.values());
    }

    @Override
    public List<ScheduleSlot> getSchedule(final String conferenceDay) {
        return RestCallHelper.readOptionalFrom(config.getEventBaseUri() + "schedules/" + conferenceDay, listOfMaps())
                .orElse(List.of())
                .stream()
                .map(this::convertScheduleSlot)
                .toList();
    }

    @Override
    public List<ScheduleSlot> getSchedule(final String conferenceDay, final String roomName) {
        return RestCallHelper
                .readOptionalFrom(config.getEventBaseUri() + "schedules/" + conferenceDay + '/' + roomName,
                        listOfMaps())
                .orElse(List.of())
                .stream()
                .map(this::convertScheduleSlot)
                .toList();
    }

    @Override
    public List<Speaker> getSpeakers() {
        return RestCallHelper
                .readOptionalFrom(config.getEventBaseUri() + "speakers", listOfMaps(), (a, b) -> {
                    a.addAll(b);
                    return a;
                })
                .orElse(List.of())
                .stream()
                .map(this::convertSpeaker)
                .toList();
    }

    @Override
    public Optional<Speaker> getSpeaker(final String speakerId) {
        return RestCallHelper.readOptionalFrom(config.getEventBaseUri() + "speakers/" + speakerId, map())
                .map(this::convertSpeaker);
    }

    @Override
    public List<Talk> getTalks() {
        return RestCallHelper.readOptionalFrom(config.getEventBaseUri() + "talks", listOfMaps())
                .orElse(List.of())
                .stream()
                .map(this::convertTalk)
                .toList();
    }

    @Override
    public Optional<Talk> getTalk(final String talkId) {
        return RestCallHelper.readOptionalFrom(config.getEventBaseUri() + "talks/" + talkId, map())
                .map(this::convertTalk);
    }

    @Override
    public List<Track> getTracks() {
        return List.copyOf(tracks.values());
    }

    private Optional<ConferenceClientSettings> getRatingClientEnabledConfig() {
        return Optional.of(config)
                .filter(ccs -> Objects.nonNull(ccs.getEventStatsBaseUri()))
                .filter(ccs -> Objects.nonNull(ccs.getEventStatsToken()));
    }

    @Override
    public Optional<RatingClient> getRatingClient() {
        return getRatingClientEnabledConfig()
                .map(_ignored_ -> this);
    }

    @Override
    public List<RatedTalk> getRatedTalks(final String conferenceDay) {
        if (Boolean.getBoolean("org.tweetwallfx.conference.randomRatedTalks")) {
            LOG.debug("######## randomizedRatedTalksPerDay");
            return randomizedRatedTalks();
        } else {
            final Map<WeekDay, List<RatedTalk>> votingResults = ratedTalks.getValue();

            return votingResults.entrySet().stream()
                    .filter(e -> e.getKey().dayId().equals(conferenceDay))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElseGet(() -> {
                        LOG.warn(
                                "Lookup of voting results for conferenceDay='{}' failed among the following keySet: {}",
                                conferenceDay, votingResults.keySet());
                        return List.of();
                    });
        }
    }

    @Override
    public List<RatedTalk> getRatedTalksOverall() {
        if (Boolean.getBoolean("org.tweetwallfx.conference.randomRatedTalks")) {
            LOG.debug("######## randomizedRatedTalksWeek");
            return randomizedRatedTalks();
        } else {
            return ratedTalks.getValue().entrySet().stream()
                    .map(Map.Entry::getValue)
                    .flatMap(List::stream)
                    .toList();
        }
    }

    private List<RatedTalk> randomizedRatedTalks() {
        LOG.debug("######## randomizedRatedTalks");
        return RestCallHelper.readOptionalFrom(config.getEventBaseUri() + "talks", listOfMaps())
                .orElse(List.of())
                .stream()
                .filter(talk -> RandomGenerator.getDefault().nextBoolean())
                .map(this::convertTalkToRatedTalk)
                .toList();
    }

    private Map<WeekDay, List<RatedTalk>> getVotingResults() {
        LOG.info("Loading PublicEventStats");
        return getRatingClientEnabledConfig()
                .map(_ignored -> Stream
                .of("monday", "tuesday", "wednesday", "thursday", "friday")
                .collect(Collectors.toMap(
                        WeekDay::new,
                        day -> RestCallHelper
                                .getOptionalResponse(
                                        config.getEventStatsBaseUri() + "getAllRatingStats",
                                        Map.of(
                                                "eventSlug", "dvbe25",
                                                "day", day,
                                                "token", config.getEventStatsToken()))
                                .flatMap(r -> RestCallHelper.readOptionalFrom(r, map()))
                                .map(this::convertVotingResults)
                                .orElseGet(List::of))))
                .orElseGet(Map::of);
    }

    private Map<String, Integer> getTalkFavoriteCounts() {
        LOG.info("Loading TalksFavoriteCounts");
        return getRatingClientEnabledConfig()
                .flatMap(
                        _ignored -> RestCallHelper.postOptionalResponse(
                                config.getEventStatsBaseUri() + "getAllFavoriteCounts",
                                Map.of(),
                                Entity.json(Map.of(
                                        "data", Map.of(
                                                "eventSlug", "dvbe25")))))
                .flatMap(r -> RestCallHelper.readOptionalFrom(r, map()))
                .map(this::convertTalksStats)
                .orElseGet(Map::of);
    }

    private static GenericType<List<Map<String, Object>>> listOfMaps() {
        return new GenericType<List<Map<String, Object>>>() {
        };
    }

    private static GenericType<Map<String, Object>> map() {
        return new GenericType<Map<String, Object>>() {
        };
    }

    @SuppressWarnings("unchecked")
    private List<RatedTalk> convertVotingResults(final Map<String, Object> input) {
        LOG.info("Converting VotingResults: {}", input);
        return retrieveValue(input, "talkRatings", List.class,
                talkRatings -> ((List<?>) talkRatings).stream()
                        .map(o -> (Map<String, Object>) o)
                        .map(this::convertRatedTalk)
                        .toList());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Integer> convertTalksStats(final Map<String, Object> input) {
        LOG.info("Converting TalksStats: {}", input);
        final Map<String, Integer> result = retrieveValue(input, "result", Map.class,
                r -> retrieveValue((Map<String, Object>) r, "talkFavorites", List.class,
                        talkFavorites -> (List<?>) talkFavorites).stream()
                        .map(o -> (Map<String, Object>) o)
                        .collect(Collectors.toMap(
                                perTalkStats -> retrieveValue(perTalkStats, "talkId", String.class),
                                perTalkStats -> retrieveValue(perTalkStats, "favoriteCount", Number.class,
                                        Number::intValue))));
        LOG.info("Updated talkFavoriteCounts to: {}", result);
        return result;
    }

    private RatedTalk convertTalkToRatedTalk(final Map<String, Object> input) {
        LOG.debug("Converting Talk to RatedTalk: {}", input);
        return RatedTalkImpl.builder()
                .withAverageRating(RandomGenerator.getDefault().nextDouble(5))
                .withTotalRating(RandomGenerator.getDefault().nextInt(200))
                .withTalk(convertTalk(input))
                .build();
    }

    private RatedTalk convertRatedTalk(final Map<String, Object> input) {
        LOG.debug("Converting to RatedTalk: {}", input);
        return RatedTalkImpl.builder()
                .withAverageRating(retrieveValue(input, "averageRating", Number.class, Number::doubleValue))
                .withTotalRating(retrieveValue(input, "totalRatings", Number.class, Number::intValue))
                .withTalk(retrieveValue(input, "talkId", String.class,
                        talkId -> getTalk(talkId).get()))
                .build();
    }

    private Room convertRoom(final Map<String, Object> input) {
        LOG.debug("Converting to Room: {}", input);
        return RoomImpl.builder()
                .withId(retrieveValue(input, "id", Number.class, Number::toString))
                .withName(retrieveValue(input, "name", String.class))
                .withCapacity(retrieveValue(input, "capacity", Number.class, Number::intValue))
                .withWeight(retrieveValue(input, "weight", Number.class, Number::doubleValue))
                .build();
    }

    @SuppressWarnings("unchecked")
    private ScheduleSlot convertScheduleSlot(final Map<String, Object> input) {
        LOG.debug("Converting to ScheduleSlot: {}", input);
        return ScheduleSlotImpl.builder()
                .withId(retrieveValue(input, "id", Number.class, Number::toString))
                .withOverflow(retrieveValue(input, "overflow", Boolean.class))
                .withDateTimeRange(DateTimeRangeImpl.builder()
                        .withEnd(retrieveValue(input, "toDate", String.class, Instant::parse))
                        .withStart(retrieveValue(input, "fromDate", String.class, Instant::parse))
                        .build())
                .withFavoriteCount(retrieveValue(input, "totalFavourites", Number.class, Number::intValue))
                .withRoom(rooms.get(alternatives(
                        // either by direct reference to the room ID
                        retrieveValue(input, "roomId", Number.class, Number::toString),
                        // or by having the room object as value
                        retrieveValue(input, "room", Map.class,
                                m -> retrieveValue(m, "id", Number.class, Number::toString)))))
                .withTalk(retrieveValue(input, "proposal", Map.class,
                        m -> convertTalk((Map<String, Object>) m)))
                .build();
    }

    private SessionType convertSessionType(final Map<String, Object> input) {
        LOG.debug("Converting to SessionType: {}", input);
        return SessionTypeImpl.builder()
                .withColor(retrieveValue(input, "cssColor", String.class))
                .withDescription(retrieveValue(input, "description", String.class))
                .withDuration(retrieveValue(input, "duration", Number.class, n -> Duration.ofMinutes(n.longValue())))
                .withId(retrieveValue(input, "id", Number.class, Number::toString))
                .withName(retrieveValue(input, "name", String.class))
                .withPause(retrieveValue(input, "pause", Boolean.class))
                .build();
    }

    @SuppressWarnings("unchecked")
    private Speaker convertSpeaker(final Map<String, Object> input) {
        LOG.debug("Converting to Speaker: {}", input);
        final SpeakerImpl.Builder builder = SpeakerImpl.builder()
                .withId(retrieveValue(input, "id", Number.class, Number::toString))
                .withFirstName(retrieveValue(input, "firstName", String.class, String::trim))
                .withLastName(retrieveValue(input, "lastName", String.class, String::trim))
                .withFullName(String.format("%s %s",
                        retrieveValue(input, "firstName", String.class, String::trim),
                        retrieveValue(input, "lastName", String.class, String::trim)))
                .withCompany(retrieveValue(input, "company", String.class, String::trim))
                .withAvatarURL(retrieveValue(input, "imageUrl", String.class))
                .withTalks(retrieveValue(input, "talks", List.class,
                        list -> ((List<?>) list).stream()
                                .map(o -> (Map<String, Object>) o)
                                .map(this::convertTalk)
                                .toList()));

        // collect optionally configured social user names
        Map.of(
                "twitterHandle", "twitter",
                "linkedInUsername", "linkedin",
                "blueskyUsername", "bluesky",
                "mastodonUsername", "mastodon"
        ).forEach((jsonKey, socialMediaName) -> processValue(
                retrieveValue(input, jsonKey, String.class, String::trim),
                Predicate.not(String::isBlank),
                h -> builder.addSocialMedia(socialMediaName, h)));

        return builder.build();
    }

    @SuppressWarnings("unchecked")
    private Talk convertTalk(final Map<String, Object> input) {
        LOG.debug("Converting to Talk: {}", input);
        String talkId = retrieveValue(input, "id", Number.class, Number::toString);
        return TalkImpl.builder()
                .withId(talkId)
                .withName(retrieveValue(input, "title", String.class))
                .withAudienceLevel(retrieveValue(input, "audienceLevel", String.class))
                .withSessionType(sessionTypes.get(alternatives(
                        // either by direct reference to the session type ID
                        retrieveValue(input, "sessionTypeId", Number.class, Number::toString),
                        // or by having the session type object as value
                        retrieveValue(input, "sessionType", Map.class,
                                m -> retrieveValue(m, "id", Number.class, Number::toString)))))
                .withFavoriteCount(alternatives(
                        // if value is available from public event stats
                        talkFavoriteCounts.getValue().get(talkId),
                        // otherwise fall back to value from talk
                        retrieveValue(input, "totalFavourites", Number.class, Number::intValue)))
                .withLanguage(Locale.ENGLISH)
                .withScheduleSlots(retrieveValue(input, "timeSlots", List.class,
                        list -> ((List<?>) list).stream()
                                .map(o -> (Map<String, Object>) o)
                                .map(this::convertScheduleSlot)
                                .toList()))
                .withSpeakers(retrieveValue(input, "speakers", List.class,
                        list -> ((List<?>) list).stream()
                                .map(o -> (Map<String, Object>) o)
                                .map(this::convertSpeaker)
                                .toList()))
                .withTags(retrieveValue(input, "tags", List.class,
                        list -> ((List<?>) list).stream()
                                .map(o -> (Map<String, Object>) o)
                                .map(m -> retrieveValue(m, "name", String.class))
                                .toList()))
                .withTrack(tracks.get(alternatives(
                        // either by direct reference to the track ID
                        retrieveValue(input, "trackId", Number.class, Number::toString),
                        // or by having the track object as value
                        retrieveValue(input, "track", Map.class,
                                m -> retrieveValue(m, "id", Number.class, Number::toString)))))
                .build();
    }

    private Track convertTrack(final Map<String, Object> input) {
        LOG.debug("Converting to Track: {}", input);
        return TrackImpl.builder()
                .withAvatarURL(retrieveValue(input, "imageURL", String.class))
                .withDescription(retrieveValue(input, "description", String.class))
                .withId(retrieveValue(input, "id", Number.class, Number::toString))
                .withName(retrieveValue(input, "name", String.class))
                .build();
    }

    private static <T> T retrieveValue(final Map<String, Object> data, final String key, final Class<T> type) {
        return type.cast(data.get(key));
    }

    private static <T, R> R retrieveValue(final Map<String, Object> data, final String key, final Class<T> type,
            final Function<T, R> converter) {
        final T t = retrieveValue(data, key, type);
        return null == t
                ? null
                : converter.apply(t);
    }

    private static <T> void processValue(final T value, final Predicate<T> filter, final Consumer<T> consumer) {
        if (null != value && filter.test(value)) {
            consumer.accept(value);
        }
    }

    @SafeVarargs
    private static <T> T alternatives(final T... ts) {
        for (T t : ts) {
            if (null != t) {
                return t;
            }
        }

        return null;
    }

    protected static record WeekDay(String dayId) {

        static WeekDay of(String date) {
            return new WeekDay(LocalDate.parse(date).getDayOfWeek()
                    .getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                    .toLowerCase(Locale.ENGLISH));
        }
    }
}
