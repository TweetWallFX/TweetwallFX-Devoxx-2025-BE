#!/bin/bash -eu

#
# MIT License
#
# Copyright (c) 2022-2025 TweetWallFX
#
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included in all
# copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.
#
# SPDX-License-Identifier: MIT
#

##############################################################################

# Check presence of type value
type=${1:?type was not provided}

# Ensure (for the purposes of this script) that LANG is set to en_US
export LANG=en_US.UTF-8

# Set Java Platform to 25
export JAVA_PLATFORM_VERSION=25

# Update the git repos
echo "# Pull the latest changes for TweetwallFX"
test -d ../TweetwallFX \
&& (cd ../TweetwallFX \
&& git branch \
&& git pull --all --prune)

echo "# Pull the latest changes for $(basename ${PWD})"
git branch \
&& git pull --all --prune

# Run the chosen Tweetwall (based on type)
runTask=$(echo "run_${type}" | awk -F _ '{printf "%s", $1; for(i=2; i<=NF; i++) printf "%s", toupper(substr($i,1,1)) substr($i,2); print"";}')
./gradlew ${runTask}
