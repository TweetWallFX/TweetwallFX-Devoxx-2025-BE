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

import com.flickr4java.flickr.Flickr;
import com.flickr4java.flickr.FlickrException;
import com.flickr4java.flickr.REST;
import com.flickr4java.flickr.photos.Exif;
import com.flickr4java.flickr.photos.Extras;
import com.flickr4java.flickr.photos.Photo;
import com.flickr4java.flickr.photos.PhotosInterface;
import com.flickr4java.flickr.photosets.Photoset;
import com.flickr4java.flickr.photosets.PhotosetsInterface;

import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tweetwallfx.config.Configuration;
import org.tweetwallfx.util.ExpiringValue;

public class FlickrService {

    private static final int TOTAL_PHOTOS_PER_PAGE = 100;
    private static final Logger LOG = LoggerFactory.getLogger(FlickrService.class);
    private static final FlickrSettings FLICKR_SETTINGS = Configuration.getInstance()
            .getConfigTyped(FlickrSettings.CONFIG_KEY, FlickrSettings.class);
    private final PhotosInterface photosInterface;
    private final PhotosetsInterface photosetsInterface;
    private final ExpiringValue<List<Photoset>> allPhotosets
            = new ExpiringValue<>(this::loadAllPhotosets, Duration.ofSeconds(60));

    public FlickrService() {
        final Flickr flickr = new Flickr(
                FLICKR_SETTINGS.apiKey(),
                FLICKR_SETTINGS.apiSecret(),
                new REST());
        photosetsInterface = flickr.getPhotosetsInterface();
        photosInterface = flickr.getPhotosInterface();
    }

    /**
     * Get all albums from the Flickr account.
     *
     * @return list of albums
     */
    public List<Photoset> getAllPhotosets() {
        return allPhotosets.getValue();
    }

    private List<Photoset> loadAllPhotosets() {
        LOG.debug("Request to get all Flickr albums");

        try {
            return List.copyOf(photosetsInterface.getList(FLICKR_SETTINGS.userId()).getPhotosets());
        } catch (FlickrException e) {
            throw new RuntimeException("Something went wrong while retrieving the photosets", e);
        }
    }

    /**
     * Get all photos for a specific photoset.
     *
     * @param photoset the album
     *
     * @return the list of loaded Photo instances
     */
    public List<Photo> loadAllPhotos(final Photoset photoset) {
        LOG.debug("Request to get [{}] Flickr photos for photoset '{}'", photoset.getPhotoCount(), photoset.getTitle());
        final int totalItems = photoset.getPhotoCount();
        final int totalPages = totalItems / TOTAL_PHOTOS_PER_PAGE;

        return IntStream.rangeClosed(1, totalPages)
                .parallel()
                .mapToObj(page -> {
                    try {
                        return photosetsInterface.getPhotos(
                                photoset.getId(),
                                Extras.ALL_EXTRAS,
                                Flickr.PRIVACY_LEVEL_NO_FILTER,
                                TOTAL_PHOTOS_PER_PAGE,
                                page);
                    } catch (FlickrException fe) {
                        throw new RuntimeException("Failed loding page " + page + " of photoset " + photoset.getId(), fe);
                    }
                })
                .flatMap(List::stream)
                .toList();
    }

    /**
     * Gets all EXIF informartion for the given photo.
     *
     * @param photo the photo
     *
     * @return the loaded EXIF data
     */
    public List<Exif> getExif(final Photo photo) {
        try {
            return List.copyOf(photosInterface.getExif(photo.getId(), null));
        } catch (FlickrException e) {
            throw new RuntimeException("Something went wrong while retrieving the exif", e);
        }
    }
}
