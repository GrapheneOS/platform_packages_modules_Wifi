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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.location.Location;

import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Random;

/**
 * Unit tests for {@link AfcLocation}.
 */
@SmallTest
public class AfcEllipseLocationTest {
    @Rule
    public final MockitoRule mockito = MockitoJUnit.rule();
    @Mock
    private Location mLocation;
    @Mock
    private Random mRandom;
    private AfcEllipseLocation mAfcEllipseLocation;
    private double mLongitudeCenterOfEllipse;
    private double mLatitudeCenterOfEllipse;
    private static final double RANDOM_DOUBLE = .5;
    private static final double STARTING_LATITUDE = 9.9;
    private static final double STARTING_LONGITUDE = 2.5;

    @Before
    public void setUp() {
        when(mLocation.getLatitude()).thenReturn(STARTING_LATITUDE);
        when(mLocation.getLongitude()).thenReturn(STARTING_LONGITUDE);
        when(mRandom.nextDouble()).thenReturn(RANDOM_DOUBLE);

        mAfcEllipseLocation = new AfcEllipseLocation(
                AfcEllipseLocation.DEFAULT_SEMI_MINOR_AXIS_METERS,
                AfcEllipseLocation.DEFAULT_SEMI_MAJOR_AXIS_METERS,
                AfcEllipseLocation.DEFAULT_ORIENTATION,
                AfcEllipseLocation.DEFAULT_CENTER_LEEWAY_METERS, mRandom, mLocation
        );

        mLongitudeCenterOfEllipse = mAfcEllipseLocation.mLongitude;
        mLatitudeCenterOfEllipse = mAfcEllipseLocation.mLatitude;

        // Center point of ellipse is at point (mLongitudeCenterOfEllipse, mLatitudeCenterOfEllipse)
        when(mLocation.getLongitude()).thenReturn(mLongitudeCenterOfEllipse);
        when(mLocation.getLatitude()).thenReturn(mLatitudeCenterOfEllipse);
    }

    /**
     * Verify that the ellipse center longitude and latitude fields are equal to expected values.
     */
    @Test
    public void verifyCenterValues() {
        assertThat(mLongitudeCenterOfEllipse).isEqualTo(
                RANDOM_DOUBLE * 2 * AfcEllipseLocation.DEFAULT_CENTER_LEEWAY_METERS + (
                        STARTING_LONGITUDE
                                - AfcEllipseLocation.DEFAULT_CENTER_LEEWAY_METERS));
        assertThat(mLatitudeCenterOfEllipse).isEqualTo(
                RANDOM_DOUBLE * 2 * AfcEllipseLocation.DEFAULT_CENTER_LEEWAY_METERS + (
                        STARTING_LATITUDE
                                - AfcEllipseLocation.DEFAULT_CENTER_LEEWAY_METERS));
    }

    /**
     * Verify that moving the longitude center coordinate gradually out causes the correct
     * return value in {@link AfcEllipseLocation#checkLocation(Location)}.
     */
    @Test
    public void adjustLongitudeValue() {
        // Center point of ellipse is at point (mLongitudeCenterOfEllipse, mLatitudeCenterOfEllipse)
        when(mLocation.getLongitude()).thenReturn(mLongitudeCenterOfEllipse);
        when(mLocation.getLatitude()).thenReturn(mLatitudeCenterOfEllipse);

        assertThat(mAfcEllipseLocation.checkLocation(mLocation)).isEqualTo(
                AfcLocationUtil.InBoundsCheckResult.INSIDE_AFC_LOCATION);

        double movingLongitudePoint =
                mLongitudeCenterOfEllipse + AfcEllipseLocation.DEFAULT_SEMI_MAJOR_AXIS_METERS - 2;
        when(mLocation.getLongitude()).thenReturn(movingLongitudePoint);
        assertThat(mAfcEllipseLocation.checkLocation(mLocation)).isEqualTo(
                AfcLocationUtil.InBoundsCheckResult.INSIDE_AFC_LOCATION);

        movingLongitudePoint =
                mLongitudeCenterOfEllipse + AfcEllipseLocation.DEFAULT_SEMI_MAJOR_AXIS_METERS;
        when(mLocation.getLongitude()).thenReturn(movingLongitudePoint);
        assertThat(mAfcEllipseLocation.checkLocation(mLocation)).isEqualTo(
                AfcLocationUtil.InBoundsCheckResult.ON_BORDER);

        movingLongitudePoint =
                mLongitudeCenterOfEllipse + AfcEllipseLocation.DEFAULT_SEMI_MAJOR_AXIS_METERS + 2;
        when(mLocation.getLongitude()).thenReturn(movingLongitudePoint);
        assertThat(mAfcEllipseLocation.checkLocation(mLocation)).isEqualTo(
                AfcLocationUtil.InBoundsCheckResult.OUTSIDE_AFC_LOCATION);

        movingLongitudePoint =
                mLongitudeCenterOfEllipse + AfcEllipseLocation.DEFAULT_SEMI_MAJOR_AXIS_METERS + 10;
        when(mLocation.getLongitude()).thenReturn(movingLongitudePoint);
        assertThat(mAfcEllipseLocation.checkLocation(mLocation)).isEqualTo(
                AfcLocationUtil.InBoundsCheckResult.OUTSIDE_AFC_LOCATION);
    }

    /**
     * Verify that moving the latitude center coordinate gradually out causes the correct
     * return value in {@link AfcEllipseLocation#checkLocation(Location)}.
     */
    @Test
    public void adjustLatitudeValue() {
        // Center point of ellipse is at point (mLongitudeCenterOfEllipse, mLatitudeCenterOfEllipse)
        when(mLocation.getLongitude()).thenReturn(mLongitudeCenterOfEllipse);
        when(mLocation.getLatitude()).thenReturn(mLatitudeCenterOfEllipse);

        assertThat(mAfcEllipseLocation.checkLocation(mLocation)).isEqualTo(
                AfcLocationUtil.InBoundsCheckResult.INSIDE_AFC_LOCATION);

        double movingLatitudePoint =
                mLatitudeCenterOfEllipse + AfcEllipseLocation.DEFAULT_SEMI_MINOR_AXIS_METERS - 2;
        when(mLocation.getLatitude()).thenReturn(movingLatitudePoint);
        assertThat(mAfcEllipseLocation.checkLocation(mLocation)).isEqualTo(
                AfcLocationUtil.InBoundsCheckResult.INSIDE_AFC_LOCATION);

        movingLatitudePoint =
                mLatitudeCenterOfEllipse + AfcEllipseLocation.DEFAULT_SEMI_MAJOR_AXIS_METERS;
        when(mLocation.getLatitude()).thenReturn(movingLatitudePoint);
        assertThat(mAfcEllipseLocation.checkLocation(mLocation)).isEqualTo(
                AfcLocationUtil.InBoundsCheckResult.ON_BORDER);

        movingLatitudePoint =
                mLatitudeCenterOfEllipse + AfcEllipseLocation.DEFAULT_SEMI_MAJOR_AXIS_METERS + 2;
        when(mLocation.getLatitude()).thenReturn(movingLatitudePoint);
        assertThat(mAfcEllipseLocation.checkLocation(mLocation)).isEqualTo(
                AfcLocationUtil.InBoundsCheckResult.OUTSIDE_AFC_LOCATION);

        movingLatitudePoint =
                mLatitudeCenterOfEllipse + AfcEllipseLocation.DEFAULT_SEMI_MAJOR_AXIS_METERS + 10;
        when(mLocation.getLatitude()).thenReturn(movingLatitudePoint);
        assertThat(mAfcEllipseLocation.checkLocation(mLocation)).isEqualTo(
                AfcLocationUtil.InBoundsCheckResult.OUTSIDE_AFC_LOCATION);
    }
}
