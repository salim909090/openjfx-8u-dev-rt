/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package javafx.animation;

import com.sun.javafx.animation.TickCalculation;
import javafx.animation.Animation.Status;
import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.util.Duration;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class SequentialTransitionPlayTest {

    public static final double TICK_MILLIS = TickCalculation.toMillis(100);
    public static final long TICK_STEP = Math.round(TICK_MILLIS);

    LongProperty xProperty = new SimpleLongProperty();
    LongProperty yProperty = new SimpleLongProperty();
    AbstractMasterTimerMock amt;
    SequentialTransition st;
    Transition child1X;
    Transition child1Y;

    @Before
    public void setUp() {
        amt = new AbstractMasterTimerMock();
        st = new SequentialTransition(amt);
        child1X = new Transition() {
            {
                setCycleDuration(Duration.minutes(1));
                setInterpolator(Interpolator.LINEAR);
            }

            @Override
            protected void interpolate(double d) {
                xProperty.set(Math.round(d * 60000));
            }
        };
        child1Y = new Transition() {
            {
                setCycleDuration(Duration.seconds(10));
                setInterpolator(Interpolator.LINEAR);
            }

            @Override
            protected void interpolate(double d) {
                yProperty.set(Math.round(d * 10000));
            }
        };
    }

    @Test
    public void testSimplePlay() {
        st.getChildren().addAll(child1X, child1Y);

        st.play();
        assertEquals(Status.RUNNING, st.getStatus());
        assertEquals(Status.STOPPED, child1X.getStatus());
        assertEquals(Status.STOPPED, child1Y.getStatus());

        amt.pulse();
        assertEquals(TickCalculation.toDuration(100), st.getCurrentTime());
        assertEquals(TickCalculation.toDuration(100), child1X.getCurrentTime());
        assertEquals(Duration.ZERO, child1Y.getCurrentTime());
        assertEquals(Math.round(TICK_MILLIS), xProperty.get());
        assertEquals(0, yProperty.get());

        assertEquals(Status.RUNNING, st.getStatus());
        assertEquals(Status.RUNNING, child1X.getStatus());
        assertEquals(Status.STOPPED, child1Y.getStatus());

        st.jumpTo(Duration.minutes(1).subtract(TickCalculation.toDuration(100)));

        assertEquals(Status.RUNNING, st.getStatus());
        assertEquals(Status.RUNNING, child1X.getStatus());
        assertEquals(Status.STOPPED, child1Y.getStatus());
        assertEquals(60000 - Math.round(TICK_MILLIS), xProperty.get());
        assertEquals(0, yProperty.get());

        amt.pulse();
        assertEquals(Status.RUNNING, st.getStatus());
        assertEquals(Status.STOPPED, child1X.getStatus());
        assertEquals(Status.STOPPED, child1Y.getStatus());
        assertEquals(60000, xProperty.get());
        assertEquals(0, yProperty.get());

        amt.pulse();

        assertEquals(Status.RUNNING, st.getStatus());
        assertEquals(Status.STOPPED, child1X.getStatus());
        assertEquals(Status.RUNNING, child1Y.getStatus());
        assertEquals(60000, xProperty.get());
        assertEquals(Math.round(TICK_MILLIS), yProperty.get());

        st.jumpTo(Duration.minutes(1).add(Duration.seconds(10)).subtract(TickCalculation.toDuration(100)));

        assertEquals(Status.RUNNING, st.getStatus());
        assertEquals(Status.STOPPED, child1X.getStatus());
        assertEquals(Status.RUNNING, child1Y.getStatus());
        assertEquals(60000, xProperty.get());
        assertEquals(10000 - Math.round(TICK_MILLIS), yProperty.get());

        amt.pulse();

        assertEquals(Status.STOPPED, st.getStatus());
        assertEquals(Status.STOPPED, child1X.getStatus());
        assertEquals(Status.STOPPED, child1Y.getStatus());
        assertEquals(60000, xProperty.get());
        assertEquals(10000, yProperty.get());
    }


    @Test
    public void testSimplePlayReversed() {
        st.getChildren().addAll(child1X, child1Y);
        st.setRate(-1.0);
        st.jumpTo(Duration.seconds(70));

        st.play();
        assertEquals(Status.RUNNING, st.getStatus());
        assertEquals(Status.STOPPED, child1X.getStatus());
        assertEquals(Status.STOPPED, child1Y.getStatus());
        assertEquals(Duration.seconds(70), st.getCurrentTime());
        assertEquals(Duration.seconds(60), child1X.getCurrentTime());
        assertEquals(Duration.seconds(10), child1Y.getCurrentTime());

        amt.pulse();
        assertEquals(Duration.seconds(70).subtract(TickCalculation.toDuration(100)), st.getCurrentTime());
        assertEquals(Duration.seconds(60), child1X.getCurrentTime());
        assertEquals(Duration.seconds(10).subtract(TickCalculation.toDuration(100)), child1Y.getCurrentTime());
        assertEquals(60000, xProperty.get());
        assertEquals(10000 - Math.round(TICK_MILLIS), yProperty.get());

        assertEquals(Status.RUNNING, st.getStatus());
        assertEquals(Status.STOPPED, child1X.getStatus());
        assertEquals(Status.RUNNING, child1Y.getStatus());

        st.jumpTo(Duration.minutes(1).add(TickCalculation.toDuration(100)));

        assertEquals(Status.RUNNING, st.getStatus());
        assertEquals(Status.STOPPED, child1X.getStatus());
        assertEquals(Status.RUNNING, child1Y.getStatus());
        assertEquals(60000, xProperty.get());
        assertEquals(Math.round(TICK_MILLIS), yProperty.get());

        amt.pulse();
        assertEquals(Status.RUNNING, st.getStatus());
        assertEquals(Status.STOPPED, child1X.getStatus());
        assertEquals(Status.STOPPED, child1Y.getStatus());
        assertEquals(60000, xProperty.get());
        assertEquals(0, yProperty.get());

        amt.pulse();

        assertEquals(Status.RUNNING, st.getStatus());
        assertEquals(Status.RUNNING, child1X.getStatus());
        assertEquals(Status.STOPPED, child1Y.getStatus());
        assertEquals(60000 - Math.round(TICK_MILLIS), xProperty.get());
        assertEquals(0, yProperty.get());

        st.jumpTo(TickCalculation.toDuration(100));

        assertEquals(Status.RUNNING, st.getStatus());
        assertEquals(Status.RUNNING, child1X.getStatus());
        assertEquals(Status.STOPPED, child1Y.getStatus());
        assertEquals(Math.round(TICK_MILLIS), xProperty.get());
        assertEquals(0, yProperty.get());

        amt.pulse();

        assertEquals(Status.STOPPED, st.getStatus());
        assertEquals(Status.STOPPED, child1X.getStatus());
        assertEquals(Status.STOPPED, child1Y.getStatus());
        assertEquals(0, xProperty.get());
        assertEquals(0, yProperty.get());
    }

    @Test
    public void testPause() {
        
    }

    @Test
    public void testJumpAndPlay() {
        st.getChildren().addAll(child1X, child1Y);

        st.jumpTo(Duration.seconds(65));
        st.play();

        assertEquals(Status.RUNNING, st.getStatus());
        assertEquals(Status.STOPPED, child1X.getStatus());
        assertEquals(Status.RUNNING, child1Y.getStatus());
        assertEquals(60000, xProperty.get());
        assertEquals(5000, yProperty.get());


        amt.pulse();
        assertEquals(Status.RUNNING, st.getStatus());
        assertEquals(Status.STOPPED, child1X.getStatus());
        assertEquals(Status.RUNNING, child1Y.getStatus());
        assertEquals(60000, xProperty.get());
        assertEquals(5000 + Math.round(TICK_MILLIS), yProperty.get());

    }

    @Test
    public void testJumpAndPlayReversed() {
        st.getChildren().addAll(child1X, child1Y);
        st.setRate(-1.0);

        st.jumpTo(Duration.seconds(65));
        st.play();

        assertEquals(Status.RUNNING, st.getStatus());
        assertEquals(Status.STOPPED, child1X.getStatus());
        assertEquals(Status.RUNNING, child1Y.getStatus());
        assertEquals(60000, xProperty.get());
        assertEquals(5000, yProperty.get());


        amt.pulse();
        assertEquals(Status.RUNNING, st.getStatus());
        assertEquals(Status.STOPPED, child1X.getStatus());
        assertEquals(Status.RUNNING, child1Y.getStatus());
        assertEquals(60000, xProperty.get());
        assertEquals(5000 - Math.round(TICK_MILLIS), yProperty.get());

    }

    
    @Test
    public void testCycle() {
        st.getChildren().addAll(child1X, child1Y);
        st.setCycleCount(2);

        st.play();

        assertEquals(Status.RUNNING, st.getStatus());
        assertEquals(Status.STOPPED, child1X.getStatus());
        assertEquals(Status.STOPPED, child1Y.getStatus());
        assertEquals(0, xProperty.get());
        assertEquals(0, yProperty.get());

        st.jumpTo(Duration.minutes(1).add(Duration.seconds(10)).subtract(TickCalculation.toDuration(100)));

        assertEquals(Status.RUNNING, st.getStatus());
        assertEquals(Status.STOPPED, child1X.getStatus());
        assertEquals(Status.RUNNING, child1Y.getStatus());
        assertEquals(60000, xProperty.get());
        assertEquals(10000 - Math.round(TICK_MILLIS), yProperty.get());

        amt.pulse();

        assertEquals(Status.RUNNING, st.getStatus());
        assertEquals(Status.RUNNING, child1X.getStatus());
        assertEquals(Status.STOPPED, child1Y.getStatus());
        assertEquals(0, xProperty.get());
        assertEquals(0, yProperty.get());

        amt.pulse();

        assertEquals(TickCalculation.toDuration(100), st.getCurrentTime());
        assertEquals(TickCalculation.toDuration(100), child1X.getCurrentTime());
        assertEquals(Duration.ZERO, child1Y.getCurrentTime());
        assertEquals(Math.round(TICK_MILLIS), xProperty.get());
        assertEquals(0, yProperty.get());

        st.jumpTo(Duration.minutes(2).add(Duration.seconds(20)).subtract(TickCalculation.toDuration(100)));

        assertEquals(Status.RUNNING, st.getStatus());
        assertEquals(Status.STOPPED, child1X.getStatus());
        assertEquals(Status.RUNNING, child1Y.getStatus());
        assertEquals(60000, xProperty.get());
        assertEquals(10000 - Math.round(TICK_MILLIS), yProperty.get());

        amt.pulse();

        assertEquals(Status.STOPPED, st.getStatus());
        assertEquals(Status.STOPPED, child1X.getStatus());
        assertEquals(Status.STOPPED, child1Y.getStatus());
        assertEquals(60000, xProperty.get());
        assertEquals(10000, yProperty.get());

    }

    @Test
    public void testCycleReverse() {
        st.getChildren().addAll(child1X, child1Y);
        st.setCycleCount(-1);
        st.setRate(-1.0);

        st.play();

        assertEquals(Status.RUNNING, st.getStatus());
        assertEquals(Status.RUNNING, child1X.getStatus());
        assertEquals(Status.STOPPED, child1Y.getStatus());
        assertEquals(0, xProperty.get());
        assertEquals(0, yProperty.get());

        st.jumpTo(TickCalculation.toDuration(100));

        assertEquals(Status.RUNNING, st.getStatus());
        assertEquals(Status.RUNNING, child1X.getStatus());
        assertEquals(Status.STOPPED, child1Y.getStatus());
        assertEquals(Math.round(TICK_MILLIS), xProperty.get());
        assertEquals(0, yProperty.get());

        amt.pulse();

        assertEquals(Status.RUNNING, st.getStatus());
        assertEquals(Status.STOPPED, child1X.getStatus());
        assertEquals(Status.RUNNING, child1Y.getStatus());
        assertEquals(60000, xProperty.get());
        assertEquals(10000, yProperty.get());

        amt.pulse();

        assertEquals(Status.RUNNING, st.getStatus());
        assertEquals(Status.STOPPED, child1X.getStatus());
        assertEquals(Status.RUNNING, child1Y.getStatus());
        assertEquals(60000, xProperty.get());
        assertEquals(10000 - Math.round(TICK_MILLIS), yProperty.get());

        st.jumpTo(Duration.minutes(1).add(Duration.seconds(10)).subtract(TickCalculation.toDuration(100)));

        assertEquals(Status.RUNNING, st.getStatus());
        assertEquals(Status.STOPPED, child1X.getStatus());
        assertEquals(Status.RUNNING, child1Y.getStatus());
        assertEquals(60000, xProperty.get());
        assertEquals(10000 - Math.round(TICK_MILLIS), yProperty.get());

        amt.pulse();

        assertEquals(Status.RUNNING, st.getStatus());
        assertEquals(Status.STOPPED, child1X.getStatus());
        assertEquals(Status.RUNNING, child1Y.getStatus());
        assertEquals(60000, xProperty.get());
        assertEquals(10000 - Math.round(TickCalculation.toMillis(200)), yProperty.get());

    }

    @Test
    public void testJump() {
        st.getChildren().addAll(child1X, child1Y);

        assertEquals(Status.STOPPED, st.getStatus());
        assertEquals(Status.STOPPED, child1X.getStatus());
        assertEquals(Status.STOPPED, child1Y.getStatus());
        assertEquals(0, xProperty.get());
        assertEquals(0, yProperty.get());

        st.jumpTo(Duration.seconds(10));

        assertEquals(Status.STOPPED, st.getStatus());
        assertEquals(Status.STOPPED, child1X.getStatus());
        assertEquals(Status.STOPPED, child1Y.getStatus());
        assertEquals(0, xProperty.get());
        assertEquals(0, yProperty.get());

        st.play();

        assertEquals(Status.RUNNING, st.getStatus());
        assertEquals(Status.RUNNING, child1X.getStatus());  //Note: Not sure if we need to have also child1X running at this point
        assertEquals(Status.STOPPED, child1Y.getStatus());
        assertEquals(10000, xProperty.get());
        assertEquals(0, yProperty.get());

        amt.pulse();

        assertEquals(Status.RUNNING, st.getStatus());
        assertEquals(Status.RUNNING, child1X.getStatus());
        assertEquals(Status.STOPPED, child1Y.getStatus());
        assertEquals(10000 + Math.round(TICK_MILLIS), xProperty.get());
        assertEquals(0, yProperty.get());

        st.jumpTo(Duration.seconds(65));

        assertEquals(Status.RUNNING, st.getStatus());
        assertEquals(Status.STOPPED, child1X.getStatus());
        assertEquals(Status.RUNNING, child1Y.getStatus());
        assertEquals(60000, xProperty.get());
        assertEquals(5000, yProperty.get());

        st.jumpTo(Duration.seconds(10));

        assertEquals(Status.RUNNING, st.getStatus());
        assertEquals(Status.RUNNING, child1X.getStatus());
        assertEquals(Status.STOPPED, child1Y.getStatus());
        assertEquals(10000, xProperty.get());
        assertEquals(0, yProperty.get());

        st.stop();

        assertEquals(Status.STOPPED, st.getStatus());
        assertEquals(Status.STOPPED, child1X.getStatus());
        assertEquals(Status.STOPPED, child1Y.getStatus());
        assertEquals(10000, xProperty.get());
        assertEquals(0, yProperty.get());

    }

    @Test
    public void testAutoReverse() {
        st.getChildren().addAll(child1X, child1Y);
        st.setAutoReverse(true);
        st.setCycleCount(-1);

        st.play();

        for (int i = 0; i < TickCalculation.fromDuration(Duration.seconds(70)) / 100 - 1; ++i) {
            amt.pulse();
        }

        amt.pulse();

        assertEquals(Status.RUNNING, st.getStatus());
        assertEquals(Status.STOPPED, child1X.getStatus());
        assertEquals(Status.STOPPED, child1Y.getStatus());

        assertEquals(60000, xProperty.get());
        assertEquals(10000, yProperty.get());

        amt.pulse();

        assertEquals(Status.RUNNING, st.getStatus());
        assertEquals(Status.STOPPED, child1X.getStatus());
        assertEquals(Status.RUNNING, child1Y.getStatus());

        assertEquals(60000, xProperty.get());
        assertEquals(10000 - Math.round(TICK_MILLIS), yProperty.get());

    }

    @Test
    public void testAutoReverseWithJump() {
        st.getChildren().addAll(child1X, child1Y);
        st.setAutoReverse(true);
        st.setCycleCount(-1);

        st.play();

        st.jumpTo(Duration.seconds(70).subtract(TickCalculation.toDuration(100)));

        amt.pulse();

        assertEquals(Status.RUNNING, st.getStatus());
        assertEquals(Status.STOPPED, child1X.getStatus());
        assertEquals(Status.STOPPED, child1Y.getStatus());

        assertEquals(60000, xProperty.get());
        assertEquals(10000, yProperty.get());

        amt.pulse();

        assertEquals(Status.RUNNING, st.getStatus());
        assertEquals(Status.STOPPED, child1X.getStatus());
        assertEquals(Status.RUNNING, child1Y.getStatus());

        assertEquals(60000, xProperty.get());
        assertEquals(10000 - Math.round(TICK_MILLIS), yProperty.get());

    }

    @Test
    public void testChildWithDifferentRate() {
        st.getChildren().addAll(child1X, child1Y);
        child1X.setRate(2.0);

        st.play();

        amt.pulse();

        assertEquals(Math.round(TICK_MILLIS * 2), xProperty.get());

        st.jumpTo(Duration.seconds(30));

        assertEquals(60000, xProperty.get());
        assertEquals(0, yProperty.get());

        st.jumpTo(Duration.seconds(40));

        assertEquals(60000, xProperty.get());
        assertEquals(10000, yProperty.get());


        st.jumpTo(Duration.seconds(5));
        amt.pulse();

        st.setRate(-1.0);

        amt.pulse();
        amt.pulse();

        assertEquals(10000 - Math.round(TICK_MILLIS * 2), xProperty.get());
        assertEquals(0, yProperty.get());

        st.setRate(1.0);

        amt.pulse();
        amt.pulse();

        assertEquals(10000 + Math.round(TICK_MILLIS * 2), xProperty.get());
        assertEquals(0, yProperty.get());

    }

    @Test
    public void testToggleRate() {
        st.getChildren().addAll(child1X, child1Y);

        st.play();

        st.jumpTo(Duration.seconds(60));

        amt.pulse();

        assertEquals(Status.RUNNING, st.getStatus());
        assertEquals(Status.STOPPED, child1X.getStatus());
        assertEquals(Status.RUNNING, child1Y.getStatus());

        assertEquals(60000, xProperty.get());
        assertEquals(Math.round(TICK_MILLIS), yProperty.get());

        st.setRate(-1.0);

        amt.pulse();
        amt.pulse();

        assertEquals(Status.RUNNING, st.getStatus());
        assertEquals(Status.RUNNING, child1X.getStatus());
        assertEquals(Status.STOPPED, child1Y.getStatus());

        assertEquals(60000 - Math.round(TICK_MILLIS), xProperty.get());
        assertEquals(0, yProperty.get());

        st.setRate(1.0);

        amt.pulse();
        amt.pulse();

        assertEquals(Status.RUNNING, st.getStatus());
        assertEquals(Status.STOPPED, child1X.getStatus());
        assertEquals(Status.RUNNING, child1Y.getStatus());

        assertEquals(60000, xProperty.get());
        assertEquals(Math.round(TICK_MILLIS), yProperty.get());

    }

    @Test
    public void testToggleRate_2() {
        st.getChildren().addAll(child1X, child1Y);

        st.play();

        st.jumpTo(Duration.seconds(10));

        amt.pulse();

        assertEquals(Status.RUNNING, st.getStatus());
        assertEquals(Status.RUNNING, child1X.getStatus());
        assertEquals(Status.STOPPED, child1Y.getStatus());

        assertEquals(10000 + Math.round(TICK_MILLIS), xProperty.get());
        assertEquals(0, yProperty.get());

        st.setRate(-1.0);

        amt.pulse();
        amt.pulse();

        assertEquals(Status.RUNNING, st.getStatus());
        assertEquals(Status.RUNNING, child1X.getStatus());
        assertEquals(Status.STOPPED, child1Y.getStatus());

        assertEquals(10000 - Math.round(TICK_MILLIS), xProperty.get());
        assertEquals(0, yProperty.get());

        st.setRate(1.0);

        amt.pulse();
        amt.pulse();

        assertEquals(Status.RUNNING, st.getStatus());
        assertEquals(Status.RUNNING, child1X.getStatus());
        assertEquals(Status.STOPPED, child1Y.getStatus());

        assertEquals(10000 + Math.round(TICK_MILLIS), xProperty.get());
        assertEquals(0, yProperty.get());

    }
}
