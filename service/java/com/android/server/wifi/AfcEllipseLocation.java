/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.wifi;

import android.location.Location;

import org.json.JSONObject;

import java.util.Random;

/**
 * Helper class to store a coarse ellipse location used to send to the AFC server. Randomizes the
 * center point of the ellipse with a center leeway parameter.
 */
public class AfcEllipseLocation extends AfcLocation {
    double mLatitude;
    double mLongitude;
    int mSemiMajorAxis;
    int mSemiMinorAxis;
    double mOrientation;
    double mCenterLeeway;

    static final int DEFAULT_SEMI_MAJOR_AXIS_METERS = 500;
    static final int DEFAULT_SEMI_MINOR_AXIS_METERS = 500;
    static final float DEFAULT_ORIENTATION = 90f;
    static final int DEFAULT_CENTER_LEEWAY_METERS = 250;

    /**
     * @param semiMinorAxisMeters The length of the semi major axis of an ellipse which is a
     *                            positive integer in meters.
     * @param semiMajorAxisMeters The length of the semi minor axis of an ellipse which is a
     *                            positive integer in meters.
     * @param orientation         The orientation of the majorAxis field in decimal degrees,
     *                            measured clockwise from True North.
     * @param centerLeeway        The amount in meters that the center of the generated ellipse
     *                            will be randomly shifted +- by. This variable be reasonably
     *                            smaller than the major and minor axes.
     * @param random              The random variable to randomize the center ellipse coordinates.
     * @param location            The geographic coordinates within which the AP or Fixed Client
     *                            Device is located and the AfcEllipseLocation is centered around.
     */
    public AfcEllipseLocation(int semiMinorAxisMeters, int semiMajorAxisMeters, double orientation,
            double centerLeeway, Random random, Location location) {
        super(location);

        mSemiMajorAxis = semiMajorAxisMeters;
        mSemiMinorAxis = semiMinorAxisMeters;
        mCenterLeeway = centerLeeway;
        mOrientation = orientation;

        // The ellipse latitude point is in between the location latitude point +- mCenterLeeway
        mLatitude = random.nextDouble() * (2 * mCenterLeeway) + (location.getLatitude()
                - mCenterLeeway);

        // The ellipse longitude point is in between the location longitude point +- mCenterLeeway
        mLongitude = random.nextDouble() * (2 * mCenterLeeway) + (location.getLongitude()
                - mCenterLeeway);
    }

    // TODO(b/288195475): Implement in future CL
    @Override
    public JSONObject toJson() {
        return null;
    }

    /**
     * Determine if location comparingLocation is within bounds of this AfcEllipseLocation object.
     */
    @Override
    public AfcLocationUtil.InBoundsCheckResult checkLocation(Location comparingLocation) {
        double comparingLatitude = comparingLocation.getLatitude();
        double comparingLongitude = comparingLocation.getLongitude();
        AfcLocationUtil.InBoundsCheckResult inBoundsResult;

        // Equation for circles and ellipses on the cartesian plane that have the major axis
        // centered on the x-axis
        double checkPoint = Math.pow((comparingLongitude - mLongitude) / mSemiMajorAxis, 2)
                + Math.pow((comparingLatitude - mLatitude) / mSemiMinorAxis, 2);

        if (checkPoint > 1.005) {
            inBoundsResult = AfcLocationUtil.InBoundsCheckResult.OUTSIDE_AFC_LOCATION;
        } else if (checkPoint < 0.995) {
            inBoundsResult = AfcLocationUtil.InBoundsCheckResult.INSIDE_AFC_LOCATION;
        } else {
            inBoundsResult = AfcLocationUtil.InBoundsCheckResult.ON_BORDER;
        }
        return inBoundsResult;
    }
}
