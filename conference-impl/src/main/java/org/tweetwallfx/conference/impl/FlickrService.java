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
import com.flickr4java.flickr.photos.Extras;
import com.flickr4java.flickr.photos.Photo;
import com.flickr4java.flickr.photosets.Photoset;
import com.flickr4java.flickr.photosets.PhotosetsInterface;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tweetwallfx.config.Configuration;
import org.tweetwallfx.util.ExpiringValue;

public class FlickrService {

    private static final int TOTAL_PHOTOS_PER_PAGE = 100;
    private static final Logger LOG = LoggerFactory.getLogger(FlickrService.class);
    private static final FlickrSettings FLICKR_SETTINGS = Configuration.getInstance()
            .getConfigTyped(FlickrSettings.CONFIG_KEY, FlickrSettings.class);
    private final PhotosetsInterface photosInterface;
    private final ExpiringValue<List<Photoset>> allPhotosets
            = new ExpiringValue<>(this::loadAllPhotosets, Duration.ofSeconds(60));

    public FlickrService() {
        photosInterface = new Flickr(
                FLICKR_SETTINGS.apiKey(),
                FLICKR_SETTINGS.apiSecret(),
                new REST()
        ).getPhotosetsInterface();
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
            return List.copyOf(photosInterface.getList(FLICKR_SETTINGS.userId()).getPhotosets());
        } catch (FlickrException e) {
            throw new RuntimeException("Something went wrong while retrieving the photosets", e);
        }
    }

    /**
     * Get all photos for a specific photoset.
     *
     * @param photoset the album
     */
    public List<Photo> loadAllPhotos(final Photoset photoset) {
        LOG.debug("Request to get [{}] Flickr photos for photoset '{}'", photoset.getPhotoCount(), photoset.getTitle());

        List<Photo> photos = new ArrayList<>();
        int totalItems = photoset.getPhotoCount();

        try {
            for (int i = 0; i < totalItems; i += TOTAL_PHOTOS_PER_PAGE) {
                // Calculate the current page number and the number of items to retrieve on this iteration
                int currentPage = i / TOTAL_PHOTOS_PER_PAGE + 1;
                int itemsToRetrieve = Math.min(TOTAL_PHOTOS_PER_PAGE, totalItems - i);

                // Retrieve the items for the current page
                LOG.info("Requesting {} items to retrieve from page {} for photoset {}", itemsToRetrieve, currentPage, photoset.getTitle());

                photos.addAll(photosInterface.getPhotos(photoset.getId(),
                        Extras.ALL_EXTRAS,
                        Flickr.PRIVACY_LEVEL_NO_FILTER,
                        TOTAL_PHOTOS_PER_PAGE,
                        currentPage));
            }
        } catch (FlickrException e) {
            throw new RuntimeException(e);
        }

        return photos;
    }
}
