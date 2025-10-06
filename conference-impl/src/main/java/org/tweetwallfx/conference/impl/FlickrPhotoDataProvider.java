/*
 * MIT License
 *
 * Copyright (c) 2025 TweetWallFX
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

import com.flickr4java.flickr.photos.Photo;
import com.flickr4java.flickr.photosets.Photoset;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.tweetwallfx.cache.URLContentCacheBase;
import org.tweetwallfx.stepengine.api.DataProvider;
import org.tweetwallfx.stepengine.api.config.StepEngineSettings;
import org.tweetwallfx.stepengine.dataproviders.ImageStorageDataProvider;

public class FlickrPhotoDataProvider
        extends ImageStorageDataProvider.Base
        implements DataProvider.Scheduled {

    private final Config config;
    private volatile boolean initialized = false;

    private FlickrPhotoDataProvider(final Config config) {
        super(config.cacheSize());
        this.config = config;
    }

    @Override
    public ScheduledConfig getScheduleConfig() {
        return config;
    }

    @Override
    public boolean requiresInitialization() {
        return true;
    }

    @Override
    public boolean isInitialized() {
        return initialized;
    }

    @Override
    public void run() {
        loadPhotos();
        initialized = true;
    }

    private void loadPhotos() {
        final FlickrService fs = new FlickrService();

        // determine photoset to load from all available ones
        final List<Photoset> photosets = fs.getAllPhotosets().stream()
                .filter(p -> config.acceptsTitle(p.getTitle()))
                .toList();

        final URLContentCacheBase cacheBase = URLContentCacheBase.getDefault();
        for (final Photoset photoset : photosets) {
            for (Photo photo : fs.loadAllPhotos(photoset)) {
                final Map<String, Object> additionalData = new TreeMap<>();
                additionalData.put("photoId", photo.getId());
                additionalData.put("photosetId", photoset.getId());
                additionalData.put("dateAdded", Optional.ofNullable(photo.getDateAdded()).map(FlickrPhotoDataProvider::date).map(Object::toString).orElse("N/A"));
                additionalData.put("datePosted", Optional.ofNullable(photo.getDatePosted()).map(FlickrPhotoDataProvider::date).map(Object::toString).orElse("N/A"));
                additionalData.put("dateTaken", Optional.ofNullable(photo.getDateTaken()).map(FlickrPhotoDataProvider::date).map(Object::toString).orElse("N/A"));
//                additionalData.put(ImageStorage.KEY_CATEGORY, photoset.getTitle()); // for grouping pictures by day/album

                Stream.of(
//                        photo.getOriginalSize(),
                        photo.getLargeSize(),
                        photo.getMediumSize(),
                        photo.getSmallSize())
                        .filter(Objects::nonNull)
                        .findAny()
                        .ifPresent(s -> cacheBase.getCachedOrLoad(
                        s.getSource(),
                        uc -> add(
                                uc,
                                date(photo.getDatePosted()),
                                additionalData)));
            }
        }
    }

    private static Instant date(final Date date) {
        return Objects.requireNonNull(date).toInstant();
    }

    /**
     * Implementation of {@link DataProvider.Factory} as Service implementation
     * creating {@link FlickrPhotoDataProvider}.
     */
    public static class FactoryImpl implements DataProvider.Factory {

        @Override
        public FlickrPhotoDataProvider create(final StepEngineSettings.DataProviderSetting dataProviderSetting) {
            return new FlickrPhotoDataProvider(dataProviderSetting.getConfig(Config.class));
        }

        @Override
        public Class<FlickrPhotoDataProvider> getDataProviderClass() {
            return FlickrPhotoDataProvider.class;
        }
    }

    /**
     * POJO used to configure {@link FlickrPhotoDataProvider}.
     *
     * <p>
     * Param {@code initialDelay} The type of scheduling to perform. Defaults to
     * {@link ScheduleType#FIXED_RATE}.
     *
     * <p>
     * Param {@code initialDelay} Delay until the first execution in seconds.
     * Defaults to {@code 0L}.
     *
     * <p>
     * Param {@code scheduleDuration} Fixed rate of / delay between consecutive
     * executions in seconds. Defaults to {@code 1800L}.
     */
    public static record Config(
            Integer cacheSize,
            Set<String> photosetTitleFilters,
            // for ScheduledConfig
            ScheduleType scheduleType,
            Long initialDelay,
            Long scheduleDuration) implements ScheduledConfig {

        @SuppressWarnings("unused")
        public Config {
            cacheSize = Objects.requireNonNullElse(cacheSize, 100);
            if (cacheSize <= 0) {
                throw new IllegalArgumentException("property 'cacheSize' must be larger than zero");
            }
            photosetTitleFilters = null == photosetTitleFilters
                    ? Set.of()
                    : Set.copyOf(Objects.requireNonNull(photosetTitleFilters));
            // for ScheduledConfig
            scheduleType = Objects.requireNonNullElse(scheduleType, ScheduleType.FIXED_RATE);
            initialDelay = Objects.requireNonNullElse(initialDelay, 5L);
            scheduleDuration = Objects.requireNonNullElse(scheduleDuration, 30 * 60L);
        }

        @Override
        public Set<String> photosetTitleFilters() {
            return Set.copyOf(photosetTitleFilters);
        }

        public boolean acceptsTitle(final String photosetTitle) {
            return photosetTitleFilters.isEmpty()
                    || photosetTitleFilters.stream().anyMatch(photosetTitle::startsWith);
        }
    }
}
