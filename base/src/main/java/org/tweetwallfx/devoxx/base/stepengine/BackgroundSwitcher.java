/*
 * MIT License
 *
 * Copyright (c) 2016-2025 TweetWallFX
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
package org.tweetwallfx.devoxx.base.stepengine;

import java.util.Collection;
import java.util.List;

import org.tweetwallfx.controls.Wordle;
import org.tweetwallfx.controls.WordleSkin;
import org.tweetwallfx.stepengine.api.DataProvider;
import org.tweetwallfx.stepengine.api.Step;
import org.tweetwallfx.stepengine.api.StepEngine.MachineContext;
import org.tweetwallfx.stepengine.api.config.StepEngineSettings;

public class BackgroundSwitcher implements Step {

    private final Config config;
    private int currentIndexOfBackground = 0;

    public BackgroundSwitcher(Config config) {
        this.config = config;
    }

    @Override
    public boolean shouldSkip(MachineContext context) {
        return false;
    }

    @Override
    public void doStep(MachineContext machineContext) {
        var wordleSkin = (WordleSkin) machineContext.get("WordleSkin");
        ((Wordle)wordleSkin.getNode()).backgroundGraphicProperty().set(config.backgrounds.get(currentIndexOfBackground));
        currentIndexOfBackground = (currentIndexOfBackground+1) < config.backgrounds.size() ? currentIndexOfBackground+1 : 0;
        machineContext.proceed();
    }

    @Override
    public boolean requiresPlatformThread() {
        return true;
    }

   /**
     * Implementation of {@link Step.Factory} as Service implementation creating
     * {@link BackgroundSwitcher}.
     */
    public static final class FactoryImpl implements Step.Factory {

        @Override
        public BackgroundSwitcher create(final StepEngineSettings.StepDefinition stepDefinition) {
            return new BackgroundSwitcher(stepDefinition.getConfig(Config.class));
        }

        @Override
        public Class<BackgroundSwitcher> getStepClass() {
            return BackgroundSwitcher.class;
        }

        @Override
        public Collection<Class<? extends DataProvider>> getRequiredDataProviders(final StepEngineSettings.StepDefinition stepSettings) {
            return List.of();
        }
    }

    public static class Config {
        public List<String> backgrounds = List.of();
    }
}
