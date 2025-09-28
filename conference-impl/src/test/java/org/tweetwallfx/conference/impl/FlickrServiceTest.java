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

import static org.tweetwallfx.util.ToString.createToString;
import static org.tweetwallfx.util.ToString.mapEntry;
import static org.tweetwallfx.util.ToString.mapOf;

import com.flickr4java.flickr.photosets.Photoset;

@org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable(named = "CI", matches = ".*")
public class FlickrServiceTest {

    private final FlickrService fs = new FlickrService();

    @org.junit.jupiter.api.Test
    void getAllPhotosets() {
        for (Photoset p : fs.getAllPhotosets()) {
            System.out.println("allPhotoSet: " + toString(p));
        }
    }

    private String toString(Photoset p) {
        return createToString(
                p,
                mapOf(
            mapEntry("id", p.getId()),
            mapEntry("url", p.getUrl()),
            mapEntry("owner", p.getOwner()),
            mapEntry("primaryPhoto", p.getPrimaryPhoto()),
//            mapEntry("secret", p.getSecret()),
//            mapEntry("server", p.getServer()),
//            mapEntry("farm", p.getFarm()),
            mapEntry("photoCount", p.getPhotoCount()),
            mapEntry("videoCount", p.getVideoCount()),
            mapEntry("viewCount", p.getViewCount()),
            mapEntry("commentCount", p.getCommentCount()),
            mapEntry("dateCreate", p.getDateCreate()),
            mapEntry("dateUpdate", p.getDateUpdate()),
            mapEntry("title", p.getTitle()),
            mapEntry("description", p.getDescription()),
            mapEntry("isVisible", p.isVisible()),
            mapEntry("canComment", p.isCanComment()),
            mapEntry("needsInterstitial", p.isNeedsInterstitial())
                ));
    }
}
